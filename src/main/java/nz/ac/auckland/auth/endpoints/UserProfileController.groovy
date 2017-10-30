package nz.ac.auckland.auth.endpoints

import nz.ac.auckland.auth.contract.KongContract
import nz.ac.auckland.auth.contract.ClientInfo
import nz.ac.auckland.auth.contract.Token
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

	// a workaround for Kong deleting refresh tokens after 14 days is to save refresh token
	//   separately from its origianl access token, and set TTL on this custom token to nil (keep forever)
	//   to identify custom tokens later, the expires_in is set to 1010
	public static long REFRESH_TOKEN_MAGIC = 1010L

	@Value('${as.log.verbose}')
	private boolean verboseLogs = false

	@Value('${as.development}')
	private boolean development = false

	@Autowired
	KongContract kong

	@RequestMapping("/self")
	public String selfPage(@RequestHeader(value = "REMOTE_USER", defaultValue = "NULL") String userId,
	                       @RequestHeader(value = "displayName", defaultValue = "NULL") String userName, Model model) {


		String displayName = userName != "NULL"? userName :  "Unknown (${userId})"
		model.addAttribute("name", displayName);

		if ((!userId || userId == "NULL") && development)
			userId = "user"


		prepareView(userId, model)
		return "self"
	}

	@RequestMapping(method = RequestMethod.DELETE, value="/self/token/{tokenId}")
	public ResponseEntity<Void> token(@RequestHeader(value = "REMOTE_USER", defaultValue = "NULL") String userId,
	                            @RequestHeader(value = "displayName", defaultValue = "NULL") String userName,
	                            @PathVariable("tokenId") String tokenId, Model model) {

		String displayName = userName != "NULL"? userName :  "Unknown (${userId})"
		model.addAttribute("name", displayName);
		if ((!userId || userId == "NULL") && development)
			userId = "user"

		Map token = getToken(tokenId, userId)

		if (!token){
			logger.warn("Unable to delete token - user $userId does not have a token $tokenId")
			return new ResponseEntity<Void>(HttpStatus.NOT_FOUND)
		}else{
			if (verboseLogs)
				logger.info("Deleting the token of user $userId - $tokenId")
			int code = kong.deleteResource(kong.getTokenQuery(tokenId))
			return new ResponseEntity<Void>(HttpStatus.valueOf(code))
		}
	}

	private void prepareView(String userId, Model model) {
		List<Token> allTokens =  kong.getTokens4User(userId)
		// note, tokens are paginated - have 'total' and 'next', ignoring it for now

		Map<String, ClientInfo> apps = [:]

		List tokens = []
		List consents = []

		allTokens.each { Token kongToken ->
			def displayToken = JsonHelper.convert(kongToken, HashMap.class)
			long issued = kongToken.created_at
			displayToken.issuedStr = new Date(issued).format("yyyy-MM-dd")
			displayToken.issuedHint = new Date(issued).format("HH:mm:ss")
			long expiresIn = kongToken.expires_in
			if (expiresIn == 0l || expiresIn == REFRESH_TOKEN_MAGIC){
				displayToken.expiresStr = "Never"
				displayToken.expiresHint = (expiresIn==REFRESH_TOKEN_MAGIC?"This is a refresh token":"")
				displayToken.access_token="" // don't return token itself, for extra security
			}else{
				long expires = kongToken.created_at + expiresIn * 1000
				displayToken.expiresStr = new Date(expires).format("yyyy-MM-dd")
				displayToken.expiresHint = new Date(expires).format("HH:mm:ss")
			}
			String appId = kongToken.credential_id
			if (!(apps[appId])) {
				ClientInfo ci = kong.getClientFromCredentialsId(appId)
				if (ci)
					apps.put(appId, ci)
			}
			displayToken.name = apps[appId]?.name
			displayToken.callbacks = apps[appId]?.displayRedirects()
			displayToken.host = apps[appId]?.redirectHost()
			//displayToken.access_token = kongToken.access_token
			if (kongToken.isConsentToken()){
				consents.add(displayToken)
			}else
				tokens.add(displayToken)
		}

		model.addAttribute("tokens", tokens)
		model.addAttribute("consents", consents)
	}

	private Map getToken(String tokenId, String userId){
		if (!( tokenId ==~ /^[a-z\d\-]+$/ ))
			return null
		Map response = kong.getMap(kong.getTokenQuery(tokenId))
		// either token, or {"message":"Not found"}
		if (response && response.authenticated_userid &&
			(response.authenticated_userid.equalsIgnoreCase(userId)
			|| response.authenticated_userid.equalsIgnoreCase(Token.generateConsentTokenString(userId, KongContract.AUTHORIZE_CODE_FLOW))
			|| response.authenticated_userid.equalsIgnoreCase(Token.generateConsentTokenString(userId, KongContract.AUTHORIZE_IMPLICIT_FLOW))))
			return response
		else
			return null
	}

}
