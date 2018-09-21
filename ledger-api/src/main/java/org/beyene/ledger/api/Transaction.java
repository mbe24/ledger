package org.beyene.ledger.api;

import java.time.Instant;

public interface Transaction<T> {

    // e.g. address
    String getIdentifier();

    Instant getTimestamp();

    String getTag();

    T getObject();
}
