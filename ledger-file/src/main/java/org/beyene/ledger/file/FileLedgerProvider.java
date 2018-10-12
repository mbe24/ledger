package org.beyene.ledger.file;

import org.beyene.ledger.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileLedgerProvider implements LedgerProvider {

    private static final Logger LOGGER = Logger.getLogger(FileLedgerProvider.class.getName());

    @Override
    public <M, D> Ledger<M, D> newLedger(Serializer<M, D> serializer,
                                         Deserializer<M, D> deserializer,
                                         Format<D> format,
                                         Map<String, TransactionListener<M>> listeners,
                                         Map<String, Object> properties) {
        Objects.requireNonNull(serializer);
        Objects.requireNonNull(deserializer);
        Objects.requireNonNull(format);
        Objects.requireNonNull(listeners);
        Objects.requireNonNull(properties);

        String path = Objects.toString(properties.get("file.directory"));
        Objects.requireNonNull(path, "file.directory is not set!");

        Path directory = Paths.get(path);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            LOGGER.log(Level.INFO, e.toString(), e);
            throw new IllegalStateException("file.directory could not be created: " + path, e);
        }

        return new FileLedger<>(serializer, deserializer, format, directory);
    }
}
