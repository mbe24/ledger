package org.beyene.ledger.api;

public interface Deserializer<T, R> {

    T deserialize(R r);
}
