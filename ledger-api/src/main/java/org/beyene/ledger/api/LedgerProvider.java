package org.beyene.ledger.api;

import java.util.Map;

/**
 * <p>Service provider interface for Ledger</p>
 */
public interface LedgerProvider {

    /**
     * <p>Provider interface for creating a new ledger.</p>
     * <p>
     * <p>General properties and default values:</p>
     * <ul>
     * <li>ledger.poll.interval</li>
     * <p>Interval in which the ledger should be polled for new data (if necessary).</p>
     * <p>Default value is 5000 (in ms, int).</p>
     * <p>
     * <li>client.push.interval</li>
     * (necessary?)
     * <p>Interval in which new data should be pushed to the client.</p>
     * <p>Default value is 5000 (in ms, int)</p>
     * <p>
     * <li>ssl.certificate.check</li>
     * <p>Flag that indicates whether server certificates should be validated.</p>
     * <p>Default is true.</p>
     * <p>
     * <li>tx.push.threshold</li>
     * <p>Time which separates old from new transactions. All old transactions don't get pushed and instead
     * have to be polled by the user.</p>
     * <p>Default is the current time (in ms, long).</p>
     * <p>
     * <li>ledger.pool.threads</li>
     * <p>Number of threads in the used pool. Use 0 to set threads to number of available processors.</p>
     * <p>Default is 2 (int).</p>
     * <p>
     * </ul>
     *
     * @param serializer
     * @param deserializer
     * @param format
     * @param listeners
     * @param properties
     * @param <M>        Message type
     * @param <D>        Data type in ledger, e.g. String or byte[]
     * @return
     */
    <M, D> Ledger<M, D> newLedger(Serializer<M, D> serializer,
                                  Deserializer<M, D> deserializer,
                                  Format<D> format,
                                  Map<String, TransactionListener<M>> listeners,
                                  Map<String, Object> properties);
}
