// Copyright (c) 2014-2016 Bitmark Inc.
// Use of this source code is governed by an ISC
// license that can be found in the LICENSE file.

package bitmark.com.pay;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Iterator;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.MoreExecutors;

/**
 * <p>
 * Structure to hold a payment address and the amount to send
 * </p>
 */
class Payment {

	public final Address paymentAddress;
	public final Coin amountSatoshi;

	/**
	 * <p>
	 * send some satoshis to an address
	 * </p>
	 *
	 * @param address
	 *            address to make payment to
	 * @param amount
	 *            number of Satoshis to send
	 */
	public Payment(Address address, Coin amount) {
		this.paymentAddress = address;
		this.amountSatoshi = amount;
	}

}


/**
 * <p>
 * BitmarkWalletKit can create WalletAppKit according to different net setting,
 * and provides functions for BitmarkWalletService.
 * </p>
 *
 * @author yuntai
 *
 */
public class BitmarkWalletKit {
	private static final Logger log = LoggerFactory.getLogger(BitmarkWalletKit.class);

	/**
	 * Fee charged by other bitcoin peer to mine the transaction
	 */
	public static final Coin MINE_FEE = Coin.valueOf(5000L);

	private String bitmarkWalletFileName;
	private String walletFolder;
	private Pattern hexPattern;

	private static Scanner scanner;

	private WalletAppKit walletAppkit = null;

	/**
	 * <p>
	 * Set a bitcoinj walletAppKit.
	 * </p>
	 *
	 * @param net
	 *            specify the net for the walletAppKit. @see NetType
	 * @throws IOException
	 */
	public BitmarkWalletKit(NetType net, String walletFolder, List<PeerAddress> peerAddresses) throws IOException {
		NetworkParameters netParams;
		this.walletFolder = walletFolder;

		String filePrefix = "bitmarkWallet";

		bitmarkWalletFileName = filePrefix + "-" + net;

		switch (net) {
		case BITMARK:
			netParams = MainNetParams.get();
			break;
		case DEVELOPMENT:
			netParams = BitmarkRegTestParams.get();
			break;
		case TESTING:
			netParams = BitmarkRegTestParams.get();
			break;
		case LOCAL_BITCOIN_TESTNET:
			netParams = TestNet3Params.get();
			break;
		case LOCAL_BITCOIN_REG:
			netParams = RegTestParams.get();
			break;
		default:
			throw new IOException("Invalid net: " + net);
		}

		walletAppkit = new WalletAppKit(netParams, new File(walletFolder), bitmarkWalletFileName);
		if (net.equals(NetType.LOCAL_BITCOIN_REG)) {
			walletAppkit.connectToLocalHost();
		}

		if (peerAddresses != null) { // peerAddress is specified
			PeerAddress[] arrayPeerAddr = new PeerAddress[peerAddresses.size()];
			peerAddresses.toArray(arrayPeerAddr);
			walletAppkit.setPeerNodes(arrayPeerAddr);
		}
	}

	public WalletAppKit getWalletAppkit() {
		return walletAppkit;
	}

	/**
	 * <p>
	 * Set wallet listener.
	 * </p>
	 *
	 * @param wallet
	 */
	public void setWalletListener() {
		Wallet wallet = walletAppkit.wallet();
		wallet.addCoinsSentEventListener(new WalletCoinsSentEventListener() {

			@Override
			public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
				System.out.println("Send coin, preBalance: " + prevBalance);
				System.out.println("new balance: " + newBalance);
			}
		});
	}

	/**
	 * <p>
	 * Get an address for the wallet. Will not refresh the address and always
	 * use the same one
	 * </p>
	 *
	 * @param wallet
	 * @param netParams
	 * @return Address
	 * @throws IOException
	 */

	public Address getAddress() throws IOException {
		Wallet wallet = walletAppkit.wallet();
		NetworkParameters netParams = walletAppkit.params();
		Address address;
		if (wallet.getIssuedReceiveAddresses().size() < 1) {
			address = wallet.currentReceiveKey().toAddress(netParams);
			log.info("Create new address " + address);
		} else {
			address = wallet.getIssuedReceiveAddresses().get(0);
			log.info("Using existing address " + address);
		}

		return address;
	}


	/**
	 * <p>
	 * Send coins (bitmark and mine fee) from this wallet.
	 * </p>
	 *
	 * @param txid
	 *            bitmark transaction ID to pay
	 * @param payments
	 *            address will receive the bitmark coins
	 * @param changeAddress
	 *            address will receive the changes. Set null to send to new
	 *            address from the wallet
	 * @param password
	 *            required if the wallet is encrypted
	 * @return true after the payment has been broadcasted successfully
	 */
        //public boolean sendCoins(String payId, Address forwardingAddress, Address changeAddress, Long amountSatoshi, String password) {
	public String sendCoins(String payId, List<Payment>payments, Address changeAddress, String password) {

		if (null == payments || payments.size() < 1) {
			log.error("Need at least one payment address/amount item");
			return null;
		}
		Iterator<Payment> paymentIterator = payments.iterator();

                Wallet wallet = walletAppkit.wallet();
		try {
			if (!paymentIterator.hasNext()) {
				log.error("Need at least one payment address/amount item");
				return null;
			}
			Payment item = paymentIterator.next();

			log.info("Sending {} satoshis to {}", item.amountSatoshi, item.paymentAddress);
			SendRequest sendRequest = SendRequest.to(item.paymentAddress, item.amountSatoshi);

			while (paymentIterator.hasNext()) {
				item = paymentIterator.next();
				log.info("Sending {} satoshis to {}", item.amountSatoshi, item.paymentAddress);
				sendRequest.tx.addOutput(item.amountSatoshi, item.paymentAddress);
                        }

                        // Create the OP_Return
                        sendRequest.tx.addOutput(Coin.valueOf(0), generateBitmarkScript(payId));

                        // Set the default fee
			sendRequest.feePerKb = MINE_FEE;

			if (changeAddress != null) {
				sendRequest.changeAddress = changeAddress;
			}

			if (password != null) {
				sendRequest.aesKey = wallet.getKeyCrypter().deriveKey(password);
			}

                        // Perform the transfer
			Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);

			sendResult.broadcastComplete.addListener(new Runnable() {
				public void run() {
					log.info("Send for: {}  success: TxId: {}",
						 payId,
						 sendResult.tx.getHashAsString());
					log.info("Change address: " + sendRequest.changeAddress);
					log.info("Wallet balance (estimated satoshi): {}",
						 wallet.getBalance(BalanceType.ESTIMATED_SPENDABLE));
					log.info("Wallet balance (available satoshi): {}",
						 wallet.getBalance(BalanceType.AVAILABLE_SPENDABLE));
				}
			}, MoreExecutors.directExecutor());

			// Make the thread wait until the tx broadcast has completed
			try {
				sendResult.broadcastComplete.get();
			} catch (InterruptedException | ExecutionException e) {
				log.error("Broadcast {} to peers failed and tx has been commit to wallet. Please try to re-broadcast the tx: {}",
					  sendResult.tx.getHashAsString());
				return null;
			}
			return sendResult.tx.getHashAsString();
		} catch (KeyCrypterException | InsufficientMoneyException e) {
			log.error("Send for: {}  error: {}", payId, e);
			return null;
		}
	}

	private Script generateBitmarkScript(String payId) {
		if (!checkHex(payId)) {
			log.error("PayId is not hex string: {}", payId);
			return null;
		}
		int countPayId = payId.length() / 2;
		String scriptStr = "6a" + Integer.toHexString(countPayId) + payId;
		byte[] bytes = new BigInteger(scriptStr, 16).toByteArray();
		return new Script(bytes);
	}

	public boolean checkHex(String str) {
		if (hexPattern == null) {
			hexPattern = Pattern.compile("[0-9a-fA-F]+");
		}
		return hexPattern.matcher(str).matches();
	}

	/**
	 * <p>
	 * The prefix file name for the wallet and block chain
	 * </p>
	 *
	 * @return prefix name
	 */
	public String getBitmarkWalletFileName() {
		return bitmarkWalletFileName;
	}

	/**
	 * <p>
	 * Check if the wallet is encrypted.
	 * </p>
	 *
	 * @param wallet
	 * @return true if encrypted
	 */
	public boolean walletIsEncrypted() {
		return walletAppkit.wallet().isEncrypted();
	}

	public File getWalletFile() {
		return new File(walletFolder, bitmarkWalletFileName + ".wallet");
	}

	/**
	 * <p>
	 * Create a system console to let the user type password.
	 * </p>
	 *
	 * @param msg
	 *            Message would like to show in the console
	 * @return password string
	 */
	public static String getPasswordConsole(String msg) {
		Console console = System.console();
		if (console == null) {
			System.out.println("Couldn't get Console instance");
			System.exit(0);
		}
		char passwordArray[] = console.readPassword(msg);
		return String.valueOf(passwordArray);
	}

	public static String getPassword(String consoleMsg, String password) {
		if (password != null) {
			return password;
		}
		return getPasswordConsole(consoleMsg);
	}

	public static String getStdinPassword() {
		if (scanner == null) {
			scanner = new Scanner(System.in);
		}
		return scanner.nextLine();
	}
}
