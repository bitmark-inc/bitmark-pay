package bitmark.com.json;

import bitmark.com.json.BalanceJsonResponse;

public class InfoJsonResponse {
	private BalanceJsonResponse balance;
	private String address;

	public InfoJsonResponse(Long estimated, Long available, String address) {
		this.balance = new BalanceJsonResponse(estimated, available);
		this.address = address;
	}

}
