package org.beyene.ledger.api;

public class Data {

    public static final Format<String> STRING = () -> String.class;

    public static final Format<byte[]> BYTES = () -> byte[].class;

    private Data() {
        throw new AssertionError("no instances allowed");
    }

}
