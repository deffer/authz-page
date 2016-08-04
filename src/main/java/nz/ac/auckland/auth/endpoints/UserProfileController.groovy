package nz.ac.auckland.auth.endpoints

import nz.ac.auckland.auth.contract.KongContract
import nz.ac.auckland.auth.formdata.ClientInfo
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping

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

	private Map getTokens(String userId){
		return KongContract.getMap(KongContract.listUserTokensQuery(kongAdminUrl, userId), kongAdminKey)
	}

	private ClientInfo getClientInfo(String credentialsId){
		ClientInfo result = new ClientInfo()

		Map map = KongContract.getMap(KongContract.oauth2ClientQueryById(kongAdminUrl,credentialsId), kongAdminKey)

		if (map && ClientInfo.loadFromClientResponse(map, result)){
			map = KongContract.getMap(KongContract.joinUrl(kongAdminUrl, "/consumers/${result.consumerId}/acls"), kongAdminKey);
			ClientInfo.loadFromConsumerResponse(map, result)
			return result
		}else
			return null

	}

}
