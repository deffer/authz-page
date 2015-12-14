package nz.ac.auckland.auth.formdata;


public class AuthRequest {
	private String clientId;
	private String responseType;
	private String scope;

	public AuthRequest(String clientId, String responseType, String scope){
		this.clientId = clientId;
		this.responseType = responseType;
		this.scope = scope;
	}

	public String getClientId() {
		return clientId;
	}

	public String getResponseType() {
		return responseType;
	}

	public String getScope() {
		return scope;
	}
}
