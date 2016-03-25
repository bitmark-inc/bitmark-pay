// Copyright (c) 2014-2016 Bitmark Inc.
// Use of this source code is governed by an ISC
// license that can be found in the LICENSE file.

package bitmark.com.wallet;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.Wallet.BalanceType;
import org.bitcoinj.core.Wallet.SendRequest;
import org.bitcoinj.core.WalletEventListener;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.MoreExecutors;

/**
 * <p>BitmarkWalletKit can create WalletAppKit according to different net setting, and provides functions for BitmarkWalletService.</p>
 * @author yuntai
 *
 */
public class BitmarkWalletKit {
	private static final Logger log = LoggerFactory.getLogger(BitmarkWalletKit.class);

	/**
	 * Fee charged by bitmark, when you issue, transfer your bitmark item. 
	 */
	public static final long BITMARK_FEE = 200000L;
	
	/**
	 * Fee charged by other bitcoin peer to mine the transaction
	 */
	public static final long MINE_FEE = 5000L;

	private static String bitmarkWalletFileName;

	/**
	 * <p>Get a bitcoing walletAppKit.</p>
	 * @param net specify the net for the walletAppKit. @see NetType
	 * @return bitcoinj.WalletAppKit
	 * @throws IOException
	 */
	public static WalletAppKit getWalletKit(NetType net, String walletFolder, List<PeerAddress> peerAddresses) throws IOException {
		NetworkParameters netParams;
		String filePrefix = "bitmarkWallet";
		
		bitmarkWalletFileName = filePrefix + "-" + net;

		switch (net) {
		case LIVENET:
			netParams = MainNetParams.get();
		case TESTNET:
			netParams = TestNet3Params.get();
			break;
		case REGTEST:
			netParams = BitmarkRegTestParams.get();
			break;
		case LOCAL:
			netParams = BitmarkRegTestParams.get();
			break;
		default:
			return null;
		}

		WalletAppKit kit = new WalletAppKit(netParams, new File(walletFolder), bitmarkWalletFileName);
		if (net.equals(NetType.LOCAL)) {
			kit.connectToLocalHost();
			
		}else if(peerAddresses != null) { // net is not local and peerAddress is specified
			PeerAddress[] arrayPeerAddr = new PeerAddress[peerAddresses.size()];
			peerAddresses.toArray(arrayPeerAddr);
			kit.setPeerNodes(arrayPeerAddr);
		}

		// Set the default fee
		SendRequest.DEFAULT_FEE_PER_KB = Coin.valueOf(MINE_FEE);

		return kit;
	}
	
	/**
	 * <p>Set wallet listener.</p>
	 * @param wallet
	 */
	public static void setWalletListener(Wallet wallet) {
		wallet.addEventListener(new WalletEventListener() {

			public void onKeysAdded(List<ECKey> keys) {
				// TODO Auto-generated method stub

			}

			public void onCoinsReceived(Wallet wallet, final Transaction tx, Coin prevBalance, Coin newBalance) {
			}

			public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
				System.out.println("Send coin, preBalance: " + prevBalance);
				System.out.println("new balance: " + newBalance);
			}

			public void onReorganize(Wallet wallet) {
				System.out.println("Reveiced new block: " + wallet.getLastBlockSeenHeight());
				// TODO Auto-generated method stub

			}

			public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
			}

			public void onWalletChanged(Wallet wallet) {
				// TODO Auto-generated method stub

			}

			public void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts) {
				// TODO Auto-generated method stub

			}

		});
	}
	
	/**
	 * <p>Get an address for the wallet. Will not refresh the address and always use the same one</p>
	 * @param wallet
	 * @param netParams
	 * @return Address
	 * @throws IOException
	 */

	public static Address getAddress(Wallet wallet, NetworkParameters netParams) throws IOException {
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
	 * <p>Send coins (bitmark and mine fee) from this wallet.</p>
	 * @param wallet
	 * @param forwardingAddress address will receive the bitmark coins
	 * @param changeAddress address will receive the changes
	 * @param password required if the wallet is encrypted
	 * @return true after the payment has been broadcasted successfully
	 */
	public static boolean sendCoins(Wallet wallet, Address forwardingAddress, Address changeAddress, String password) {
		try {
			Coin bitmarkFee = Coin.valueOf(BITMARK_FEE);
			log.info("Sending {} satoshis to {}", BITMARK_FEE, forwardingAddress);
			SendRequest sendRequest = SendRequest.to(forwardingAddress, bitmarkFee);
			sendRequest.changeAddress = changeAddress;
			if (password != null) {
				sendRequest.aesKey = wallet.getKeyCrypter().deriveKey(password);	
			}
			
			
			Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);

			sendResult.broadcastComplete.addListener(new Runnable() {
				public void run() {
					log.info("Sent to {} success! Transaction hash: {}", forwardingAddress,
							sendResult.tx.getHashAsString());
					log.info("Change address: " + sendRequest.changeAddress);
					log.info("Wallet balance (estimated satoshi): {}", wallet.getBalance(BalanceType.ESTIMATED_SPENDABLE));
					log.info("Wallet balance (available satoshi): {}", wallet.getBalance(BalanceType.AVAILABLE_SPENDABLE));
				}
			}, MoreExecutors.sameThreadExecutor());

			// Make the thread wait until the tx broadcast has completed
			try {
				sendResult.broadcastComplete.get();
			} catch (InterruptedException | ExecutionException e) {
				log.error(
						"Broadcast {} to peers failed and tx has been commit to wallet. Please try to re-broadcast the tx: {}",
						sendResult.tx.getHashAsString());
				return false;
			}
			return true;
		} catch (KeyCrypterException | InsufficientMoneyException e) {
			log.error("Sent to {} failled: {}", forwardingAddress, e);
			return false;
		}
	}

	/**
	 * <p>The prefix file name for the wallet and block chain</p>
	 * @return prefix name 
	 */
	public static String getBitmarkWalletFileName() {
		return bitmarkWalletFileName;
	}

	/**
	 * <p>Check if the wallet is encrypted.</p> 
	 * @param wallet
	 * @return true if encrypted
	 */
	public static boolean walletIsEncrypted(Wallet wallet){
		return wallet.isEncrypted();
	}
	
	/**
	 * <p>Create a system console to let the user type password.</p>
	 * @param msg Message would like to show in the console
	 * @return password string
	 */
	public static String getPasswordConsole(String msg){
		Console console = System.console();
		if (console == null) {
			System.out.println("Couldn't get Console instance");
			System.exit(0);
		}
		char passwordArray[] = console.readPassword(msg);
		return String.valueOf(passwordArray);
	}
	
	/**
	 * <p>Interface for getting password. 
	 * If the wallet is encrypted, and command is decrypt or pay, 
	 * and if the password is null, will prompt system console for user.
	 * If the wallet is not encrypted, command is encrypt and the password is null, prompt system console for user.
	 * </p>
	 * @param consoleMsg
	 * @param password
	 * @param walletIsEncrypt
	 * @param cmd
	 * @return
	 */
	public static String getPassword(String consoleMsg, String password, boolean walletIsEncrypt, Commands cmd){
		if (walletIsEncrypt && cmd.equals(Commands.ENCRYPT)) {
			return null;
		}
		if (!walletIsEncrypt && (cmd.equals(Commands.DECRYPT) || cmd.equals(Commands.PAY))) {
			return null;
		}
		
		if (password != null) {
			return password;
		}
		return getPasswordConsole(consoleMsg);
		
	}
}
