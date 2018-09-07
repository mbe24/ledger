package org.beyene.ledger.file;

import org.beyene.ledger.api.DataRepresentation;
import org.beyene.ledger.api.Ledger;
import org.beyene.ledger.api.LedgerProvider;
import org.beyene.ledger.api.Mapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

public class FileLedgerProvider implements LedgerProvider {

    @Override
    public <M, T> Ledger<M, T> newLedger(Mapper<M, T> mapper, DataRepresentation<T> data, Map<String, String> properties) {
        Objects.requireNonNull(mapper);
        Objects.requireNonNull(data);
        Objects.requireNonNull(properties);

        String path = properties.get("file.directory");
        Objects.requireNonNull(path, "file.directory is not set!");

        Path directory = Paths.get(path);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("file.directory could not be created: " + path, e);
        }
        return new FileLedger<>(mapper, data, directory);
    }
}
