package bitmark.com.wallet;

import static org.junit.Assert.*;

import org.bitcoinj.core.Wallet.SendRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestBitmarkWalletKit {

	private String walletFolder = "./_testWallet";
	private NetType net;
	private BitmarkWalletKit bitmarkWalletKit;
	
	@Before
	public void setUp() throws Exception {
		net = NetType.LOCAL;
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
		
		// Check default DEFAULT_FEE_PER_KB
		assertEquals(BitmarkWalletKit.MINE_FEE, SendRequest.DEFAULT_FEE_PER_KB.value);
	}

}
