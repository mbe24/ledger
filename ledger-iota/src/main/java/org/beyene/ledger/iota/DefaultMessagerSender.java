package org.beyene.ledger.iota;

import jota.error.ArgumentException;
import jota.model.Bundle;
import jota.utils.Constants;
import org.apache.commons.lang3.StringUtils;
import org.beyene.ledger.api.Format;
import org.beyene.ledger.api.Mapper;
import org.beyene.ledger.api.Transaction;
import org.beyene.ledger.iota.util.Iota;

import javax.xml.bind.DatatypeConverter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

class DefaultMessagerSender<M, D> implements MessageSender<M> {

    private static final Logger LOGGER = Logger.getLogger(DefaultMessagerSender.class.getName());

    private final Iota api;
    private final Format<D> format;
    private final Mapper<M, D> mapper;

    public DefaultMessagerSender(Builder builder) {
        this.api = builder.api;
        this.format = builder.format;
        this.mapper = builder.mapper;
    }

    @Override
    public Transaction<M> addTransaction(M message) {
        D serialized = mapper.serialize(message);

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

        List<String> signatureFragments = fragmentData(trytes);

        int signatureMessageLength = signatureFragments.size();
        // TODO read address that should be used here
        String address = StringUtils.rightPad("", Constants.ADDRESS_LENGTH_WITHOUT_CHECKSUM, '9');
        long value = 0;

        // TODO read tag from parameter
        // wrap message object in Transaction
        String tagRaw = "BEYENE";
        String tag = StringUtils.rightPad(tagRaw, Constants.TAG_LENGTH, '9');

        Bundle bundle = new Bundle();
        bundle.addEntry(signatureMessageLength, address, value, tag, 0);
        bundle.addTrytes(signatureFragments);

        bundle.getTransactions().stream().forEach(this::copyTag);

        // TODO same timestamp for all transactions of bundle
        Instant timestamp = Instant.now();
        bundle.getTransactions().stream().forEach(tx -> fixTimestamps(tx, timestamp.toEpochMilli()));
        bundle.finalize(null);

        // TODO maybe read from builder
        int depth = 5;

        List<jota.model.Transaction> transactions = bundle.getTransactions();
        Collections.reverse(transactions);
        String[] txTrytes = transactions.stream().map(jota.model.Transaction::toTrytes).toArray(String[]::new);

        // TODO read from builder
        int minWeightMagnitude = 13;

        List<jota.model.Transaction> sentTxs = null;
        try {
            sentTxs = api.sendTrytes(txTrytes, depth, minWeightMagnitude);
        } catch (ArgumentException e) {
            e.printStackTrace();
            // TODO throw some exception
        }

        //Collections.reverse(sentTxs);
        //System.out.println(sentTxs);

        return new MessageTransaction<>(address, timestamp, tag, message);
    }

    private List<String> fragmentData(String trytes) {
        List<String> signatureFragments = new ArrayList<>();
        int messageLength = Constants.MESSAGE_LENGTH;
        for (int i = 0; i < trytes.length(); i += messageLength) {
            String fragment = trytes.substring(i, Math.min(i + messageLength, trytes.length()));
            signatureFragments.add(StringUtils.rightPad(fragment, messageLength, '9'));
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
        private Mapper<M, D> mapper;
        private Format<D> format;

        public Builder setApi(Iota api) {
            this.api = api;
            return this;
        }

        public Builder setFormat(Format<D> format) {
            this.format = format;
            return this;
        }

        public Builder setMapper(Mapper<M, D> mapper) {
            this.mapper = mapper;
            return this;
        }

        public DefaultMessagerSender build() {
            return new DefaultMessagerSender(this);
        }
    }
}
