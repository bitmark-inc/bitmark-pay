package bitmark.com.json;

public class InfoJsonResponse {
	private Long Estimated_balance;
	private Long Available_balance;
	private String Address;
	
	public InfoJsonResponse(Long estimated, Long available, String address) {
		this.Estimated_balance = estimated;
		this.Available_balance = available;
		this.Address = address;
	}
	
}
