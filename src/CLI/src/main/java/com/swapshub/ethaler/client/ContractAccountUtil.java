/**
Copyright 2020 Swapshub
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
*/

package com.swapshub.ethaler.client;

import com.swapshub.ethaler.w3generated.EThaler;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
* This class contains methods for interacting with contract methods
*
*/
public class ContractAccountUtil extends GenUtil {
    static final int OWNER_MINT = 1;
    static final int OWMER_OWN_BALANCE = 2;
    static final int OWNER_OTHER_BALANCE = 3;
    static final int OWNER_ADD_NEW_TOKEN = 4;
    static final int OWNER_REGISTER = 5;
    static final int OWNER_TRANSFER = 6;
    static final int OWNER_UNREGISTER = 7;
    static final int OWNER_PAUSE_TOKEN = 8;
    static final int OWNER_RESUME_TOKEN = 9;
    static final int OWNER_BURN = 10;
    static final int OWNER_TOKEN_DETAILS = 11;

    static final int DEALER_OWN_BALANCE = 1;
    static final int DEALER_TRANSFER = 2;
    static final int DEALER_TOKEN_DETAILS = 3;

    static final int EXIT_APPLICATION = 0;

    public static final String FUNC_PAUSE = "pause";

    private EThaler contract;

    /**
     * @param web3j Start the application process for various menu options
     *              Registers for event listening
     */
    public void startApplication(Web3j web3j) {
        try {
            contract = createEThalerContract(web3j, EThalerApplication.ETHALER_CONTRACT_ADDR, WalletInitUtil.privateKey);
            populateTokenIds();
            Thread thread = new Thread() {
                public synchronized void run() {
                    ContractEventListener eventListener = new ContractEventListener(contract);
                    eventListener.startListeningEthEvents(web3j);
                }
            };
            thread.start();
            try {
                EThalerApplication.isOwner = contract.isOwner().send();
            } catch (Exception ex) {
                System.out.println("Error in getting isOwner : " + ex.getMessage());
            }
            boolean cont = true;
            while (cont) {
                GenUtil.printUserOptions();
                String userOption = collectUserInput();
                processUserOption(userOption);
            }

        } catch (Exception ex) {
            printLog("Error could not complete startApplication : " + ex.getMessage());
        }
    }

    private void processUserOption(String userOption) {
        if (EThalerApplication.isOwner) {
            processOwnerOptions(userOption);
        } else {
            processDealerOption(userOption);
        }
    }

    private void processDealerOption(String option) {
        try {
            int opt = Integer.parseInt(option);
            switch (opt) {
                case DEALER_TOKEN_DETAILS:
                    this.printAllTokenDetails();
                    break;
                case DEALER_TRANSFER:
                    transferDealerBalance();
                    break;
                case DEALER_OWN_BALANCE:
                    checkDealerOwnAccountBalance();
                    break;
                case EXIT_APPLICATION:
                    shutdownApp("Exiting");
                    break;
                default:
                    printLog("Unknown option. Ignored");
            }
        } catch (TransactionException te) {
            printLog(" Error in dealer function : " + te.getMessage());
            te.printStackTrace();
        } catch (IOException io) {
            printLog(" Error in dealer function : " + io.getMessage());
            io.printStackTrace();
        } catch (Exception ex) {
            printLog("Error in dealer function : " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /*======================================*/
    /* Start of Dealer funcations */

    /**
     * Transfers fund from one account to another account
     */
    private void transferDealerBalance() throws TransactionException, IOException, Exception {
        String tokenId = this.getUserEnteredTokenId();
        if (!this.checkForTokenIdExistance(tokenId)) {
            System.out.println("Entered token id does not exist");
        } else {
            System.out.print("Enter target account address : ");
            String acctAdd = collectUserInput();
            System.out.print("Enter amount to transfer : ");
            String amt = collectUserInput();
            BigInteger convertedAmt = this.getConvertedValForNetwork(new BigInteger(tokenId), new BigInteger(amt));
            BigInteger beforeOwnBal = contract.balanceOf(new BigInteger(tokenId)).send();
            TransactionReceipt receipt = contract.transfer(acctAdd, new BigInteger(tokenId), convertedAmt).send();
            printLog("Amount : " + amt + " transferred to account address [" + acctAdd);
            printTransactionHash(receipt);
            BigInteger afterOwnBal = contract.balanceOf(new BigInteger(tokenId)).send();
            System.out.println("Transfer --  Balance for account [" + WalletInitUtil.acctAddress + "] : Before ["
                    + getConvertedValForDisplay(new BigInteger(tokenId),  beforeOwnBal) + "] :: After [" + getConvertedValForDisplay(new BigInteger(tokenId), afterOwnBal) + "]");
        }
    }

    /**
     * Cheecks balacnce for Dealer's own account
     */
    private void checkDealerOwnAccountBalance() throws TransactionException, IOException, Exception {
        String tokenId = getUserEnteredTokenId();
        if (this.checkForTokenIdExistance(tokenId)) {
            checkDealerOwnBalance(new BigInteger(tokenId));
        } else {
            System.out.println("Entered token id does not exist");
        }
    }

    /*End of Dealer functions */
    /*=============================*/


    private void processOwnerOptions(String option) {
        try {
            int opt = Integer.parseInt(option);
            switch (opt) {
                case OWNER_TOKEN_DETAILS:
                    printAllTokenDetails();
                    break;
                case OWNER_ADD_NEW_TOKEN:
                    addNewToken();
                    break;
                case OWNER_MINT:
                    mint();
                    break;
                case OWMER_OWN_BALANCE:
                    checkCBOWnAccountBalance();
                    break;
                case OWNER_OTHER_BALANCE:
                    checkCBOtherAccountBalance();
                    break;
                case OWNER_REGISTER:
                    registerDealer();
                    break;
                case OWNER_TRANSFER:
                    transferCBBalance();
                    break;
                case OWNER_UNREGISTER:
                    unregisterDealer();
                    break;
                case OWNER_PAUSE_TOKEN:
                    pauseToken();
                    break;
                case OWNER_RESUME_TOKEN:
                    resumeToken();
                    break;
                case OWNER_BURN:
                    burn();
                    break;
                case EXIT_APPLICATION:
                    shutdownApp("Exiting");
                    break;
                default:
                    printLog("Unknown option. Ignored");
            }
        } catch (TransactionException te) {
            printLog("Error in processing Central Banker Options TE: " + te.getMessage() );
            printLog("The cause is :"  + te.getCause().getMessage());
            //te.printStackTrace();
        } catch (IOException io) {
            printLog(" Error in processing Central Banker Options IO: " + io.getMessage());
            io.printStackTrace();
        } catch (Exception ex) {
            printLog("Error in processing Central Banker Options EX: " + ex.getMessage() );
            ex.printStackTrace();
        }
    }
    private void printErrorStack(StackTraceElement[] elements)
    {
        printLog("the element count is : " + elements.length);
        String info = "";
        for (StackTraceElement ele : elements)
        {
            info = info +  "Class name : " + ele.getClassName() + " : " + " method name : " + ele.getMethodName() +
                    " Line number : " + ele.getLineNumber();
        }
        printLog(" Stack Trace : " + info);
    }
    /*=========================================*/

    /**
     * Central Banker functions -- Adds a new token to the network deployed contract.
     */
    private void addNewToken() throws TransactionException, IOException, Exception {
        String newTokenId = getUserEnteredTokenId();
        boolean exists = checkForTokenIdExistance(newTokenId);
        if (exists) {
            System.out.println("Token Id entered already exists");
        } else {
            System.out.print("Enter New token name : ");
            String newTokenName = collectUserInput();
            System.out.print("Enter New token decimal places : ");
            String newTokenDecimals = collectUserInput();
            System.out.print("Enter New token url : ");
            String newTokenUrl = collectUserInput();//just a sanity check
            TransactionReceipt receipt = contract.addNewTokenDefinition(new BigInteger(newTokenId), newTokenName, new BigInteger(newTokenDecimals), newTokenUrl).send();
            System.out.println("New token with id [" + newTokenId + " ] with name [" + newTokenName + "] has been added.");
            printTransactionHash(receipt);
            EThalerApplication.tokenIdList.add(new BigInteger(newTokenId));
            EThalerApplication.tokenNameList.add(newTokenName);
            EThalerApplication.tokenDecimalList.add(new BigInteger(newTokenDecimals));
            EThalerApplication.tokenTTFList.add(newTokenUrl);
        }
    }

    /**
     * Mints token for a given token id
     */
    private void mint() throws TransactionException, IOException, Exception {
        if (EThalerApplication.isOwner) {
            String tokenId = getUserEnteredTokenId();
            boolean exists = checkForTokenIdExistance(tokenId);
            if (!exists) {
                System.out.println("Token id entered is not available");
            } else {
                System.out.print("Enter amount to mint : ");
                String amount = collectUserInput();
                BigInteger convertedAmount = this.getConvertedValForNetwork(new BigInteger(tokenId), new BigInteger(amount));
                printLog("Balance before minting : " + getConvertedValForDisplay(new BigInteger(tokenId), contract.balanceOf(WalletInitUtil.acctAddress, new BigInteger(tokenId)).send()));
                TransactionReceipt receipt = contract.mint(new BigInteger(tokenId), convertedAmount, new byte[0]).send();
                printLog("Balance after minting  : " + getConvertedValForDisplay(new BigInteger(tokenId), contract.balanceOf(WalletInitUtil.acctAddress, new BigInteger(tokenId)).send()));
                printTransactionHash(receipt);
            }
        } else {
            printLog("You are not owner of the contract to mint");
        }
    }

    /**
     * checks central banker's own account balance
     */
    private void checkCBOWnAccountBalance() throws TransactionException, IOException, Exception {
        String tokenId = this.getUserEnteredTokenId();
        if (!this.checkForTokenIdExistance(tokenId)) {
            System.out.println("Entered token id does not exist");
        } else {
            BigInteger bal = checkCBBalance(WalletInitUtil.acctAddress, new BigInteger(tokenId));
            System.out.println("Balance for address [" + WalletInitUtil.acctAddress + "] is [" + this.getConvertedValForDisplay(new BigInteger(tokenId), bal) + "]");
        }
    }

    /**
     * facilitates checking other dealer account balance by central banker
     */
    private void checkCBOtherAccountBalance() throws TransactionException, IOException, Exception {
        String tokenId = this.getUserEnteredTokenId();
        if (!this.checkForTokenIdExistance(tokenId)) {
            System.out.println("Entered token id does not exist");
        } else {
            System.out.print("Enter account address : ");
            String acctAddress = collectUserInput();
            BigInteger bal = checkCBBalance(acctAddress, new BigInteger(tokenId));
            System.out.println("Balance for address [" + acctAddress + "] is [" +
                    getConvertedValForDisplay(new BigInteger(tokenId), bal) + "]");
        }
    }

    /**
     * registers dealer account for a given token
     */
    private void registerDealer() throws TransactionException, IOException, Exception {
        if (EThalerApplication.isOwner) {
            String tokenId = getUserEnteredTokenId();
            if (!this.checkForTokenIdExistance(tokenId)) {
                System.out.println("Entered token id does not exist");
            } else {
                System.out.print("Enter target account address to register : ");
                String acctAdd = collectUserInput();
                TransactionReceipt receipt = contract.registerDealer(acctAdd, new BigInteger(tokenId)).send();
                printTransactionHash(receipt);
                printLog("Registering of dealer [" + acctAdd + "] , token id [" + tokenId + "] completed.");
            }
        } else {
            printLog("Only owner can register ");
        }
    }

    /**
     * facilitates Central banker to transfer amount to a dealer account
     */
    private void transferCBBalance() throws TransactionException, IOException, Exception {
        String tokenId = this.getUserEnteredTokenId();
        BigInteger tokenIdBI = new BigInteger(tokenId);
        if (!this.checkForTokenIdExistance(tokenId)) {
            System.out.println("Entered token id does not exist");
        } else {
            System.out.print("Enter target account address : ");
            String acctAdd = collectUserInput();
            System.out.print("Enter amount to transfer : ");
            String amt = collectUserInput();
            BigInteger convertedAmt = this.getConvertedValForNetwork(tokenIdBI, new BigInteger(amt));
            BigInteger beforeOwnBal = contract.balanceOf(WalletInitUtil.acctAddress, tokenIdBI).send();
            BigInteger beforeTargetBal = contract.balanceOf(acctAdd, tokenIdBI).send();
            TransactionReceipt receipt = contract.transfer(acctAdd, tokenIdBI, convertedAmt).send();
            printLog("Amount : " + amt + " transferred to account address [" + acctAdd);
            printTransactionHash(receipt);
            BigInteger afterOwnBal = contract.balanceOf(WalletInitUtil.acctAddress, tokenIdBI).send();
            BigInteger afterTargetBal = contract.balanceOf(acctAdd, tokenIdBI).send();
            System.out.println("Transfer --  Balance for account [" + WalletInitUtil.acctAddress + "] : Before ["
                    + getConvertedValForDisplay(tokenIdBI, beforeOwnBal) + "] :: After [" + getConvertedValForDisplay(tokenIdBI, afterOwnBal) + "]");
            System.out.println("Transfer --  Balance for account [" + acctAdd + "] : Before ["
                    + getConvertedValForDisplay(tokenIdBI, beforeTargetBal) + "] :: After [" + getConvertedValForDisplay(tokenIdBI, afterTargetBal) + "]");
        }
    }

    /**
     * Unregisters a dealer account for a given token
     */
    private void unregisterDealer() throws TransactionException, IOException, Exception {
        if (EThalerApplication.isOwner) {
            String tokenId = this.getUserEnteredTokenId();
            BigInteger tokenIdBI = new BigInteger(tokenId);
            if (!this.checkForTokenIdExistance(tokenId)) {
                System.out.println("Entered token id does not exist");
            } else {
                System.out.print("Enter target account address to unregister : ");
                String acctAddUn = collectUserInput();
                TransactionReceipt receipt = contract.unregisterDealer(acctAddUn, tokenIdBI).send();
                printTransactionHash(receipt);
                System.out.print("Unregistering of account address [" + acctAddUn + "] , tokenId [" + tokenId + "] done");
            }
        } else {
            printLog("Only owner can do unregister");
        }
    }

    /**
     * pauses a token from doing any further transactions
     */
    private void pauseToken() throws TransactionException, IOException, Exception {
        String tokenId = this.getUserEnteredTokenId();
        try {
            if (!checkForTokenIdExistance(tokenId)) {
                System.out.println("Entered token id does not exist");
            } else {
                TransactionReceipt receipt = contract.pause(new BigInteger(tokenId)).send();
                System.out.println("the receipt values : " + receipt.getStatus() + " :: " + receipt.toString() + " :: " + receipt);
                System.out.println("Token id [" + tokenId + "] is paused.");
                System.out.println("Pause status from contract for token id : [" + tokenId + "] is : [" + contract.isPaused(new BigInteger(tokenId)).send() + "]");
                printTransactionHash(receipt);
            }
        }
        catch(Exception te)
        {
            //the error is still the generic: Transaction has failed with status: 0x0. Gas used: 25668. (not-enough gas?)
            final org.web3j.abi.datatypes.Function pauseFunction = new org.web3j.abi.datatypes.Function(
                    FUNC_PAUSE,
                    Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(Long.parseLong(tokenId))),
                    Collections.<TypeReference<?>>emptyList());
            String encodedFunction = FunctionEncoder.encode(pauseFunction);
            System.out.println("the acctAddress :" + WalletInitUtil.acctAddress + " :: " + " contractAddress " + contract.getContractAddress());
            Credentials credentials = Credentials.create(WalletInitUtil.privateKey);
            TransactionManager rawTransMgr = getRawTransactionManager(web3j , credentials , EThalerApplication.CHAIN_ID , EThalerApplication.POLLING_ATTEMPTS , EThalerApplication.POLLING_INTERVAL);
            String response = rawTransMgr.sendCall(contract.getContractAddress() , encodedFunction, DefaultBlockParameterName.LATEST);
            System.out.println("the response code is : " + response);
            BigInteger weiValue = BigInteger.ZERO;
           // rawTransMgr.sendTransaction(new BigInteger("0") ,new BigInteger("45000") , contract.getContractAddress() ,
             //       encodedFunction)
            EthCall ethCall = web3j.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                            WalletInitUtil.acctAddress, // this is your wallet's address. Use `credentials.getAddress();` if you do not know what yours is
                            contract.getContractAddress(), // this should be the same as what is in the load function above
                            encodedFunction
                    ),
                    DefaultBlockParameterName.LATEST
            ).send();

            //String errorMethodId = "0x08c379a0" ;
            String encodedRevertReason = ethCall.getValue() ; //.substring(errorMethodId.length());
            System.out.println("the error value is : " + encodedRevertReason);
            List<Type> list = FunctionReturnDecoder.decode(encodedRevertReason, pauseFunction.getOutputParameters());
            System.out.println("the error value list  : " + list.toString());
            System.out.println( " the zeroth element is : " + ((Bytes32) list.get(0)).getValue()) ;

            //Optional<String> revertReason = getRevertReason(ethCall); // this is the same function from the blog post mentioned
            //System.out.println(revertReason.get()); // outputs: 'Already voted.'
        }
    }
    private String getRevertReason(EthCall ethCall){
        // Numeric.toHexString(Hash.sha3("Error(string)".getBytes())).substring(0, 10)
        /*
        String errorMethodId = "0x08c379a0" ;
        val revertReasonTypes = listOf<TypeReference<Type<*>>>(TypeReference.create<Type<*>>(AbiTypes.getType("string") as Class<Type<*>>))

        if (!ethCall.hasError() && ethCall.value != null && ethCall.value.startsWith(errorMethodId)) {
            String encodedRevertReason = ethCall.getValue().substring(errorMethodId.length());
            val decoded = FunctionReturnDecoder.decode(encodedRevertReason, revertReasonTypes)
            val decodedRevertReason = decoded[0] as Utf8String
            return Optional.of(decodedRevertReason.value)
        }
        return Optional.empty()
        */
        /*
        String value = ethCall.getValue();
        List<Type> list = FunctionReturnDecoder.decode(value, function.getOutputParameters());
        return ((Bytes32) list.get(0)).getValue();
        */
        return "";
    }
    /**
     * resumes a token from pause state
     */
    private void resumeToken() throws TransactionException, IOException, Exception {
        String tokenId = this.getUserEnteredTokenId();
        if (!this.checkForTokenIdExistance(tokenId)) {
            System.out.println("Entered token id does not exist");
        } else {
            TransactionReceipt receipt = contract.resume(new BigInteger(tokenId)).send();
            System.out.println("Token id [" + tokenId + "] is resumed.");
            printTransactionHash(receipt);
        }
    }

    /**
     * bruns given quantity for a token
     */
    private void burn() throws TransactionException, IOException, Exception {
        String tokenId = this.getUserEnteredTokenId();
        BigInteger tokenIdBI = new BigInteger(tokenId);
        if (!checkForTokenIdExistance(tokenId)) {
            System.out.println("Entered token id does not exist");
        } else {
            BigInteger beforeBal = checkCBBalance(WalletInitUtil.acctAddress, tokenIdBI);
            printLog("Available balance for address [" + WalletInitUtil.acctAddress + "] is ["
                    + this.getConvertedValForDisplay(tokenIdBI, beforeBal) + "] for token id [" + tokenId + "]");
            System.out.print("Enter amount to burn : ");
            String amount = this.collectUserInput();
            BigInteger amountBI = this.getConvertedValForNetwork(tokenIdBI, new BigInteger(amount));
            TransactionReceipt receipt = contract.burn(tokenIdBI, amountBI).send();
            BigInteger afterBal = checkCBBalance(WalletInitUtil.acctAddress, tokenIdBI);
            System.out.println("Amount [" + amount + "] for token id [" + tokenId + "] is burnt.");
            System.out.println("Burn -- Balance before [" + getConvertedValForDisplay(tokenIdBI, beforeBal) + "] after ["
                    + getConvertedValForDisplay(tokenIdBI, afterBal) + "] for token id [" + tokenId + "]");
            printTransactionHash(receipt);
        }
    }

    /**
     * prints all available tokens for a cotnact
     */
    private void printAllTokenDetails() {
        int count = EThalerApplication.tokenIdList.size();
        if (count == 0) {
            System.out.println("No token has been added to contract");
        } else {
            System.out.println("Available tokens ");
            for (int i = 0; i < count; i++) {
                System.out.println("{ Id   : \"" + EThalerApplication.tokenIdList.get(i) + "\" ; Name : \"" + EThalerApplication.tokenNameList.get(i)
                        + "\" ; Decimals : \"" + EThalerApplication.tokenDecimalList.get(i) + "\" ; TTF : \"" + EThalerApplication.tokenTTFList.get(i) + "\"}");
            }
        }
    }
    /*=========================================*/
    /* End of Central Banker functions*/

    /**
     * @param web3j
     * @param contractAddr      -- obtained from thw wallet file
     * @param accountPrivateKey -- obtained from the wallet file
     * @return EThaler contract object
     * @throws Exception
     */
    private EThaler createEThalerContract_old(Web3j web3j, String contractAddr, String accountPrivateKey) throws Exception {
        Credentials credentials = Credentials.create(accountPrivateKey);
        ContractGasProvider contractGasProvider = getGasProvider();
        return EThaler.load(contractAddr, web3j, credentials, contractGasProvider);
    }

    private EThaler createEThalerContract(Web3j web3j, String contractAddr, String accountPrivateKey) throws Exception {
        Credentials credentials = Credentials.create(accountPrivateKey);
        ContractGasProvider contractGasProvider = getGasProvider();
        TransactionManager rawTransMgr = getRawTransactionManager(web3j , credentials , EThalerApplication.CHAIN_ID , EThalerApplication.POLLING_ATTEMPTS , EThalerApplication.POLLING_INTERVAL);
        return EThaler.load(contractAddr , web3j ,rawTransMgr , contractGasProvider);
    }
    private TransactionManager getRawTransactionManager(Web3j web3j, Credentials credentials, long chainId, int attempts, long sleepDuration)
    {
        return new  RawTransactionManager(web3j , credentials, chainId , attempts , sleepDuration);
    }
    /**
     * To pass zero gas price
     *
     * @return
     */
    private DefaultGasProvider getGasProvider() {
        return (new DefaultGasProvider() {
            @Override
            public BigInteger getGasPrice(String contractFunc) {
                return new BigInteger("0");
            }

            @Override
            public BigInteger getGasLimit(String contractFunc) {
                return new BigInteger("430000");
            }
        });
    }

    /**
     * get the Dealer banker's own account balance
     */
    private BigInteger checkDealerOwnBalance(BigInteger tokenId) throws TransactionException, IOException, Exception {
        BigInteger balance1 = contract.balanceOf(tokenId).send();
        printLog("Balance for addr [" + WalletInitUtil.acctAddress + "] =" + getConvertedValForDisplay(tokenId, balance1).toString());
        return balance1;
    }

    /**
     * get the balance amount for a given token for Central Banker
     *
     * @param addr
     * @param tokenId
     * @return
     * @throws Exception
     */
    private BigInteger checkCBBalance(String addr, BigInteger tokenId) throws Exception {
        return contract.balanceOf(addr, tokenId).send();
    }

    /**
     * does a sanity check for the user entered token id
     *
     * @param newTokenId
     * @return
     */
    private boolean checkForTokenIdExistance(String newTokenId) {
        try {
            int count = EThalerApplication.tokenIdList.size();
            for (int i = 0; i < count; i++) {
                BigInteger tmpId = new BigInteger("" + EThalerApplication.tokenIdList.get(i));
                BigInteger newId = new BigInteger(newTokenId);

                if (tmpId.intValue() == newId.intValue()) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            throw ex;
        }
    }

    /**
     * collects token id from user
     *
     * @return
     */
    private String getUserEnteredTokenId() {
        printExistingTokenIdNames();
        System.out.print("Enter token id : ");
        return collectUserInput();
    }

    /**
     * get the all token related information from network
     */
    private void populateTokenIds() {
        try {
            EThalerApplication.tokenIdList = (ArrayList) contract.getAllTokenIds().send();
            int size = EThalerApplication.tokenIdList.size();
            EThalerApplication.tokenNameList = new ArrayList(); //clear names
            EThalerApplication.tokenDecimalList = new ArrayList();
            EThalerApplication.tokenTTFList = new ArrayList();

            for (int i = 0; i < size; i++) {
                String tName = contract.getTokenName(new BigInteger("" + EThalerApplication.tokenIdList.get(i))).send();
                EThalerApplication.tokenNameList.add(tName);
                BigInteger tDecimal = contract.getDecimals(new BigInteger("" + EThalerApplication.tokenIdList.get(i))).send();
                EThalerApplication.tokenDecimalList.add(tDecimal);
                String tTTF = contract.getTTF_URL(new BigInteger("" + EThalerApplication.tokenIdList.get(i))).send();
                EThalerApplication.tokenTTFList.add(tTTF);
            }
        } catch (Exception ex) {
            printLog("Error in populating token ids : " + ex.getMessage());
        }
    }

    /**
     * prints all available token for user to view before typing token id value
     */
    private void printExistingTokenIdNames() {
        int count = EThalerApplication.tokenIdList.size();
        if (count == 0) {
            System.out.println("No token has been added to contract");
            return;
        }
        System.out.println("Available tokens ");
        for (int i = 0; i < count; i++) {
            System.out.println("Id   : " + EThalerApplication.tokenIdList.get(i) + " -- Name : " + EThalerApplication.tokenNameList.get(i));
        }
    }
}//end of class
