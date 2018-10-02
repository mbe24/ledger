package org.beyene.ledger.iota.util;

import jota.dto.response.*;
import jota.error.ArgumentException;

import java.util.List;

@SuppressWarnings("unused")
public interface IotaCore {
    GetNodeInfoResponse getNodeInfo();

    GetNeighborsResponse getNeighbors();

    AddNeighborsResponse addNeighbors(String... uris);

    RemoveNeighborsResponse removeNeighbors(String... uris);

    GetTipsResponse getTips();

    FindTransactionResponse findTransactions(String[] addresses, String[] tags, String[] approvees, String[] bundles);

    FindTransactionResponse findTransactionsByAddresses(String... addresses) throws ArgumentException;

    FindTransactionResponse findTransactionsByBundles(String... bundles);

    FindTransactionResponse findTransactionsByApprovees(String... approvees);

    FindTransactionResponse findTransactionsByDigests(String... digests);

    GetInclusionStateResponse getInclusionStates(String[] transactions, String[] tips) throws ArgumentException;

    GetTrytesResponse getTrytes(String... hashes) throws ArgumentException;

    GetTransactionsToApproveResponse getTransactionsToApprove(int depth);

    GetBalancesResponse getBalances(int threshold, List<String> addresses) throws ArgumentException;

    GetAttachToTangleResponse attachToTangle(String trunkTransaction, String branchTransaction, int minWeightMagnitude, String... trytes) throws ArgumentException;

    InterruptAttachingToTangleResponse interruptAttachingToTangle();

    BroadcastTransactionsResponse broadcastTransactions(String... trytes) throws ArgumentException;

    StoreTransactionsResponse storeTransactions(String... trytes);

    String getProtocol();

    String getHost();

    String getPort();
}
