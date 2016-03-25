// Copyright (c) 2014-2016 Bitmark Inc.
// Use of this source code is governed by an ISC
// license that can be found in the LICENSE file.

package bitmark.com.wallet;

import java.util.ArrayList;

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
 * <p>Provide command line service for BitmarkWalletKit.</p>
 * @author yuntai
 *
 */
public class BitmarkWalletService {
	private static final Logger log = LoggerFactory.getLogger(BitmarkWalletService.class);
	private static WalletAppKit kit;

	public static void main(String[] args) throws Exception {
		// create the Options
		Options options = new Options();
		options.addOption("h", "help", false, "print this message");
		options.addOption(Option.builder().longOpt("password").desc("give password for encrypt, decrypt, pay")
				.hasArg(true).build());
		options.addOption(Option.builder().longOpt("net").required(true)
				.desc("the net type the wallet is going to link: local|regtest|testnet").hasArg(true).build());
		options.addOption(Option.builder().longOpt("config").required(false)
				.desc("the folder to store the wallet and the spv chain.").hasArg(true).build());

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
				if (line.getArgs().length > 0) {
					cmd = Commands.valueOf(line.getArgs()[0].toUpperCase());
					line.getArgList().remove(0);
				}
			}
		} catch (org.apache.commons.cli.ParseException e) {
			e.printStackTrace();
			return;
		} catch (java.lang.IndexOutOfBoundsException e) {
			printHelpMessage(options);
			return;
		}

		BitmarkConfigReader configs = new BitmarkConfigReader(configFile);
		configs.getBitcoinPeers();
		// prepare the Wallet
		NetType netType = NetType.valueOf(net.toUpperCase());
		kit = BitmarkWalletKit.getWalletKit(netType, configs.getWalletFolder(), configs.getBitcoinPeers());
		if (kit == null) {
			System.err.println("Unrecognize net type: " + net);
			return;
		}

		kit.startAsync();
		kit.awaitRunning();

		log.info("{} balance: {}", BitmarkWalletKit.getBitmarkWalletFileName(),
				kit.wallet().getBalance(BalanceType.AVAILABLE_SPENDABLE));

		String consoleMsg;
		String passwordFromCmd;
		boolean walletIsEncrypted;
		String password = null;

		switch (cmd) {
		case ENCRYPT:
			consoleMsg = "Set password (>=8):";
			passwordFromCmd = line.getOptionValue("password");
			walletIsEncrypted = BitmarkWalletKit.walletIsEncrypted(kit.wallet());

			password = BitmarkWalletKit.getPassword(consoleMsg, passwordFromCmd, walletIsEncrypted, cmd);
			if (password == null) {
				System.out.println("Wallet is encrypted");
				return;
			}

			if (password.length() < 8) {
				System.out.println("Password length should >= 8");
				return;
			}

			kit.wallet().encrypt(password);
			break;
		case DECRYPT:
			consoleMsg = "Password:";
			passwordFromCmd = line.getOptionValue("password");
			walletIsEncrypted = BitmarkWalletKit.walletIsEncrypted(kit.wallet());

			password = BitmarkWalletKit.getPassword(consoleMsg, passwordFromCmd, walletIsEncrypted, cmd);

			if (password == null) {
				System.out.println("Wallet is not encrypt");
				return;
			}

			if (!kit.wallet().checkPassword(password)) {
				System.out.println("Wrong password");
				return;
			}

			kit.wallet().decrypt(password);
			break;
		case PAY:
			if (line.getArgs().length > 0) {
				targets = line.getArgs();
			} else {
				System.out.println("Please give addresses to pay");
				return;
			}

			consoleMsg = "Password:";
			passwordFromCmd = line.getOptionValue("password");
			walletIsEncrypted = BitmarkWalletKit.walletIsEncrypted(kit.wallet());

			password = BitmarkWalletKit.getPassword(consoleMsg, passwordFromCmd, walletIsEncrypted, cmd);
			if (password != null && !kit.wallet().checkPassword(password)) {
				System.out.println("Wrong password");
				return;
			}

			Address changeAddr = BitmarkWalletKit.getAddress(kit.wallet(), kit.params());
			ArrayList<String> failedAddresses = new ArrayList<>();
			for (String sendAddress : targets) {
				Address tmpAddr = null;
				try {
					tmpAddr = new Address(kit.params(), sendAddress);
					if (!BitmarkWalletKit.sendCoins(kit.wallet(), tmpAddr, changeAddr, password)) {
						failedAddresses.add(sendAddress);
					}
				} catch (AddressFormatException e) {
					System.out.println("Invalid address: " + tmpAddr);
					failedAddresses.add(sendAddress);
				}

			}
			int failedSize = failedAddresses.size();
			System.out.printf("%d successed, %d failed\n", targets.length - failedSize, failedSize);
			if (failedSize != 0) {
				long needSatoshi = (BitmarkWalletKit.BITMARK_FEE + BitmarkWalletKit.MINE_FEE) * failedSize;
				System.out.printf("%d Payment failed, you might need %d satoshi and wallet balance is %d\n", failedSize,
						needSatoshi, kit.wallet().getBalance(BalanceType.AVAILABLE_SPENDABLE).value);
				System.out.println("Failed payment address: ");
				for (String failedAddress : failedAddresses) {
					System.out.println(failedAddress);
				}
			}
			break;
		case BALANCE:
			System.out.println("Wallet estimated satoshi: " + kit.wallet().getBalance(BalanceType.ESTIMATED_SPENDABLE));
			System.out.println("Wallet available satoshi: " + kit.wallet().getBalance(BalanceType.AVAILABLE_SPENDABLE));
			break;
		case ADDRESS:
			System.out.println("Wallet watched address: " + BitmarkWalletKit.getAddress(kit.wallet(), kit.params()));
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

		if (!BitmarkWalletKit.walletIsEncrypted(kit.wallet())) {
			System.out.println("NOTE: Please run encrypt to protect your wallet");
		}

		log.debug("Balance: " + kit.wallet().getBalance(BalanceType.AVAILABLE_SPENDABLE));
	}

	private static void printHelpMessage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		System.out.println("bitmarkWalletService [options] <command>");
		System.out.println("command:");
		System.out.println(" pay [addresses...]    pay the addresses");
		System.out.println(" balance               get wallet balance");
		System.out.println(" address               get wallet address");
		System.out.println(" pending-tx            get pending transactions");

		formatter.printHelp(" ", options, false);

	}

}
