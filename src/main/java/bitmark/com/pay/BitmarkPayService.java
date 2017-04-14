// Copyright (c) 2014-2016 Bitmark Inc.
// Use of this source code is governed by an ISC
// license that can be found in the LICENSE file.

package bitmark.com.pay;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.RollingRandomAccessFileManager;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.lookup.MainMapLookup;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.bitcoinj.kits.WalletAppKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import bitmark.com.config.BitmarkConfigReader;
import bitmark.com.json.TxIdJsonResponse;
import bitmark.com.json.AddressJsonResponse;
import bitmark.com.json.BalanceJsonResponse;
import bitmark.com.json.InfoJsonResponse;

/**
 * <p>
 * Provide command line service for BitmarkWalletKit.
 * </p>
 *
 * @author yuntai
 *
 */
public class BitmarkPayService {

	// initialise later after we have determined the directory location
	// but cannot delay too long or some other process will start logging
	// and weird log file names will be generated
	private static Logger log = null; //LoggerFactory.getLogger(BitmarkPayService.class);

	public static void main(String[] args) throws Exception {

		{
			// just in case: set a meaningful default
			// this is overidden later
			String[] a = {"logdir", "./log",
				      "prefix", "unknown"};
			MainMapLookup.setMainArguments(a);
		}

		// create the Options
		Options options = new Options();
		options.addOption("h", "help", false, "print this message");
		options.addOption("s", "stdin", false, "send password through stdin for encrypt, decrypt, pay");
		options.addOption("j", "json", false, "json output");
		options.addOption(Option.builder().longOpt("password")
				  .desc("give password for encrypt, decrypt, pay")
				  .hasArg(true).build());
		options.addOption(Option.builder().longOpt("network").required(true)
				  .desc("*the net type the wallet is going to link: bitmark|testing|local_bitcoin_testnet|local_bitcoin_reg")
				  .hasArg(true).build());
		options.addOption(Option.builder().longOpt("config-dir").required(true)
				  .desc("*the configuration directory file")
				  .hasArg(true).build());

		boolean enableStdin = false;
		boolean enableJson = false;

		String network = "";
		String configDirectory = "";
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
				network = line.getOptionValue("network");
				configDirectory = line.getOptionValue("config-dir");
				if (line.hasOption("stdin")) {
					enableStdin = true;
				}
				if (line.hasOption("json")) {
					enableJson = true;
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

		// configure logger first or it will fail as read configs dependencies will start it
		{
			String[] a = {"logdir", configDirectory + "/log",
			      "prefix", network};
			MainMapLookup.setMainArguments(a);
		}
		log = LoggerFactory.getLogger(BitmarkPayService.class);

		// start log
		log.info("start logging..");

		// read the network configuration file
		String configFile = configDirectory + "/" + network + ".xml";

		BitmarkConfigReader configs = null;
		try {
			configs = new BitmarkConfigReader(configFile);
		} catch (ConfigurationException e) {
			log.error("read configuration from: '{}' failed: {}\n", configFile, e);
			System.err.printf("read configuration from: '%s' failed: %s\n", configFile, e);
			System.exit(1);
		}

		// indicate if real or default configuration was used
		if (configs.isDefault()) {
			log.warn("configuration file: '{}'  was missing, using built-in defaults", configFile);
		} else {
			log.info("using configuration file: '{}'", configFile);
		}

		String walletDirectory = configDirectory + "/wallet";

		// prepare the Wallet
		NetType netType = NetType.valueOf(network.toUpperCase());
		BitmarkWalletKit bitmarkWalletKit = new BitmarkWalletKit(netType, walletDirectory, configs.getBitcoinPeers());

		WalletAppKit kit = bitmarkWalletKit.getWalletAppkit();
		if (kit == null) {
			log.error("walletappkit is null!");
			return;
		}

		if (cmd == Commands.RESTORE) {
			if (line.getArgs().length == 1) {
				targets = line.getArgs();
			} else {
				System.err.println("Please give the wallet seed");
				return;
			}

			String seedStr = targets[0];
			kit.restoreWalletFromSeed(new DeterministicSeed(seedStr.getBytes(), seedStr, Utils.currentTimeSeconds()));
		}

		kit.setAutoStop(false);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					log.info("state before stop: {}", kit.state());
					log.info("kit stopping...store block to: {}", bitmarkWalletKit.getWalletFile().getAbsolutePath());
					kit.peerGroup().stop();
					kit.wallet().saveToFile(bitmarkWalletKit.getWalletFile());
					kit.store().close();
					log.info("kit stopped");
				} catch (Exception e) {
					log.info("in runtime exeption: {}", e);
					e.printStackTrace();
				}
			}
		});

		// startup and wait for bitcoin network connection
		kit.startAsync();
		kit.awaitRunning();

		log.info("state: " + kit.state());
		log.info("{} balance: {}", bitmarkWalletKit.getBitmarkWalletFileName(),
				kit.wallet().getBalance(BalanceType.AVAILABLE_SPENDABLE));

		String consoleMsg;
		String passwordFromCmd;
		String password = null;
		Long estimated = 0L;
		Long available = 0L;
		String address = null;

		// deal with command after kit start
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
			if (line.getArgs().length >= 3 && 1 == (line.getArgs().length % 2)) {
				targets = line.getArgs();
			} else {
				System.err.println("Please give txid address amount {address amount}...");
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

			String payId = targets[0];
			if (!bitmarkWalletKit.checkHex(payId)) {
				System.err.println("First parameter is not hex");
				return;
			}

			List<Payment> payments = new ArrayList<Payment>();
			Coin needSatoshi = Coin.valueOf(0);
			for (int i = 1; i < targets.length; i += 2) {
				Address paymentAddress = Address.fromBase58(kit.params(), targets[i]);
				Coin amountSatoshi = Coin.valueOf(Long.parseLong(targets[i+1]));
				//payments.add(BitmarkWalletKit.Payment(amountSatoshi, paymentAddress));
				Payment p = new Payment(paymentAddress, amountSatoshi);
				payments.add(p);
				needSatoshi.add(amountSatoshi); // accumulate the total
			}
			String txId = bitmarkWalletKit.sendCoins(payId, payments, null, password);
			if (null == txId) {
				needSatoshi.add(Transaction.DEFAULT_TX_FEE);
				System.err.printf("Payment failed, you need at least %d satoshi and wallet balance is %d\n",
						  needSatoshi.value,
						  kit.wallet().getBalance(BalanceType.AVAILABLE_SPENDABLE).value);
				System.err.printf("Failed payment for:\npayId: %s\n", payId);
				return;
			}
			if (enableJson) {
				Gson gson = new Gson();
				TxIdJsonResponse response = new TxIdJsonResponse(txId);
				System.out.println(gson.toJson(response));
			} else {
				System.out.printf("success: txid: %s\n", txId);
			}
			break;
		case BALANCE:
			estimated = kit.wallet().getBalance(BalanceType.ESTIMATED_SPENDABLE).value;
			available = kit.wallet().getBalance(BalanceType.AVAILABLE_SPENDABLE).value;
			if (enableJson) {
				Gson gson = new Gson();
				BalanceJsonResponse response = new BalanceJsonResponse(estimated, available);
				System.out.println(gson.toJson(response));
			} else {
				System.out.println("Wallet estimated satoshi: " + estimated);
				System.out.println("Wallet available satoshi: " + available);
			}
			break;
		case ADDRESS:
			address = bitmarkWalletKit.getAddress().toString();
			if (enableJson) {
				Gson gson = new Gson();
				AddressJsonResponse response = new AddressJsonResponse(address);
				System.out.println(gson.toJson(response));
			} else {
				System.out.println("Wallet watched address: " + bitmarkWalletKit.getAddress());
			}

			break;
		case PENDING_TX:
			if (kit.wallet().getPendingTransactions().size() == 0) {
				System.out.println("No pending transactions");
				return;
			}
			for (Transaction tx : kit.wallet().getPendingTransactions()) {
				System.out.println(tx.getHashAsString());
			}
			break;
		case INFO:
			estimated = kit.wallet().getBalance(BalanceType.ESTIMATED_SPENDABLE).value;
			available = kit.wallet().getBalance(BalanceType.AVAILABLE_SPENDABLE).value;
			address = bitmarkWalletKit.getAddress().toString();
			if (enableJson) {
				Gson gson = new Gson();
				InfoJsonResponse response = new InfoJsonResponse(estimated, available, address);
				System.out.println(gson.toJson(response));
			} else {
				System.out.println(
						"Wallet estimated satoshi: " + kit.wallet().getBalance(BalanceType.ESTIMATED_SPENDABLE));
				System.out.println(
						"Wallet available satoshi: " + kit.wallet().getBalance(BalanceType.AVAILABLE_SPENDABLE));
				System.out.println("Wallet watched address: " + bitmarkWalletKit.getAddress());
			}

			break;
		case RESTORE:
			log.info("wallet restore complete.");
			break;
		default:
			printHelpMessage(options);
			return;
		}

		if (!bitmarkWalletKit.walletIsEncrypted() && !enableJson) {
			System.out.println("NOTE: Please run encrypt to protect your wallet");
		}

		log.debug("Balance: " + kit.wallet().getBalance(BalanceType.AVAILABLE_SPENDABLE));
		log.info("stop logging..");
	}

	private static void printHelpMessage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		System.out.println("bitmarkWalletService [options] <command>");
		System.out.println("command:");
		System.out.println(" restore <seed>            create or restore wallet");
		System.out.println(" pay <payId> <address>     pay to the address");
		System.out.println(" balance                   get wallet balance");
		System.out.println(" address                   get wallet address");
		System.out.println(" pending-tx                get pending transactions");
		System.out.println(" info                      get wallet balance and address");

		formatter.printHelp(" ", options, false);

	}

}
