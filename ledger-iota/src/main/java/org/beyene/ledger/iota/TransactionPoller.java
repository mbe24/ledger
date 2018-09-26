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
        this.slidingWindowSize = builder.slidingWindow;
    }

    @Override
    public void run() {
        if (tags.isEmpty())
            return;

        List<jota.model.Transaction> transactions;
        try {
            transactions = readTxsFromTangle();
        } catch (ArgumentException e) {
            LOGGER.log(Level.INFO, e.toString(), e);
            // handle or rethrow?
            return;
        }

        Map<Boolean, List<jota.model.Transaction>> partition = partitionTxsByPushThreshold(transactions);
        List<jota.model.Transaction> newTransactions = processNewTransactions(partition);
        // NOTE newTransactions still has duplicates, unique txs are already forwarded to queue
        updatePushThreshold(newTransactions);

        List<jota.model.Transaction> oldTxs = partition.getOrDefault(Boolean.FALSE, Collections.emptyList());
        processOldTransactions(oldTxs);
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

    private List<jota.model.Transaction> readTxsFromTangle() throws ArgumentException {
        String[] array = this.tags.stream().toArray(String[]::new);
        return api.findTransactionObjectsByTag(array);
    }

    private Map<Boolean, List<jota.model.Transaction>> partitionTxsByPushThreshold(List<jota.model.Transaction> transactions) {
        return transactions.stream()
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

        Collections.sort(txsBeforePush, comparator);
        txsBeforePush.forEach(tx -> knownHashes.put(tx.getHash(), Boolean.TRUE));
        // use dedicated cache with no size restriction to guarantee that ols tx are unique
        Set<String> oldTxCache = new HashSet<>();

        List<Transaction<jota.model.Transaction>> oldTxs = txsBeforePush.stream()
                .sequential()
                .filter(tx -> !oldTxCache.contains(tx.getHash()))
                .peek(tx -> oldTxCache.add(tx.getHash()))
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
        private Duration slidingWindow;
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

        public Builder setSlidingWindow(Duration slidingWindow) {
            this.slidingWindow = slidingWindow;
            return this;
        }

        public Builder setTransactionBeforePushThresholdConsumer(
                Consumer<Collection<Transaction<jota.model.Transaction>>> oldTxsConsumer) {
            this.oldTxsConsumer = oldTxsConsumer;
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
