package org.beyene.ledger.file;

import org.beyene.ledger.api.DataRepresentation;
import org.beyene.ledger.api.Ledger;
import org.beyene.ledger.api.Mapper;
import org.beyene.ledger.api.Transaction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class FileLedger<M, D> implements Ledger<M, D> {

    private final Mapper<M, D> mapper;
    private final DataRepresentation<D> dataRepresentation;
    private final Path directory;
    private final AtomicInteger counter;

    public FileLedger(Mapper<M, D> mapper, DataRepresentation<D> dataRepresentation, Path directory) {
        this.mapper = mapper;
        this.dataRepresentation = dataRepresentation;
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
            max = Optional.of(0);
        }

        return max.get();
    }

    @Override
    public Transaction<M> addTransaction(final M message) {
        Path path = createFile();

        D serialized = mapper.serialize(message);
        write(path, serialized);

        return () -> message;
    }

    private Path createFile() {
        Path path = Paths.get(directory.toString(), Objects.toString(counter.getAndIncrement()));

        while (true) {
            try {
                Files.createDirectories(path.getParent());
                Files.createFile(path);
                break;
            } catch (FileAlreadyExistsException e) {
                counter.incrementAndGet();
            } catch (IOException e) {
                throw new IllegalStateException("Could not create tx file", e);
            }
        }

        return path;
    }

    private void write(Path path, D serialized) {
        try {
            if (String.class.isAssignableFrom(dataRepresentation.getType())) {
                Files.write(path, String.class.cast(serialized).getBytes(StandardCharsets.UTF_8));
            } else if (byte[].class.isAssignableFrom(dataRepresentation.getType())) {
                Files.write(path, byte[].class.cast(serialized));
            } else {
                throw new IllegalStateException("Unsupported data type: " + dataRepresentation.getType().getName());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Writing tx failed", e);
        }
    }

    @Override
    public List<Transaction<M>> getTransactions() {
        List<Path> files = new ArrayList<>();
        try {
            Files.list(directory).forEach(files::add);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        List<Transaction<M>> messages = files.stream()
                .map(this::read)
                .map(m -> (Transaction<M>) () -> m)
                .collect(Collectors.toList());
        return messages;
    }

    private M read(Path path) {
        M message;

        try {
            if (String.class.isAssignableFrom(dataRepresentation.getType())) {
                String string = Files.readAllLines(path, StandardCharsets.UTF_8).stream().collect(Collectors.joining());
                D data = dataRepresentation.getType().cast(string);
                message = mapper.deserialize(data);
            } else if (byte[].class.isAssignableFrom(dataRepresentation.getType())) {
                byte[] bytes = Files.readAllBytes(path);
                D data = dataRepresentation.getType().cast(bytes);
                message = mapper.deserialize(data);
            } else {
                throw new IllegalStateException("Unsupported data type: " + dataRepresentation.getType().getName());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Writing tx failed", e);
        }

        return message;
    }

    @Override
    public List<Transaction<M>> getTransactions(LocalTime since) {
        return Collections.emptyList();
    }

    @Override
    public List<Transaction<M>> getTransactions(LocalTime since, LocalTime to) {
        return Collections.emptyList();
    }

    @Override
    public DataRepresentation<D> getDataRepresentation() {
        return dataRepresentation;
    }
}
