package org.beyene.ledger.iota.util;

import jota.IotaAPI;
import jota.dto.response.*;
import jota.error.ArgumentException;
import jota.model.Bundle;
import jota.model.Input;
import jota.model.Transaction;
import jota.model.Transfer;
import jota.utils.StopWatch;

import java.util.List;

@SuppressWarnings("unused")
public class IotaAPIAdapter implements Iota {

    private final IotaAPI delegate;

    public IotaAPIAdapter(IotaAPI delegate) {
        this.delegate = delegate;
    }

    @Override
    public GetNewAddressResponse getNewAddress(String seed, int security, int index, boolean checksum, int total, boolean returnAll) throws ArgumentException {
        return delegate.getNewAddress(seed, security, index, checksum, total, returnAll);
    }

    @Override
    public GetTransferResponse getTransfers(String seed, int security, int start, int end, boolean inclusionStates) throws ArgumentException {
        return delegate.getTransfers(seed, security, start, end, inclusionStates);
    }

    @Override
    public Bundle[] bundlesFromAddresses(String[] addresses, boolean inclusionStates) throws ArgumentException {
        return delegate.bundlesFromAddresses(addresses, inclusionStates);
    }

    @Override
    public StoreTransactionsResponse broadcastAndStore(String... trytes) throws ArgumentException {
        return delegate.broadcastAndStore(trytes);
    }

    @Override
    public List<Transaction> sendTrytes(String[] trytes, int depth, int minWeightMagnitude) throws ArgumentException {
        return delegate.sendTrytes(trytes, depth, minWeightMagnitude);
    }

    @Override
    public List<Transaction> findTransactionsObjectsByHashes(String[] hashes) throws ArgumentException {
        return delegate.findTransactionsObjectsByHashes(hashes);
    }

    @Override
    public List<Transaction> findTransactionObjectsByAddresses(String[] addresses) throws ArgumentException {
        return delegate.findTransactionObjectsByAddresses(addresses);
    }

    @Override
    public List<Transaction> findTransactionObjectsByTag(String[] tags) throws ArgumentException {
        return delegate.findTransactionObjectsByTag(tags);
    }

    @Override
    public List<Transaction> findTransactionObjectsByApprovees(String[] approvees) throws ArgumentException {
        return delegate.findTransactionObjectsByApprovees(approvees);
    }

    @Override
    public List<Transaction> findTransactionObjectsByBundle(String[] bundles) throws ArgumentException {
        return delegate.findTransactionObjectsByBundle(bundles);
    }

    @Override
    public List<String> prepareTransfers(String seed, int security, List<Transfer> transfers, String remainder, List<Input> inputs, boolean validateInputs) throws ArgumentException {
        return delegate.prepareTransfers(seed, security, transfers, remainder, inputs, validateInputs);
    }

    @Override
    public GetBalancesAndFormatResponse getInputs(String seed, int security, int start, int end, long threshold) throws ArgumentException {
        return delegate.getInputs(seed, security, start, end, threshold);
    }

    @Override
    public GetBalancesAndFormatResponse getBalanceAndFormat(List<String> addresses, long threshold, int start, StopWatch stopWatch, int security) throws ArgumentException, IllegalStateException {
        return delegate.getBalanceAndFormat(addresses, threshold, start, stopWatch, security);
    }

    @Override
    public GetBundleResponse getBundle(String transaction) throws ArgumentException {
        return delegate.getBundle(transaction);
    }

    @Override
    public GetAccountDataResponse getAccountData(String seed, int security, int index, boolean checksum, int total, boolean returnAll, int start, int end, boolean inclusionStates, long threshold) throws ArgumentException {
        return delegate.getAccountData(seed, security, index, checksum, total, returnAll, start, end, inclusionStates, threshold);
    }

    @Override
    public ReplayBundleResponse replayBundle(String transaction, int depth, int minWeightMagnitude) throws ArgumentException {
        return delegate.replayBundle(transaction, depth, minWeightMagnitude);
    }

    @Override
    public GetInclusionStateResponse getLatestInclusion(String[] hashes) throws ArgumentException {
        return delegate.getLatestInclusion(hashes);
    }

    @Override
    public SendTransferResponse sendTransfer(String seed, int security, int depth, int minWeightMagnitude, List<Transfer> transfers, List<Input> inputs, String remainderAddress, boolean validateInputs) throws ArgumentException {
        return delegate.sendTransfer(seed, security, depth, minWeightMagnitude, transfers, inputs, remainderAddress, validateInputs);
    }

    @Override
    public Bundle traverseBundle(String trunkTx, String bundleHash, Bundle bundle) throws ArgumentException {
        return delegate.traverseBundle(trunkTx, bundleHash, bundle);
    }

    @Override
    public List<Transaction> initiateTransfer(int securitySum, String inputAddress, String remainderAddress, List<Transfer> transfers, boolean testMode) throws ArgumentException {
        return delegate.initiateTransfer(securitySum, inputAddress, remainderAddress, transfers, testMode);
    }

    @Override
    public List<String> addRemainder(String seed, int security, List<Input> inputs, Bundle bundle, String tag, long totalValue, String remainderAddress, List<String> signatureFragments) throws ArgumentException {
        return delegate.addRemainder(seed, security, inputs, bundle, tag, totalValue, remainderAddress, signatureFragments);
    }

    @Override
    public GetNodeInfoResponse getNodeInfo() {
        return delegate.getNodeInfo();
    }

    @Override
    public GetNeighborsResponse getNeighbors() {
        return delegate.getNeighbors();
    }

    @Override
    public AddNeighborsResponse addNeighbors(String... uris) {
        return delegate.addNeighbors(uris);
    }

    @Override
    public RemoveNeighborsResponse removeNeighbors(String... uris) {
        return delegate.removeNeighbors(uris);
    }

    @Override
    public GetTipsResponse getTips() {
        return delegate.getTips();
    }

    @Override
    public FindTransactionResponse findTransactions(String[] addresses, String[] tags, String[] approvees, String[] bundles) {
        return delegate.findTransactions(addresses, tags, approvees, bundles);
    }

    @Override
    public FindTransactionResponse findTransactionsByAddresses(String... addresses) throws ArgumentException {
        return delegate.findTransactionsByAddresses(addresses);
    }

    @Override
    public FindTransactionResponse findTransactionsByBundles(String... bundles) {
        return delegate.findTransactionsByBundles(bundles);
    }

    @Override
    public FindTransactionResponse findTransactionsByApprovees(String... approvees) {
        return delegate.findTransactionsByApprovees(approvees);
    }

    @Override
    public FindTransactionResponse findTransactionsByDigests(String... digests) {
        return delegate.findTransactionsByDigests(digests);
    }

    @Override
    public GetInclusionStateResponse getInclusionStates(String[] transactions, String[] tips) throws ArgumentException {
        return delegate.getInclusionStates(transactions, tips);
    }

    @Override
    public GetTrytesResponse getTrytes(String... hashes) throws ArgumentException {
        return delegate.getTrytes(hashes);
    }

    @Override
    public GetTransactionsToApproveResponse getTransactionsToApprove(int depth) {
        return delegate.getTransactionsToApprove(depth);
    }

    @Override
    public GetBalancesResponse getBalances(int threshold, List<String> addresses) throws ArgumentException {
        return delegate.getBalances(threshold, addresses);
    }

    @Override
    public GetAttachToTangleResponse attachToTangle(String trunkTransaction, String branchTransaction, int minWeightMagnitude, String... trytes) throws ArgumentException {
        return delegate.attachToTangle(trunkTransaction, branchTransaction, minWeightMagnitude, trytes);
    }

    @Override
    public InterruptAttachingToTangleResponse interruptAttachingToTangle() {
        return delegate.interruptAttachingToTangle();
    }

    @Override
    public BroadcastTransactionsResponse broadcastTransactions(String... trytes) throws ArgumentException {
        return delegate.broadcastTransactions(trytes);
    }

    @Override
    public StoreTransactionsResponse storeTransactions(String... trytes) {
        return delegate.storeTransactions(trytes);
    }

    @Override
    public String getProtocol() {
        return delegate.getProtocol();
    }

    @Override
    public String getHost() {
        return delegate.getHost();
    }

    @Override
    public String getPort() {
        return delegate.getPort();
    }
}