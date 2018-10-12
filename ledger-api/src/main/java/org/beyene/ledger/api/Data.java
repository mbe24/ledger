package org.beyene.ledger.api;

/**
 * <p>Utility class that stores standard formats.</p>
 * In order to define own formats, implement org.beyene.ledger.api.Format.
 *
 */
public final class Data {

    /**
     *
     */
    public static final Format<String> STRING = () -> String.class;

    /**
     *
     */
    public static final Format<byte[]> BYTES = () -> byte[].class;

    private Data() {
        throw new AssertionError("no instances allowed");
    }

}
