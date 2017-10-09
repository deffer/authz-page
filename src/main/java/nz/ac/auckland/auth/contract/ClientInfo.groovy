package nz.ac.auckland.auth.contract;


// Info about consumer and client application (credentials), as it is registered in Kong
public class ClientInfo {

	String id
	String clientId // used to identify client application.
	String name     // application name to display to a user
	//String redirectUri // a callback url where code/token is returned to
	List<String> redirectUris // a callback url where code/token can be returned to
	String consumerId  // internal identifier of a consumer who owns application and credentials
	Set<String> groups // acl info (groups) of the consumer who owns application and credentials
	Long created_at


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
				self.redirectUris = new ArrayList<>(clientResponse.redirect_uri)
			else
				self.redirectUris = [(String) clientResponse.redirect_uri];
			self.id = clientResponse.id
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

	public static void loadFromConsumerAclResponse(Map consumerResponse, ClientInfo self){
		self.groups = []
		if (consumerResponse != null && consumerResponse["data"] != null)
			consumerResponse["data"].each{Map it-> if (it.group != null) self.groups.add((String)it.group) }
	}

	// this is used from the view
	public String redirectHost(){
		try {
			URI uri = new URI(redirectUris[0])
			return (uri.getScheme() ? uri.getScheme() + "://" : "") + uri.getHost()
		}catch (Exception e){
			e.printStackTrace()
			return "Error"
		}
	}

	public String displayRedirects(){
		return redirectUris.join(" ")
	}

	/**
	 * This method should return on of the registered callbacks
	 * to guarantee the safety of auto-grant response.
	 */
	public String determineCallback4AutoGrant(String requestedUri){
		if (redirectUris.contains(requestedUri))
			return requestedUri

		// todo find and return the one on teh same host as requestedUri
		return redirectUris[0]
	}
}
