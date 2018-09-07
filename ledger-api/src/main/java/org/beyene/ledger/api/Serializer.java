package org.beyene.ledger.api;

public interface Serializer<T, R> {
    R serialize(T t);
}
