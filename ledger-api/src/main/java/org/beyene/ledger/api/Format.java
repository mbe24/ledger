package org.beyene.ledger.api;

/**
 *
 * @param <T>
 */
public interface Format<T> {

    /**
     *
     * @return
     */
    Class<T> getType();
}
