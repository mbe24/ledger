package org.beyene.ledger.iota;

import org.beyene.ledger.api.Transaction;

import java.time.Instant;

public class MessageTransaction<M> implements Transaction<M> {
    private final String id;

    private final Instant timestamp;

    private final String tag;

    private final M message;

    public MessageTransaction(String id, Instant timestamp, String tag, M message) {
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
    public M getObject() {
        return message;
    }
}
