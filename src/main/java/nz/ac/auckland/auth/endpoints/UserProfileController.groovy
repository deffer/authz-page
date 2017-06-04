package nz.ac.auckland.auth.endpoints

import nz.ac.auckland.auth.contract.KongContract
import nz.ac.auckland.auth.contract.ClientInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam

@Controller
class UserProfileController {

	private static final Logger logger = LoggerFactory.getLogger(UserProfileController.class);

	@Value('${as.log.verbose}')
	private boolean verboseLogs = false

	@Autowired
	KongContract kong

	@RequestMapping("/self")
	public String selfPage(@RequestHeader(value = "REMOTE_USER", defaultValue = "NULL") String userId,
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

	@RequestMapping(method = RequestMethod.DELETE, value="/self/token/{tokenId}")
	public ResponseEntity<Void> token(@RequestHeader(value = "REMOTE_USER", defaultValue = "NULL") String userId,
	                            @RequestHeader(value = "displayName", defaultValue = "NULL") String userName,
	                            @PathVariable("tokenId") String accessToken, Model model) {

		String displayName = userName != "NULL"? userName :  "Unknown (${userId})"
		model.addAttribute("name", displayName);

		Map token = getToken(accessToken)

		if (!token){
			logger.warn("Unable to delete token - $accessToken is not found")
			return new ResponseEntity<Void>(HttpStatus.NOT_FOUND)
		}else{
			if (token.authenticated_userid.equalsIgnoreCase(userId)) {
				if (verboseLogs)
					logger.info("Deleting the token of user $userId - $accessToken")
				int code = kong.deleteResource(kong.getTokenQuery(accessToken))
						//kong.joinUrls(kong.OP_LIST_TOKENS, token.access_token))
				return new ResponseEntity<Void>(HttpStatus.valueOf(code))
			}else {
				logger.warn("Rejecting an unauthorized attempt to delete a token: User $userId is trying to delete token of ${token.authenticated_userid}")
				return new ResponseEntity<Void>(HttpStatus.FORBIDDEN)
			}
		}
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
				ClientInfo ci = kong.getClientFromCredentialsId(appId)
				if (ci)
					apps.put(appId, ci)
			}
			token.name = apps[appId]?.name
			// it also has 'access_token'
		}

		model.addAttribute("tokens", tokens)
		model.addAttribute("applications", apps)
	}

	private Map getTokens(String userId){
		return kong.getMap(kong.listUserTokensQuery(userId))
	}


	private Map getToken(String accessToken){
		if (!( accessToken ==~ /^[a-z\d]+$/ ))
			return null
		Map response = kong.getMap(kong.getTokenQuery(accessToken))
		// either token, or {"message":"Not found"}
		if (response && response.access_token?.equals(accessToken))
			return response
		else
			return null
	}

}
