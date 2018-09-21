package org.beyene.ledger.iota;

import org.beyene.ledger.api.Transaction;

public interface MessageSender<M> {

    Transaction<M> addTransaction(M message);
}
