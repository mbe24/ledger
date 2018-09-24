package org.beyene.ledger.api;

@FunctionalInterface
public interface Serializer<T, R> {

    R serialize(T t) throws Mapper.MappingException;
}
