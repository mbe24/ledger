package org.beyene.ledger.api;

import org.beyene.ledger.api.error.MappingException;

/**
 *
 * @param <T>
 * @param <R>
 */
@FunctionalInterface
public interface Serializer<T, R> {

    /**
     *
     * @param t object to be serialized
     * @return
     * @throws MappingException
     * @throws IllegalArgumentException if given object is not supported
     */
    R serialize(T t) throws MappingException, IllegalArgumentException;
}
