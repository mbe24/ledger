package org.beyene.ledger.api;

import org.beyene.ledger.api.error.MappingException;

/**
 *
 * @param <T>
 * @param <R>
 */
@FunctionalInterface
public interface Deserializer<T, R> {

    /**
     *
     * @param r data to be deserialized
     * @return
     * @throws MappingException
     * @throws IllegalArgumentException if supplied data is not supported
     */
    T deserialize(R r) throws MappingException, IllegalArgumentException;

}
