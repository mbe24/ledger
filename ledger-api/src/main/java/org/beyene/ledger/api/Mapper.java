package org.beyene.ledger.api;

public interface Mapper<T, R> extends Serializer<T, R>, Deserializer<T, R> {

    @Override
    T deserialize(R r) throws MappingException;

    @Override
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
