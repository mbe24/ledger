package org.beyene.ledger.api;

@FunctionalInterface
public interface Deserializer<T, R> {

    T deserialize(R r) throws Mapper.MappingException;

}
