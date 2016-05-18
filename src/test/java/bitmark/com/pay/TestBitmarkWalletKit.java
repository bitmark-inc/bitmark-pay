package bitmark.com.pay;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import bitmark.com.pay.BitmarkWalletKit;
import bitmark.com.pay.NetType;

public class TestBitmarkWalletKit {

	private String walletFolder = "./_testWallet";
	private NetType net;
	private BitmarkWalletKit bitmarkWalletKit;
	
	@Before
	public void setUp() throws Exception {
		net = NetType.LOCAL_BITCOIN_REG;
		bitmarkWalletKit = new BitmarkWalletKit(net, walletFolder, null);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testBitmarkWalletKit() {
		
		// Check wallet app kit is not null
		assertFalse(bitmarkWalletKit.getWalletAppkit() == null);
		
		//Check wallet file name
		String walletFileName = "bitmarkWallet-" +  net;
		assertEquals(walletFileName, bitmarkWalletKit.getBitmarkWalletFileName());
	}

}
