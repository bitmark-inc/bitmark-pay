package bitmark.com.json;

public class InfoJsonResponse {
	private Long estimated_balance;
	private Long available_balance;
	private String address;
	
	public InfoJsonResponse(Long estimated, Long available, String address) {
		this.estimated_balance = estimated;
		this.available_balance = available;
		this.address = address;
	}
	
}
