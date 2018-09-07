package org.beyene.ledger.api;

import java.time.LocalTime;
import java.util.List;

/**
 *
 * @param <M> Message type
 * @param <D> Data type in ledger, e.g. String or byte[]
 */
public interface Ledger<M, D> {

    // Return object that contains timestamps
    Transaction<M> addTransaction(M message);

    List<Transaction<M>> getTransactions();

    List<Transaction<M>> getTransactions(LocalTime since);

    List<Transaction<M>> getTransactions(LocalTime since, LocalTime to);

    // register listener...
    // maybe add AbstractLedger or LedgerDelegate that handles listener code

    DataRepresentation<D> getDataRepresentation();

    // update configuration
}
