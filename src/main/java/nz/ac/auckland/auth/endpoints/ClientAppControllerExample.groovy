package nz.ac.auckland.auth.endpoints

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.support.SessionStatus

@Controller
@SessionAttributes(["serverInfo", "token"])
class ClientAppControllerExample {
	// to do take from properties
	private String kongProxyUrl = "https://proxy.api.dev.auckland.ac.nz/";
	private String apiAuthPath ="hello-oauth2/"

	@Value('${lily.authzEndpoint}')
	private String authzEndpoint

	@Value('${lily.clientId}')
	private String clientId

	@Value('${lily.clientSecret}')
	private String clientSecret

	@RequestMapping("/lily")
	public String appForm(Model model) {

		ServerInfo serverInfo = model.asMap().get("serverInfo") as ServerInfo
		model.addAttribute("apiurl", kongProxyUrl+apiAuthPath)
		model.addAttribute("linkurl", serverInfo.authUrl)
		model.addAttribute("client_id", serverInfo.clientId)
		model.addAttribute("response_type", serverInfo.authRespType)

		return "lily";
	}

	@RequestMapping(value="/lily/auth_callback")
	public String authCallback(@RequestParam(name = "code", defaultValue = "") String code,
							   @RequestParam(name = "error", defaultValue = "") String error,
							   Model model) {

		if (error || !code){
			if (error.contains("access_denied"))
				model.addAttribute("text", "Sorry you don't trust me :(");
			else
				model.addAttribute("text", "Unknown error, code - '"+code+"'");
			model.addAttribute("action", "Back to start");
			model.addAttribute("nextUrl", "lily")
		}else {
			println "Client application received code " + code
			// obtain token
			TokenResponse token = requestToken(model.asMap().get("serverInfo") as ServerInfo, code)

			model.addAttribute("text", token.toString());
			model.addAttribute("action", "Next");
			model.addAttribute("nextUrl", "lily/person")
			model.addAttribute("token", token)
		}
		// note: I wanted to return a plain text here, however Spring MVC doesnt support both session attributes
		//   and plain text results in the same class, therefore I have to render a view
		return "generic_response";
	}

	@RequestMapping(value="/lily", method= RequestMethod.POST)
	public String saveInSession(@ModelAttribute("serverInfo") ServerInfo serverInfo, Model model) {
		model.addAttribute("apiurl", kongProxyUrl+apiAuthPath)
		model.addAttribute("linkurl", serverInfo.authUrl)
		model.addAttribute("client_id", serverInfo.clientId)
		model.addAttribute("response_type", serverInfo.authRespType)
		// todo scopes
		return "lily";
	}

	// example
	@RequestMapping("/lily/closeSession")
	public String closeSession(SessionStatus sessionStatus){
		sessionStatus.setComplete();
		return "redirect:/lily/"
	}

	@RequestMapping("/lily/person")
	public String showData(Model model){
		TokenResponse tokenResponse = model.asMap().get("token") as TokenResponse;
		if (tokenResponse?.access_token) {
			try {
				String result = queryData(tokenResponse)
				model.addAttribute("text", result);
				model.addAttribute("action", "Close");
				model.addAttribute("nextUrl", "lily/closeSession")
			}catch (Exception e){
				e.printStackTrace()
				model.addAttribute("text", "[ERROR] Unexpected error while querying API - "+e.getMessage())
				model.addAttribute("action", "Back to start")
				model.addAttribute("nextUrl", "lily")
			}
		}else{
			model.addAttribute("text", "[ERROR] No token info found in session")
			model.addAttribute("action", "Back to start")
			model.addAttribute("nextUrl", "lily")
		}
		return "generic_response"
	}

	@ModelAttribute("serverInfo")
	public ServerInfo addStuffToScope(){
		return new ServerInfo(authUrl: authzEndpoint,
				clientId: clientId,
				clientSecret: clientSecret,
				authRespType: "code", scopes: "read, write"
		)
	}

	def handler = {resp, reader, func ->
		println "response status: ${resp.statusLine}"
		println 'Headers: -----------'
		resp.headers.each { h ->
			println " ${h.name} : ${h.value}"
		}
		println 'Response data: -----'
		println reader
		println '--------------------'
		func(resp, reader);
	}

	private TokenResponse requestToken(ServerInfo serverInfo, String code){

		String tokenUrl = kongProxyUrl+apiAuthPath+"oauth2/token" // no double slash!!!
		println "Calling $tokenUrl"
		//token?grant_type=authorization_code&code=aL4HD6&client_id=faedda93-4c40-4f91-bc63-9efd5e5ba85f
		//String fullUrl = "${tokenUrl}?grant_type=authorization_code&code=${code}&client_id=${serverInfo.clientId}&client_secret=${serverInfo.clientSecret}"

		TokenResponse result = null;

		// perform a POST request, expecting JSON response
		// https://rs.dev.auckland.ac.nz//pcfdevo/oauth2/token
		def http = new HTTPBuilder(tokenUrl)
		http.request(Method.POST, ContentType.JSON) {
			requestContentType = ContentType.URLENC
			body = [client_id: serverInfo.clientId, grant_type: "authorization_code",
					client_secret: serverInfo.clientSecret, code:code ]
			response.success = handler.rcurry({resp, reader->
				// (new JsonSlurper()).parse(this.parseText(resp));
				result = reader as TokenResponse
			})
			response.failure = handler.rcurry({resp, reader->
				result = new TokenResponse(errorMessage: reader.toString())
			})
		}
		return result
	}

	private String queryData(TokenResponse token){
		//curl -i --url http://localhost:80/hello-oauth2   --header 'Content-Type: application/json'

		String result = "Unexpected failure (catch)"
		// should work when a correct access_token is being sent in the querystring: access_token = token.access_token)
		// or in an authorization header (bearer): authorization = "bearer "..token.access_token
		// or in an authorization header (token): authorization = "token "..token.access_token
		def http = new HTTPBuilder(kongProxyUrl+apiAuthPath)
		println "Calling "+kongProxyUrl+apiAuthPath
		http.request(Method.POST, ContentType.TEXT) {
			//requestContentType = ContentType.JSON
			//body = queryData
			headers = [Authorization: "bearer "+token.access_token]
			println "Passing toke in header: "+token.access_token
			response.success = handler.rcurry({resp, reader->
				result = reader.text
			})
			response.failure = handler.rcurry({resp, reader->
				result = reader?.text
				println result
			})
		}
		return result;
	}

	@Deprecated
	private String queryDataOld(TokenResponse token){
		//curl -i -X POST --url http://localhost:80/pcfdevo   --header 'Content-Type: application/json'
		// --data '{"query":{"keywords":"museum","filter":"All","undergraduate":true},"nPerPage":8,"page":0}'

		String result = "Unexpected failure (catch)"
		// should work when a correct access_token is being sent in the querystring: access_token = token.access_token)
		// or in an authorization header (bearer): authorization = "bearer "..token.access_token
		// or in an authorization header (token): authorization = "token "..token.access_token
		def queryData = [query: [keywords:"museum", filter:"All", undergraduate: true], nPerPage: 8, page: 0]
		def http = new HTTPBuilder(kongProxyUrl+apiAuthPath)
		http.request(Method.POST, ContentType.TEXT) {
			requestContentType = ContentType.JSON
			body = queryData
			headers = [Authorization: "bearer "+token.access_token]
			response.success = handler.rcurry({resp, reader->
				result = reader.toString()
			})
			response.failure = handler.rcurry({resp, reader->
				result = reader?.text
				println result
			})
		}
		return result;
	}

	public static class ServerInfo {
		String authUrl
		String clientId
		String clientSecret
		String authRespType
		String scopes
	}


	public static class TokenResponse {
		/*
		{
		"refresh_token": "4b6398f09b6e40f5c0735864c3cc4d07"
		"token_type": "bearer"
		"access_token": "c1fa464ac3e943dfc31190dc58aec227"
		"expires_in": 7200
		}
		*/
		String refresh_token
		String token_type
		String access_token
		String expires_in

		String errorMessage // not part of teh response. just a way to pass error message to browser if needed

		public String toString(){
			if (errorMessage)
				return errorMessage
			else{
				return [refresh_token: refresh_token, token_type: token_type, access_token: access_token, expires_in: expires_in].toString()
			}
		}
	}
}
