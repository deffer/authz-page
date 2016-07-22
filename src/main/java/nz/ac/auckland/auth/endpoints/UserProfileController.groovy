package nz.ac.auckland.auth.endpoints

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import nz.ac.auckland.auth.contract.KongContract
import nz.ac.auckland.auth.formdata.AuthRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class UserProfileController {


	@Value("kong.admin.url")
	private String kongAdminUrl = "https://admin.api.dev.auckland.ac.nz/";

	@Value("kong.proxy.url")
	private String kongProxyUrl = "https://proxy.api.dev.auckland.ac.nz";

	@RequestMapping("/self")
	public String authForm(@RequestHeader(value = "REMOTE_USER", defaultValue = "NULL") String userId,
	                       @RequestHeader(value = "HTTP_DISPLAYNAME", defaultValue = "NULL") String userName, Model model) {

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

	private String queryTokens(String upi){
		//  curl http://localhost:8001/oauth2_tokens?authenticated_userid=mtuz243

		String result = "Unexpected failure (catch)"

		def http = new HTTPBuilder(KongContract.joinUrl(kongProxyUrl,apiAuthPath))
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
}
