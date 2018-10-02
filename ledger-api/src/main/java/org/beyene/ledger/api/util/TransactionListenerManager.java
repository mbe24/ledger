package org.beyene.ledger.api.util;

import org.beyene.ledger.api.TransactionListener;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Use for managing tags and listeners. Try to combine with ScheduledExecutorService
public class TransactionListenerManager<M> {

    private final Map<String, List<TransactionListener<M>>> listeners;

    public TransactionListenerManager(Map<String, List<TransactionListener<M>>> listeners) {
        this.listeners = new ConcurrentHashMap<>();
        listeners.forEach((k, v) -> this.listeners.put(k, new CopyOnWriteArrayList<>(v)));
    }

    public TransactionListenerManager() {
        this(Collections.emptyMap());
    }

    public boolean addTransactionListener(String tag, TransactionListener<M> listener) {
        List<TransactionListener<M>> tagListeners = listeners.computeIfAbsent(tag, k -> new CopyOnWriteArrayList<>());
        return tagListeners.add(listener);
    }

    public boolean removeTransactionListener(String tag, TransactionListener<M> listener) {
        List<TransactionListener<M>> tagListeners = listeners.computeIfAbsent(tag, k -> new CopyOnWriteArrayList<>());
        return tagListeners.remove(listener);
    }

    public List<TransactionListener<M>> getTransactionListenersByTag(String tag) {
        return listeners.getOrDefault(tag, Collections.emptyList());
    }

    public Map<String, List<TransactionListener<M>>> getTransactionListeners() {
        return listeners;
    }
}
