package bitmark.com.json;

public class BalanceJsonResponse {
	private Long estimated_balance;
	private Long available_balance;
	
	public BalanceJsonResponse(Long estimated, Long available) {
		this.estimated_balance = estimated;
		this.available_balance = available;
	}
}
