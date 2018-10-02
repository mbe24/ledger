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

@SuppressWarnings(value = {"unchecked", "rawtypes"})
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
        if(total != 0) {
            for(i = index; i < index + total; ++i) {
                allAddresses.add(IotaAPIUtils.newAddress(seed, security, i, checksum, this.customCurl.clone()));
            }

            return GetNewAddressResponse.create(allAddresses, stopWatch.getElapsedTimeMili());
        } else {
            i = index;

            while(true) {
                String newAddress = IotaAPIUtils.newAddress(seed, security, i, checksum, this.customCurl.clone());
                FindTransactionResponse response = this.findTransactionsByAddresses(new String[]{newAddress});
                ((List)allAddresses).add(newAddress);
                if(response.getHashes().length == 0) {
                    if(!returnAll) {
                        allAddresses = ((List)allAddresses).subList(((List)allAddresses).size() - 1, ((List)allAddresses).size());
                    }

                    return GetNewAddressResponse.create((List)allAddresses, stopWatch.getElapsedTimeMili());
                }

                ++i;
            }
        }
    }

    @Override
    public GetTransferResponse getTransfers(String seed, int security, int start, int end, boolean inclusionStates) throws ArgumentException {
        if(!InputValidator.isValidSeed(seed)) {
            throw new IllegalStateException("Invalid seed provided.");
        } else if(start <= end && end <= start + 500) {
            StopWatch stopWatch = new StopWatch();
            GetNewAddressResponse gnr = this.getNewAddress(seed, security, start, false, end, true);
            if(gnr != null && gnr.getAddresses() != null) {
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
        List trxs = this.findTransactionObjectsByAddresses(addresses);
        ArrayList tailTransactions = new ArrayList();
        ArrayList nonTailBundleHashes = new ArrayList();
        Iterator bundleObjects = trxs.iterator();

        while(bundleObjects.hasNext()) {
            Transaction finalBundles = (Transaction)bundleObjects.next();
            if(finalBundles.getCurrentIndex() == 0L) {
                tailTransactions.add(finalBundles.getHash());
            } else if(nonTailBundleHashes.indexOf(finalBundles.getBundle()) == -1) {
                nonTailBundleHashes.add(finalBundles.getBundle());
            }
        }

        List var13 = this.findTransactionObjectsByBundle((String[])nonTailBundleHashes.toArray(new String[nonTailBundleHashes.size()]));
        Iterator var14 = var13.iterator();

        while(var14.hasNext()) {
            Transaction tailTxArray = (Transaction)var14.next();
            if(tailTxArray.getCurrentIndex() == 0L && tailTransactions.indexOf(tailTxArray.getHash()) == -1) {
                tailTransactions.add(tailTxArray.getHash());
            }
        }

        final ArrayList var15 = new ArrayList();
        final String[] var16 = (String[])tailTransactions.toArray(new String[tailTransactions.size()]);
        GetInclusionStateResponse gisr = null;
        if(var16.length != 0 && inclusionStates) {
            gisr = this.getLatestInclusion(var16);
            if(gisr == null || gisr.getStates() == null || gisr.getStates().length == 0) {
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
                    if(gbr.getTransactions() != null) {
                        if(inclusionStates) {
                            boolean thisInclusion = false;
                            if(gisr_ != null) {
                                thisInclusion = gisr_.getStates()[Arrays.asList(var16).indexOf(param)];
                            }

                            Iterator var5 = gbr.getTransactions().iterator();

                            while(var5.hasNext()) {
                                Transaction t = (Transaction)var5.next();
                                t.setPersistence(thisInclusion);
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

        for(int i = 0; i < var15.size(); ++i) {
            returnValue[i] = new Bundle(((Bundle)var15.get(i)).getTransactions(), ((Bundle)var15.get(i)).getTransactions().size());
        }

        return returnValue;
    }

    @Override
    public StoreTransactionsResponse broadcastAndStore(String... trytes) throws ArgumentException {
        if(!InputValidator.isArrayOfAttachedTrytes(trytes)) {
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

    @Override
    public List<Transaction> sendTrytes(String[] trytes, int depth, int minWeightMagnitude) throws ArgumentException {
        GetTransactionsToApproveResponse txs = this.getTransactionsToApprove(depth);
        GetAttachToTangleResponse res = this.attachToTangle(txs.getTrunkTransaction(), txs.getBranchTransaction(), Integer.valueOf(minWeightMagnitude), trytes);

        try {
            this.broadcastAndStore(res.getTrytes());
        } catch (ArgumentException var9) {
            return new ArrayList<>();
        }

        List<Transaction> trx = new ArrayList<>();
        Iterator var7 = Arrays.asList(res.getTrytes()).iterator();

        while(var7.hasNext()) {
            String tryte = (String)var7.next();
            trx.add(new Transaction(tryte, this.customCurl.clone()));
        }

        return trx;
    }

    @Override
    public List<Transaction> findTransactionsObjectsByHashes(String[] hashes) throws ArgumentException {
        if(!InputValidator.isArrayOfHashes(hashes)) {
            throw new IllegalStateException("Invalid hashes provided.");
        } else {
            GetTrytesResponse trytesResponse = this.getTrytes(hashes);
            ArrayList trxs = new ArrayList();
            String[] var4 = trytesResponse.getTrytes();
            int var5 = var4.length;

            for(int var6 = 0; var6 < var5; ++var6) {
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

        for(int var5 = 0; var5 < var4; ++var5) {
            String address = ftr[var5];
            String addressO = Checksum.removeChecksum(address);
            addressesWithoutChecksum.add(addressO);
        }

        FindTransactionResponse var8 = this.findTransactions(addressesWithoutChecksum.toArray(new String[0]), null, null, null);
        return var8 != null && var8.getHashes() != null?this.findTransactionsObjectsByHashes(var8.getHashes()):new ArrayList<>();
    }

    @Override
    public List<Transaction> findTransactionObjectsByTag(String[] tags) throws ArgumentException {
        FindTransactionResponse ftr = this.findTransactions(null, tags, null, null);
        return ftr != null && ftr.getHashes() != null?this.findTransactionsObjectsByHashes(ftr.getHashes()):new ArrayList<>();
    }

    @Override
    public List<Transaction> findTransactionObjectsByApprovees(String[] approvees) throws ArgumentException {
        FindTransactionResponse ftr = this.findTransactions(null, null, approvees, null);
        return ftr != null && ftr.getHashes() != null?this.findTransactionsObjectsByHashes(ftr.getHashes()):new ArrayList<>();
    }

    @Override
    public List<Transaction> findTransactionObjectsByBundle(String[] bundles) throws ArgumentException {
        FindTransactionResponse ftr = this.findTransactions(null, null, null, bundles);
        return ftr != null && ftr.getHashes() != null?this.findTransactionsObjectsByHashes(ftr.getHashes()):new ArrayList<>();
    }

    @Override
    public List<String> prepareTransfers(String seed, int security, List<Transfer> transfers, String remainder, List<Input> inputs, boolean validateInputs) throws ArgumentException {
        if(!InputValidator.isValidSeed(seed)) {
            throw new IllegalStateException("Invalid seed provided.");
        } else if(security < 1) {
            throw new ArgumentException("Invalid security level provided.");
        } else if(!InputValidator.isTransfersCollectionValid(transfers)) {
            throw new ArgumentException("Invalid transfers provided.");
        } else {
            Bundle bundle = new Bundle();
            List<String> signatureFragments = new ArrayList<>();
            long totalValue = 0L;
            String tag = "";

            Transfer bundleTrytes;
            for(Iterator trxb = transfers.iterator(); trxb.hasNext(); totalValue += bundleTrytes.getValue()) {
                bundleTrytes = (Transfer)trxb.next();
                if(Checksum.isValidChecksum(bundleTrytes.getAddress())) {
                    bundleTrytes.setAddress(Checksum.removeChecksum(bundleTrytes.getAddress()));
                }

                int balances = 1;
                String trx;
                if(bundleTrytes.getMessage().length() > Constants.MESSAGE_LENGTH) {
                    balances = (int)((double)balances + Math.floor((double)(bundleTrytes.getMessage().length() / Constants.MESSAGE_LENGTH)));
                    trx = bundleTrytes.getMessage();

                    while(!trx.isEmpty()) {
                        String totalBalance = StringUtils.substring(trx, 0, Constants.MESSAGE_LENGTH);
                        trx = StringUtils.substring(trx, Constants.MESSAGE_LENGTH, trx.length());
                        totalBalance = StringUtils.rightPad(totalBalance, Constants.MESSAGE_LENGTH, '9');
                        signatureFragments.add(totalBalance);
                    }
                } else {
                    trx = bundleTrytes.getMessage();
                    if(bundleTrytes.getMessage().length() < Constants.MESSAGE_LENGTH) {
                        trx = StringUtils.rightPad(trx, Constants.MESSAGE_LENGTH, '9');
                    }

                    signatureFragments.add(trx);
                }

                tag = bundleTrytes.getTag();
                if(bundleTrytes.getTag().length() < Constants.TAG_LENGTH) {
                    tag = StringUtils.rightPad(tag, Constants.TAG_LENGTH, '9');
                }

                long var36 = (long)Math.floor((double)(Calendar.getInstance().getTimeInMillis() / 1000L));
                bundle.addEntry(balances, bundleTrytes.getAddress(), bundleTrytes.getValue(), tag, var36);
            }

            if(totalValue != 0L) {
                if(inputs != null && !inputs.isEmpty()) {
                    if(!validateInputs) {
                        return this.addRemainder(seed, security, inputs, bundle, tag, totalValue, remainder, signatureFragments);
                    } else {
                        ArrayList var28 = new ArrayList();
                        Iterator var30 = inputs.iterator();

                        while(var30.hasNext()) {
                            Input var33 = (Input)var30.next();
                            var28.add(var33.getAddress());
                        }

                        GetBalancesResponse var31 = this.getBalances(100, var28);
                        String[] var34 = var31.getBalances();
                        ArrayList var38 = new ArrayList();
                        long var35 = 0L;
                        int i = 0;
                        String[] var19 = var34;
                        int var20 = var34.length;

                        for(int var21 = 0; var21 < var20; ++var21) {
                            String balance = var19[var21];
                            long thisBalance = Long.parseLong(balance);
                            if(thisBalance > 0L) {
                                var35 += thisBalance;
                                Input inputEl = inputs.get(i++);
                                inputEl.setBalance(thisBalance);
                                var38.add(inputEl);
                                if(var35 >= totalValue) {
                                    log.info("Total balance already reached ");
                                    break;
                                }
                            }
                        }

                        if(totalValue > var35) {
                            throw new IllegalStateException("Not enough balance.");
                        } else {
                            return this.addRemainder(seed, security, var38, bundle, tag, totalValue, remainder, signatureFragments);
                        }
                    }
                } else {
                    GetBalancesAndFormatResponse var27 = this.getInputs(seed, security, 0, 0, totalValue);
                    return this.addRemainder(seed, security, var27.getInputs(), bundle, tag, totalValue, remainder, signatureFragments);
                }
            } else {
                bundle.finalize(this.customCurl.clone());
                bundle.addTrytes(signatureFragments);
                List var26 = bundle.getTransactions();
                ArrayList var29 = new ArrayList();
                Iterator var32 = var26.iterator();

                while(var32.hasNext()) {
                    Transaction var37 = (Transaction)var32.next();
                    var29.add(var37.toTrytes());
                }

                Collections.reverse(var29);
                return var29;
            }
        }
    }

    @Override
    public GetBalancesAndFormatResponse getInputs(String seed, int security, int start, int end, long threshold) throws ArgumentException {
        if(!InputValidator.isValidSeed(seed)) {
            throw new IllegalStateException("Invalid seed provided.");
        } else if(security < 1) {
            throw new ArgumentException("Invalid security level provided.");
        } else if(start <= end && end <= start + 500) {
            StopWatch stopWatch = new StopWatch();
            if(end == 0) {
                GetNewAddressResponse var11 = this.getNewAddress(seed, security, start, false, 0, true);
                return this.getBalanceAndFormat(var11.getAddresses(), threshold, start, stopWatch, security);
            } else {
                ArrayList res = new ArrayList();

                for(int i = start; i < end; ++i) {
                    String address = IotaAPIUtils.newAddress(seed, security, i, false, this.customCurl.clone());
                    res.add(address);
                }

                return this.getBalanceAndFormat(res, threshold, start, stopWatch, security);
            }
        } else {
            throw new IllegalStateException("Invalid input provided.");
        }
    }

    @Override
    public GetBalancesAndFormatResponse getBalanceAndFormat(List<String> addresses, long threshold, int start, StopWatch stopWatch, int security) throws ArgumentException, IllegalStateException {
        if(security < 1) {
            throw new ArgumentException("Invalid security level provided.");
        } else {
            GetBalancesResponse getBalancesResponse = this.getBalances(Integer.valueOf(100), addresses);
            List<String> balances = Arrays.asList(getBalancesResponse.getBalances());
            boolean thresholdReached = threshold == 0L;
            int i = -1;
            ArrayList inputs = new ArrayList();
            long totalBalance = 0L;
            Iterator var14 = addresses.iterator();

            while(var14.hasNext()) {
                String address = (String)var14.next();
                ++i;
                long balance = Long.parseLong(balances.get(i));
                if(balance > 0L) {
                    Input newEntry = new Input(address, balance, start + i, security);
                    inputs.add(newEntry);
                    totalBalance += balance;
                    if(!thresholdReached && totalBalance >= threshold) {
                        thresholdReached = true;
                        break;
                    }
                }
            }

            if(thresholdReached) {
                return GetBalancesAndFormatResponse.create(inputs, totalBalance, stopWatch.getElapsedTimeMili());
            } else {
                throw new IllegalStateException("Not enough balance.");
            }
        }
    }

    @Override
    public GetBundleResponse getBundle(String transaction) throws ArgumentException {
        if(!InputValidator.isHash(transaction)) {
            throw new ArgumentException("Invalid hashes provided.");
        } else {
            Bundle bundle = this.traverseBundle(transaction, null, new Bundle());
            if(bundle == null) {
                throw new ArgumentException("Invalid bundle.");
            } else {
                StopWatch stopWatch = new StopWatch();
                long totalSum = 0L;
                String bundleHash = (bundle.getTransactions().get(0)).getBundle();
                ICurl curl = SpongeFactory.create(SpongeFactory.Mode.KERL);
                curl.reset();
                ArrayList signaturesToValidate = new ArrayList();

                for(int bundleFromTrxs = 0; bundleFromTrxs < bundle.getTransactions().size(); ++bundleFromTrxs) {
                    Transaction bundleFromTxString = bundle.getTransactions().get(bundleFromTrxs);
                    Long bundleValue = Long.valueOf(bundleFromTxString.getValue());
                    totalSum += bundleValue.longValue();
                    if((long)bundleFromTrxs != (bundle.getTransactions().get(bundleFromTrxs)).getCurrentIndex()) {
                        throw new ArgumentException("Invalid bundle.");
                    }

                    String aSignaturesToValidate = bundleFromTxString.toTrytes().substring(2187, 2349);
                    curl.absorb(Converter.trits(aSignaturesToValidate));
                    if(bundleValue.longValue() < 0L) {
                        String signatureFragments = bundleFromTxString.getAddress();
                        Signature address = new Signature();
                        address.setAddress(signatureFragments);
                        address.getSignatureFragments().add(bundleFromTxString.getSignatureFragments());

                        for(int isValidSignature = bundleFromTrxs; isValidSignature < bundle.getTransactions().size() - 1; ++isValidSignature) {
                            Transaction newBundleTx = bundle.getTransactions().get(bundleFromTrxs + 1);
                            if(newBundleTx.getAddress().equals(signatureFragments) && newBundleTx.getValue() == 0L && address.getSignatureFragments().indexOf(newBundleTx.getSignatureFragments()) == -1) {
                                address.getSignatureFragments().add(newBundleTx.getSignatureFragments());
                            }
                        }

                        signaturesToValidate.add(address);
                    }
                }

                if(totalSum != 0L) {
                    throw new ArgumentException("Invalid bundle sum.");
                } else {
                    int[] var17 = new int[243];
                    curl.squeeze(var17);
                    String var18 = Converter.trytes(var17);
                    if(!var18.equals(bundleHash)) {
                        throw new ArgumentException("Invalid bundle hash.");
                    } else {
                        bundle.setLength(bundle.getTransactions().size());
                        if((bundle.getTransactions().get(bundle.getLength() - 1)).getCurrentIndex() != (bundle.getTransactions().get(bundle.getLength() - 1)).getLastIndex()) {
                            throw new ArgumentException("Invalid bundle.");
                        } else {
                            Iterator var19 = signaturesToValidate.iterator();

                            boolean var23;
                            do {
                                if(!var19.hasNext()) {
                                    return GetBundleResponse.create(bundle.getTransactions(), stopWatch.getElapsedTimeMili());
                                }

                                Signature var20 = (Signature)var19.next();
                                String[] var21 = var20.getSignatureFragments().toArray(new String[var20.getSignatureFragments().size()]);
                                String var22 = var20.getAddress();
                                var23 = (new Signing(this.customCurl.clone())).validateSignatures(var22, var21, bundleHash).booleanValue();
                            } while(var23);

                            throw new ArgumentException("Invalid signatures.");
                        }
                    }
                }
            }
        }
    }

    @Override
    public GetAccountDataResponse getAccountData(String seed, int security, int index, boolean checksum, int total, boolean returnAll, int start, int end, boolean inclusionStates, long threshold) throws ArgumentException {
        if(start <= end && end <= start + 1000) {
            StopWatch stopWatch = new StopWatch();
            GetNewAddressResponse gna = this.getNewAddress(seed, security, index, checksum, total, returnAll);
            GetTransferResponse gtr = this.getTransfers(seed, security, Integer.valueOf(start), Integer.valueOf(end), Boolean.valueOf(inclusionStates));
            GetBalancesAndFormatResponse gbr = this.getInputs(seed, security, start, end, threshold);
            return GetAccountDataResponse.create(gna.getAddresses(), gtr.getTransfers(), gbr.getInputs(), gbr.getTotalBalance(), stopWatch.getElapsedTimeMili());
        } else {
            throw new ArgumentException("Invalid input provided.");
        }
    }

    @Override
    public ReplayBundleResponse replayBundle(String transaction, int depth, int minWeightMagnitude) throws ArgumentException {
        if(!InputValidator.isHash(transaction)) {
            throw new ArgumentException("Invalid tail hash provided.");
        } else {
            StopWatch stopWatch = new StopWatch();
            ArrayList bundleTrytes = new ArrayList();
            GetBundleResponse bundleResponse = this.getBundle(transaction);
            Bundle bundle = new Bundle(bundleResponse.getTransactions(), bundleResponse.getTransactions().size());
            Iterator trxs = bundle.getTransactions().iterator();

            while(trxs.hasNext()) {
                Transaction successful = (Transaction)trxs.next();
                bundleTrytes.add(successful.toTrytes());
            }

            Collections.reverse(bundleTrytes);
            List var12 = this.sendTrytes((String[])bundleTrytes.toArray(new String[bundleTrytes.size()]), depth, minWeightMagnitude);
            Boolean[] var13 = new Boolean[var12.size()];

            for(int i = 0; i < var12.size(); ++i) {
                FindTransactionResponse response = this.findTransactionsByBundles(new String[]{((Transaction)var12.get(i)).getBundle()});
                var13[i] = Boolean.valueOf(response.getHashes().length != 0);
            }

            return ReplayBundleResponse.create(var13, stopWatch.getElapsedTimeMili());
        }
    }

    @Override
    public GetInclusionStateResponse getLatestInclusion(String[] hashes) throws ArgumentException {
        GetNodeInfoResponse getNodeInfoResponse = this.getNodeInfo();
        String[] latestMilestone = new String[]{getNodeInfoResponse.getLatestSolidSubtangleMilestone()};
        return this.getInclusionStates(hashes, latestMilestone);
    }

    @Override
    public SendTransferResponse sendTransfer(String seed, int security, int depth, int minWeightMagnitude, List<Transfer> transfers, List<Input> inputs, String remainderAddress, boolean validateInputs) throws ArgumentException {
        StopWatch stopWatch = new StopWatch();
        List trytes = this.prepareTransfers(seed, security, transfers, remainderAddress, inputs, validateInputs);
        List trxs = this.sendTrytes((String[])trytes.toArray(new String[trytes.size()]), depth, minWeightMagnitude);
        Boolean[] successful = new Boolean[trxs.size()];

        for(int i = 0; i < trxs.size(); ++i) {
            FindTransactionResponse response = this.findTransactionsByBundles(new String[]{((Transaction)trxs.get(i)).getBundle()});
            successful[i] = response.getHashes().length != 0;
        }

        return SendTransferResponse.create(trxs, successful, stopWatch.getElapsedTimeMili());
    }

    @Override
    public Bundle traverseBundle(String trunkTx, String bundleHash, Bundle bundle) throws ArgumentException {
        GetTrytesResponse gtr = this.getTrytes(new String[]{trunkTx});
        if(gtr != null) {
            if(gtr.getTrytes().length == 0) {
                throw new ArgumentException("Invalid bundle.");
            } else {
                Transaction trx = new Transaction(gtr.getTrytes()[0], this.customCurl.clone());
                if(trx.getBundle() == null) {
                    throw new ArgumentException("Invalid trytes provided.");
                } else if(bundleHash == null && trx.getCurrentIndex() != 0L) {
                    throw new ArgumentException("Invalid tail hash provided.");
                } else {
                    if(bundleHash == null) {
                        bundleHash = trx.getBundle();
                    }

                    if(!bundleHash.equals(trx.getBundle())) {
                        return bundle;
                    } else if(trx.getLastIndex() == 0L && trx.getCurrentIndex() == 0L) {
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
        if(!InputValidator.isAddress(inputAddress)) {
            throw new ArgumentException("Invalid addresses provided.");
        } else if(remainderAddress != null && !InputValidator.isAddress(remainderAddress)) {
            throw new ArgumentException("Invalid addresses provided.");
        } else if(!InputValidator.isTransfersCollectionValid(transfers)) {
            throw new ArgumentException("Invalid transfers provided.");
        } else {
            Bundle bundle = new Bundle();
            int totalValue = 0;
            ArrayList signatureFragments = new ArrayList();
            String tag = "";

            Transfer balances;
            for(Iterator balancesResponse = transfers.iterator(); balancesResponse.hasNext(); totalValue = (int)((long)totalValue + balances.getValue())) {
                balances = (Transfer)balancesResponse.next();
                if(Checksum.isValidChecksum(balances.getAddress())) {
                    balances.setAddress(Checksum.removeChecksum(balances.getAddress()));
                }

                int totalBalance = 1;
                String timestamp;
                if(balances.getMessage().length() > Constants.MESSAGE_LENGTH) {
                    totalBalance = (int)((double)totalBalance + Math.floor((double)(balances.getMessage().length() / Constants.MESSAGE_LENGTH)));
                    timestamp = balances.getMessage();

                    while(!timestamp.isEmpty()) {
                        String timestamp1 = StringUtils.substring(timestamp, 0, Constants.MESSAGE_LENGTH);
                        timestamp = StringUtils.substring(timestamp, Constants.MESSAGE_LENGTH, timestamp.length());
                        timestamp1 = StringUtils.rightPad(timestamp1, Constants.MESSAGE_LENGTH, '9');
                        signatureFragments.add(timestamp1);
                    }
                } else {
                    timestamp = balances.getMessage();
                    if(balances.getMessage().length() < Constants.MESSAGE_LENGTH) {
                        timestamp = StringUtils.rightPad(timestamp, Constants.MESSAGE_LENGTH, '9');
                    }

                    signatureFragments.add(timestamp);
                }

                tag = balances.getTag();
                if(balances.getTag().length() < Constants.TAG_LENGTH) {
                    tag = StringUtils.rightPad(tag, Constants.TAG_LENGTH, '9');
                }

                long var24 = (long)Math.floor((double)(Calendar.getInstance().getTimeInMillis() / 1000L));
                bundle.addEntry(totalBalance, balances.getAddress(), balances.getValue(), tag, var24);
            }

            if(totalValue == 0) {
                throw new RuntimeException("Invalid value transfer: the transfer does not require a signature.");
            } else {
                GetBalancesResponse var20 = this.getBalances(100, Collections.singletonList(inputAddress));
                String[] var21 = var20.getBalances();
                long var22 = 0L;
                String[] var23 = var21;
                int var15 = var21.length;

                for(int remainder = 0; remainder < var15; ++remainder) {
                    String balance = var23[remainder];
                    long thisBalance = Long.parseLong(balance);
                    var22 += thisBalance;
                }

                long var25 = (long)Math.floor((double)(Calendar.getInstance().getTimeInMillis() / 1000L));
                if(testMode) {
                    var22 += 1000L;
                }

                long var26;
                if(var22 > 0L) {
                    var26 = 0L - var22;
                    bundle.addEntry(securitySum, inputAddress, var26, tag, var25);
                }

                if((long)totalValue > var22) {
                    throw new IllegalStateException("Not enough balance.");
                } else {
                    if(var22 > (long)totalValue) {
                        var26 = var22 - (long)totalValue;
                        if(remainderAddress == null) {
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

    @Override
    public List<String> addRemainder(String seed, int security, List<Input> inputs, Bundle bundle, String tag, long totalValue, String remainderAddress, List<String> signatureFragments) throws ArgumentException {
        long totalTransferValue = totalValue;

        for(int i = 0; i < inputs.size(); ++i) {
            long thisBalance = inputs.get(i).getBalance();
            long toSubtract = 0L - thisBalance;
            long timestamp = (long)Math.floor((double)(Calendar.getInstance().getTimeInMillis() / 1000L));
            bundle.addEntry(security, inputs.get(i).getAddress(), toSubtract, tag, timestamp);
            if(thisBalance >= totalTransferValue) {
                long remainder = thisBalance - totalTransferValue;
                if(remainder > 0L && remainderAddress != null) {
                    bundle.addEntry(1, remainderAddress, remainder, tag, timestamp);
                    return IotaAPIUtils.signInputsAndReturn(seed, inputs, bundle, signatureFragments, this.customCurl.clone());
                }

                if(remainder > 0L) {
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
