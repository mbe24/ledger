package org.beyene.ledger.iota.util;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import jota.IotaAPIService;
import jota.IotaLocalPoW;
import jota.dto.request.*;
import jota.dto.response.*;
import jota.error.ArgumentException;
import jota.model.Transaction;
import jota.utils.Checksum;
import jota.utils.InputValidator;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class IotaAPICoreExtended implements IotaCore {

    private static final Logger log = LoggerFactory.getLogger(IotaAPICoreExtended.class);

    private IotaAPIService service;
    private String protocol;
    private String host;
    private String port;
    private IotaLocalPoW localPoW;

    protected <T extends IotaAPICoreExtended.Builder<T>> IotaAPICoreExtended(IotaAPICoreExtended.Builder<T> builder) {
        this.protocol = builder.protocol;
        this.host = builder.host;
        this.port = builder.port;
        this.localPoW = builder.localPoW;

        InetSocketAddress socket = new InetSocketAddress(host, Integer.valueOf(port));
        this.service = constructService(protocol, socket, builder.httpClientModifier);
    }

    protected static <T> Response<T> wrapCheckedException(Call<T> call) {
        try {
            Response<T> response = call.execute();
            String error = "";
            ResponseBody responseBody = response.errorBody();
            if (responseBody != null) {
                error = responseBody.string();
            }

            if (response.code() == 400) {
                try {
                    throw new ArgumentException(error);
                } catch (ArgumentException var4) {
                    var4.printStackTrace();
                }
            } else {
                if (response.code() == 401) {
                    throw new IllegalAccessError("401 " + error);
                }

                if (response.code() == 500) {
                    throw new IllegalAccessError("500 " + error);
                }
            }

            return response;
        } catch (IOException var5) {
            log.error("Execution of the API call raised exception. IOTA Node not reachable?", var5);
            throw new IllegalStateException(var5.getMessage());
        }
    }

    private static String env(String env, String def) {
        String value = System.getenv(env);
        if (value == null) {
            log.warn("Environment variable \'{}\' is not defined, and actual value has not been specified. Rolling back to default value: \'{}\'", env, def);
            return def;
        } else {
            return value;
        }
    }

    protected IotaAPIService constructService(String protocol, InetSocketAddress socket, Consumer<okhttp3.OkHttpClient.Builder> httpClientModifier) {
        String nodeUrl = protocol + "://" + socket.getHostString() + ":" + socket.getPort();

        okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder();
        httpClientModifier.accept(builder);

        OkHttpClient client = builder
                .readTimeout(5000L, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    Request newRequest = request.newBuilder().addHeader("X-IOTA-API-Version", "1").build();
                    return chain.proceed(newRequest);
                })
                .connectTimeout(5000L, TimeUnit.SECONDS).build();
        Retrofit retrofit = (new retrofit2.Retrofit.Builder()).baseUrl(nodeUrl).addConverterFactory(GsonConverterFactory.create()).client(client).build();

        log.debug("Jota-API Java proxy pointing to node url: \'{}\'", nodeUrl);
        return retrofit.create(IotaAPIService.class);
    }

    @Override
    public GetNodeInfoResponse getNodeInfo() {
        Call<GetNodeInfoResponse> res = this.service.getNodeInfo(IotaCommandRequest.createNodeInfoRequest());
        return wrapCheckedException(res).body();
    }

    @Override
    public GetNeighborsResponse getNeighbors() {
        Call<GetNeighborsResponse> res = this.service.getNeighbors(IotaCommandRequest.createGetNeighborsRequest());
        return wrapCheckedException(res).body();
    }

    @Override
    public AddNeighborsResponse addNeighbors(String... uris) {
        Call<AddNeighborsResponse> res = this.service.addNeighbors(IotaNeighborsRequest.createAddNeighborsRequest(uris));
        return wrapCheckedException(res).body();
    }

    @Override
    public RemoveNeighborsResponse removeNeighbors(String... uris) {
        Call<RemoveNeighborsResponse> res = this.service.removeNeighbors(IotaNeighborsRequest.createRemoveNeighborsRequest(uris));
        return wrapCheckedException(res).body();
    }

    @Override
    public GetTipsResponse getTips() {
        Call<GetTipsResponse> res = this.service.getTips(IotaCommandRequest.createGetTipsRequest());
        return wrapCheckedException(res).body();
    }

    @Override
    public FindTransactionResponse findTransactions(String[] addresses, String[] tags, String[] approvees, String[] bundles) {
        IotaFindTransactionsRequest findTransRequest = IotaFindTransactionsRequest.createFindTransactionRequest().byAddresses(addresses).byTags(tags).byApprovees(approvees).byBundles(bundles);
        Call<FindTransactionResponse> res = this.service.findTransactions(findTransRequest);
        return wrapCheckedException(res).body();
    }

    @Override
    public FindTransactionResponse findTransactionsByAddresses(String... addresses) throws ArgumentException {
        List<String> addressesWithoutChecksum = new ArrayList<>();

        for (String address : addresses) {
            String addressO = Checksum.removeChecksum(address);
            addressesWithoutChecksum.add(addressO);
        }

        return this.findTransactions(addressesWithoutChecksum.toArray(new String[addressesWithoutChecksum.size()]), null, null, null);
    }

    @Override
    public FindTransactionResponse findTransactionsByBundles(String... bundles) {
        return this.findTransactions(null, null, null, bundles);
    }

    @Override
    public FindTransactionResponse findTransactionsByApprovees(String... approvees) {
        return this.findTransactions(null, null, approvees, null);
    }

    @Override
    public FindTransactionResponse findTransactionsByDigests(String... digests) {
        return this.findTransactions(null, digests, null, null);
    }

    @Override
    public GetInclusionStateResponse getInclusionStates(String[] transactions, String[] tips) throws ArgumentException {
        if (!InputValidator.isArrayOfHashes(transactions)) {
            throw new ArgumentException("Invalid hashes provided.");
        } else if (!InputValidator.isArrayOfHashes(tips)) {
            throw new ArgumentException("Invalid hashes provided.");
        } else {
            Call<GetInclusionStateResponse> res = this.service.getInclusionStates(IotaGetInclusionStateRequest.createGetInclusionStateRequest(transactions, tips));
            return wrapCheckedException(res).body();
        }
    }

    @Override
    public GetTrytesResponse getTrytes(String... hashes) throws ArgumentException {
        if (!InputValidator.isArrayOfHashes(hashes)) {
            throw new ArgumentException("Invalid hashes provided.");
        } else {
            Call<GetTrytesResponse> res = this.service.getTrytes(IotaGetTrytesRequest.createGetTrytesRequest(hashes));
            return wrapCheckedException(res).body();
        }
    }

    @Override
    public GetTransactionsToApproveResponse getTransactionsToApprove(int depth) {
        return getTransactionsToApprove(depth, null);
    }

    @Override
    public GetTransactionsToApproveResponse getTransactionsToApprove(int depth, String reference) {
        Call<GetTransactionsToApproveResponse> res = this.service.getTransactionsToApprove(IotaGetTransactionsToApproveRequest.createIotaGetTransactionsToApproveRequest(depth, reference));
        return wrapCheckedException(res).body();
    }

    /**
     * Similar to getInclusionStates.
     *
     * @param threshold The confirmation threshold, should be set to 100.
     * @param addresses The array list of addresses you want to get the confirmed balance from.
     * @param tips      The starting points we walk back from to find the balance of the addresses
     * @return The confirmed balance which a list of addresses have at the latest confirmed milestone.
     * @throws ArgumentException
     */
    private GetBalancesResponse getBalances(int threshold, String[] addresses, String[] tips) throws ArgumentException {
        final Call<GetBalancesResponse> res = service.getBalances(IotaGetBalancesRequest.createIotaGetBalancesRequest(threshold, addresses, tips));
        return wrapCheckedException(res).body();
    }

    /**
     * Similar to getInclusionStates.
     *
     * @param threshold The confirmation threshold, should be set to 100.
     * @param addresses The list of addresses you want to get the confirmed balance from.
     * @param tips      The starting points we walk back from to find the balance of the addresses
     * @return The confirmed balance which a list of addresses have at the latest confirmed milestone.
     */
    public GetBalancesResponse getBalances(int threshold, List<String> addresses, List<String> tips) throws ArgumentException {

        List<String> addressesWithoutChecksum = new ArrayList<>();

        for (String address : addresses) {
            String addressO = Checksum.removeChecksum(address);
            addressesWithoutChecksum.add(addressO);
        }
        String[] tipsArray = tips != null ? tips.toArray(new String[tips.size()]) : null;
        return getBalances(threshold, addressesWithoutChecksum.toArray(new String[addressesWithoutChecksum.size()]), tipsArray);
    }

    /**
     * Similar to getInclusionStates.
     *
     * @param threshold The confirmation threshold, should be set to 100.
     * @param addresses The list of addresses you want to get the confirmed balance from.
     * @return The confirmed balance which a list of addresses have at the latest confirmed milestone.
     */
    public GetBalancesResponse getBalances(int threshold, List<String> addresses) throws ArgumentException {
        return getBalances(threshold, addresses, null);
    }

    @Override
    public GetAttachToTangleResponse attachToTangle(String trunkTransaction, String branchTransaction, int minWeightMagnitude, String... trytes) throws ArgumentException {
        if (!InputValidator.isHash(trunkTransaction)) {
            throw new ArgumentException("Invalid hashes provided.");
        } else if (!InputValidator.isHash(branchTransaction)) {
            throw new ArgumentException("Invalid hashes provided.");
        } else if (!InputValidator.isArrayOfTrytes(trytes)) {
            throw new ArgumentException("Invalid trytes provided.");
        } else if (this.localPoW == null) {
            Call<GetAttachToTangleResponse> var9 = this.service.attachToTangle(IotaAttachToTangleRequest.createAttachToTangleRequest(trunkTransaction, branchTransaction, minWeightMagnitude, trytes));
            return wrapCheckedException(var9).body();
        } else {
            String[] res = new String[trytes.length];
            String previousTransaction = null;

            for (int i = 0; i < trytes.length; ++i) {
                Transaction txn = new Transaction(trytes[i]);
                txn.setTrunkTransaction(previousTransaction == null ? trunkTransaction : previousTransaction);
                txn.setBranchTransaction(previousTransaction == null ? branchTransaction : trunkTransaction);
                if (txn.getTag().isEmpty() || txn.getTag().matches("9*")) {
                    txn.setTag(txn.getObsoleteTag());
                }

                txn.setAttachmentTimestamp(System.currentTimeMillis());
                txn.setAttachmentTimestampLowerBound(0L);
                txn.setAttachmentTimestampUpperBound(3812798742493L);
                res[i] = this.localPoW.performPoW(txn.toTrytes(), minWeightMagnitude);
                previousTransaction = (new Transaction(res[i])).getHash();
            }

            return new GetAttachToTangleResponse(res);
        }
    }

    @Override
    public InterruptAttachingToTangleResponse interruptAttachingToTangle() {
        Call<InterruptAttachingToTangleResponse> res = this.service.interruptAttachingToTangle(IotaCommandRequest.createInterruptAttachToTangleRequest());
        return wrapCheckedException(res).body();
    }

    @Override
    public BroadcastTransactionsResponse broadcastTransactions(String... trytes) throws ArgumentException {
        if (!InputValidator.isArrayOfAttachedTrytes(trytes)) {
            throw new ArgumentException("Invalid attached trytes provided.");
        } else {
            Call<BroadcastTransactionsResponse> res = this.service.broadcastTransactions(IotaBroadcastTransactionRequest.createBroadcastTransactionsRequest(trytes));
            return wrapCheckedException(res).body();
        }
    }

    @Override
    public StoreTransactionsResponse storeTransactions(String... trytes) {
        Call<StoreTransactionsResponse> res = this.service.storeTransactions(IotaStoreTransactionsRequest.createStoreTransactionsRequest(trytes));
        return wrapCheckedException(res).body();
    }

    @Override
    public String getProtocol() {
        return this.protocol;
    }

    @Override
    public String getHost() {
        return this.host;
    }

    @Override
    public String getPort() {
        return this.port;
    }

    public static class Builder<T extends IotaAPICoreExtended.Builder<T>> {
        protected String protocol;
        protected String host;
        protected String port;
        protected IotaLocalPoW localPoW;
        protected Consumer<okhttp3.OkHttpClient.Builder> httpClientModifier = builder -> {
        };

        private FileReader fileReader = null;
        private BufferedReader bufferedReader = null;
        private Properties nodeConfig = null;

        @SuppressWarnings("unchecked")
        public T self() {
            return (T) this;
        }

        public IotaAPICoreExtended build() {
            readConfigIfNotSet();
            return new IotaAPICoreExtended(this);
        }

        private String getFromConfigurationOrEnvironment(String propertyKey, String envName, String defaultValue) {
            return this.getNodeConfig().getProperty(propertyKey) != null ? this.nodeConfig.getProperty(propertyKey) : IotaAPICoreExtended.env(envName, defaultValue);
        }

        private Properties getNodeConfig() {
            if (null != this.nodeConfig) {
                return this.nodeConfig;
            } else {
                this.nodeConfig = new Properties();
                if (null == this.fileReader) {
                    try {
                        this.fileReader = new FileReader("../node_config.properties");
                        if (null == this.bufferedReader) {
                            this.bufferedReader = new BufferedReader(this.fileReader);
                        }

                        this.nodeConfig.load(this.bufferedReader);
                    } catch (IOException var2) {
                        IotaAPICoreExtended.log.debug("node_config.properties not found. Rolling back for another solution...");
                    }
                }

                return this.nodeConfig;
            }
        }

        public final T readConfigIfNotSet() {
            if (null == this.protocol) {
                this.protocol = this.getFromConfigurationOrEnvironment("iota.node.protocol", "IOTA_NODE_PROTOCOL", "http");
            }

            if (null == this.host) {
                this.host = this.getFromConfigurationOrEnvironment("iota.node.host", "IOTA_NODE_HOST", "localhost");
            }

            if (null == this.port) {
                this.port = this.getFromConfigurationOrEnvironment("iota.node.port", "IOTA_NODE_PORT", "14265");
            }

            return self();
        }

        public T config(Properties properties) {
            this.nodeConfig = properties;
            return self();
        }

        public T host(String host) {
            this.host = host;
            return self();
        }

        public T port(String port) {
            this.port = port;
            return self();
        }

        public T protocol(String protocol) {
            this.protocol = protocol;
            return self();
        }

        public T localPoW(IotaLocalPoW localPoW) {
            this.localPoW = localPoW;
            return self();
        }

        public T httpClientModifier(Consumer<okhttp3.OkHttpClient.Builder> httpClientModifier) {
            Objects.requireNonNull(httpClientModifier);
            this.httpClientModifier = httpClientModifier;
            return self();
        }
    }
}
