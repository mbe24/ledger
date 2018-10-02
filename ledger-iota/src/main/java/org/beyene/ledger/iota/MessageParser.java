package org.beyene.ledger.iota;

import jota.utils.TrytesConverter;
import org.apache.commons.lang3.StringUtils;
import org.beyene.ledger.api.Deserializer;
import org.beyene.ledger.api.Format;
import org.beyene.ledger.api.Mapper.MappingException;
import org.beyene.ledger.api.Transaction;

import javax.xml.bind.DatatypeConverter;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MessageParser<M, D> implements Runnable, TagChangeListener {

    private static final Logger LOGGER = Logger.getLogger(MessageParser.class.getName());

    private final BlockingQueue<Transaction<M>> messageQueue;
    private final BlockingQueue<Transaction<jota.model.Transaction>> transactionQueue;
    private final BlockingQueue<Transaction<jota.model.Transaction>> txsBeforePushThreshold;
    private final Consumer<Collection<Transaction<M>>> oldMessageConsumer;

    // use set to avoid duplicates
    private final KeySetView<Transaction<jota.model.Transaction>, Boolean> inWork;
    private final Duration keepAliveInterval;

    private final Format<D> format;
    private final Deserializer<M, D> deserializer;

    private final Comparator<Transaction<jota.model.Transaction>> comparator;

    // concurrent sorted set
    // ConcurrentSkipListSet
    // could be used for tx handling OR map by bundle hash

    public MessageParser(Builder<M, D> builder) {
        this.messageQueue = builder.messageQueue;
        this.transactionQueue = builder.transactionQueue;
        this.txsBeforePushThreshold = builder.txsBeforePushThreshold;
        this.oldMessageConsumer = builder.oldMessageConsumer;
        this.format = builder.format;
        this.inWork = ConcurrentHashMap.newKeySet();
        this.keepAliveInterval = builder.keepAliveInterval;
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
        boolean hasOldTxs = !txsBeforePushThreshold.isEmpty();
        if (hasOldTxs) {
            List<Transaction<jota.model.Transaction>> txs = new ArrayList<>();
            txsBeforePushThreshold.drainTo(txs);
            handleMessages(txs, oldMessageConsumer);
        }

        if (transactionQueue.isEmpty())
            return;

        List<Transaction<jota.model.Transaction>> batch = getCurrentBatch();
        handleMessages(batch, messageQueue::addAll);
    }

    @Override
    public void tagChanged(TagChangeEvent e) {
        switch (e.getAction()) {
            case ADD:
                // to nothing -- old txs queue handles case
                break;
            case REMOVE:
                // remove tx with tags from 'in work' queue
                // or leave them for clean up
                break;
        }
    }

    private void handleMessages(Collection<Transaction<jota.model.Transaction>> batch,
                                Consumer<Collection<Transaction<M>>> messageConsumer) {
        Map<String, List<Transaction<jota.model.Transaction>>> bundles = groupByBundles(batch);
        // extracts and defragments messages, only leaves incomplete ones in map
        List<Transaction<String>> messagesRaw = defragmentRawMessages(bundles);
        dropOldFragments();
        aggregateUnprocessedTransactions(bundles);
        processMessages(messagesRaw, messageConsumer);
    }

    private Map<String, List<Transaction<jota.model.Transaction>>> groupByBundles(Collection<Transaction<jota.model.Transaction>> batch) {
        return batch
                .stream()
                .collect(Collectors.groupingBy(tx -> tx.getObject().getBundle(),
                        // use linked hash map to preserver order
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private void dropOldFragments() {
        Instant dropTime = Instant.now().minus(keepAliveInterval);
        inWork.removeIf(tx -> tx.getTimestamp().isBefore(dropTime));
    }

    private void aggregateUnprocessedTransactions(Map<String, List<Transaction<jota.model.Transaction>>> bundles) {
        List<Transaction<jota.model.Transaction>> unprocessed = bundles.values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        inWork.addAll(unprocessed);
    }

    private void processMessages(List<Transaction<String>> messagesRaw,
                                 Consumer<Collection<Transaction<M>>> messageConsumer) {
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
                object = DatatypeConverter.parseHexBinary(hexBinary);
            } else {
                throw new IllegalStateException("Unsupported data type: " + format.getType().getName());
            }

            D data = format.getType().cast(object);

            try {
                M message = deserializer.deserialize(data);
                String tag = StringUtils.stripEnd(tx.getTag(), "9");
                Transaction<M> mtx = new MessageTransaction<>(tx.getIdentifier(), tx.getTimestamp(), tag, message);
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
        private BlockingQueue<Transaction<jota.model.Transaction>> txsBeforePushThreshold;
        private Consumer<Collection<Transaction<M>>> oldMessageConsumer;
        private Format<D> format;
        private Duration keepAliveInterval;
        private Deserializer<M, D> deserializer;

        public Builder<M, D> setMessageQueue(BlockingQueue<Transaction<M>> queue) {
            this.messageQueue = queue;
            return this;
        }

        public Builder<M, D> setTransactionQueue(BlockingQueue<Transaction<jota.model.Transaction>> queue) {
            this.transactionQueue = queue;
            return this;
        }

        public Builder<M, D> setTransactionsBeforePushThreshold(BlockingQueue<Transaction<jota.model.Transaction>> txsBeforePushThreshold) {
            this.txsBeforePushThreshold = txsBeforePushThreshold;
            return this;
        }

        public Builder<M, D> setMessagesBeforePushThresholdConsumer(Consumer<Collection<Transaction<M>>> consumer) {
            this.oldMessageConsumer = consumer;
            return this;
        }

        public Builder<M, D> setFormat(Format<D> format) {
            this.format = format;
            return this;
        }

        public Builder<M, D> setKeepAliveInterval(Duration keepAliveInterval) {
            this.keepAliveInterval = keepAliveInterval;
            return this;
        }

        public Builder<M, D> setDeserializer(Deserializer<M, D> deserializer) {
            this.deserializer = deserializer;
            return this;
        }

        public MessageParser<M, D> build() {
            return new MessageParser<>(this);
        }

    }

}
