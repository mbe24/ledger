package org.beyene.ledger.api;

public class Data {

    public static final DataRepresentation<String> STRING = () -> String.class;

    public static final DataRepresentation<byte[]> BYTES = () -> byte[].class;

    private Data() {
        throw new AssertionError("no instances allowed");
    }

}
