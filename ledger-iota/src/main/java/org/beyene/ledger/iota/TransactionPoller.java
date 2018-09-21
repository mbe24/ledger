package org.beyene.ledger.iota;

import com.google.common.cache.CacheBuilder;
import jota.error.ArgumentException;
import org.beyene.ledger.api.Transaction;
import org.beyene.ledger.iota.util.Iota;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TransactionPoller implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(TransactionPoller.class.getName());

    private final Iota api;
    private final BlockingQueue<Transaction<jota.model.Transaction>> queue;
    private final Set<String> tags;
    private final Map<String, Boolean> knownHashes;

    // grows with every call
    private final AtomicReference<Instant> pushThreshold;
    private final Duration slidingWindowSize;

    // old txs get saved exactly once
    private final AtomicBoolean isFirstRun = new AtomicBoolean(false);
    private final Consumer<Collection<Transaction<jota.model.Transaction>>> oldTxsConsumer;

    private final Comparator<jota.model.Transaction> comparator;

    public TransactionPoller(Builder builder) {
        this.api = builder.api;
        this.queue = builder.queue;
        this.tags = builder.tags;
        this.pushThreshold = new AtomicReference<>(builder.pushThreshold);
        this.oldTxsConsumer = builder.oldTxsConsumer;
        this.comparator = Comparator.comparing(jota.model.Transaction::getTimestamp)
                .thenComparing(jota.model.Transaction::getBundle)
                .thenComparing(jota.model.Transaction::getCurrentIndex);
        this.knownHashes = CacheBuilder.newBuilder().maximumSize(builder.cacheSize).<String, Boolean>build().asMap();
        this.slidingWindowSize = Duration.ofMinutes(builder.slidingWindow);
    }

    @Override
    public void run() {
        if (tags.isEmpty())
            return;

        try {
            List<jota.model.Transaction> transactions = readTxsFromTangle();

            Map<Boolean, List<jota.model.Transaction>> partition = partitionTxsByPushThreshold(transactions);
            List<jota.model.Transaction> newTransactions = processNewTransactions(partition);
            updatePushThreshold(newTransactions);

            processOldTransactions(partition.get(Boolean.FALSE));
        } catch (ArgumentException e) {
            LOGGER.log(Level.INFO, e.toString(), e);
        }
    }

    private List<jota.model.Transaction> processNewTransactions(Map<Boolean, List<jota.model.Transaction>> partition) {
        List<jota.model.Transaction> newTransactions = partition.get(Boolean.TRUE);
        Collections.sort(newTransactions, comparator);
        newTransactions.forEach(tx -> knownHashes.put(tx.getHash(), Boolean.TRUE));
        newTransactions.stream().map(TransactionDecorator::new).forEach(queue::add);
        return newTransactions;
    }

    private List<jota.model.Transaction> readTxsFromTangle() throws ArgumentException {
        String[] array = this.tags.stream().toArray(String[]::new);
        return api.findTransactionObjectsByTag(array);
    }

    private Map<Boolean, List<jota.model.Transaction>> partitionTxsByPushThreshold(List<jota.model.Transaction> transactions) {
        return transactions
                .stream()
                .filter(tx -> tx.getTimestamp() > 0)
                .filter(tx -> !knownHashes.containsKey(tx.getHash()))
                .collect(Collectors
                        .groupingBy(tx -> Instant.ofEpochMilli(tx.getTimestamp())
                                .isAfter(pushThreshold.get().minus(slidingWindowSize))));
    }

    private void updatePushThreshold(List<jota.model.Transaction> newTransactions) {
        if (!newTransactions.isEmpty()) {
            int lastIndex = newTransactions.size() - 1;
            Instant nextThreshold = Instant.ofEpochMilli(newTransactions.get(lastIndex).getTimestamp());
            pushThreshold.set(nextThreshold);
        }
    }

    private void processOldTransactions(List<jota.model.Transaction> txsBeforePush) {
        boolean firstRun = isFirstRun.compareAndSet(false, true);
        if (!firstRun)
            return;

        txsBeforePush.forEach(tx -> knownHashes.put(tx.getHash(), Boolean.TRUE));
        List<Transaction<jota.model.Transaction>> oldTxs = txsBeforePush
                .stream()
                .map(TransactionDecorator::new)
                .collect(Collectors.toList());
        oldTxsConsumer.accept(oldTxs);
    }

    static class Builder {

        private Iota api;
        private BlockingQueue<org.beyene.ledger.api.Transaction<jota.model.Transaction>> queue;
        private Set<String> tags;
        private Instant pushThreshold;
        private Consumer<Collection<Transaction<jota.model.Transaction>>> oldTxsConsumer;
        private int slidingWindow;
        private int cacheSize;

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

        public Builder setTransactionBeforePushThresholdConsumer(
                Consumer<Collection<Transaction<jota.model.Transaction>>> oldTxsConsumer) {
            this.oldTxsConsumer = oldTxsConsumer;
            return this;
        }

        public Builder setSlidingWindow(int slidingWindow) {
            this.slidingWindow = slidingWindow;
            return this;
        }

        public Builder setCacheSize(int cacheSize) {
            this.cacheSize = cacheSize;
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
            return Instant.ofEpochMilli(delegate.getTimestamp());
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
