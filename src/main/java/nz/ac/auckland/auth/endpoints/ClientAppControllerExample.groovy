package nz.ac.auckland.auth.endpoints

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.support.SessionStatus

@Controller
@SessionAttributes("serverInfo")
class ClientAppControllerExample {
	// to do take from properties
	private String kongProxyUrl = "https://rs.dev.auckland.ac.nz/";
	private String apiAuthPath ="pcfdevo/"
	private String personApiPath = "person/api/"
	private String personMethodUrl = "person/me"

	@RequestMapping("/lily")
	public String appForm(Model model) {

		ServerInfo serverInfo = model.asMap().get("serverInfo") as ServerInfo
		model.addAttribute("linkurl", serverInfo.authUrl)
		model.addAttribute("client_id", serverInfo.clientId)
		model.addAttribute("response_type", serverInfo.authRespType)

		return "lily";
	}

	@RequestMapping("/lily/auth_callback")
	@ResponseBody // temporarily
	public String authCallback(@RequestParam String code, Model model) {

		println "Client application received code "+code
		// obtain token
		String token = requestToken(model.asMap().get("serverInfo") as ServerInfo, code)

		// todo
		//model.addAttribute("code", code);
		return token;
	}

	@RequestMapping(value="/lily", method= RequestMethod.POST)
	public String saveInSession(@ModelAttribute("serverInfo") ServerInfo serverInfo, Model model) {
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
		return "something"
	}


	@ModelAttribute("serverInfo")
	public ServerInfo addStuffToScope(){
		return new ServerInfo(authUrl: "http://localhost:8090/pcfdev-oauth/auth",
				clientId: "irina_oauth2_pluto",
				clientSecret: "124955fdb9544860c86ac2d7fce63ec2",
				authRespType: "code", scopes: "read, write"
		)
	}

	private String requestToken(ServerInfo serverInfo, String code){

		String tokenUrl = kongProxyUrl+apiAuthPath+"oauth2/token" // no double slash!!!
		println "Calling $tokenUrl"
		//token?grant_type=authorization_code&code=aL4HD6&client_id=faedda93-4c40-4f91-bc63-9efd5e5ba85f
		//String fullUrl = "${tokenUrl}?grant_type=authorization_code&code=${code}&client_id=${serverInfo.clientId}&client_secret=${serverInfo.clientSecret}"

		def result = null;

		// perform a POST request, expecting JSON response
		// https://rs.dev.auckland.ac.nz//pcfdevo/oauth2/token
		def http = new HTTPBuilder(tokenUrl)
		http.request(Method.POST, ContentType.JSON) {
			requestContentType = ContentType.URLENC
			body = [client_id: serverInfo.clientId, grant_type: "authorization_code",
					client_secret: serverInfo.clientSecret, code:code ]

			// response handler for a success response code
			response.success = { resp, reader ->
				println "response status: ${resp.statusLine}"
				println 'Headers: -----------'
				resp.headers.each { h ->
					println " ${h.name} : ${h.value}"
				}

				/*if (reader instanceof Map && reader.containsKey("token"))
					result = reader.get("token")
				else {*/
					println 'Response data: -----'
					println reader
					println '--------------------'
				//}
				result = reader.toString()
			}
			response.failure = {resp, reader ->
				println "ERROR response status: ${resp.statusLine}"
				println 'Headers: -----------'
				resp.headers.each { h ->
					println " ${h.name} : ${h.value}"
				}
				println 'Response data: -----'
				println reader
				println '--------------------'
				result = reader.toString()
			}
		}
		return result
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
	}
}
