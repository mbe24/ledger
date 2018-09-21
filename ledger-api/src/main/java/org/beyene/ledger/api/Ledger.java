package org.beyene.ledger.api;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @param <M> Message type
 * @param <D> Data type in ledger, e.g. String or byte[]
 */
public interface Ledger<M, D> extends AutoCloseable {

    // is it necessary to return the meta data here?
    // another possibility is to use completion handler
    Transaction<M> addTransaction(M message);

    /**
     *
     * @param since has to be greater or equal to Instant.MIN
     * @param to has to be smaller or equal to Instant.MAX
     * @return
     */
    List<Transaction<M>> getTransactions(Instant since, Instant to);

    // addTransactionListener(tag, listener)
    // tag == null means listen to all, already registered, tags (i.e. through configuration)
    // TODO check whether null tag makes sense
    default boolean addTransactionListener(String tag, TransactionListener<M> listener) {
        return true;
    }

    // removeTransactionListener
    default boolean removeTransactionListener(String tag, TransactionListener<M> listener) {
        return true;
    }

    // getTransactionListeners
    default Map<String, List<TransactionListener<M>>> getTransactionListeners() {
        return Collections.emptyMap();
    }

    Format<D> getFormat();

    // update configuration
    // necessary now that TransactionListener is added?

    @Override
    void close() throws IOException;
}
