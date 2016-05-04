package bitmark.com.config;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.bitcoinj.core.PeerAddress;

public class BitmarkConfigReader {

	private String dataDirectory;
	private List<PeerAddress> bitcoinPeers;
	
	private final String configDataDirectoryName = "data_directory";
	
	public BitmarkConfigReader(String file) throws Exception {
		Configurations configs = new Configurations();
		if (file == null || file.equals("")) {
			throw new ConfigurationException("Cannot find config file");
		}
		
		try {
			XMLConfiguration config = configs.xml(file);
			dataDirectory = parseDataDirectory(config);
			bitcoinPeers = parseBitCoinPeers(config);
		} catch (ConfigurationException e) {
			System.err.printf("Generate wallet config from %s failded: %s\n", file, e);
		}
	}

	private String parseDataDirectory(XMLConfiguration config) throws ConfigurationException {
		String dataDirectory = config.getString(configDataDirectoryName);
		if (dataDirectory == null) {
			throw new ConfigurationException("Cannot find required filed: "+configDataDirectoryName);
		}
		return dataDirectory;
	}
	
	private List<PeerAddress> parseBitCoinPeers(XMLConfiguration config) throws Exception {
		List<String> peers = config.getList(String.class, "bitcoin.peers.ip");
		System.out.printf("Get %d bitcoin peers from config\n", peers.size());
		if (peers.size() == 0) {
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
				System.err.printf("Failed to convert address: %s\n", e);
			}
		}
		return peerAddresses;
	}
	
	private byte[] convertToInetAddress(String address) throws Exception{
		byte[] byteAddr = new byte[4];
		String[] tmpAddr = address.split("\\.");
		if (tmpAddr.length != 4) {
			String msg = "Incorrect address format: "+ address; 
			System.err.println(msg);
			throw new Exception(msg);
		}
		
		for (int i = 0; i < tmpAddr.length; i++) {
			byteAddr[i] = Integer.valueOf(tmpAddr[i]).byteValue();
		}
		return byteAddr;
	}

	public String getDataDirectory() {
		return dataDirectory;
	}

	public List<PeerAddress> getBitcoinPeers() {
		return bitcoinPeers;
	}

}
