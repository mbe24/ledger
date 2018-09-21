package org.beyene.ledger.iota;

import org.beyene.ledger.api.Transaction;

import java.io.IOException;

public interface MessageSender<M> {

    Transaction<M> addTransaction(Transaction<M> transaction) throws IOException;
}
