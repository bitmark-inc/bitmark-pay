package bitmark.com.json;

public class BalanceJsonResponse {
	private Long estimated;
	private Long available;

	public BalanceJsonResponse(Long estimated, Long available) {
		this.estimated = estimated;
		this.available = available;
	}
}
