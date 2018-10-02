package org.beyene.ledger.api;

public interface Mapper<T, R> extends Serializer<T, R>, Deserializer<T, R> {

    @Override
    T deserialize(R r) throws MappingException;

    @Override
    R serialize(T t) throws MappingException;

    class MappingException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        public MappingException(String s) {
            super(s);
        }

        public MappingException() {
            super();
        }
    }
}
