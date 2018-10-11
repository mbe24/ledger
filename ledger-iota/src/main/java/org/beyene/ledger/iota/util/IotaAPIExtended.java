package org.beyene.ledger.iota.util;

import jota.dto.response.*;
import jota.error.ArgumentException;
import jota.model.*;
import jota.pow.ICurl;
import jota.pow.SpongeFactory;
import jota.utils.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static jota.utils.Constants.*;

/**
 * IotaAPI Builder. Usage:
 * <p>
 * {@code IotaApiProxy api = IotaApiProxy.Builder}
 * {@code .protocol("http")}
 * {@code .nodeAddress("localhost")}
 * {@code .port(12345)}
 * {@code .build();}
 * <p>
 * {@code GetNodeInfoResponse response = api.getNodeInfo();}
 *
 * @author davassi
 */
public class IotaAPIExtended extends IotaAPICoreExtended implements Iota {

    private static final Logger log = LoggerFactory.getLogger(IotaAPICoreExtended.class);

    private ICurl customCurl;

    protected IotaAPIExtended(Builder builder) {
        super(builder);
        this.customCurl = builder.customCurl;
    }

    @Override
    public GetNewAddressResponse getNewAddress(String seed, int security, int index, boolean checksum, int total, boolean returnAll) throws ArgumentException {
        StopWatch stopWatch = new StopWatch();
        List<String> allAddresses = new ArrayList<>();
        int i;
        if (total != 0) {
            for (i = index; i < index + total; ++i) {
                allAddresses.add(IotaAPIUtils.newAddress(seed, security, i, checksum, this.customCurl.clone()));
            }

            return GetNewAddressResponse.create(allAddresses, stopWatch.getElapsedTimeMili());
        } else {
            i = index;

            while (true) {
                String newAddress = IotaAPIUtils.newAddress(seed, security, i, checksum, this.customCurl.clone());
                FindTransactionResponse response = this.findTransactionsByAddresses(new String[]{newAddress});
                allAddresses.add(newAddress);
                if (response.getHashes().length == 0) {
                    if (!returnAll) {
                        allAddresses = allAddresses.subList(allAddresses.size() - 1, allAddresses.size());
                    }

                    return GetNewAddressResponse.create(allAddresses, stopWatch.getElapsedTimeMili());
                }

                ++i;
            }
        }
    }

    @Override
    public GetTransferResponse getTransfers(String seed, int security, int start, int end, boolean inclusionStates) throws ArgumentException {
        if (!InputValidator.isValidSeed(seed)) {
            throw new IllegalStateException("Invalid seed provided.");
        } else if (start <= end && end <= start + 500) {
            StopWatch stopWatch = new StopWatch();
            GetNewAddressResponse gnr = this.getNewAddress(seed, security, start, false, end, true);
            if (gnr != null && gnr.getAddresses() != null) {
                Bundle[] bundles = this.bundlesFromAddresses(gnr.getAddresses().toArray(new String[gnr.getAddresses().size()]), inclusionStates);
                return GetTransferResponse.create(bundles, stopWatch.getElapsedTimeMili());
            } else {
                return GetTransferResponse.create(new Bundle[0], stopWatch.getElapsedTimeMili());
            }
        } else {
            throw new ArgumentException("Invalid input provided.");
        }
    }

    @Override
    public Bundle[] bundlesFromAddresses(String[] addresses, final boolean inclusionStates) throws ArgumentException {
        List<Transaction> trxs = this.findTransactionObjectsByAddresses(addresses);
        List<String> tailTransactions = new ArrayList<>();
        List<String> nonTailBundleHashes = new ArrayList<>();
        Iterator<Transaction> bundleObjects = trxs.iterator();

        while (bundleObjects.hasNext()) {
            Transaction finalBundles = bundleObjects.next();
            if (finalBundles.getCurrentIndex() == 0L) {
                tailTransactions.add(finalBundles.getHash());
            } else if (nonTailBundleHashes.indexOf(finalBundles.getBundle()) == -1) {
                nonTailBundleHashes.add(finalBundles.getBundle());
            }
        }

        List<Transaction> var13 = this.findTransactionObjectsByBundle(nonTailBundleHashes.toArray(new String[nonTailBundleHashes.size()]));
        Iterator<Transaction> var14 = var13.iterator();

        while (var14.hasNext()) {
            Transaction tailTxArray = var14.next();
            if (tailTxArray.getCurrentIndex() == 0L && tailTransactions.indexOf(tailTxArray.getHash()) == -1) {
                tailTransactions.add(tailTxArray.getHash());
            }
        }

        final List<Bundle> var15 = new ArrayList<>();
        final String[] var16 = tailTransactions.toArray(new String[tailTransactions.size()]);
        GetInclusionStateResponse gisr = null;
        if (var16.length != 0 && inclusionStates) {
            gisr = this.getLatestInclusion(var16);
            if (gisr == null || gisr.getStates() == null || gisr.getStates().length == 0) {
                throw new IllegalStateException("Get inclusion state response was null.");
            }
        }

        final GetInclusionStateResponse gisr_ = gisr;
        Parallel.For(Arrays.asList(var16), new Parallel.Operation<String>() {
            @Override
            public void perform(String param) {
                try {
                    GetBundleResponse e = IotaAPIExtended.this.getBundle(param);
                    Bundle gbr = new Bundle(e.getTransactions(), e.getTransactions().size());
                    if (gbr.getTransactions() != null) {
                        if (inclusionStates) {
                            boolean thisInclusion = false;
                            if (gisr_ != null) {
                                thisInclusion = gisr_.getStates()[Arrays.asList(var16).indexOf(param)];
                            }

                            Iterator<Transaction> var5 = gbr.getTransactions().iterator();

                            while (var5.hasNext()) {
                                Transaction t = var5.next();
                                t.setPersistence(Boolean.valueOf(thisInclusion));
                            }
                        }

                        var15.add(gbr);
                    }
                } catch (ArgumentException var7) {
                    IotaAPIExtended.log.warn("Get bundle response was null.");
                }

            }
        });
        Collections.sort(var15);
        Bundle[] returnValue = new Bundle[var15.size()];

        for (int i = 0; i < var15.size(); ++i) {
            returnValue[i] = new Bundle(var15.get(i).getTransactions(), var15.get(i).getTransactions().size());
        }

        return returnValue;
    }

    @Override
    public StoreTransactionsResponse broadcastAndStore(String... trytes) throws ArgumentException {
        if (!InputValidator.isArrayOfAttachedTrytes(trytes)) {
            throw new ArgumentException("Invalid trytes provided.");
        } else {
            try {
                this.broadcastTransactions(trytes);
            } catch (Exception var3) {
                throw new ArgumentException(var3.toString());
            }

            return this.storeTransactions(trytes);
        }
    }


    /**
     * Facade method: Gets transactions to approve, attaches to Tangle, broadcasts and stores.
     *
     * @param trytes             The trytes.
     * @param depth              The depth.
     * @param minWeightMagnitude The minimum weight magnitude.
     * @param reference          Hash of transaction to start random-walk from, used to make sure the tips returned reference a given transaction in their past.
     * @return Transactions objects.
     * @throws ArgumentException is thrown when invalid trytes is provided.
     */
    public List<Transaction> sendTrytes(final String[] trytes, final int depth, final int minWeightMagnitude, final String reference) throws ArgumentException {
        final GetTransactionsToApproveResponse txs = getTransactionsToApprove(depth, reference);

        // attach to tangle - do pow
        final GetAttachToTangleResponse res = attachToTangle(txs.getTrunkTransaction(), txs.getBranchTransaction(), minWeightMagnitude, trytes);

        try {
            broadcastAndStore(res.getTrytes());
        } catch (ArgumentException e) {
            return new ArrayList<>();
        }

        final List<Transaction> trx = new ArrayList<>();

        for (final String tryte : Arrays.asList(res.getTrytes())) {
            trx.add(new Transaction(tryte, customCurl.clone()));
        }
        return trx;
    }

    @Override
    public List<Transaction> findTransactionsObjectsByHashes(String[] hashes) throws ArgumentException {
        if (!InputValidator.isArrayOfHashes(hashes)) {
            throw new IllegalStateException("Invalid hashes provided.");
        } else {
            GetTrytesResponse trytesResponse = this.getTrytes(hashes);
            List<Transaction> trxs = new ArrayList<>();
            String[] var4 = trytesResponse.getTrytes();
            int var5 = var4.length;

            for (int var6 = 0; var6 < var5; ++var6) {
                String tryte = var4[var6];
                trxs.add(new Transaction(tryte, this.customCurl.clone()));
            }

            return trxs;
        }
    }

    @Override
    public List<Transaction> findTransactionObjectsByAddresses(String[] addresses) throws ArgumentException {
        List<String> addressesWithoutChecksum = new ArrayList<>();
        String[] ftr = addresses;
        int var4 = addresses.length;

        for (int var5 = 0; var5 < var4; ++var5) {
            String address = ftr[var5];
            String addressO = Checksum.removeChecksum(address);
            addressesWithoutChecksum.add(addressO);
        }

        FindTransactionResponse var8 = this.findTransactions(addressesWithoutChecksum.toArray(new String[0]), null, null, null);
        return var8 != null && var8.getHashes() != null ? this.findTransactionsObjectsByHashes(var8.getHashes()) : new ArrayList<>();
    }

    @Override
    public List<Transaction> findTransactionObjectsByTag(String[] tags) throws ArgumentException {
        FindTransactionResponse ftr = this.findTransactions(null, tags, null, null);
        return ftr != null && ftr.getHashes() != null ? this.findTransactionsObjectsByHashes(ftr.getHashes()) : new ArrayList<>();
    }

    @Override
    public List<Transaction> findTransactionObjectsByApprovees(String[] approvees) throws ArgumentException {
        FindTransactionResponse ftr = this.findTransactions(null, null, approvees, null);
        return ftr != null && ftr.getHashes() != null ? this.findTransactionsObjectsByHashes(ftr.getHashes()) : new ArrayList<>();
    }

    @Override
    public List<Transaction> findTransactionObjectsByBundle(String[] bundles) throws ArgumentException {
        FindTransactionResponse ftr = this.findTransactions(null, null, null, bundles);
        return ftr != null && ftr.getHashes() != null ? this.findTransactionsObjectsByHashes(ftr.getHashes()) : new ArrayList<>();
    }

    /**
     * Prepares transfer by generating bundle, finding and signing inputs.
     *
     * @param seed           Tryte-encoded private key / seed.
     * @param security       The security level of private key / seed.
     * @param transfers      Array of transfer objects.
     * @param remainder      If defined, this address will be used for sending the remainder value (of the inputs) to.
     * @param inputs         The inputs.
     * @param tips           The starting points we walk back from to find the balance of the addresses
     * @param validateInputs whether or not to validate the balances of the provided inputs
     * @return Returns bundle trytes.
     * @throws ArgumentException is thrown when the specified input is not valid.
     */
    public List<String> prepareTransfers(String seed, int security, final List<Transfer> transfers, String remainder, List<Input> inputs, List<Transaction> tips, boolean validateInputs) throws ArgumentException {

        // validate seed
        if ((!InputValidator.isValidSeed(seed))) {
            throw new IllegalStateException(INVALID_SEED_INPUT_ERROR);
        }

        if (security < 1) {
            throw new ArgumentException(INVALID_SECURITY_LEVEL_INPUT_ERROR);
        }

        // Input validation of transfers object
        if (!InputValidator.isTransfersCollectionValid(transfers)) {
            throw new ArgumentException(INVALID_TRANSFERS_INPUT_ERROR);
        }

        // Create a new bundle
        final Bundle bundle = new Bundle();
        final List<String> signatureFragments = new ArrayList<>();

        long totalValue = 0;
        String tag = "";
        //  Iterate over all transfers, get totalValue
        //  and prepare the signatureFragments, message and tag
        for (final Transfer transfer : transfers) {

            // remove the checksum of the address if provided
            if (Checksum.isValidChecksum(transfer.getAddress())) {
                transfer.setAddress(Checksum.removeChecksum(transfer.getAddress()));
            }

            int signatureMessageLength = 1;

            // If message longer than 2187 trytes, increase signatureMessageLength (add 2nd transaction)
            if (transfer.getMessage().length() > Constants.MESSAGE_LENGTH) {

                // Get total length, message / maxLength (2187 trytes)
                signatureMessageLength += Math.floor(transfer.getMessage().length() / Constants.MESSAGE_LENGTH);

                String msgCopy = transfer.getMessage();

                // While there is still a message, copy it
                while (!msgCopy.isEmpty()) {

                    String fragment = StringUtils.substring(msgCopy, 0, Constants.MESSAGE_LENGTH);
                    msgCopy = StringUtils.substring(msgCopy, Constants.MESSAGE_LENGTH, msgCopy.length());

                    // Pad remainder of fragment

                    fragment = StringUtils.rightPad(fragment, Constants.MESSAGE_LENGTH, '9');

                    signatureFragments.add(fragment);
                }
            } else {
                // Else, get single fragment with 2187 of 9's trytes
                String fragment = transfer.getMessage();

                if (transfer.getMessage().length() < Constants.MESSAGE_LENGTH) {
                    fragment = StringUtils.rightPad(fragment, Constants.MESSAGE_LENGTH, '9');
                }
                signatureFragments.add(fragment);
            }

            tag = transfer.getTag();

            // pad for required 27 tryte length
            if (transfer.getTag().length() < Constants.TAG_LENGTH) {
                tag = StringUtils.rightPad(tag, Constants.TAG_LENGTH, '9');
            }


            // get current timestamp in seconds
            long timestamp = (long) Math.floor(Calendar.getInstance().getTimeInMillis() / 1000);

            // Add first entry to the bundle
            bundle.addEntry(signatureMessageLength, transfer.getAddress(), transfer.getValue(), tag, timestamp);
            // Sum up total value
            totalValue += transfer.getValue();
        }

        // Get inputs if we are sending tokens
        if (totalValue != 0) {

            //  Case 1: user provided inputs
            //  Validate the inputs by calling getBalances
            if (inputs != null && !inputs.isEmpty()) {

                if (!validateInputs) {
                    return addRemainder(seed, security, inputs, bundle, tag, totalValue, remainder, signatureFragments);
                }
                // Get list if addresses of the provided inputs
                List<String> inputsAddresses = new ArrayList<>();
                for (final Input i : inputs) {
                    inputsAddresses.add(i.getAddress());
                }

                List<String> tipHashes = null;
                if (tips != null) {
                    tipHashes = new ArrayList<>();

                    for (final Transaction tx : tips) {
                        tipHashes.add(tx.getHash());
                    }
                }

                GetBalancesResponse balancesResponse = getBalances(100, inputsAddresses, tipHashes);
                String[] balances = balancesResponse.getBalances();

                List<Input> confirmedInputs = new ArrayList<>();
                long totalBalance = 0;

                for (int i = 0; i < balances.length; i++) {
                    long thisBalance = Long.parseLong(balances[i]);

                    // If input has balance, add it to confirmedInputs
                    if (thisBalance > 0) {
                        totalBalance += thisBalance;
                        Input inputEl = inputs.get(i);
                        inputEl.setBalance(thisBalance);
                        confirmedInputs.add(inputEl);

                        // if we've already reached the intended input value, break out of loop
                        if (totalBalance >= totalValue) {
                            log.info("Total balance already reached ");
                            break;
                        }
                    }

                }

                // Return not enough balance error
                if (totalValue > totalBalance) {
                    throw new IllegalStateException(NOT_ENOUGH_BALANCE_ERROR);
                }

                return addRemainder(seed, security, confirmedInputs, bundle, tag, totalValue, remainder, signatureFragments);
            }

            //  Case 2: Get inputs deterministically
            //
            //  If no inputs provided, derive the addresses from the seed and
            //  confirm that the inputs exceed the threshold
            else {
                GetBalancesAndFormatResponse newinputs = getInputs(seed, security, 0, 0, totalValue);

                // If inputs with enough balance
                return addRemainder(seed, security, newinputs.getInputs(), bundle, tag, totalValue, remainder, signatureFragments);
            }
        } else {

            // If no input required, don't sign and simply finalize the bundle
            bundle.finalize(customCurl.clone());
            bundle.addTrytes(signatureFragments);

            List<Transaction> trxb = bundle.getTransactions();
            List<String> bundleTrytes = new ArrayList<>();

            for (Transaction trx : trxb) {
                bundleTrytes.add(trx.toTrytes());
            }
            Collections.reverse(bundleTrytes);
            return bundleTrytes;
        }
    }

    /**
     * Gets the inputs of a seed
     *
     * @param seed      Tryte-encoded seed. It should be noted that this seed is not transferred.
     * @param security  The Security level of private key / seed.
     * @param start     Starting key index.
     * @param end       Ending key index.
     * @param threshold Min balance required.
     * @param tips      The starting points we walk back from to find the balance of the addresses
     * @throws ArgumentException is thrown when the specified input is not valid.
     **/
    public GetBalancesAndFormatResponse getInputs(String seed, int security, int start, int end, long threshold, final String... tips) throws ArgumentException {

        // validate the seed
        if ((!InputValidator.isValidSeed(seed))) {
            throw new IllegalStateException(INVALID_SEED_INPUT_ERROR);
        }

        if (security < 1) {
            throw new ArgumentException(INVALID_SECURITY_LEVEL_INPUT_ERROR);
        }

        // If start value bigger than end, return error
        // or if difference between end and start is bigger than 500 keys
        if (start > end || end > (start + 500)) {
            throw new IllegalStateException(INVALID_INPUT_ERROR);
        }

        StopWatch stopWatch = new StopWatch();

        //  Case 1: start and end
        //
        //  If start and end is defined by the user, simply iterate through the keys
        //  and call getBalances
        if (end != 0) {

            List<String> allAddresses = new ArrayList<>();

            for (int i = start; i < end; i++) {

                String address = IotaAPIUtils.newAddress(seed, security, i, false, customCurl.clone());
                allAddresses.add(address);
            }

            return getBalanceAndFormat(allAddresses, Arrays.asList(tips), threshold, start, stopWatch, security);
        }
        //  Case 2: iterate till threshold || end
        //
        //  Either start from index: 0 or start (if defined) until threshold is reached.
        //  Calls getNewAddress and deterministically generates and returns all addresses
        //  We then do getBalance, format the output and return it
        else {
            final GetNewAddressResponse res = getNewAddress(seed, security, start, false, 0, true);
            return getBalanceAndFormat(res.getAddresses(), Arrays.asList(tips), threshold, start, stopWatch, security);
        }
    }

    @Override
    public GetBalancesAndFormatResponse getBalanceAndFormat(final List<String> addresses, final List<String> tips, long threshold, int start, StopWatch stopWatch, int security) throws ArgumentException, IllegalStateException {
        if (security < 1) {
            throw new ArgumentException("Invalid security level provided.");
        } else {
            GetBalancesResponse getBalancesResponse = this.getBalances(Integer.valueOf(100), addresses);
            List<String> balances = Arrays.asList(getBalancesResponse.getBalances());
            boolean thresholdReached = threshold == 0L;
            int i = -1;
            List<Input> inputs = new ArrayList<>();
            long totalBalance = 0L;
            Iterator<String> var14 = addresses.iterator();

            while (var14.hasNext()) {
                String address = var14.next();
                ++i;
                long balance = Long.parseLong(balances.get(i));
                if (balance > 0L) {
                    Input newEntry = new Input(address, balance, start + i, security);
                    inputs.add(newEntry);
                    totalBalance += balance;
                    if (!thresholdReached && totalBalance >= threshold) {
                        thresholdReached = true;
                        break;
                    }
                }
            }

            if (thresholdReached) {
                return GetBalancesAndFormatResponse.create(inputs, totalBalance, stopWatch.getElapsedTimeMili());
            } else {
                throw new IllegalStateException("Not enough balance.");
            }
        }
    }

    @Override
    public GetBundleResponse getBundle(String transaction) throws ArgumentException {
        if (!InputValidator.isHash(transaction)) {
            throw new ArgumentException("Invalid hashes provided.");
        } else {
            Bundle bundle = this.traverseBundle(transaction, null, new Bundle());
            if (bundle == null) {
                throw new ArgumentException("Invalid bundle.");
            } else {
                StopWatch stopWatch = new StopWatch();
                long totalSum = 0L;
                String bundleHash = (bundle.getTransactions().get(0)).getBundle();
                ICurl curl = SpongeFactory.create(SpongeFactory.Mode.KERL);
                curl.reset();
                List<Signature> signaturesToValidate = new ArrayList<>();

                for (int bundleFromTrxs = 0; bundleFromTrxs < bundle.getTransactions().size(); ++bundleFromTrxs) {
                    Transaction bundleFromTxString = bundle.getTransactions().get(bundleFromTrxs);
                    Long bundleValue = Long.valueOf(bundleFromTxString.getValue());
                    totalSum += bundleValue.longValue();
                    if ((long) bundleFromTrxs != bundle.getTransactions().get(bundleFromTrxs).getCurrentIndex()) {
                        throw new ArgumentException("Invalid bundle.");
                    }

                    String aSignaturesToValidate = bundleFromTxString.toTrytes().substring(2187, 2349);
                    curl.absorb(Converter.trits(aSignaturesToValidate));
                    if (bundleValue.longValue() < 0L) {
                        String signatureFragments = bundleFromTxString.getAddress();
                        Signature address = new Signature();
                        address.setAddress(signatureFragments);
                        address.getSignatureFragments().add(bundleFromTxString.getSignatureFragments());

                        for (int isValidSignature = bundleFromTrxs; isValidSignature < bundle.getTransactions().size() - 1; ++isValidSignature) {
                            Transaction newBundleTx = bundle.getTransactions().get(bundleFromTrxs + 1);
                            if (newBundleTx.getAddress().equals(signatureFragments) && newBundleTx.getValue() == 0L && address.getSignatureFragments().indexOf(newBundleTx.getSignatureFragments()) == -1) {
                                address.getSignatureFragments().add(newBundleTx.getSignatureFragments());
                            }
                        }

                        signaturesToValidate.add(address);
                    }
                }

                if (totalSum != 0L) {
                    throw new ArgumentException("Invalid bundle sum.");
                } else {
                    int[] var17 = new int[243];
                    curl.squeeze(var17);
                    String var18 = Converter.trytes(var17);
                    if (!var18.equals(bundleHash)) {
                        throw new ArgumentException("Invalid bundle hash.");
                    } else {
                        bundle.setLength(bundle.getTransactions().size());
                        if (bundle.getTransactions().get(bundle.getLength() - 1).getCurrentIndex() != bundle.getTransactions().get(bundle.getLength() - 1).getLastIndex()) {
                            throw new ArgumentException("Invalid bundle.");
                        } else {
                            Iterator<Signature> var19 = signaturesToValidate.iterator();

                            boolean var23;
                            do {
                                if (!var19.hasNext()) {
                                    return GetBundleResponse.create(bundle.getTransactions(), stopWatch.getElapsedTimeMili());
                                }

                                Signature var20 = var19.next();
                                String[] var21 = var20.getSignatureFragments().toArray(new String[var20.getSignatureFragments().size()]);
                                String var22 = var20.getAddress();
                                var23 = (new Signing(this.customCurl.clone())).validateSignatures(var22, var21, bundleHash).booleanValue();
                            } while (var23);

                            throw new ArgumentException("Invalid signatures.");
                        }
                    }
                }
            }
        }
    }

    @Override
    public GetAccountDataResponse getAccountData(String seed, int security, int index, boolean checksum, int total, boolean returnAll, int start, int end, boolean inclusionStates, long threshold) throws ArgumentException {
        if (start <= end && end <= start + 1000) {
            StopWatch stopWatch = new StopWatch();
            GetNewAddressResponse gna = this.getNewAddress(seed, security, index, checksum, total, returnAll);
            GetTransferResponse gtr = this.getTransfers(seed, security, Integer.valueOf(start), Integer.valueOf(end), Boolean.valueOf(inclusionStates));
            GetBalancesAndFormatResponse gbr = this.getInputs(seed, security, start, end, threshold);
            return GetAccountDataResponse.create(gna.getAddresses(), gtr.getTransfers(), gbr.getInputs(), gbr.getTotalBalance(), stopWatch.getElapsedTimeMili());
        } else {
            throw new ArgumentException("Invalid input provided.");
        }
    }

    /**
     * Replays a transfer by doing Proof of Work again.
     *
     * @param tailTransactionHash The hash of tail transaction.
     * @param depth               The depth.
     * @param minWeightMagnitude  The minimum weight magnitude.
     * @param reference           Hash of transaction to start random-walk from, used to make sure the tips returned reference a given transaction in their past.
     * @return Analyzed Transaction objects.
     * @throws ArgumentException is thrown when the specified input is not valid.
     */
    @Override
    public ReplayBundleResponse replayBundle(String tailTransactionHash, int depth, int minWeightMagnitude, String reference) throws ArgumentException {

        if (!InputValidator.isHash(tailTransactionHash)) {
            throw new ArgumentException(INVALID_TAIL_HASH_INPUT_ERROR);
        }

        StopWatch stopWatch = new StopWatch();

        List<String> bundleTrytes = new ArrayList<>();

        GetBundleResponse bundleResponse = getBundle(tailTransactionHash);
        Bundle bundle = new Bundle(bundleResponse.getTransactions(), bundleResponse.getTransactions().size());
        for (Transaction trx : bundle.getTransactions()) {

            bundleTrytes.add(trx.toTrytes());
        }

        Collections.reverse(bundleTrytes);
        List<Transaction> trxs = sendTrytes(bundleTrytes.toArray(new String[bundleTrytes.size()]), depth, minWeightMagnitude, reference);

        Boolean[] successful = new Boolean[trxs.size()];

        for (int i = 0; i < trxs.size(); i++) {

            final FindTransactionResponse response = findTransactionsByBundles(trxs.get(i).getBundle());


            successful[i] = response.getHashes().length != 0;
        }

        return ReplayBundleResponse.create(successful, stopWatch.getElapsedTimeMili());
    }

    @Override
    public GetInclusionStateResponse getLatestInclusion(String[] hashes) throws ArgumentException {
        GetNodeInfoResponse getNodeInfoResponse = this.getNodeInfo();
        String[] latestMilestone = new String[]{getNodeInfoResponse.getLatestSolidSubtangleMilestone()};
        return this.getInclusionStates(hashes, latestMilestone);
    }

    /**
     * Wrapper function that basically does prepareTransfers, as well as attachToTangle and finally, it broadcasts and stores the transactions locally.
     *
     * @param seed                   Tryte-encoded seed
     * @param security               The security level of private key / seed.
     * @param depth                  The depth.
     * @param minWeightMagnitude     The minimum weight magnitude.
     * @param transfers              Array of transfer objects.
     * @param inputs                 List of inputs used for funding the transfer.
     * @param remainderAddress       If defined, this remainderAddress will be used for sending the remainder value (of the inputs) to.
     * @param validateInputs         Whether or not to validate the balances of the provided inputs.
     * @param validateInputAddresses Whether or not to validate if the destination address is already used, if a key reuse is detect ot it's send to inputs.
     * @param tips                   The starting points we walk back from to find the balance of the addresses
     * @return Array of valid Transaction objects.
     * @throws ArgumentException is thrown when the specified input is not valid.
     */
    public SendTransferResponse sendTransfer(String seed, int security, int depth, int minWeightMagnitude, final List<Transfer> transfers, List<Input> inputs, String remainderAddress, boolean validateInputs, boolean validateInputAddresses, final List<Transaction> tips) throws ArgumentException {

        StopWatch stopWatch = new StopWatch();

        List<String> trytes = prepareTransfers(seed, security, transfers, remainderAddress, inputs, tips, validateInputs);

        if (validateInputAddresses) {
            validateTransfersAddresses(seed, security, trytes);
        }

        String reference = tips != null && tips.size() > 0 ? tips.get(0).getHash() : null;

        List<Transaction> trxs = sendTrytes(trytes.toArray(new String[trytes.size()]), depth, minWeightMagnitude, reference);

        Boolean[] successful = new Boolean[trxs.size()];

        for (int i = 0; i < trxs.size(); i++) {
            final FindTransactionResponse response = findTransactionsByBundles(trxs.get(i).getBundle());
            successful[i] = response.getHashes().length != 0;
        }

        return SendTransferResponse.create(trxs, successful, stopWatch.getElapsedTimeMili());
    }

    @Override
    public Bundle traverseBundle(String trunkTx, String bundleHash, Bundle bundle) throws ArgumentException {
        GetTrytesResponse gtr = this.getTrytes(new String[]{trunkTx});
        if (gtr != null) {
            if (gtr.getTrytes().length == 0) {
                throw new ArgumentException("Invalid bundle.");
            } else {
                Transaction trx = new Transaction(gtr.getTrytes()[0], this.customCurl.clone());
                if (trx.getBundle() == null) {
                    throw new ArgumentException("Invalid trytes provided.");
                } else if (bundleHash == null && trx.getCurrentIndex() != 0L) {
                    throw new ArgumentException("Invalid tail hash provided.");
                } else {
                    if (bundleHash == null) {
                        bundleHash = trx.getBundle();
                    }

                    if (!bundleHash.equals(trx.getBundle())) {
                        return bundle;
                    } else if (trx.getLastIndex() == 0L && trx.getCurrentIndex() == 0L) {
                        return new Bundle(Collections.singletonList(trx), 1);
                    } else {
                        trunkTx = trx.getTrunkTransaction();
                        bundle.getTransactions().add(trx);
                        return this.traverseBundle(trunkTx, bundleHash, bundle);
                    }
                }
            }
        } else {
            throw new ArgumentException("Get trytes response was null.");
        }
    }

    @Override
    public List<Transaction> initiateTransfer(int securitySum, String inputAddress, String remainderAddress, List<Transfer> transfers, boolean testMode) throws ArgumentException {
        if (!InputValidator.isAddress(inputAddress)) {
            throw new ArgumentException("Invalid addresses provided.");
        } else if (remainderAddress != null && !InputValidator.isAddress(remainderAddress)) {
            throw new ArgumentException("Invalid addresses provided.");
        } else if (!InputValidator.isTransfersCollectionValid(transfers)) {
            throw new ArgumentException("Invalid transfers provided.");
        } else {
            Bundle bundle = new Bundle();
            int totalValue = 0;
            List<String> signatureFragments = new ArrayList<>();
            String tag = "";

            Transfer balances;
            for (Iterator<Transfer> balancesResponse = transfers.iterator(); balancesResponse.hasNext(); totalValue = (int) ((long) totalValue + balances.getValue())) {
                balances = balancesResponse.next();
                if (Checksum.isValidChecksum(balances.getAddress())) {
                    balances.setAddress(Checksum.removeChecksum(balances.getAddress()));
                }

                int totalBalance = 1;
                String timestamp;
                if (balances.getMessage().length() > Constants.MESSAGE_LENGTH) {
                    totalBalance = (int) ((double) totalBalance + Math.floor((double) (balances.getMessage().length() / Constants.MESSAGE_LENGTH)));
                    timestamp = balances.getMessage();

                    while (!timestamp.isEmpty()) {
                        String timestamp1 = StringUtils.substring(timestamp, 0, Constants.MESSAGE_LENGTH);
                        timestamp = StringUtils.substring(timestamp, Constants.MESSAGE_LENGTH, timestamp.length());
                        timestamp1 = StringUtils.rightPad(timestamp1, Constants.MESSAGE_LENGTH, '9');
                        signatureFragments.add(timestamp1);
                    }
                } else {
                    timestamp = balances.getMessage();
                    if (balances.getMessage().length() < Constants.MESSAGE_LENGTH) {
                        timestamp = StringUtils.rightPad(timestamp, Constants.MESSAGE_LENGTH, '9');
                    }

                    signatureFragments.add(timestamp);
                }

                tag = balances.getTag();
                if (balances.getTag().length() < Constants.TAG_LENGTH) {
                    tag = StringUtils.rightPad(tag, Constants.TAG_LENGTH, '9');
                }

                long var24 = (long) Math.floor((double) (Calendar.getInstance().getTimeInMillis() / 1000L));
                bundle.addEntry(totalBalance, balances.getAddress(), balances.getValue(), tag, var24);
            }

            if (totalValue == 0) {
                throw new RuntimeException("Invalid value transfer: the transfer does not require a signature.");
            } else {
                GetBalancesResponse var20 = this.getBalances(Integer.valueOf(100), Collections.singletonList(inputAddress));
                String[] var21 = var20.getBalances();
                long var22 = 0L;
                String[] var23 = var21;
                int var15 = var21.length;

                for (int remainder = 0; remainder < var15; ++remainder) {
                    String balance = var23[remainder];
                    long thisBalance = Long.parseLong(balance);
                    var22 += thisBalance;
                }

                long var25 = (long) Math.floor((double) (Calendar.getInstance().getTimeInMillis() / 1000L));
                if (testMode) {
                    var22 += 1000L;
                }

                long var26;
                if (var22 > 0L) {
                    var26 = 0L - var22;
                    bundle.addEntry(securitySum, inputAddress, var26, tag, var25);
                }

                if ((long) totalValue > var22) {
                    throw new IllegalStateException("Not enough balance.");
                } else {
                    if (var22 > (long) totalValue) {
                        var26 = var22 - (long) totalValue;
                        if (remainderAddress == null) {
                            throw new IllegalStateException("No remainder address defined.");
                        }

                        bundle.addEntry(1, remainderAddress, var26, tag, var25);
                    }

                    bundle.finalize(SpongeFactory.create(SpongeFactory.Mode.CURLP81));
                    bundle.addTrytes(signatureFragments);
                    return bundle.getTransactions();
                }
            }
        }
    }

    /**
     * @param seed     Tryte-encoded seed
     * @param security The security level of private key / seed.
     * @param trytes   The trytes.
     * @throws ArgumentException is thrown when the specified input is not valid.
     */
    @Override
    public void validateTransfersAddresses(String seed, int security, List<String> trytes) throws ArgumentException {

        HashSet<String> addresses = new HashSet<>();
        List<Transaction> inputTransactions = new ArrayList<>();
        List<String> inputAddresses = new ArrayList<>();

        for (String trx : trytes) {
            addresses.add(new Transaction(trx, customCurl.clone()).getAddress());
            inputTransactions.add(new Transaction(trx, customCurl.clone()));
        }

        String[] hashes = findTransactionsByAddresses(addresses.toArray(new String[addresses.size()])).getHashes();
        List<Transaction> transactions = findTransactionsObjectsByHashes(hashes);
        GetNewAddressResponse gna = getNewAddress(seed, security, 0, false, 0, true);
        GetBalancesAndFormatResponse gbr = getInputs(seed, security, 0, 0, 0);

        for (Input input : gbr.getInputs()) {
            inputAddresses.add(input.getAddress());
        }

        //check if send to input
        for (Transaction trx : inputTransactions) {
            if (trx.getValue() > 0 && inputAddresses.contains(trx.getAddress()))
                throw new ArgumentException(Constants.SEND_TO_INPUTS_ERROR);
        }

        for (Transaction trx : transactions) {

            //check if destination address is already in use
            if (trx.getValue() < 0 && !inputAddresses.contains(trx.getAddress())) {
                throw new ArgumentException(Constants.SENDING_TO_USED_ADDRESS_ERROR);
            }

            //check if key reuse
            if (trx.getValue() < 0 && gna.getAddresses().contains(trx.getAddress())) {
                throw new ArgumentException(Constants.PRIVATE_KEY_REUSE_ERROR);
            }

        }
    }

    @Override
    public List<String> addRemainder(String seed, int security, List<Input> inputs, Bundle bundle, String tag, long totalValue, String remainderAddress, List<String> signatureFragments) throws ArgumentException {
        long totalTransferValue = totalValue;

        for (int i = 0; i < inputs.size(); ++i) {
            long thisBalance = (inputs.get(i)).getBalance();
            long toSubtract = 0L - thisBalance;
            long timestamp = (long) Math.floor((double) (Calendar.getInstance().getTimeInMillis() / 1000L));
            bundle.addEntry(security, (inputs.get(i)).getAddress(), toSubtract, tag, timestamp);
            if (thisBalance >= totalTransferValue) {
                long remainder = thisBalance - totalTransferValue;
                if (remainder > 0L && remainderAddress != null) {
                    bundle.addEntry(1, remainderAddress, remainder, tag, timestamp);
                    return IotaAPIUtils.signInputsAndReturn(seed, inputs, bundle, signatureFragments, this.customCurl.clone());
                }

                if (remainder > 0L) {
                    GetNewAddressResponse res = this.getNewAddress(seed, security, 0, false, 0, false);
                    bundle.addEntry(1, res.getAddresses().get(0), remainder, tag, timestamp);
                    return IotaAPIUtils.signInputsAndReturn(seed, inputs, bundle, signatureFragments, this.customCurl.clone());
                }

                return IotaAPIUtils.signInputsAndReturn(seed, inputs, bundle, signatureFragments, this.customCurl.clone());
            }

            totalTransferValue -= thisBalance;
        }

        throw new IllegalStateException("Not enough balance.");
    }

    public static class Builder extends IotaAPICoreExtended.Builder<IotaAPIExtended.Builder> {
        private ICurl customCurl;

        public Builder() {
            this.customCurl = SpongeFactory.create(SpongeFactory.Mode.KERL);
        }

        public IotaAPIExtended.Builder withCustomCurl(ICurl curl) {
            this.customCurl = curl;
            return this;
        }

        @Override
        public IotaAPIExtended build() {
            readConfigIfNotSet();
            return new IotaAPIExtended(this);
        }

    }
}
