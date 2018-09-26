package org.beyene.ledger.iota;

import jota.error.ArgumentException;
import org.apache.commons.lang3.StringUtils;
import org.beyene.ledger.api.Data;
import org.beyene.ledger.api.Format;
import org.beyene.ledger.api.Transaction;
import org.beyene.ledger.iota.util.Iota;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.*;

public class DefaultMessagerSenderTest {

    private List<String> transactionTrytes = new ArrayList<>();
    private Iota api;
    private MessageSender<String> sender;
    private AtomicReference<Boolean> throwException = new AtomicReference<>(false);

    @Before
    public void setUp() throws Exception {
        this.api = mock(Iota.class);

        doAnswer(invocation -> {
            Stream.of(invocation.<String[]>getArgument(0)).forEach(transactionTrytes::add);
            if (throwException.get())
                throw new ArgumentException("exception for unit test");

            return Collections.emptyList();
        }).when(api).sendTrytes(any(String[].class), any(int.class), any(int.class));

        this.sender = new DefaultMessagerSender.Builder<String, String>()
                .setApi(api)
                .setFormat(Data.STRING)
                .setSerializer(s -> s)
                //.setMinWeightMagnitude(13)
                //.setTipAnalysisDepth(3)
                //.setUseConfiguredAddress(false)
                .build();
    }

    @After
    public void tearDown() throws Exception {
        transactionTrytes.clear();
        throwException.set(false);
    }

    @Test
    public void testAddTransaction() throws Exception {
        String message = "This is how we do";
        Instant timestamp = Instant.now();
        Transaction<String> txIn = new MessageTransaction<>("ID", timestamp, "TAG", message);
        sender.addTransaction(txIn);

        Assert.assertThat("signature fragments", transactionTrytes.size(), is(1));

        jota.model.Transaction txOut = new jota.model.Transaction(transactionTrytes.get(0), null);
        String trytes = txOut.getSignatureFragments();
        Assert.assertThat("signature message length", trytes.length(), is(2187));

        String trytesTrimmed = StringUtils.stripEnd(trytes, "9");
        String reconstructedMessage = jota.utils.TrytesConverter.toString(trytesTrimmed);
        Assert.assertThat("message", reconstructedMessage, is(message));

        // timestamp is in seconds since epoch
        Assert.assertThat("timestamp", txOut.getTimestamp(), is(timestamp.toEpochMilli() / 1000));
        Assert.assertThat("attachment timestamp", txOut.getAttachmentTimestamp(), is(timestamp.toEpochMilli()));

        // tag is filled up with 9s
        Assert.assertThat("tag", StringUtils.stripEnd(txOut.getTag(), "9"), is(txIn.getTag()));
    }

    @Test
    public void testAddTransactionExcessLength() throws Exception {
        Random random = new Random();
        int min = 48; // ASCII 0
        int max = 122; // ASCII z

        String message = IntStream
                .generate(() -> min + random.nextInt(max - min))
                .mapToObj(i -> "" + (char) i)
                .limit(2500)
                .collect(Collectors.joining());

        Transaction<String> txIn = new MessageTransaction<>("ID", Instant.now(), "TAG", message);
        sender.addTransaction(txIn);

        // 3
        int size = (int) Math.ceil(2.0 * message.length() / 2187);
        Assert.assertThat("bundle size", transactionTrytes.size(), is(size));

        List<String> signatureFragments = transactionTrytes.stream()
                .map(txTrytes -> new jota.model.Transaction(txTrytes, null))
                .map(jota.model.Transaction::getSignatureFragments)
                .collect(Collectors.toList());
        signatureFragments.forEach(fragment -> Assert.assertThat("message length", fragment.length(), is(2187)));
        Collections.reverse(signatureFragments);

        String trytes = signatureFragments.stream().collect(Collectors.joining());
        String trytesTrimmed = StringUtils.stripEnd(trytes, "9");
        Assert.assertThat("even tryte length", trytesTrimmed.length() % 2, is(0));

        String reconstructedMessage = jota.utils.TrytesConverter.toString(trytesTrimmed);
        Assert.assertThat("message", reconstructedMessage, is(message));
    }

    @Test(expected = IOException.class)
    public void testAddTransactionApiError() throws Exception {
        throwException.set(true);
        sender.addTransaction(new MessageTransaction<>("ID", Instant.now(), "TAG", "This is how we do"));
    }

    @Test(expected = IllegalStateException.class)
    public void testAddTransactionFormatError() throws Exception {
        this.sender = new DefaultMessagerSender.Builder<String, Void>()
                .setApi(api)
                // setting unsupported format leads to illegal state exception
                .setFormat(() -> Void.class)
                .setSerializer(s -> s)
                .build();
        sender.addTransaction(new MessageTransaction<>("ID", Instant.now(), "TAG", "This is how we do"));
    }
}
