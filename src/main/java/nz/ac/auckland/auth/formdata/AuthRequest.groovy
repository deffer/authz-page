package nz.ac.auckland.auth.formdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthRequest {

	String api_id; // called provision_key in Kong
	String client_id;
	String response_type;
	String scope;
	String actionAllow = null;
	String actionDeny = null;

	String user_id; // temp
	String provision_key; // temp

	public Map toProps(){
		Map result = new HashMap<>()
		this.getProperties().each {k,v -> if (!k.equals("class")) result.put(k, v)}
		return result
	}
}
