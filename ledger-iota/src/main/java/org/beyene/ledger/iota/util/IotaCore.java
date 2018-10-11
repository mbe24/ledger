package org.beyene.ledger.iota.util;

import jota.dto.response.*;
import jota.error.ArgumentException;

import java.util.List;

public interface IotaCore {
    GetNodeInfoResponse getNodeInfo() throws ArgumentException;

    GetNeighborsResponse getNeighbors() throws ArgumentException;

    AddNeighborsResponse addNeighbors(String... uris) throws ArgumentException;

    RemoveNeighborsResponse removeNeighbors(String... uris) throws ArgumentException;

    GetTipsResponse getTips() throws ArgumentException;

    FindTransactionResponse findTransactions(String[] addresses, String[] tags, String[] approvees, String[] bundles) throws ArgumentException;

    FindTransactionResponse findTransactionsByAddresses(String... addresses) throws ArgumentException;

    FindTransactionResponse findTransactionsByBundles(String... bundles) throws ArgumentException;

    FindTransactionResponse findTransactionsByApprovees(String... approvees) throws ArgumentException;

    FindTransactionResponse findTransactionsByDigests(String... digests) throws ArgumentException;

    GetInclusionStateResponse getInclusionStates(String[] transactions, String[] tips) throws ArgumentException;

    GetTrytesResponse getTrytes(String... hashes) throws ArgumentException;

    GetTransactionsToApproveResponse getTransactionsToApprove(int depth, String reference) throws ArgumentException;

    GetTransactionsToApproveResponse getTransactionsToApprove(int depth) throws ArgumentException;

    GetBalancesResponse getBalances(int threshold, List<String> addresses) throws ArgumentException;

    GetBalancesResponse getBalances(int threshold, List<String> addresses, List<String> tips) throws ArgumentException;

    GetAttachToTangleResponse attachToTangle(String trunkTransaction, String branchTransaction, int minWeightMagnitude, String... trytes) throws ArgumentException;

    InterruptAttachingToTangleResponse interruptAttachingToTangle() throws ArgumentException;

    BroadcastTransactionsResponse broadcastTransactions(String... trytes) throws ArgumentException;

    StoreTransactionsResponse storeTransactions(String... trytes) throws ArgumentException;

    String getProtocol();

    String getHost();

    String getPort();
}
