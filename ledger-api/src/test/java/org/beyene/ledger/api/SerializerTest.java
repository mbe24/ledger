package org.beyene.ledger.api;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.is;

public class SerializerTest {

    private final Serializer<String, Integer> serializer = new StringIntegerSerializer();
    private final Deserializer<String, Integer> deserializer = new StringIntegerDeserializer();

    @Test
    public void testSerialize() throws Exception {
        Assert.assertThat(serializer.serialize("10"), is(10));
    }

    @Test
    public void testDeserialize() throws Exception {
        Assert.assertThat(deserializer.deserialize(10), is("10"));
    }

    @Test
    public void testIdentity() throws Exception {
        Assert.assertThat(deserializer.deserialize(serializer.serialize("10")), is("10"));
        Assert.assertThat(serializer.serialize(deserializer.deserialize(10)), is(10));

        List<Integer> numbersSource = IntStream.range(0, 10).boxed().collect(Collectors.toList());
        List<Integer> numbers = numbersSource.stream()
                .map(deserializer::deserialize)
                .map(serializer::serialize)
                .collect(Collectors.toList());

        Assert.assertThat(numbersSource, is(numbers));
    }

    private static class StringIntegerSerializer implements Serializer<String, Integer> {

        @Override
        public Integer serialize(String object) {
            return Integer.parseInt(object);
        }
    }

    private static class StringIntegerDeserializer implements Deserializer<String, Integer> {

        @Override
        public String deserialize(Integer object) {
            return Objects.toString(object);
        }
    }
}