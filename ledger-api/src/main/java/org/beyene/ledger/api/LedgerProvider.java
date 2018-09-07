package org.beyene.ledger.api;

import java.util.Map;

public interface LedgerProvider {

    <M, T> Ledger<M, T> newLedger(Mapper<M, T> mapper, DataRepresentation<T> data, Map<String, String> properties);
}
