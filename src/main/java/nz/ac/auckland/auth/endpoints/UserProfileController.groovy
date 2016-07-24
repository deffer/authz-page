package nz.ac.auckland.auth.endpoints

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import nz.ac.auckland.auth.contract.KongContract
import nz.ac.auckland.auth.formdata.ClientInfo
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.RestTemplate

@Controller
class UserProfileController {


	@Value('${kong.admin.url}')
	private String kongAdminUrl = "https://admin.api.dev.auckland.ac.nz/";

	@Value('${kong.proxy.url}')
	private String kongProxyUrl = "https://proxy.api.dev.auckland.ac.nz";

	@Value('${kong.admin.key}')
	private String kongAdminKey = "none";




	@RequestMapping("/self")
	public String authForm(@RequestHeader(value = "REMOTE_USER", defaultValue = "iben883") String userId,
	                       @RequestHeader(value = "displayName", defaultValue = "NULL") String userName, Model model) {


		String displayName = userName != "NULL"? userName :  "Unknown (${userId})"
		model.addAttribute("name", displayName);

		Map tokensResponse = getTokens(userId)
		if (!tokensResponse.data) {
			model.addAttribute("tokens", [:])
			model.addAttribute("applications", [:])
		}else {
			prepareView(tokensResponse, model)
		}
		return "self"
	}

	private void prepareView(Map tokensResponse, Model model) {
		List tokens = tokensResponse.data // also 'total' and 'next'
		Map apps = [:]

		tokens.each { token ->
			long issued = token.created_at
			token.issuedStr = new Date(issued).format("yyyy-dd-MM")
			token.issuedHint = new Date(issued).format("HH:mm:ss")
			long expires = token.created_at + token.expires_in * 1000
			token.expiresStr = new Date(expires).format("yyyy-dd-MM")
			token.expiresHint = new Date(expires).format("HH:mm:ss")
			String appId = token.credential_id
			if (!(apps[appId])) {
				ClientInfo ci = getClientInfo(appId)
				if (ci)
					apps.put(appId, ci)
			}
			token.name = apps[appId]?.name
		}

		model.addAttribute("tokens", tokens)
		model.addAttribute("applications", apps)
	}


	def handler = {resp, reader, func ->
		println "response status: ${resp.statusLine}"
		if (resp.status !=200) {
			println 'Headers: -----------'
			resp.headers.each { h ->
				println " ${h.name} : ${h.value}"
			}
			println 'Response data: -----'
			println reader
			println '--------------------'
		}
		func(resp, reader);
	}


	private Map getTokens(String userId){
		return getMap(KongContract.listUserTokensQuery(kongAdminUrl, userId))
	}

	private ClientInfo getClientInfo(String credentialsId){
		ClientInfo result = new ClientInfo()

		Map map = getMap(KongContract.oauth2ClientQueryById(kongAdminUrl,credentialsId))

		if (map && ClientInfo.loadFromClientResponse(map, result)){
			map = getMap(KongContract.joinUrl(kongAdminUrl, "/consumers/${result.consumerId}/acls"));
			ClientInfo.loadFromConsumerResponse(map, result)
			return result
		}else
			return null

	}

	private Map getMap(String url){
		Map map = null
		def http = new HTTPBuilder(url)
		println "Calling "+http.uri
		http.request(Method.GET, ContentType.JSON) {
			requestContentType = ContentType.JSON
			//body = queryData
			if (kongAdminKey && kongAdminKey!="none")
				headers = [apikey: kongAdminKey]

			response.success = handler.rcurry({resp, reader->
				if (reader instanceof Map)
					map = reader
			})
			response.failure = handler.rcurry({resp, reader->
				println "ERROR:\r$reader"
			})
		}

		return map
	}
}
