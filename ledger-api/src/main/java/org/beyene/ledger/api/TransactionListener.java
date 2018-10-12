package org.beyene.ledger.api;

/**
 *
 * @param <T>
 */
public interface TransactionListener<T> {

    /**
     *
     * @param tx
     */
    void onTransaction(Transaction<T> tx);
}
