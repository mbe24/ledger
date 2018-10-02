package org.beyene.ledger.iota;

import org.beyene.ledger.api.Data;
import org.beyene.ledger.api.Transaction;
import org.beyene.ledger.iota.util.Iota;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class MessageParserTest {

    private List<String> transactionTrytes = new ArrayList<>();
    private MessageSender<String> sender;

    private BlockingQueue<Transaction<String>> messageQueue;
    private BlockingQueue<Transaction<jota.model.Transaction>> transactionQueue;
    private BlockingQueue<Transaction<jota.model.Transaction>> txsBeforePushThreshold;
    private List<Transaction<String>> oldMessages;

    private MessageParser<String, String> messageParser;

    @Before
    public void setUp() throws Exception {
        Iota api = mock(Iota.class);

        doAnswer(invocation -> {
            Stream.of(invocation.<String[]>getArgument(0)).forEach(transactionTrytes::add);
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

        this.messageQueue = new LinkedBlockingQueue<>();
        this.transactionQueue = new LinkedBlockingQueue<>();
        this.txsBeforePushThreshold = new LinkedBlockingQueue<>();
        this.oldMessages = new ArrayList<>();

        this.messageParser = new MessageParser.Builder<String, String>()
                .setMessageQueue(messageQueue)
                .setTransactionQueue(transactionQueue)
                .setTransactionsBeforePushThreshold(txsBeforePushThreshold)
                .setMessagesBeforePushThresholdConsumer(oldMessages::addAll)
                .setFormat(Data.STRING)
                .setKeepAliveInterval(Duration.ofMinutes(60))
                .setDeserializer(s -> s)
                .build();
    }

    @After
    public void tearDown() throws Exception {
        transactionTrytes.clear();
    }

    @Test
    public void testSingleMessage() throws Exception {
        String message = "This is how we do";
        Instant timestamp = Instant.now();
        Transaction<String> txIn = new MessageTransaction<>("ID", timestamp, "TAG", message);
        sender.addTransaction(txIn);

        List<jota.model.Transaction> txs = transactionTrytes.stream()
                .map(txTrytes -> new jota.model.Transaction(txTrytes, null))
                .collect(Collectors.toList());
        // reversing not necessary since there are no bundles, just single txs
        Collections.reverse(txs);
        Assert.assertThat("no txs", transactionTrytes.size(), is(1));

        txs.stream().map(TransactionDecorator::new).forEach(transactionQueue::offer);

        messageParser.run();

        Transaction<String> received = messageQueue.take();
        Assert.assertThat("message", received.getObject(), is(message));
    }

    @Test
    public void testMultiMessage() throws Exception {
        List<String> messages = Arrays.asList("A", "B", "C");
        Instant timestamp = Instant.now();

        List<Transaction<String>> source = new ArrayList<>();
        for (String message : messages) {
            // min resolution for bundle hash to be different
            timestamp = timestamp.plusMillis(1000);
            // alternatively, just change id (which translates to address (cf. from/to swap by use of tags))
            source.add(new MessageTransaction<>("ID" + message, timestamp, "TAG", message));
        }

        for (Transaction<String> tx : source) {
            sender.addTransaction(tx);
        }
        Assert.assertThat("messages", transactionTrytes.size(), is(messages.size()));

        List<jota.model.Transaction> txs = transactionTrytes.stream()
                .map(txTrytes -> new jota.model.Transaction(txTrytes, null))
                .collect(Collectors.toList());
        // reversing not necessary since there are no bundles, just single txs
        Collections.reverse(txs);
        Assert.assertThat("no txs", transactionTrytes.size(), is(3));

        txs.stream().map(TransactionDecorator::new).forEach(transactionQueue::offer);

        messageParser.run();

        List<Transaction<String>> drain = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            drain.add(messageQueue.take());
        }
        //Collections.sort(drain, Comparator.comparing(Transaction::getTimestamp));

        String sent = messages.stream().collect(Collectors.joining());
        String received = drain.stream().map(Transaction::getObject).collect(Collectors.joining());
        Assert.assertThat("message", received, is(sent));
    }

    @Test
    public void testSingleBundle() throws Exception {
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

        List<jota.model.Transaction> txs = transactionTrytes.stream()
                .map(txTrytes -> new jota.model.Transaction(txTrytes, null))
                .collect(Collectors.toList());
        // should work without creating the right order
        // message parser has to fix order by itself
        //Collections.reverse(txs);

        txs.stream().map(TransactionDecorator::new).forEach(transactionQueue::offer);

        messageParser.run();

        Transaction<String> received = messageQueue.take();
        Assert.assertThat("message", received.getObject(), is(message));
    }

    private static class TransactionDecorator implements Transaction<jota.model.Transaction> {

        private final jota.model.Transaction delegate;

        public TransactionDecorator(jota.model.Transaction delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TransactionDecorator)) return false;

            TransactionDecorator that = (TransactionDecorator) o;
            return delegate.getHash().equals(that.delegate.getHash());

        }

        @Override
        public int hashCode() {
            return delegate.getHash().hashCode();
        }

        @Override
        public String getIdentifier() {
            return delegate.getAddress();
        }

        @Override
        public Instant getTimestamp() {
            return Instant.ofEpochMilli(delegate.getTimestamp());
        }

        @Override
        public String getTag() {
            return delegate.getTag();
        }

        @Override
        public jota.model.Transaction getObject() {
            return delegate;
        }
    }
}