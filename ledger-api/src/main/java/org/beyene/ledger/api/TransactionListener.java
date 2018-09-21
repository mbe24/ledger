package org.beyene.ledger.api;

public interface TransactionListener<T> {

    void onTransaction(Transaction<T> tx);
}
