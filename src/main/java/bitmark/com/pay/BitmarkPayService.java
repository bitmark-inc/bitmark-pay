// Copyright (c) 2014-2016 Bitmark Inc.
// Use of this source code is governed by an ISC
// license that can be found in the LICENSE file.

package bitmark.com.pay;

import java.io.File;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
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
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.bitcoinj.kits.WalletAppKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import bitmark.com.config.BitmarkConfigReader;
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
	private static Logger log = LoggerFactory.getLogger(BitmarkPayService.class);;

	public static void main(String[] args) throws Exception {

		// create the Options
		Options options = new Options();
		options.addOption("h", "help", false, "print this message");
		options.addOption("s", "stdin", false, "send password through stdin for encrypt, decrypt, pay");
		options.addOption("j", "json", false, "json output");
		options.addOption(Option.builder().longOpt("password").desc("give password for encrypt, decrypt, pay")
				.hasArg(true).build());
		options.addOption(Option.builder().longOpt("net").required(true)
				.desc("*the net type the wallet is going to link: bitmark|testing|local_bitcoin_testnet|local_bitcoin_reg")
				.hasArg(true).build());
		options.addOption(
				Option.builder().longOpt("config").required(true).desc("*the config file").hasArg(true).build());
		options.addOption(Option.builder().longOpt("log-config").required(false).desc("the log4j config file")
				.hasArg(true).build());

		boolean enableStdin = false;
		boolean enableJson = false;

		String net = "";
		String configFile = "";
		String logConfigFile = "";
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
				logConfigFile = line.getOptionValue("log-config");
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

		BitmarkConfigReader configs = new BitmarkConfigReader(configFile);
		String walletDirectory = configs.getDataDirectory() + "/wallet";
		String logDirectory = configs.getDataDirectory() + "/log";

		// start log

		if (logConfigFile == "" || logConfigFile == null) {
			configure(logDirectory);
		} else {
			File file = new File(logConfigFile);
			LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
			context.setConfigLocation(file.toURI());
		}
		log.info("start logging..");

		// prepare the Wallet
		NetType netType = NetType.valueOf(net.toUpperCase());
		BitmarkWalletKit bitmarkWalletKit = new BitmarkWalletKit(netType, walletDirectory, configs.getBitcoinPeers());
		WalletAppKit kit = bitmarkWalletKit.getWalletAppkit();
		if (kit == null) {
			log.error("walletappkit is null!");
			return;
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

			Address paymentAddr = Address.fromBase58(kit.params(), targets[1]);
			if (!bitmarkWalletKit.sendCoins(txid, paymentAddr, null, password)) {
				long needSatoshi = BitmarkWalletKit.BITMARK_FEE + BitmarkWalletKit.MINE_FEE;
				System.err.printf("Payment failed, you might need %d satoshi and wallet balance is %d\n", needSatoshi,
						kit.wallet().getBalance(BalanceType.AVAILABLE_SPENDABLE).value);
				System.err.printf("Failed payment:\ntxid:%s\naddress:%s\n", txid, paymentAddr);
			}
			System.out.println("Payment successed.");
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
				break;
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
		System.out.println(" pay txid addresses    pay the addresses");
		System.out.println(" balance               get wallet balance");
		System.out.println(" address               get wallet address");
		System.out.println(" pending-tx            get pending transactions");
		System.out.println(" info                  get wallet balance and address");

		formatter.printHelp(" ", options, false);

	}

	public static void configure(String logDirecotry) {

		LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
		Configuration config = (AbstractConfiguration) context.getConfiguration();

		final String fileName = "/bitmarkPay.log";
		final String filePattern = "/bitmarkPay-%i.log";
		final String pattern = "%d{yyyy-MM-dd'T'HH:mm:ssZ} %p %c [%t] %m%n";

		SizeBasedTriggeringPolicy policy = SizeBasedTriggeringPolicy.createPolicy("10 MB");
		DefaultRolloverStrategy strategy = DefaultRolloverStrategy.createStrategy("20", "1", "max", "0", null, true,
				config);
		PatternLayout layout = PatternLayout.createLayout(pattern, null, config, null, null, true, false, "", "");

		RollingRandomAccessFileAppender fileAppender = RollingRandomAccessFileAppender.createAppender(
				logDirecotry + fileName, logDirecotry + filePattern, "true", "RollingFiles", "true",
				String.valueOf(RollingRandomAccessFileManager.DEFAULT_BUFFER_SIZE), policy, strategy, layout, null,
				"true", "true", "", config);
		fileAppender.start();
		config.addAppender(fileAppender);

		AppenderRef[] refs = new AppenderRef[] { AppenderRef.createAppenderRef(fileAppender.getName(), null, null) };
		LoggerConfig loggerConfig = LoggerConfig.createLogger("false", Level.INFO, LogManager.ROOT_LOGGER_NAME, "true",
				refs, null, config, null);
		loggerConfig.addAppender(fileAppender, null, null);
		config.addLogger(LogManager.ROOT_LOGGER_NAME, loggerConfig);
		context.updateLoggers();

	}

}
