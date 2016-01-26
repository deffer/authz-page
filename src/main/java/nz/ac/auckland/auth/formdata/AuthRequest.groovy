package nz.ac.auckland.auth.formdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthRequest {

	String client_id;
	String response_type;
	String scope;

	// temporarily using these flags as indicators of what button was pressed
	String actionAllow = null;
	String actionDeny = null;
	String actionDebug = null;

	String user_id; // temp, for debug

	public Map toProps(){
		Map result = new HashMap<>()
		this.getProperties().each {k,v -> if (!k.equals("class")) result.put(k, v)}
		return result
	}
}
