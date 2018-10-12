package org.beyene.ledger.iota;

import com.google.common.cache.CacheBuilder;
import org.beyene.ledger.api.*;
import org.beyene.ledger.iota.TagChangeListener.TagChangeAction;
import org.beyene.ledger.iota.TagChangeListener.TagChangeEvent;
import org.beyene.ledger.iota.util.Iota;

import javax.swing.event.EventListenerList;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IotaLedger<M, D> implements Ledger<M, D> {

    private static final Logger LOGGER = Logger.getLogger(IotaLedger.class.getName());

    private final MessageSender<M> sender;
    private final Format<D> format;

    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService executorService;

    private final ConcurrentMap<String, TransactionListener<M>> listeners;
    private final EventListenerList tagListeners;

    private final List<Transaction<M>> messagesBeforePushThreshold;

    private IotaLedger(Builder<M, D> builder) {
        LOGGER.entering(IotaLedger.class.getSimpleName(), "IotaLedger()");

        Iota api = builder.api;
        this.sender = builder.sender;

        this.format = builder.format;
        this.listeners = new ConcurrentHashMap<>(builder.listeners);
        this.tagListeners = new EventListenerList();

        int poolSize = builder.poolThreads;
        if (poolSize == 0)
            poolSize = Runtime.getRuntime().availableProcessors();

        BlockingQueue<Transaction<jota.model.Transaction>> transactionQueue = new LinkedBlockingQueue<>();
        this.scheduledExecutor = Executors.newScheduledThreadPool(poolSize);

        // TODO
        // Use queue for old txs
        BlockingQueue<Transaction<jota.model.Transaction>> txsBeforePushThreshold = new LinkedBlockingQueue<>();
        this.messagesBeforePushThreshold = new CopyOnWriteArrayList<>();

        // Guava Cache is only LRU-ish
        Map<String, Boolean> knownHashes = CacheBuilder.newBuilder().maximumSize(builder.hashCacheSize).<String, Boolean>build().asMap();
        // refactor out?
        TransactionPoller txProducer = new TransactionPoller.Builder()
                .setApi(api)
                .setQueue(transactionQueue)
                .setTags(listeners.keySet())
                .setPushThreshold(builder.pushThreshold)
                .setSlidingWindow(Duration.ofMinutes(builder.slidingWindow))
                .setTransactionBeforePushThresholdConsumer(txsBeforePushThreshold::addAll)
                .setKnownHashesCache(knownHashes)
                .build();
        scheduledExecutor.scheduleWithFixedDelay(txProducer, 0, builder.pollDelayInterval, TimeUnit.MILLISECONDS);
        tagListeners.add(TagChangeListener.class, txProducer);

        BlockingQueue<Transaction<M>> messageQueue = new LinkedBlockingQueue<>();
        MessageParser<M, D> messageParser = new MessageParser.Builder<M, D>()
                .setMessageQueue(messageQueue)
                .setTransactionQueue(transactionQueue)
                .setTransactionsBeforePushThreshold(txsBeforePushThreshold)
                .setMessagesBeforePushThresholdConsumer(messagesBeforePushThreshold::addAll)
                .setFormat(format)
                .setKeepAliveInterval(Duration.ofMinutes(builder.keepFragmentsAliveMinutes))
                .setDeserializer(builder.deserializer)
                .build();

        long initialDelay = builder.pollDelayInterval / 2;
        scheduledExecutor.scheduleWithFixedDelay(messageParser, initialDelay, builder.pushDelayIntervall, TimeUnit.MILLISECONDS);
        tagListeners.add(TagChangeListener.class, messageParser);

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

        LOGGER.exiting(IotaLedger.class.getSimpleName(), "IotaLedger()");
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
        Objects.requireNonNull(listener);

        listeners.put(tag, listener);

        TagChangeEvent event = new TagChangeEvent(listeners.keySet(), tag, TagChangeAction.ADD);
        fireTagChanged(event);
        return true;
    }

    @Override
    public boolean removeTransactionListener(String tag) {
        Objects.requireNonNull(tag);

        listeners.remove(tag);
        // remove messages of unsubscribed tags
        messagesBeforePushThreshold.removeIf(tx -> tx.getTag().equals(tag));

        TagChangeEvent event = new TagChangeEvent(listeners.keySet(), tag, TagChangeAction.REMOVE);
        fireTagChanged(event);
        return true;
    }

    private void fireTagChanged(TagChangeEvent event) {
        Stream.of(tagListeners.getListeners(TagChangeListener.class)).forEach(l -> l.tagChanged(event));
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
        private Serializer<M, D> serializer;
        private Deserializer<M, D> deserializer;
        private Format<D> format;
        private Map<String, TransactionListener<M>> listeners;
        private int pollDelayInterval = 5_000; // in ms
        private int pushDelayIntervall = 5_000; // in ms
        private int poolThreads = 2;
        private int slidingWindow = 5; // in minutes
        private int hashCacheSize = 1_000;
        private int listenerThreads = 2;
        private int keepFragmentsAliveMinutes = 60;
        private Instant pushThreshold = Instant.now();

        public Builder<M, D>  setApi(Iota api) {
            this.api = api;
            return this;
        }

        public Builder<M, D>  setMessageSender(MessageSender<M> sender) {
            this.sender = sender;
            return this;
        }

        public Builder<M, D>  setSerializer(Serializer<M, D> serializer) {
            this.serializer = serializer;
            return this;
        }

        public Builder<M, D>  setDeserializer(Deserializer<M, D> deserializer) {
            this.deserializer = deserializer;
            return this;
        }

        public Builder<M, D>  setFormat(Format<D> format) {
            this.format = format;
            return this;
        }

        public Builder<M, D>  setListeners(Map<String, TransactionListener<M>> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder<M, D>  setPollInterval(int pollInterval) {
            this.pollDelayInterval = pollInterval;
            return this;
        }

        public Builder<M, D>  setPushIntervall(int pushIntervall) {
            this.pushDelayIntervall = pushIntervall;
            return this;
        }

        public Builder<M, D>  setPoolThreads(int poolThreads) {
            this.poolThreads = poolThreads;
            return this;
        }

        public Builder<M, D>  setSlidingWindow(int slidingWindow) {
            this.slidingWindow = slidingWindow;
            return this;
        }

        public Builder<M, D>  setHashCacheSize(int cacheSize) {
            this.hashCacheSize = cacheSize;
            return this;
        }

        public Builder<M, D>  setListenerThreads(int listenerThreads) {
            this.listenerThreads = listenerThreads;
            return this;
        }

        public Builder<M, D>  setKeepFragmentsAlive(int keepAliveMinutes) {
            this.keepFragmentsAliveMinutes = keepAliveMinutes;
            return this;
        }

        public Builder<M, D>  setPushThreshold(Instant pushThreshold) {
            Objects.requireNonNull(pushThreshold);
            this.pushThreshold = pushThreshold;
            return this;
        }

        public IotaLedger<M, D> build() {
            return new IotaLedger<>(this);
        }
    }
}
