package org.beyene.ledger.iota.util;

import jota.dto.response.*;
import jota.error.ArgumentException;
import jota.model.Bundle;
import jota.model.Input;
import jota.model.Transaction;
import jota.model.Transfer;
import jota.utils.StopWatch;

import java.util.List;

public interface Iota extends IotaCore {
    GetNewAddressResponse getNewAddress(String seed, int security, int index, boolean checksum, int total, boolean returnAll) throws ArgumentException;

    GetTransferResponse getTransfers(String seed, int security, int start, int end, boolean inclusionStates) throws ArgumentException;

    Bundle[] bundlesFromAddresses(String[] addresses, boolean inclusionStates) throws ArgumentException;

    StoreTransactionsResponse broadcastAndStore(String... trytes) throws ArgumentException;

    List<Transaction> sendTrytes(String[] trytes, int depth, int minWeightMagnitude) throws ArgumentException;

    List<Transaction> findTransactionsObjectsByHashes(String[] hashes) throws ArgumentException;

    List<Transaction> findTransactionObjectsByAddresses(String[] addresses) throws ArgumentException;

    List<Transaction> findTransactionObjectsByTag(String[] tags) throws ArgumentException;

    List<Transaction> findTransactionObjectsByApprovees(String[] approvees) throws ArgumentException;

    List<Transaction> findTransactionObjectsByBundle(String[] bundles) throws ArgumentException;

    List<String> prepareTransfers(String seed, int security, List<Transfer> transfers, String remainder, List<Input> inputs, boolean validateInputs) throws ArgumentException;

    GetBalancesAndFormatResponse getInputs(String seed, int security, int start, int end, long threshold) throws ArgumentException;

    GetBalancesAndFormatResponse getBalanceAndFormat(List<String> addresses, long threshold, int start, StopWatch stopWatch, int security) throws ArgumentException, IllegalStateException;

    GetBundleResponse getBundle(String transaction) throws ArgumentException;

    GetAccountDataResponse getAccountData(String seed, int security, int index, boolean checksum, int total, boolean returnAll, int start, int end, boolean inclusionStates, long threshold) throws ArgumentException;

    ReplayBundleResponse replayBundle(String transaction, int depth, int minWeightMagnitude) throws ArgumentException;

    GetInclusionStateResponse getLatestInclusion(String[] hashes) throws ArgumentException;

    SendTransferResponse sendTransfer(String seed, int security, int depth, int minWeightMagnitude, List<Transfer> transfers, List<Input> inputs, String remainderAddress, boolean validateInputs) throws ArgumentException;

    Bundle traverseBundle(String trunkTx, String bundleHash, Bundle bundle) throws ArgumentException;

    List<Transaction> initiateTransfer(int securitySum, String inputAddress, String remainderAddress, List<Transfer> transfers, boolean testMode) throws ArgumentException;

    List<String> addRemainder(String seed, int security, List<Input> inputs, Bundle bundle, String tag, long totalValue, String remainderAddress, List<String> signatureFragments) throws ArgumentException;
}
