package nz.ac.auckland.auth.formdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthRequest {

	public static final String REMEMBER_NOT = "remember-not"
	public static final String REMEMBER_FOREVER = "remember-forever"
	public static final String REMEMBER_DAY = "remember-day"

	String client_id;
	String response_type;
	String scope;
	String redirect_uri;

	// temporarily using these flags as indicators of what button was pressed
	String actionAllow = null;
	String actionDeny = null;
	String actionDebug = null;
	String remember = REMEMBER_FOREVER;

	String user_id; // temp, for debug

	public Map toProps(){
		Map result = new HashMap<>()
		this.getProperties().each {k,v -> if (!k.equals("class")) result.put(k, v)}
		return result
	}
}
