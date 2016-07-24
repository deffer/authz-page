package nz.ac.auckland.auth.formdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Set;
// Info about consumer and client application, as registered in Kong
public class ClientInfo {

	String clientId
	String name
	String redirectUri
	String consumerId
	Set<String> groups


	// CLIENT
	// returned by a call to http://localhost:8001/oauth2?client_id=irina_oauth2_pluto
	//In Kong 0.5
	/*
	"consumer_id": "adc9be31-c062-4975-c9a0-752fc640c534",
	"client_id": "irina_oauth2_pluto",
	"id": "ce924627-df0a-4a5e-ca79-88227508a76c",
	"redirect_uri": "http:\/\/localhost:8090\/auth\/callback",
	"name": "Pluto Application",
	"created_at": 1450127454000,
	"client_secret": "845aae84513143e7c3d4aa51824fbe9c"
	*/

	//In Kong 0.8 redirect_uri is an array
	/*
	"consumer_id": "adc9be31-c062-4975-c9a0-752fc640c534",
	"client_id": "irina_oauth2_pluto",
	"id": "ce924627-df0a-4a5e-ca79-88227508a76c",
	"redirect_uri": ["http:\/\/localhost:8090\/auth\/callback"],
	"name": "Pluto Application",
	"created_at": 1450127454000,
	"client_secret": "845aae84513143e7c3d4aa51824fbe9c"
	*/
	public static boolean loadFromClientResponse(Map clientInfo, ClientInfo self){
		if (clientInfo["data"] != null && clientInfo["data"] instanceof List && clientInfo["data"].size() > 0){
			Map clientResponse = clientInfo.data[0];
			self.name = clientResponse.name
			// was string, now its an array since Kong > 0.6
			if (clientResponse.redirect_uri != null && !(clientResponse.redirect_uri instanceof String))
				self.redirectUri = (String) clientResponse.redirect_uri[0];
			else
				self.redirectUri = (String) clientResponse.redirect_uri;
			self.clientId = clientResponse.client_id
			self.consumerId = clientResponse.consumer_id
			return true
		}else
			return false
	}


	// CONSUMER
	//curl http://localhost:8001/consumers/2d0e911c-7dae-4bc6-b3a1-f10b23120dd1/acls
	// {"data":[{
	//   "group":"system-hash",
	//   "consumer_id":"2d0e911c-7dae-4bc6-b3a1-f10b23120dd1",
	//   "created_at":1468803057000,
	//   "id":"741c4bb1-1cc4-428f-8f77-c55676c71cb8"}
	// ],"total":1}

	public static void loadFromConsumerResponse(Map consumerResponse, ClientInfo self){
		self.groups = []
		if (consumerResponse != null && consumerResponse["data"] != null)
			consumerResponse["data"].each{Map it-> if (it.group != null) self.groups.add((String)it.group) }
	}

	public String redirectHost(){
		try {
			URI uri = new URI(redirectUri)
			return (uri.getScheme() ? uri.getScheme() + "://" : "") + uri.getHost()
		}catch (Exception e){
			e.printStackTrace()
			return "Error"
		}
	}

}
