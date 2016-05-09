package bitmark.com.json;

public class BalanceJsonResponse {
	private Long Estimated_balance;
	private Long Available_balance;
	
	public BalanceJsonResponse(Long estimated, Long available) {
		this.Estimated_balance = estimated;
		this.Available_balance = available;
	}
}
