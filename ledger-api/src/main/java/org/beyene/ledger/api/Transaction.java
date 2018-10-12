package org.beyene.ledger.api;

import java.time.Instant;

/**
 *
 * @param <T>
 */
public interface Transaction<T> {

    // e.g. address

    /**
     *
     * @return
     */
    String getIdentifier();

    /**
     *
     * @return
     */
    Instant getTimestamp();

    /**
     *
     * @return
     */
    String getTag();

    /**
     *
     * @return
     */
    T getObject();
}
