package org.beyene.ledger.api;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.is;

public class MapperTest {

    private Mapper<String, Integer> mapper;

    @Before
    public void initialize() {
        mapper = new StringIntegerMapper();
    }

    @Test
    public void testSerialize() throws Exception {
        Assert.assertThat(mapper.serialize("10"), is(10));
    }

    @Test
    public void testDeserialize() throws Exception {
        Assert.assertThat(mapper.deserialize(10), is("10"));
    }

    @Test
    public void testIdentity() throws Exception {
        Assert.assertThat(mapper.deserialize(mapper.serialize("10")), is("10"));
        Assert.assertThat(mapper.serialize(mapper.deserialize(10)), is(10));

        List<Integer> numbersSource = IntStream.range(0, 10).boxed().collect(Collectors.toList());
        List<Integer> numbers = numbersSource.stream()
                .map(mapper::deserialize)
                .map(mapper::serialize)
                .collect(Collectors.toList());

        Assert.assertThat(numbersSource, is(numbers));
    }


    private static class StringIntegerMapper implements Mapper<String, Integer> {

        @Override
        public Integer serialize(String object) {
            return Integer.parseInt(object);
        }

        @Override
        public String deserialize(Integer object) {
            return Objects.toString(object);
        }
    }
}