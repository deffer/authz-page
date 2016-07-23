package nz.ac.auckland.auth.endpoints

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import nz.ac.auckland.auth.contract.KongContract
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class UserProfileController {


	@Value("kong.admin.url")
	private String kongAdminUrl = "https://admin.api.dev.auckland.ac.nz/";

	@Value("kong.proxy.url")
	private String kongProxyUrl = "https://proxy.api.dev.auckland.ac.nz";

	@Value("kong.admin.key")
	private String kongAdminKey = "none";

	//private String kongProxyUrl = "https://proxy.api.dev.auckland.ac.nz/gelato-admin";



	@RequestMapping("/self")
	public String authForm(@RequestHeader(value = "REMOTE_USER", defaultValue = "NULL") String userId,
	                       @RequestHeader(value = "HTTP_DISPLAYNAME", defaultValue = "NULL") String userName, Model model) {

		println getTokens(userId)
		return "self"
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


	private String getTokens(String userId){
		String result = ""
		def http = new HTTPBuilder(KongContract.listUserTokensQuery(kongAdminUrl, userId))
		println "Calling "+kongProxyUrl+"/oauth2_tokens"
		http.request(Method.GET, ContentType.TEXT) {
			requestContentType = ContentType.JSON
			//body = queryData
			if (kongAdminKey && kongAdminKey!="none")
			headers = [apikey: kongAdminKey]

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
}
