package org.beyene.ledger.api;

public interface Mapper<T, R> {

    T deserialize(R r) throws MappingException;

    R serialize(T t) throws MappingException;

    class MappingException extends IllegalArgumentException {

        public MappingException(String s) {
            super(s);
        }

        public MappingException() {
            super();
        }
    }
}
