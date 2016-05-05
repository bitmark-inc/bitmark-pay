// Copyright (c) 2014-2016 Bitmark Inc.
// Use of this source code is governed by an ISC
// license that can be found in the LICENSE file.

package bitmark.com.pay;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.params.TestNet2Params;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

import static com.google.common.base.Preconditions.checkState;

/**
 * <p>Network parameters for bitmark regression and local test.</p>
 */
public class BitmarkRegTestParams extends TestNet2Params {
	private static final Logger log = LoggerFactory.getLogger(BitmarkRegTestParams.class);
	private static final BigInteger MAX_TARGET = new BigInteger(
			"7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16);

	public BitmarkRegTestParams() {
		super();
		maxTarget = MAX_TARGET;
		subsidyDecreaseBlockCount = 150;
		port = 17002;
		id = ID_REGTEST;
	}

	@Override
	public boolean allowEmptyPeerChain() {
		return true;
	}

	private static Block genesis;

	@Override
	public Block getGenesisBlock() {
		synchronized (BitmarkRegTestParams.class) {
			if (genesis == null) {
				genesis = super.getGenesisBlock();
				genesis.setNonce(2);
				genesis.setDifficultyTarget(0x207fFFFFL);
				genesis.setTime(1296688602L);
				checkState(genesis.getHashAsString().toLowerCase()
						.equals("0f9188f13cb7b2c71f2a335e3a4fc328bf5beb436012afca590b1a11466e2206"));
			}
			return genesis;
		}
	}

	private static BitmarkRegTestParams instance;

	public static synchronized BitmarkRegTestParams get() {
		if (instance == null) {
			instance = new BitmarkRegTestParams();
		}
		return instance;
	}

	@Override
	public String getPaymentProtocolId() {
		return PAYMENT_PROTOCOL_ID_REGTEST;
	}

	/**
	 * Override the checkDifficulty for bitmark reg test
	 */
	@Override
	public void checkDifficultyTransitions(final StoredBlock storedPrev, final Block nextBlock,
			final BlockStore blockStore) throws VerificationException, BlockStoreException {
		Block prev = storedPrev.getHeader();

		long now = System.currentTimeMillis();
		StoredBlock cursor = blockStore.get(prev.getHash());
		for (int i = 0; i < this.getInterval() - 1; i++) {
			if (cursor == null) {
				// This should never happen. If it does, it means we are
				// following an incorrect or busted chain.
				throw new VerificationException(
						"Difficulty transition point but we did not find a way back to the genesis block.");
			}
			cursor = blockStore.get(cursor.getHeader().getPrevBlockHash());
		}
		long elapsed = System.currentTimeMillis() - now;
		if (elapsed > 50)
			log.info("Difficulty transition traversal took {}msec", elapsed);

	}

}
