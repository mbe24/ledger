package org.beyene.ledger.iota;

import org.beyene.ledger.api.Transaction;
import org.beyene.ledger.iota.TagChangeListener.TagChangeAction;
import org.beyene.ledger.iota.TagChangeListener.TagChangeEvent;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.beyene.ledger.iota.TransactionPollerTest.ReturnOption.*;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class TransactionPollerTest {

    enum ReturnOption {
        // add old txs, i.e. tx with timestamp < pushThreshold - slidingWindow
        OLD_TXS,
        // add new txs, i.e. tx with timestamp > pushThreshold - slidingWindow
        NEW_TXS,
        // only add 1 tx per option
        SINGLE,
        // add multiple txs per option
        MULTI,
        // duplicate all txs
        DUPLICATE,
        // txs are provided in 'returnedTransactions'
        PROVIDED
    }

    private static final int MULTI_SIZE = 5;
    private final List<jota.model.Transaction> returnedTransactions = new ArrayList<>();

    private EnumSet<ReturnOption> options = EnumSet.noneOf(ReturnOption.class);
    private final EnumSet<ReturnOption> timeOptions = EnumSet.of(OLD_TXS, NEW_TXS);
    private final EnumSet<ReturnOption> multiplicityOptions = EnumSet.of(SINGLE, MULTI);

    private Set<String> tags;
    // contains new txs, i.e. txs that arrived after threshold time
    private BlockingQueue<Transaction<jota.model.Transaction>> queue;
    private Instant pushThreshold;
    private Duration slidingWindow;

    // contains old tx, i.e. before threshold time (and not in sliding window)
    private List<Transaction<jota.model.Transaction>> txsBeforePushThreshold;
    private int hashCacheSize = 25;

    private TransactionPoller poller;

    @Before
    public void setUp() throws Exception {
        Iota api = mock(Iota.class);
        doAnswer(invocation -> {
            Set<String> tags = Stream.of(invocation.<String[]>getArgument(0)).collect(Collectors.toSet());
            // fill txs dependent on state
            return provideTransactions(tags);
        }).when(api).findTransactionObjectsByTag(any(String[].class));

        this.tags = new HashSet<>(Collections.singletonList("A"));
        this.queue = new LinkedBlockingQueue<>();
        this.pushThreshold = Instant.now();
        this.slidingWindow = Duration.ofMinutes(5);
        this.txsBeforePushThreshold = new ArrayList<>();
        Consumer<Collection<Transaction<jota.model.Transaction>>> oldTxsConsumer = txsBeforePushThreshold::addAll;

        Map<String, Boolean> knownHashes = new LinkedHashMap<String, Boolean>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > hashCacheSize;
            }
        };

        this.poller = new TransactionPoller.Builder()
                .setApi(api)
                .setQueue(queue)
                .setTags(tags)
                .setPushThreshold(pushThreshold)
                .setSlidingWindow(slidingWindow)
                .setTransactionBeforePushThresholdConsumer(oldTxsConsumer)
                .setKnownHashesCache(knownHashes)
                .build();
    }

    private List<jota.model.Transaction> provideTransactions(Set<String> tags) {
        if (options.contains(PROVIDED))
            return new ArrayList<>(returnedTransactions);

        if (!options.stream().anyMatch(timeOptions::contains))
            throw new IllegalStateException("option for time missing");

        if (!options.stream().anyMatch(multiplicityOptions::contains))
            throw new IllegalStateException("option for multiplicity missing");

        int n = 0;
        if (options.contains(SINGLE))
            n = 1;
        else if (options.contains(MULTI))
            n = MULTI_SIZE;

        Instant adjustedThreshold = pushThreshold.minus(slidingWindow);

        List<jota.model.Transaction> oldTxs = new ArrayList<>();
        if (options.contains(OLD_TXS)) {
            oldTxs.addAll(createTxs(adjustedThreshold, 0, n, -1));
        }

        List<jota.model.Transaction> newTxs = new ArrayList<>();
        if (options.contains(NEW_TXS)) {
            newTxs.addAll(createTxs(adjustedThreshold, 0, n, +1));
        }

        int uniqueElements = oldTxs.size() + newTxs.size();
        if (uniqueElements > hashCacheSize) {
            String message = "Test influenced by cache. Capacity: %d/%d. Use PROVIDED.";
            throw new IllegalStateException(String.format(message, uniqueElements, hashCacheSize));
        }

        if (options.contains(DUPLICATE)) {
            oldTxs.addAll(oldTxs);
            newTxs.addAll(newTxs);
        }

        List<jota.model.Transaction> txs = new ArrayList<>(oldTxs);
        txs.addAll(newTxs);

        if (tags.size() > 1)
            throw new IllegalArgumentException("number of tags is restricted to 1 for test case");
        String tag = tags.stream().findFirst().orElse("");
        return txs.stream()
                .peek(tx -> tx.setTag(tag))
                .collect(Collectors.toList());
    }

    private List<jota.model.Transaction> createTxs(Instant reference, int offset, int n, int delta, String tag) {
        // add tolerance
        Instant threshold = reference.plusSeconds(delta);
        AtomicInteger counter = new AtomicInteger(offset);
        String prefix = (delta == 1) ? "NEW" : "OLD";

        return IntStream.range(offset, offset + n)
                .sequential()
                .boxed()
                .map(i -> threshold.plusSeconds(delta * i))
                .map(timestamp -> new jota.model.Transaction("ADDRESS", 0, tag, timestamp.toEpochMilli() / 1000))
                //.peek(tx -> tx.setHash(Objects.toString(Long.hashCode(tx.getTimestamp()))))
                .peek(tx -> tx.setHash(prefix + "_" + tag + "_" + counter.get()))
                .peek(tx -> tx.setBundle(tx.getHash()))
                .peek(tx -> tx.setTag(tag))
                // make counter incremental for debugging purposes
                .peek(tx -> tx.setCurrentIndex(counter.getAndIncrement()))
                .collect(Collectors.toList());
    }

    private List<jota.model.Transaction> createTxs(Instant reference, int offset, int n, int delta) {
        return createTxs(reference, offset, n, delta, "TAG");
    }

    @After
    public void tearDown() throws Exception {
        options.clear();
        returnedTransactions.clear();
    }

    @Test
    public void testNewTxCache() throws Exception {
        options = EnumSet.of(PROVIDED);

        Instant adjustedThreshold = pushThreshold.minus(slidingWindow);
        List<jota.model.Transaction> txs = createTxs(adjustedThreshold, 0, hashCacheSize, 1, "A");

        returnedTransactions.addAll(txs);
        returnedTransactions.addAll(txs);

        // cache should filter all duplicate transactions
        poller.run();
        Assert.assertThat("queue size unexpected", queue, hasSize(hashCacheSize));
        Assert.assertThat("no duplicate element", queue.size(), is(new HashSet<>(queue).size()));

        returnedTransactions.clear();
        // add (n+1)-th unique element for LRU strategy to kick in (n = hashCacheSize)
        // NEW_A_25
        returnedTransactions.addAll(createTxs(adjustedThreshold, hashCacheSize, 1, 1, "A"));

        poller.run();
        Assert.assertThat("queue size unexpected", queue, hasSize(1 + hashCacheSize));
        Assert.assertThat("no duplicate element", queue.size(), is(new HashSet<>(queue).size()));

        returnedTransactions.clear();

        returnedTransactions.add(txs.get(0));
        // add first element again
        poller.run();

        Assert.assertThat("queue size unexpected", queue, hasSize(2 + hashCacheSize));
        Assert.assertThat("has duplicate element", queue.size() - 1, is(new HashSet<>(queue).size()));
        Assert.assertThat("no old tx", txsBeforePushThreshold, is(empty()));
    }

    @Test
    public void testOldTxCache() throws Exception {
        options = EnumSet.of(PROVIDED);

        int n = 100;
        Instant adjustedThreshold = pushThreshold.minus(slidingWindow);
        List<jota.model.Transaction> txs = createTxs(adjustedThreshold, 0, n, -1, "A");

        returnedTransactions.addAll(txs);
        returnedTransactions.addAll(txs);

        poller.run();
        Assert.assertThat("queue size unexpected", txsBeforePushThreshold, hasSize(n));
        Set<Transaction<jota.model.Transaction>> set = new HashSet<>(txsBeforePushThreshold);
        Assert.assertThat("no duplicate elements", txsBeforePushThreshold.size(), is(set.size()));
    }

    @Test
    public void testOldTxTagSwitch() throws Exception {
        options = EnumSet.of(PROVIDED);

        int n = 50;
        Instant adjustedThreshold = pushThreshold.minus(slidingWindow);
        returnedTransactions.addAll(createTxs(adjustedThreshold, 0, n, -1, "A"));
        returnedTransactions.addAll(createTxs(adjustedThreshold, 0, n, +1, "A"));

        poller.run();
        returnedTransactions.clear();
        Assert.assertThat("queue size unexpected", txsBeforePushThreshold, hasSize(n));
        Assert.assertThat("queue size unexpected", queue, hasSize(n));
        queue.clear();
        txsBeforePushThreshold.clear();

        n = 50;
        // A should be irrelevant, because txs are below threshold
        returnedTransactions.addAll(createTxs(adjustedThreshold, n, 2 * n, -1, "A"));

        String newTag = "B";
        returnedTransactions.addAll(createTxs(adjustedThreshold, 0, n, -1, newTag));

        tags.add(newTag); // simulate change of listener set
        poller.tagChanged(new TagChangeEvent(Collections.emptySet(), newTag, TagChangeAction.ADD));

        poller.run();
        Assert.assertThat("old tx size unexpected", txsBeforePushThreshold, hasSize(n));
        Assert.assertThat("queue size unexpected", queue, hasSize(0));
    }

    @Test
    public void testNewTxSingle() throws Exception {
        options = EnumSet.of(NEW_TXS, SINGLE, DUPLICATE);
        poller.run();

        Assert.assertThat("no old tx", txsBeforePushThreshold, is(empty()));
        Assert.assertThat("new tx not empty", queue, is(not(empty())));
        Assert.assertThat("new tx size match", queue, hasSize(1));
    }

    @Test
    public void testNewTxMulti() throws Exception {
        options = EnumSet.of(NEW_TXS, MULTI);
        poller.run();

        Assert.assertThat("no old tx", txsBeforePushThreshold, is(empty()));
        Assert.assertThat("new tx not empty", queue, is(not(empty())));
        Assert.assertThat("new tx size match", queue, hasSize(MULTI_SIZE));
    }

    @Test
    public void testOldTxSingle() throws Exception {
        options = EnumSet.of(OLD_TXS, SINGLE);
        poller.run();

        Assert.assertThat("no new tx", queue, is(empty()));
        Assert.assertThat("old tx not empty", txsBeforePushThreshold, is(not(empty())));
        Assert.assertThat("old tx size match", txsBeforePushThreshold, hasSize(1));
    }

    @Test
    public void testOldTxMulti() throws Exception {
        options = EnumSet.of(OLD_TXS, MULTI, DUPLICATE);
        poller.run();

        Assert.assertThat("no new tx", queue, is(empty()));
        Assert.assertThat("old tx not empty", txsBeforePushThreshold, is(not(empty())));
        Assert.assertThat("old tx size match", txsBeforePushThreshold, hasSize(MULTI_SIZE));
    }
}
