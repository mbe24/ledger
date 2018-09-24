package org.beyene.ledger.iota;

import jota.utils.TrytesConverter;
import org.apache.commons.lang3.StringUtils;
import org.beyene.ledger.api.Deserializer;
import org.beyene.ledger.api.Format;
import org.beyene.ledger.api.Mapper.MappingException;
import org.beyene.ledger.api.Transaction;

import javax.xml.bind.DatatypeConverter;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MessageParser<M, D> implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(MessageParser.class.getName());

    private final AtomicBoolean isFirstRun = new AtomicBoolean(false);

    private final BlockingQueue<Transaction<M>> messageQueue;
    private final BlockingQueue<Transaction<jota.model.Transaction>> transactionQueue;
    private final List<Transaction<jota.model.Transaction>> txsBeforePushThreshold;
    private final List<Transaction<M>> oldMessages;;

    // use set to avoid duplicates
    private final KeySetView<Transaction<jota.model.Transaction>, Boolean> inWork;

    private final Format<D> format;
    private final Deserializer<M, D> deserializer;

    private final Comparator<Transaction<jota.model.Transaction>> comparator;

    // concurrent sorted set
    // ConcurrentSkipListSet
    // could be used for tx handling OR map by bundle hash

    public MessageParser(Builder builder) {
        this.messageQueue = builder.messageQueue;
        this.transactionQueue = builder.transactionQueue;
        this.txsBeforePushThreshold = builder.txsBeforePushThreshold;
        this.oldMessages = builder.oldMessages;
        this.format = builder.format;
        this.inWork = ConcurrentHashMap.newKeySet();
        this.deserializer = builder.deserializer;

        // TODO
        // share comparator within transaction poller
        Comparator<jota.model.Transaction> cmp = Comparator.comparing(jota.model.Transaction::getTimestamp)
                .thenComparing(jota.model.Transaction::getBundle)
                .thenComparing(jota.model.Transaction::getCurrentIndex);

        this.comparator = (lhs, rhs) -> cmp.compare(lhs.getObject(), rhs.getObject());
    }

    @Override
    public void run() {
        // handle old transactions
        boolean firstRun = isFirstRun.compareAndSet(false, true);
        if (firstRun) {
            handleMessages(txsBeforePushThreshold, oldMessages::addAll);
        }

        if (transactionQueue.isEmpty())
            return;

        List<Transaction<jota.model.Transaction>> batch = getCurrentBatch();
        handleMessages(batch, messageQueue::addAll);
    }

    private void handleMessages(List<Transaction<jota.model.Transaction>> batch,
                                Consumer<Collection<? extends Transaction<M>>> messageConsumer) {
        Map<String, List<Transaction<jota.model.Transaction>>> bundles = groupByBundles(batch);
        List<Transaction<String>> messagesRaw = defragmentRawMessages(bundles);
        aggregateUnprocessedTransactions(bundles);
        processMessages(messagesRaw, messageConsumer);
    }

    private void aggregateUnprocessedTransactions(Map<String, List<Transaction<jota.model.Transaction>>> bundles) {
        List<Transaction<jota.model.Transaction>> unprocessed = bundles.values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        inWork.addAll(unprocessed);
    }

    private void processMessages(List<Transaction<String>> messagesRaw,
                                 Consumer<Collection<? extends Transaction<M>>> messageConsumer) {
        List<Transaction<M>> messages = new ArrayList<>(messagesRaw.size());

        for (Transaction<String> tx : messagesRaw) {
            Object object;
            // refactor out: 'preformat' data with lamda of Function<String, D>
            // BUT that presupposes backend represents data as strings
            // SO don't to anything for now - or use higher abstractions, if possible
            if (String.class.isAssignableFrom(format.getType())) {
                String trytesTrimmed = StringUtils.stripEnd(tx.getObject(), "9");
                object = jota.utils.TrytesConverter.toString(trytesTrimmed);
            } else if (byte[].class.isAssignableFrom(format.getType())) {
                // bytes are encoded as hex strings,
                // since byte to trits conversion is incomplete (values 243-255 are not supported)
                // cf. https://iota.stackexchange.com/questions/2159/converting-bytes-to-trites-using-iota-libraries

                // TODO
                // already strip 9s off trytes on concatenation in order to avoid copying everything here
                String trytesTrimmed = StringUtils.stripEnd(tx.getObject(), "9");
                String hexBinary = TrytesConverter.toString(trytesTrimmed);
                byte[] bytes = DatatypeConverter.parseHexBinary(hexBinary);
                object = bytes;
            } else {
                throw new IllegalStateException("Unsupported data type: " + format.getType().getName());
            }

            D data = format.getType().cast(object);

            try {
                M message = deserializer.deserialize(data);
                Transaction<M> mtx = new MessageTransaction<>(tx.getIdentifier(), tx.getTimestamp(), tx.getTag(), message);
                messages.add(mtx);
            } catch (MappingException e) {
                // possible that non-conforming clients wrote to tag
                // just log and ignore
                LOGGER.log(Level.INFO, e.toString(), e);
            }

        }

        messageConsumer.accept(messages);
    }

    private List<Transaction<String>> defragmentRawMessages(Map<String, List<Transaction<jota.model.Transaction>>> bundles) {
        List<Transaction<String>> defragmented = new ArrayList<>();

        Iterator<Map.Entry<String, List<Transaction<jota.model.Transaction>>>> it = bundles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, List<Transaction<jota.model.Transaction>>> entry = it.next();
            List<Transaction<jota.model.Transaction>> bundle = entry.getValue();
            Transaction<jota.model.Transaction> tx = bundle.get(0);

            // zero-based bundle index
            boolean bundleComplete = tx.getObject().getLastIndex() == bundle.size() - 1;
            if (bundleComplete) {
                String bundleTrytes = bundle
                        .stream()
                        .map(Transaction::getObject)
                        .map(jota.model.Transaction::getSignatureFragments)
                        .collect(Collectors.joining());
                defragmented.add(new RawTransaction(tx.getIdentifier(), tx.getTimestamp(), tx.getTag(), bundleTrytes));
                it.remove();
            }
        }

        return defragmented;
    }

    private Map<String, List<Transaction<jota.model.Transaction>>> groupByBundles(List<Transaction<jota.model.Transaction>> batch) {
        return batch
                .stream()
                .collect(Collectors.groupingBy(tx -> tx.getObject().getBundle(),
                        // use linked hash map to preserver order
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private List<Transaction<jota.model.Transaction>> getCurrentBatch() {
        List<Transaction<jota.model.Transaction>> batch = new ArrayList<>();
        transactionQueue.drainTo(batch);
        batch.addAll(inWork);
        Collections.sort(batch, comparator);
        return batch;
    }

    private static class RawTransaction implements Transaction<String> {

        private final String id;

        private final Instant timestamp;

        private final String tag;

        private final String message;

        public RawTransaction(String id, Instant timestamp, String tag, String message) {
            this.id = id;
            this.timestamp = timestamp;
            this.tag = tag;
            this.message = message;
        }

        @Override
        public String getIdentifier() {
            return id;
        }

        @Override
        public Instant getTimestamp() {
            return timestamp;
        }

        @Override
        public String getTag() {
            return tag;
        }

        @Override
        public String getObject() {
            return message;
        }
    }

    static class Builder<M, D> {

        private BlockingQueue<Transaction<M>> messageQueue;
        private BlockingQueue<Transaction<jota.model.Transaction>> transactionQueue;
        private List<Transaction<jota.model.Transaction>> txsBeforePushThreshold;
        private List<Transaction<M>> oldMessages;
        private Format<D> format;
        private Deserializer<M, D> deserializer;

        public Builder setMessageQueue(BlockingQueue<Transaction<M>> queue) {
            this.messageQueue = queue;
            return this;
        }

        public Builder setTransactionQueue(BlockingQueue<Transaction<jota.model.Transaction>> queue) {
            this.transactionQueue = queue;
            return this;
        }

        public Builder setTransactionsBeforePushThreshold(List<Transaction<jota.model.Transaction>> txsBeforePushThreshold) {
            this.txsBeforePushThreshold = txsBeforePushThreshold;
            return this;
        }

        public Builder setMessagesBeforePushThreshold(List<Transaction<M>> messages) {
            this.oldMessages = messages;
            return this;
        }

        public Builder setFormat(Format<D> format) {
            this.format = format;
            return this;
        }

        public Builder setDeserializer(Deserializer<M, D> deserializer) {
            this.deserializer = deserializer;
            return this;
        }

        public MessageParser build() {
            return new MessageParser(this);
        }

    }

}
