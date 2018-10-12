package org.beyene.ledger.iota;

import cfb.pearldiver.PearlDiverLocalPoW;
import jota.IotaLocalPoW;
import org.beyene.ledger.api.*;
import org.beyene.ledger.iota.util.Iota;
import org.beyene.ledger.iota.util.IotaAPIExtended;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IotaLedgerProvider implements LedgerProvider {

    private static final Logger LOGGER = Logger.getLogger(IotaLedgerProvider.class.getName());

    private final NumberFormat nf = NumberFormat.getInstance();

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

        checkConfiguration(properties);

        IotaLocalPoW localPoW = new PearlDiverLocalPoW();
        IotaAPIExtended.Builder apiBuilder = new IotaAPIExtended.Builder();
        apiBuilder
                .protocol(Objects.toString(properties.get("iota.node.protocol")))
                .host(Objects.toString(properties.get("iota.node.host")))
                .port(Objects.toString(properties.get("iota.node.port")))
                .localPoW(localPoW);

        boolean sslDisabled = Boolean.valueOf(Objects.toString(properties.get("ssl.check.disable")));
        if (sslDisabled) {
            apiBuilder.httpClientModifier(this::disableCertificateCheck);
        }

        Iota api = apiBuilder.build();
        IotaLedger.Builder<M, D> builder = new IotaLedger.Builder<>();

        setNumber(properties.get("ledger.poll.interval"), Number::intValue, builder::setPollInterval);
        setNumber(properties.get("tx.push.threshold"), Number::intValue, builder::setPushIntervall);
        setNumber(properties.get("ledger.pool.threads"), Number::intValue, builder::setPoolThreads);
        setNumber(properties.get("ledger.receive.slidingwindow"), Number::intValue, builder::setSlidingWindow);
        setNumber(properties.get("ledger.receive.hash.cache"), Number::intValue, builder::setHashCacheSize);
        setNumber(properties.get("ledger.fragments.alive"), Number::intValue, builder::setKeepFragmentsAlive);

        MessageSender<M> messageSender = new DefaultMessageSender.Builder<M, D>()
                .setApi(api)
                .setFormat(format)
                .setSerializer(serializer)
                .setTipAnalysisDepth(3)
                .setMinWeightMagnitude(13)
                // TODO
                // what about the address
                //.setAddress("")
                .setUseConfiguredAddress(false)
                .build();

        return builder
                .setApi(api)
                .setMessageSender(messageSender)
                .setFormat(format)
                .setSerializer(serializer)
                .setDeserializer(deserializer)
                .setListeners(listeners)
                .setListenerThreads(2)
                .setPushThreshold(Instant.now())
                .build();
    }

    private <T extends Number> void setNumber(Object value, Function<Number, T> converter, Consumer<T> setter) {
        if (Objects.isNull(value))
            return;

        Number number = parseNumber(Objects.toString(value));
        setter.accept(converter.apply(number));
    }

    private Number parseNumber(String source) throws IllegalArgumentException {
        try {
            return nf.parse(source);
        } catch (ParseException e) {
            LOGGER.log(Level.INFO, e.toString(), e);
            throw new IllegalArgumentException("Not a valid number: " + source, e);
        }
    }

    private void checkConfiguration(Map<String, Object> properties) {
        checkKey("iota.node.protocol", properties);
        checkKey("iota.node.host", properties);
        checkKey("iota.node.port", properties);
    }

    private void checkKey(String key, Map<String, Object> properties) {
        if (!properties.containsKey(key))
            throw new IllegalArgumentException(String.format("Property '%s' is missing!", key));
    }

    private void disableCertificateCheck(okhttp3.OkHttpClient.Builder builder) {
        try {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustManagers = {new AllPermittingX509TrustManager()};

            // Install the all-trusting trust manager
            SSLContext context = SSLContext.getInstance("SSL");
            context.init(null, trustManagers, new java.security.SecureRandom());
            //HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());

            // Create an ssl socket factory with our all-trusting manager
            SSLSocketFactory socketFactory = context.getSocketFactory();
            builder.sslSocketFactory(socketFactory, new AllPermittingX509TrustManager());
            builder.hostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            LOGGER.log(Level.INFO, e.toString(), e);
            throw new IllegalStateException("Disabling trust manager failed!", e);
        }
    }

    private static class AllPermittingX509TrustManager implements X509TrustManager {

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        }
    }
}
