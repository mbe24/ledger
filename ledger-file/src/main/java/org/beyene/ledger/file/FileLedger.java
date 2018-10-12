package org.beyene.ledger.file;

import org.beyene.ledger.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class FileLedger<M, D> implements Ledger<M, D> {

    private static final Logger LOGGER = Logger.getLogger(FileLedger.class.getName());

    private final Serializer<M, D> serializer;
    private final Deserializer<M, D> deserializer;
    private final Format<D> format;
    private final Path directory;
    private final AtomicInteger counter;

    public FileLedger(Serializer<M, D> serializer,
                      Deserializer<M, D> deserializer, Format<D> format, Path directory) {
        this.serializer = serializer;
        this.deserializer = deserializer;
        this.format = format;
        this.directory = directory;
        this.counter = new AtomicInteger(determineMaxCounter());
    }

    private int determineMaxCounter() {
        Optional<Integer> max;

        try {
            max = Files.list(directory)
                    .map(Path::toFile)
                    .filter(File::isFile)
                    .map(File::getName)
                    .map(Integer::parseInt)
                    .max(Comparator.naturalOrder());

            if (!max.isPresent())
                max = Optional.of(0);

        } catch (IOException e) {
            LOGGER.log(Level.INFO, e.toString(), e);
            max = Optional.of(0);
        }

        return max.get();
    }

    @Override
    public Transaction<M> addTransaction(Transaction<M> transaction) {
        Path path = createFile();

        M message = transaction.getObject();
        D serialized = serializer.serialize(message);
        write(path, serialized);

        return fromMessage(message);
    }

    private Transaction<M> fromMessage(M message) {
        SimpleTransaction<M> tx = new SimpleTransaction<>();
        tx.object = message;
        return tx;
    }

    private Path createFile() {
        Path path = Paths.get(directory.toString(), Objects.toString(counter.getAndIncrement()));

        while (true) {
            try {
                Files.createDirectories(path.getParent());
                Files.createFile(path);
                break;
            } catch (FileAlreadyExistsException e) {
                LOGGER.log(Level.INFO, e.toString(), e);
                counter.incrementAndGet();
            } catch (IOException e) {
                LOGGER.log(Level.INFO, e.toString(), e);
                throw new IllegalStateException("Could not create tx file", e);
            }
        }

        return path;
    }

    private void write(Path path, D serialized) {
        try {
            if (String.class.isAssignableFrom(format.getType())) {
                Files.write(path, String.class.cast(serialized).getBytes(StandardCharsets.UTF_8));
            } else if (byte[].class.isAssignableFrom(format.getType())) {
                Files.write(path, byte[].class.cast(serialized));
            } else {
                throw new IllegalStateException("Unsupported data type: " + format.getType().getName());
            }
        } catch (IOException e) {
            LOGGER.log(Level.INFO, e.toString(), e);
            throw new IllegalStateException("Writing tx failed", e);
        }
    }

    private M read(Path path) {
        M message;

        try {
            Object object;
            if (String.class.isAssignableFrom(format.getType())) {
                object = Files.readAllLines(path, StandardCharsets.UTF_8).stream().collect(Collectors.joining());
            } else if (byte[].class.isAssignableFrom(format.getType())) {
                object = Files.readAllBytes(path);
            } else {
                throw new IllegalStateException("Unsupported data type: " + format.getType().getName());
            }

            D data = format.getType().cast(object);
            message = deserializer.deserialize(data);
        } catch (IOException e) {
            LOGGER.log(Level.INFO, e.toString(), e);
            throw new IllegalStateException("Writing tx failed", e);
        }

        return message;
    }

    @SuppressWarnings("unused")
    @Override
    public List<Transaction<M>> getTransactions(Instant since, Instant to) {
        List<Path> files = new ArrayList<>();
        try {
            Files.list(directory).forEach(files::add);
        } catch (IOException e) {
            LOGGER.log(Level.INFO, e.toString(), e);
            throw new IllegalStateException(e);
        }

        return files.stream()
                .map(this::read)
                .map(this::fromMessage)
                //.filter(tx -> tx.getTimestamp().isAfter(since))
                //.filter(tx -> tx.getTimestamp().isBefore(to))
                .collect(Collectors.toList());
    }

    public boolean addTransactionListener(String tag, TransactionListener<M> listener) {
        return true;
    }

    // removeTransactionListener
    public boolean removeTransactionListener(String tag) {
        return true;
    }

    // getTransactionListeners
    public Map<String, TransactionListener<M>> getTransactionListeners() {
        return Collections.emptyMap();
    }

    public Format<D> getFormat() {
        return format;
    }

    @Override
    public void close() {

    }

    private static class SimpleTransaction<M> implements Transaction<M> {

        M object;
        String identifier;
        Instant timestamp;
        String tag;

        @Override
        public M getObject() {
            return object;
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        @Override
        public Instant getTimestamp() {
            return timestamp;
        }

        @Override
        public String getTag() {
            return tag;
        }
    }
}
