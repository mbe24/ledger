package org.beyene.ledger.iota;

import jota.error.ArgumentException;
import jota.model.Bundle;
import jota.utils.Constants;
import org.apache.commons.lang3.StringUtils;
import org.beyene.ledger.api.Format;
import org.beyene.ledger.api.Serializer;
import org.beyene.ledger.api.Transaction;
import org.beyene.ledger.iota.util.Iota;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

class DefaultMessagerSender<M, D> implements MessageSender<M> {

    private static final Logger LOGGER = Logger.getLogger(DefaultMessagerSender.class.getName());

    private final Iota api;
    private final Format<D> format;
    private final Serializer<M, D> serializer;

    private final String address;
    private final boolean useConfiguredAddress;

    private final int depth;
    private final int minWeightMagnitude;

    public DefaultMessagerSender(Builder<M, D> builder) {
        this.api = builder.api;
        this.format = builder.format;
        this.serializer = builder.serializer;
        this.address = builder.address;
        this.useConfiguredAddress = builder.useConfiguredAddress;
        this.depth = builder.depth;
        this.minWeightMagnitude = builder.minWeightMagnitude;
    }

    @Override
    public Transaction<M> addTransaction(Transaction<M> transaction) throws IOException {
        M message = transaction.getObject();
        D serialized = serializer.serialize(message);
        String messageTrytes = toTrytes(serialized);
        List<String> signatureFragments = fragmentData(messageTrytes);

        Instant timestamp = Optional.ofNullable(transaction.getTimestamp()).orElse(Instant.now());
        String[] txTrytes = createTransactionTrytes(signatureFragments, transaction, timestamp);

        try {
            String reference = null;
            api.sendTrytes(txTrytes, depth, minWeightMagnitude, reference);
        } catch (ArgumentException e) {
            LOGGER.log(Level.INFO, e.toString(), e);
            throw new IOException("Sending transaction failed", e);
        }

        return new MessageTransaction<>(address, timestamp, transaction.getTag(), message);
    }

    private String[] createTransactionTrytes(List<String> signatureFragments, Transaction<M> transaction, Instant timestamp) {
        String address = transaction.getIdentifier();
        if (useConfiguredAddress)
            address = this.address;
        if (address == null || address.isEmpty())
            address = "";

        address = StringUtils.rightPad(address, Constants.ADDRESS_LENGTH_WITHOUT_CHECKSUM, '9');

        String tagRaw = transaction.getTag();
        String tag = StringUtils.rightPad(tagRaw, Constants.TAG_LENGTH, '9');

        long value = 0;
        int signatureMessageLength = signatureFragments.size();
        Bundle bundle = new Bundle();
        bundle.addEntry(signatureMessageLength, address, value, tag, 0);
        bundle.addTrytes(signatureFragments);

        bundle.getTransactions().stream().forEach(this::copyTag);
        bundle.getTransactions().stream().forEach(tx -> fixTimestamps(tx, timestamp.toEpochMilli()));
        bundle.finalize(null);

        List<jota.model.Transaction> transactions = bundle.getTransactions();
        Collections.reverse(transactions);
        return transactions.stream()
                .map(jota.model.Transaction::toTrytes)
                .toArray(String[]::new);
    }

    private String toTrytes(D serialized) {
        String trytes;
        if (String.class.isAssignableFrom(format.getType())) {
            String string = String.class.cast(serialized);
            trytes = jota.utils.TrytesConverter.toTrytes(string);
        } else if (byte[].class.isAssignableFrom(format.getType())) {
            byte[] bytes = byte[].class.cast(serialized);

            // bytes are encoded as hex strings,
            // since byte to trits conversion is incomplete (values 243-255 are not supported)
            // cf. https://iota.stackexchange.com/questions/2159/converting-bytes-to-trites-using-iota-libraries
            String hexBinary = DatatypeConverter.printHexBinary(bytes);
            trytes = jota.utils.TrytesConverter.toTrytes(hexBinary);
        } else {
            throw new IllegalStateException("Unsupported data type: " + format.getType().getName());
        }

        return trytes;
    }

    private List<String> fragmentData(String trytes) {
        List<String> signatureFragments = new ArrayList<>();
        // 2187
        int messageLength = Constants.MESSAGE_LENGTH;

        for (int i = 0; i < trytes.length(); i += messageLength) {
            String fragment = trytes.substring(i, Math.min(i + messageLength, trytes.length()));
            String padded = StringUtils.rightPad(fragment, messageLength, '9');
            signatureFragments.add(padded);
        }

        return signatureFragments;
    }

    private void copyTag(jota.model.Transaction tx) {
        tx.setTag(tx.getObsoleteTag());
    }

    private void fixTimestamps(jota.model.Transaction tx, long timestamp) {
        // cf. com.iota.iri.TransactionValidator#hasInvalidTimestamp in IRI
        tx.setTimestamp(timestamp / 1000);

        tx.setAttachmentTimestamp(timestamp);
        tx.setAttachmentTimestampLowerBound(0);
        tx.setAttachmentTimestampUpperBound(3_812_798_742_493L);
    }

    static class Builder<M, D> {
        private Iota api;
        private Serializer<M, D> serializer;
        private Format<D> format;
        private String address;
        private boolean useConfiguredAddress;
        private int depth;
        private int minWeightMagnitude;

        public Builder<M, D> setApi(Iota api) {
            this.api = api;
            return this;
        }

        public Builder<M, D> setFormat(Format<D> format) {
            this.format = format;
            return this;
        }

        public Builder<M, D> setSerializer(Serializer<M, D> serializer) {
            this.serializer = serializer;
            return this;
        }

        public Builder<M, D> setAddress(String address) {
            this.address = address;
            return this;
        }

        public Builder<M, D> setUseConfiguredAddress(boolean useConfiguredAddress) {
            this.useConfiguredAddress = useConfiguredAddress;
            return this;
        }

        public Builder<M, D> setTipAnalysisDepth(int depth) {
            this.depth = depth;
            return this;
        }

        public Builder<M, D> setMinWeightMagnitude(int minWeightMagnitude) {
            this.minWeightMagnitude = minWeightMagnitude;
            return this;
        }

        public DefaultMessagerSender<M, D> build() {
            return new DefaultMessagerSender<>(this);
        }
    }
}
