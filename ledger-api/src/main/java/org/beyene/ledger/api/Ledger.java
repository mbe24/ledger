package org.beyene.ledger.api;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @param <M> Message type
 * @param <D> Data type in ledger, e.g. String or byte[]
 */
public interface Ledger<M, D> extends AutoCloseable {

    // is it necessary to return the meta data here?
    // another possibility is to use completion handler

    /**
     * @param transaction
     * @return
     * @throws IOException
     */
    Transaction<M> addTransaction(Transaction<M> transaction) throws IOException;

    /**
     * @param since has to be greater or equal to Instant.MIN
     * @param to    has to be smaller or equal to Instant.MAX
     * @return
     */
    List<Transaction<M>> getTransactions(Instant since, Instant to);

    // tag == null means listen to all, already registered, tags (i.e. through configuration)
    // TODO check whether null tag makes sense

    /**
     * @param tag      <code>null</code> is not allowed
     * @param listener
     * @return
     */
    boolean addTransactionListener(String tag, TransactionListener<M> listener);

    /**
     * @param tag
     * @return
     */
    boolean removeTransactionListener(String tag);

    // TODO
    // think about using multiple listeners per tag
    // utilize TransactionListenerManager

    /**
     * @return
     */
    Map<String, TransactionListener<M>> getTransactionListeners();

    /**
     * @return
     */
    Format<D> getFormat();

    // update configuration
    // necessary now that TransactionListener is added?

    @Override
    void close() throws IOException;
}
