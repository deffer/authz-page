package nz.ac.auckland.auth.formdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
// use both - when client redirect user for authentication
//   and when user-choice form is submitted
public class AuthRequest {

	public static final String REMEMBER_NOT = "remember-not"
	public static final String REMEMBER_FOREVER = "remember-forever"
	public static final String REMEMBER_MONTH = "remember-month"

	// required by OAuth2 spec and set by client
	String client_id;
	String response_type;
	String scope;
	String redirect_uri;
	String state;

	// may be set by client to indicate that they want response code/token
	//  to be passed to their callback as a url-fragment instead of parameter
	String use_fragment;

	// user choice
	String remember = REMEMBER_NOT;

	// temporarily using these flags as indicators of what button was pressed
	String actionAllow = null;
	String actionDeny = null;
	String actionDebug = null;


	String user_id; // temp, for debug

	public List<String> extractedScopes(){
		return scope.trim().replaceAll(",", ' ').split(" ").findAll{!it.trim().isEmpty()} as List<String>
	}

	public Map toProps(){
		Map result = new HashMap<>()
		this.getProperties().each {k,v -> if (!k.equals("class")) result.put(k, v)}
		return result
	}
}
