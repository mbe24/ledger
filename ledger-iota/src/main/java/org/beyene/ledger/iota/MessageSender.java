package org.beyene.ledger.iota;

import org.beyene.ledger.api.Transaction;

import java.io.IOException;

/**
 * <p>Message sender</p>
 *
 *
 *
 * @param <M> message type
 */
public interface MessageSender<M> {

    /**
     *
     * @param transaction
     * @return
     * @throws IOException
     */
    Transaction<M> addTransaction(Transaction<M> transaction) throws IOException;
}
