package nz.ac.auckland.auth.formdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
// not used. just a demostration of what is returned by a call to http://localhost:8001/oauth2?client_id=irina_oauth2_pluto
public class ClientInfo {

	private String name;

	private  String redirectUri;

	public String getName() {
		return name;
	}

	@JsonProperty("redirect_uri")
	public String getRedirectUri() {
		return redirectUri;
	}


	/*"consumer_id": "adc9be31-c062-4975-c9a0-752fc640c534",
	"client_id": "irina_oauth2_pluto",
	"id": "ce924627-df0a-4a5e-ca79-88227508a76c",
	"redirect_uri": "http:\/\/localhost:8090\/auth\/callback",
	"name": "Pluto Application",
	"created_at": 1450127454000,
	"client_secret": "845aae84513143e7c3d4aa51824fbe9c"*/
}
