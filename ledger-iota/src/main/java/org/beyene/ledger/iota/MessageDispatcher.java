package org.beyene.ledger.iota;

import org.beyene.ledger.api.Transaction;
import org.beyene.ledger.api.TransactionListener;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageDispatcher<M> implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(MessageDispatcher.class.getName());

    private final BlockingQueue<Transaction<M>> queue;
    private final ConcurrentMap<String, TransactionListener<M>> listeners;
    private final boolean monitorContinuously;
    private ExecutorService executorService;

    public MessageDispatcher(Builder builder) {
        this.queue = builder.queue;
        this.listeners = builder.listeners;
        this.monitorContinuously = builder.monitorContinuously;
        this.executorService = builder.executorService;
    }

    @Override
    public void run() {

        do {
            Transaction<M> tx;

            if (monitorContinuously) {
                try {
                    tx = queue.take();
                } catch (InterruptedException e) {
                    break;
                }
            } else {
                tx = queue.poll();
            }

            TransactionListener<M> listener = listeners.get(tx.getTag());
            if (Objects.nonNull(listener)) {
                executorService.submit(() -> listener.onTransaction(tx));
            } else {
                LOGGER.log(Level.INFO, String.format("There is no listener defined for tag: %s", tx.getTag()));
            }

        } while (monitorContinuously);
    }

    static class Builder<M> {

        private BlockingQueue<Transaction<M>> queue;
        private ConcurrentMap<String, TransactionListener<M>> listeners;
        private boolean monitorContinuously;
        private ExecutorService executorService;

        public Builder setMessageQueue(BlockingQueue<Transaction<M>> queue) {
            this.queue = queue;
            return this;
        }

        public Builder setListeners(ConcurrentMap<String, TransactionListener<M>> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder setMonitorContinuously(boolean monitorContinuously) {
            this.monitorContinuously = monitorContinuously;
            return this;
        }

        public Builder setExecutorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public MessageDispatcher build() {
            return new MessageDispatcher(this);
        }

    }
}
