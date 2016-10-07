package bitmark.com.config;

import java.io.IOException;
import java.io.InputStream;

import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

import org.bitcoinj.core.PeerAddress;

public class BitmarkConfigReader {

	// set false if file was read
	// set turue if the default embedded in the jar was used
	// files from src/main/resources/*.xml are embedded in the jar
	private boolean defaultConfiguration = true;

	private List<PeerAddress> bitcoinPeers;

	public BitmarkConfigReader(String file) throws Exception {
		Configurations configs = new Configurations();
		if (file == null || file.equals("")) {
			throw new ConfigurationException("config file cannot be null");
		}

		try {
			try {
				XMLConfiguration config = configs.xml(file);
				bitcoinPeers = parseBitCoinPeers(config);
				defaultConfiguration = false;
			} catch (ConfigurationException e) {

				String path = FileSystems.getDefault().getPath(file).getFileName().toString();
				URL defaultFile = getClass().getClassLoader().getResource(path);

				//System.err.printf("read configuration from default: %s\n", defaultFile);

				XMLConfiguration config = configs.xml(defaultFile);
				bitcoinPeers = parseBitCoinPeers(config);

			}
		} catch (ConfigurationException e) {
			//System.err.printf("read configuration from %s failed: %s\n", file, e);
			throw e;
		}
	}

	private List<PeerAddress> parseBitCoinPeers(XMLConfiguration config) throws Exception {
		List<String> peers = config.getList(String.class, "bitcoin.peers.ip");
		if (peers == null || peers.size() == 0) {
			return null;
		}

		List<Integer> ports = config.getList(Integer.class, "bitcoin.peers.ip[@port]");

		List<PeerAddress> peerAddresses = new ArrayList<PeerAddress>();
		for (int i = 0; i < peers.size(); i++) {
			byte[] address = convertToInetAddress(peers.get(i));
			try {
				InetAddress tmpAddr = InetAddress.getByAddress(address);
				peerAddresses.add(new PeerAddress(tmpAddr, ports.get(i)));
			} catch (IOException e) {
				//System.err.printf("Failed to convert address: %s\n", e);
				throw e;
			}
		}
		return peerAddresses;
	}

	private byte[] convertToInetAddress(String address) throws Exception {
		byte[] byteAddr = new byte[4];
		String[] tmpAddr = address.split("\\.");
		if (tmpAddr.length != 4) {
			String msg = "Incorrect address format: " + address;
			//System.err.println(msg);
			throw new Exception(msg);
		}

		for (int i = 0; i < tmpAddr.length; i++) {
			byteAddr[i] = Integer.valueOf(tmpAddr[i]).byteValue();
		}
		return byteAddr;
	}

	public List<PeerAddress> getBitcoinPeers() {
		return bitcoinPeers;
	}

	public boolean isDefault() {
		return defaultConfiguration;
	}
}
