package org.beyene.ledger.iota;

import jota.error.ArgumentException;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.beyene.ledger.api.Transaction;
import org.beyene.ledger.iota.util.Iota;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TransactionPoller implements Runnable, TagChangeListener {

    private static final Logger LOGGER = Logger.getLogger(TransactionPoller.class.getName());

    private final Iota api;
    private final BlockingQueue<Transaction<jota.model.Transaction>> queue;
    private final Set<String> tags;
    private final Set<String> oldTxTags;
    private final Map<String, Boolean> knownHashes;

    // grows with every call
    private final AtomicReference<Instant> pushThreshold;
    private final Duration slidingWindowSize;

    // old txs get saved exactly once
    // old/new is relative to time of tag subscription
    private final AtomicBoolean processOldTxs;
    private final Consumer<Collection<Transaction<jota.model.Transaction>>> oldTxsConsumer;

    private final Comparator<jota.model.Transaction> comparator;

    private TransactionPoller(Builder builder) {
        this.api = builder.api;
        this.queue = builder.queue;

        this.tags = builder.tags;
        this.oldTxTags = ConcurrentHashMap.newKeySet();
        oldTxTags.addAll(tags);

        this.pushThreshold = new AtomicReference<>(builder.pushThreshold);
        this.processOldTxs = new AtomicBoolean(true);
        this.oldTxsConsumer = builder.oldTxsConsumer;
        this.comparator = Comparator.comparing(jota.model.Transaction::getTimestamp)
                .thenComparing(jota.model.Transaction::getBundle)
                .thenComparing(jota.model.Transaction::getCurrentIndex);
        this.knownHashes = builder.knownHashes;
        this.slidingWindowSize = builder.slidingWindow;
    }

    @Override
    public void run() {
        if (tags.isEmpty())
            return;

        // (a) complete and slow supplier (HTTP API)
        // (b) incomplete/delta and fast/push supplier (ZMQ API)
        // after change of tags use complete
        Supplier<List<jota.model.Transaction>> txSupplier = this::readTxsFromTangle;
        List<jota.model.Transaction> transactions = txSupplier.get();
        if (transactions.isEmpty())
            return;

        Map<Boolean, List<jota.model.Transaction>> partition = partitionTxsByPushThreshold(transactions);
        List<jota.model.Transaction> newTransactions = processNewTransactions(partition);
        // NOTE newTransactions still has duplicates, unique txs are already forwarded to queue
        updatePushThreshold(newTransactions);

        boolean skipOldTxs = !processOldTxs.compareAndSet(true, false);
        if (!skipOldTxs) {
            List<jota.model.Transaction> oldTxs = partition.getOrDefault(Boolean.FALSE, Collections.emptyList());
            processOldTransactions(oldTxs);
        }
    }

    @Override
    public void tagChanged(TagChangeEvent e) {
        if (e.getAction() == TagChangeAction.ADD) {
            oldTxTags.add(e.getTag());
            processOldTxs.compareAndSet(false, true);
        }
    }

    private List<jota.model.Transaction> processNewTransactions(Map<Boolean, List<jota.model.Transaction>> partition) {
        List<jota.model.Transaction> newTransactions = partition.getOrDefault(Boolean.TRUE, Collections.emptyList());
        Collections.sort(newTransactions, comparator);
        newTransactions.stream()
                .sequential()
                .filter(tx -> !knownHashes.containsKey(tx.getHash()))
                .peek(tx -> knownHashes.put(tx.getHash(), Boolean.TRUE))
                .map(TransactionDecorator::new).forEach(queue::add);
        return newTransactions;
    }

    private List<jota.model.Transaction> readTxsFromTangle() {
        String[] currentTags = this.tags.stream().toArray(String[]::new);

        List<jota.model.Transaction> txs;
        try {
            txs = api.findTransactionObjectsByTag(currentTags);
        } catch (ArgumentException e) {
            // TODO
            // Handle exception
            txs = Collections.emptyList();
            LOGGER.log(Level.INFO, e.toString(), e);
        }
        return txs;
    }

    private Map<Boolean, List<jota.model.Transaction>> partitionTxsByPushThreshold(
            List<jota.model.Transaction> transactions) {
        Instant reference = pushThreshold.get().minus(slidingWindowSize);
        return transactions.stream()
                .filter(tx -> tx.getTimestamp() > 0)
                .filter(tx -> !knownHashes.containsKey(tx.getHash()))
                .collect(Collectors.groupingBy(tx -> Instant.ofEpochMilli(1_000 * tx.getTimestamp()).isAfter(reference)));
    }

    private void updatePushThreshold(List<jota.model.Transaction> newTransactions) {
        if (!newTransactions.isEmpty()) {
            int lastIndex = newTransactions.size() - 1;
            Instant currentMax = Instant.ofEpochMilli(newTransactions.get(lastIndex).getTimestamp());
            Instant previous = pushThreshold.get();
            pushThreshold.set(ObjectUtils.max(currentMax, previous));
        }
    }

    private void processOldTransactions(List<jota.model.Transaction> txs) {
        txs.removeIf(tx -> !oldTxTags.contains(StringUtils.stripEnd(tx.getTag(), "9")));
        Collections.sort(txs, comparator);

        List<Transaction<jota.model.Transaction>> oldTxs = txs.stream()
                .sequential()
                .filter(tx -> !knownHashes.containsKey(tx.getHash()))
                .peek(tx -> knownHashes.put(tx.getHash(), Boolean.TRUE))
                .map(TransactionDecorator::new)
                .collect(Collectors.toList());

        oldTxsConsumer.accept(oldTxs);

        // old tags are processed once, delete tags afterwards
        oldTxTags.clear();
    }

    static class Builder {

        private Iota api;
        private BlockingQueue<org.beyene.ledger.api.Transaction<jota.model.Transaction>> queue;
        private Set<String> tags;
        private Instant pushThreshold;
        private Consumer<Collection<Transaction<jota.model.Transaction>>> oldTxsConsumer;
        private Duration slidingWindow;
        private Map<String, Boolean> knownHashes;

        public Builder setApi(Iota api) {
            this.api = api;
            return this;
        }

        public Builder setQueue(BlockingQueue<org.beyene.ledger.api.Transaction<jota.model.Transaction>> queue) {
            this.queue = queue;
            return this;
        }

        public Builder setTags(Set<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder setPushThreshold(Instant pushThreshold) {
            this.pushThreshold = pushThreshold;
            return this;
        }

        public Builder setSlidingWindow(Duration slidingWindow) {
            this.slidingWindow = slidingWindow;
            return this;
        }

        public Builder setTransactionBeforePushThresholdConsumer(
                Consumer<Collection<Transaction<jota.model.Transaction>>> oldTxsConsumer) {
            this.oldTxsConsumer = oldTxsConsumer;
            return this;
        }

        public Builder setKnownHashesCache(Map<String, Boolean> knownHashes) {
            this.knownHashes = knownHashes;
            return this;
        }

        public TransactionPoller build() {
            return new TransactionPoller(this);
        }
    }

    private static class TransactionDecorator implements Transaction<jota.model.Transaction> {

        private final jota.model.Transaction delegate;

        public TransactionDecorator(jota.model.Transaction delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TransactionDecorator)) return false;

            TransactionDecorator that = (TransactionDecorator) o;
            return delegate.getHash().equals(that.delegate.getHash());

        }

        @Override
        public int hashCode() {
            return delegate.getHash().hashCode();
        }

        @Override
        public String getIdentifier() {
            return delegate.getAddress();
        }

        @Override
        public Instant getTimestamp() {
            return Instant.ofEpochMilli(1_000 * delegate.getTimestamp());
        }

        @Override
        public String getTag() {
            return delegate.getTag();
        }

        @Override
        public jota.model.Transaction getObject() {
            return delegate;
        }
    }

}
