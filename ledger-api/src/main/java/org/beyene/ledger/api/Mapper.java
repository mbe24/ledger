package org.beyene.ledger.api;

public interface Mapper<T, R> extends Serializer<T, R>, Deserializer<T, R> {

    @Override
    T deserialize(R r);

    @Override
    R serialize(T t);
}
