// Copyright (c) 2014-2016 Bitmark Inc.
// Use of this source code is governed by an ISC
// license that can be found in the LICENSE file.

package bitmark.com.wallet;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.bitcoinj.core.*;
import org.bitcoinj.core.Wallet.BalanceType;
import org.bitcoinj.kits.WalletAppKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bitmark.com.config.BitmarkConfigReader;

/**
 * <p>
 * Provide command line service for BitmarkWalletKit.
 * </p>
 * 
 * @author yuntai
 *
 */
public class BitmarkWalletService {
	private static final Logger log = LoggerFactory.getLogger(BitmarkWalletService.class);
	private static boolean enableStdin = false;

	public static void main(String[] args) throws Exception {
		// create the Options
		Options options = new Options();
		options.addOption("h", "help", false, "print this message");
		options.addOption("s", "stdin", false, "send password through stdin for encrypt, decrypt, pay");
		options.addOption(Option.builder().longOpt("password").desc("give password for encrypt, decrypt, pay")
				.hasArg(true).build());
		options.addOption(Option.builder().longOpt("net").required(true)
				.desc("*the net type the wallet is going to link: local|regtest|testnet|livenet").hasArg(true).build());
		options.addOption(
				Option.builder().longOpt("config").required(true).desc("*the config file").hasArg(true).build());

		String net = "";
		String configFile = "";
		Commands cmd = null;
		String[] targets = null;
		CommandLine line;
		CommandLineParser parser = new DefaultParser();

		try {
			// parse the command line arguments
			line = parser.parse(options, args);
			if (line.hasOption("help")) {
				printHelpMessage(options);
				return;
			} else {
				net = line.getOptionValue("net");
				configFile = line.getOptionValue("config");
				if (line.hasOption("stdin")) {
					enableStdin = true;
				}
				if (line.getArgs().length > 0) {
					cmd = Commands.valueOf(line.getArgs()[0].toUpperCase());
					line.getArgList().remove(0);
				}
			}
		} catch (org.apache.commons.cli.ParseException e) {
			printHelpMessage(options);
			return;
		} catch (java.lang.IndexOutOfBoundsException e) {
			printHelpMessage(options);
			return;
		}

		BitmarkConfigReader configs = new BitmarkConfigReader(configFile);
		configs.getBitcoinPeers();
		// prepare the Wallet
		NetType netType = NetType.valueOf(net.toUpperCase());
		BitmarkWalletKit bitmarkWalletKit = new BitmarkWalletKit(netType, configs.getWalletFolder(), configs.getBitcoinPeers());
		WalletAppKit kit = bitmarkWalletKit.getWalletAppkit();
		if ( kit == null ) {
			log.error("walletappkit is null!");
			return;
		}
		
		kit.startAsync();
		kit.awaitRunning();

		log.info("{} balance: {}", bitmarkWalletKit.getBitmarkWalletFileName(),
				kit.wallet().getBalance(BalanceType.AVAILABLE_SPENDABLE));

		String consoleMsg;
		String passwordFromCmd;
		String password = null;

		switch (cmd) {
		case ENCRYPT:
			if (bitmarkWalletKit.walletIsEncrypted()) {
				System.err.println("Wallet is encrypted");
				return;
			}

			String verifyPassword;
			if (enableStdin) {
				password = BitmarkWalletKit.getStdinPassword();
				verifyPassword = BitmarkWalletKit.getStdinPassword();
			} else {
				consoleMsg = "Set password (>=8):";
				passwordFromCmd = line.getOptionValue("password");
				password = BitmarkWalletKit.getPassword(consoleMsg, passwordFromCmd);

				consoleMsg = "Verify password:";
				verifyPassword = BitmarkWalletKit.getPassword(consoleMsg, passwordFromCmd);
			}

			if (password.length() < 8) {
				System.err.println("Password length should >= 8");
				return;
			}

			if (!password.equals(verifyPassword)) {
				System.err.println("Verify wallet password failed");
				return;
			}

			kit.wallet().encrypt(password);
			break;
		case DECRYPT:
			if (!bitmarkWalletKit.walletIsEncrypted()) {
				System.err.println("Wallet is not encrypted");
				return;
			}

			if (enableStdin) {
				password = BitmarkWalletKit.getStdinPassword();
			} else {
				consoleMsg = "Password:";
				passwordFromCmd = line.getOptionValue("password");
				password = BitmarkWalletKit.getPassword(consoleMsg, passwordFromCmd);
			}

			if (!kit.wallet().checkPassword(password)) {
				System.err.println("Wrong password");
				return;
			}

			kit.wallet().decrypt(password);
			break;
		case PAY:
			if (line.getArgs().length == 2) {
				targets = line.getArgs();
			} else {
				System.err.println("Please give txid and addresses");
				return;
			}

			// get password if wallet is encrypted and check it
			if (bitmarkWalletKit.walletIsEncrypted()) {
				if (enableStdin) {
					password = BitmarkWalletKit.getStdinPassword();
				} else {
					consoleMsg = "Password:";
					passwordFromCmd = line.getOptionValue("password");
					password = BitmarkWalletKit.getPassword(consoleMsg, passwordFromCmd);
				}

				if (!kit.wallet().checkPassword(password)) {
					System.err.println("Wrong password");
					return;
				}
			}

			String txid = targets[0];
			if (!bitmarkWalletKit.checkHex(txid)) {
				System.err.println("First parameter is not hex");
				return;
			}
			Address paymentAddr = new Address(kit.params(), targets[1]);
			if (!bitmarkWalletKit.sendCoins(txid, paymentAddr, null, password)) {
				long needSatoshi = BitmarkWalletKit.BITMARK_FEE + BitmarkWalletKit.MINE_FEE;
				System.err.printf("Payment failed, you might need %d satoshi and wallet balance is %d\n", needSatoshi,
						kit.wallet().getBalance(BalanceType.AVAILABLE_SPENDABLE).value);
				System.err.printf("Failed payment:\ntxid:%s\naddress:%s\n", txid, paymentAddr);
			}
			System.out.println("Payment successed.");
			break;
		case BALANCE:
			System.out.println("Wallet estimated satoshi: " + kit.wallet().getBalance(BalanceType.ESTIMATED_SPENDABLE));
			System.out.println("Wallet available satoshi: " + kit.wallet().getBalance(BalanceType.AVAILABLE_SPENDABLE));
			break;
		case ADDRESS:
			System.out.println("Wallet watched address: " + bitmarkWalletKit.getAddress());
			break;
		case PENDING_TX:
			if (kit.wallet().getPendingTransactions().size() == 0) {
				System.out.println("No pending transactions");
				break;
			}
			for (Transaction tx : kit.wallet().getPendingTransactions()) {
				System.out.println(tx.getHashAsString());
			}
			break;
		default:
			printHelpMessage(options);
			return;
		}

		if (!bitmarkWalletKit.walletIsEncrypted()) {
			System.out.println("NOTE: Please run encrypt to protect your wallet");
		}

		log.debug("Balance: " + kit.wallet().getBalance(BalanceType.AVAILABLE_SPENDABLE));
	}

	private static void printHelpMessage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		System.out.println("bitmarkWalletService [options] <command>");
		System.out.println("command:");
		System.out.println(" pay txid addresses    pay the addresses");
		System.out.println(" balance               get wallet balance");
		System.out.println(" address               get wallet address");
		System.out.println(" pending-tx            get pending transactions");

		formatter.printHelp(" ", options, false);

	}

}
