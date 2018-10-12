package org.beyene.ledger.api.error;

/**
 *
 */
public class MappingException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public MappingException(String s) {
        super(s);
    }

    public MappingException() {
        super();
    }
}