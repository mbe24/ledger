package org.beyene.ledger.iota;

import org.beyene.ledger.api.*;
import org.beyene.ledger.iota.util.Iota;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class IotaLedger<M, D> implements Ledger<M, D> {

    private static final Logger LOGGER = Logger.getLogger(IotaLedger.class.getName());

    private final Iota api;
    private final MessageSender<M> sender;

    private final Format<D> format;

    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService executorService;
    private final ConcurrentMap<String, TransactionListener<M>> listeners;

    private final BlockingQueue<Transaction<jota.model.Transaction>> transactionQueue;
    private final BlockingQueue<Transaction<M>> messageQueue;

    private final Instant pushThreshold = Instant.now();
    private final List<Transaction<jota.model.Transaction>> txsBeforePushThreshold;
    private final List<Transaction<M>> messagesBeforePushThreshold;

    public IotaLedger(Builder builder) {
        this.api = builder.api;
        this.sender = builder.sender;

        this.format = builder.format;
        this.listeners = new ConcurrentHashMap<>(builder.listeners);

        int poolSize = builder.poolThreads;
        if (poolSize == 0)
            poolSize = Runtime.getRuntime().availableProcessors();

        this.transactionQueue = new LinkedBlockingQueue<>();
        this.scheduledExecutor = Executors.newScheduledThreadPool(poolSize);
        this.txsBeforePushThreshold = new CopyOnWriteArrayList<>();
        this.messagesBeforePushThreshold = new CopyOnWriteArrayList<>();

        // refactor out?
        TransactionPoller txProducer = new TransactionPoller.Builder()
                .setApi(api)
                .setQueue(transactionQueue)
                .setTags(listeners.keySet())
                .setPushThreshold(pushThreshold)
                .setSlidingWindow(Duration.ofMinutes(builder.slidingWindow))
                .setTransactionBeforePushThresholdConsumer(txsBeforePushThreshold::addAll)
                .setCacheSize(builder.hashCacheSize)
                .build();
        scheduledExecutor.scheduleWithFixedDelay(txProducer, 0, builder.pollDelayInterval, TimeUnit.MILLISECONDS);

        this.messageQueue = new LinkedBlockingQueue<>();
        MessageParser<M, D> messageParser = new MessageParser.Builder<M, D>()
                .setMessageQueue(messageQueue)
                .setTransactionQueue(transactionQueue)
                .setTransactionsBeforePushThreshold(txsBeforePushThreshold)
                .setMessagesBeforePushThreshold(messagesBeforePushThreshold)
                .setFormat(format)
                .setDeserializer(builder.mapper)
                .build();

        long initialDelay = builder.pollDelayInterval / 2;
        scheduledExecutor.scheduleWithFixedDelay(messageParser, initialDelay, builder.pushDelayIntervall, TimeUnit.MILLISECONDS);

        int listenerThreads = builder.listenerThreads;
        if (listenerThreads == 0)
            listenerThreads = Runtime.getRuntime().availableProcessors();
        this.executorService = Executors.newFixedThreadPool(listenerThreads);

        MessageDispatcher<M> dispatcher = new MessageDispatcher.Builder<M>()
                .setMessageQueue(messageQueue)
                .setMonitorContinuously(true)
                .setListeners(listeners)
                .setExecutorService(executorService)
                .build();
        scheduledExecutor.submit(dispatcher);
    }

    @Override
    public Transaction<M> addTransaction(Transaction<M> transaction) throws IOException {
        return sender.addTransaction(transaction);
    }

    @Override
    public List<Transaction<M>> getTransactions(Instant since, Instant to) {
        return messagesBeforePushThreshold
                .stream()
                .filter(tx -> tx.getTimestamp().isAfter(since))
                .filter(tx -> tx.getTimestamp().isBefore(to))
                .collect(Collectors.toList());
    }

    @Override
    public boolean addTransactionListener(String tag, TransactionListener<M> listener) {
        Objects.requireNonNull(tag);
        listeners.put(tag, listener);
        return true;
    }

    @Override
    public boolean removeTransactionListener(String tag) {
        listeners.remove(tag);
        return true;
    }

    @Override
    public Map<String, TransactionListener<M>> getTransactionListeners() {
        return new HashMap<>(listeners);
    }

    @Override
    public Format<D> getFormat() {
        return format;
    }

    @Override
    public void close() throws IOException {
        scheduledExecutor.shutdownNow();
        executorService.shutdownNow();
    }

    static class Builder<M, D> {

        private Iota api;
        private MessageSender<M> sender;
        private Mapper<M, D> mapper;
        private Format<D> format;
        private Map<String, TransactionListener<M>> listeners;
        private int pollDelayInterval = 5_000; // in ms
        private int pushDelayIntervall = 5_000; // in ms
        private int poolThreads = 2;
        private int slidingWindow = 5; // in minutes
        private int hashCacheSize = 1_000;
        private int listenerThreads = 2;

        public Builder setApi(Iota api) {
            this.api = api;
            return this;
        }

        public Builder setMessageSender(MessageSender<M> sender) {
            this.sender = sender;
            return this;
        }

        public Builder setMapper(Mapper<M, D> mapper) {
            this.mapper = mapper;
            return this;
        }

        public Builder setFormat(Format<D> format) {
            this.format = format;
            return this;
        }

        public Builder setListeners(Map<String, TransactionListener<M>> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder setPollInterval(int pollInterval) {
            this.pollDelayInterval = pollInterval;
            return this;
        }

        public Builder setPushIntervall(int pushIntervall) {
            this.pushDelayIntervall = pushIntervall;
            return this;
        }

        public Builder setPoolThreads(int poolThreads) {
            this.poolThreads = poolThreads;
            return this;
        }

        public Builder setSlidingWindow(int slidingWindow) {
            this.slidingWindow = slidingWindow;
            return this;
        }

        public Builder setHashCacheSize(int cacheSize) {
            this.hashCacheSize = cacheSize;
            return this;
        }

        public Builder setListenerThreads(int listenerThreads) {
            this.listenerThreads = listenerThreads;
            return this;
        }

        public IotaLedger build() {
            return new IotaLedger(this);
        }
    }
}
