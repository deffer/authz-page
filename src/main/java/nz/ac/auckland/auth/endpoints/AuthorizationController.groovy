package nz.ac.auckland.auth.endpoints


import nz.ac.auckland.auth.contract.KongContract
import nz.ac.auckland.auth.contract.ApiInfo
import nz.ac.auckland.auth.contract.Token;
import nz.ac.auckland.auth.formdata.AuthRequest
import nz.ac.auckland.auth.contract.ClientInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod



@Controller
// do NOT enable CORS on any of these method
public class AuthorizationController {

	// options for response_type when calling oauth/authorize endpoint.
	// WARNING: these values are also referenced in the property file
	public static final String AUTHORIZE_CODE_FLOW = "code"
	public static final String AUTHORIZE_IMPLICIT_FLOW = "token"

	@Value('${as.development}')
	private boolean development = false

	@Value('${as.trustedRedirectHosts}')
	private String[] trustedRedirectHosts

	@Value('${as.autogrant.flows}')
	private String[] autoGrantFlowsAllowed = ["token"]

	@Value('${as.hide.rememberme}')
	private boolean hideRememberMe

	@Value('${as.message.noScopeDescription}')
	private String msgNoScopeDescription = "Scope description is not available"

	@Value('${as.message.scopeNotAllowed}')
	private String msgScopeNotAllowed = "This scope is not available in this context. This indicates a problem with consumer application."


	@Value('${as.log.verbose:false}')
	private static boolean verboseLogs

	static final long ONE_MONTH = 2592000l // in seconds
	static final long FOREVER = 0l


	private static final Logger logger = LoggerFactory.getLogger(AuthorizationController.class);

	@Autowired
	KongContract kong

	@RequestMapping(value = "/")
	public String index(@RequestHeader(value = "displayName", defaultValue = "Unknown") String userName, Model model) {
		System.out.println("Rendering index.html");
		model.addAttribute("name", userName)
		model.addAttribute("debug", development)
		return "index";
	}

	// http://localhost:8090/identity/oauth2/authorize?client_id=my_clientid&response_type=code&scope=identity-read,identity-write
	@RequestMapping("/{api_id}/oauth2/authorize")
	public String authForm(@RequestHeader(value = "REMOTE_USER", defaultValue = "user") String userId,
	                       @RequestHeader(value = "displayName", defaultValue = "NULL") String userName,
	                       @PathVariable("api_id") String apiId, AuthRequest authRequest, Model model) {

		return renderAuthForm(userId, userName, apiId, authRequest, model);
	}

	private String renderAuthForm(String userId, String userName, String apiId, AuthRequest authRequest, Model model){

		if (!sanitizeRequestParameters(authRequest, userId, model)) {
			return "uoa-error"
		}

		// define greetings value
		String displayName = userName != "NULL"? userName :  "Unknown (${authRequest.user_id})"
		model.addAttribute("name", displayName);

		// pass request input values to the view in hidden fields
		model.addAttribute("map", authRequest);

		// check if we know this API
		ApiInfo apiInfo = kong.getApiInfo(apiId)
		if (apiInfo == null){
			model.addAttribute("user_id", userId);
			model.addAttribute("text", "API Gateway error");
			return "uoa-error";
		}
		if (!(apiInfo.id)){
			model.addAttribute("clientError", "unknown_api")
			return "auth";
		}
		if (!(apiInfo.provisionKey)){
			model.addAttribute("clientError", "noauth_api")
			return "auth";
		}

		// check if we know this application and consumer
		ClientInfo clientInfo = kong.getClientInfo(authRequest.client_id)
		if (!clientInfo){
			model.addAttribute("clientError", "unknown_client")
			return "auth"
		}

		// everything looks fine. we could proceed with rendering the form,
		//   however we need to make some UoA customizations and checks
		List<String> validScopes = processScopes(authRequest, apiInfo, model)
		authRequest.scope = validScopes.join(" ")

		// if its our internal university client application - we trust it
		//   (and so does the user) as long as the app location is registered and not overridden in request
		//   if its all good, dont ask for user's consent and proceed with issuing a token
		if (isOkToAutoGrant(clientInfo, authRequest)){
			String callbackUri = clientInfo.determineCallback4AutoGrant(authRequest.redirect_uri)
			Map kongResponse = kong.submitAuthorization(userId, callbackUri,apiInfo, authRequest);
			return makeCallback(kongResponse, authRequest, clientInfo, model, null) // null - do NOT override callbackUri
		}

		// check if request wants to override the callback url and reject if its not allowed
		def canRedirect = isOkToRedirect(authRequest, clientInfo)
		if (!canRedirect.ok) {
			model.addAttribute("clientError", "callback_match")
		}else if (canRedirect.warning) {
			// warning means callback is "overridden" (callback is different from registered),
			//   and its allowed, however
			//    - user needs to be aware that
			//    - we dont allow "Remember" options in this case
			model.addAttribute("clientWarning", canRedirect.warning)
		}else {
			// ok, no warnings. check if there is a already a remember me token
			List<Token> consentTokens = kong.getConsentTokens(userId, clientInfo.id)
			Token validConsent = consentTokens.find{
				it.isConsentToken() &&
				it.validForAllScopes(validScopes) &&
				it.stillActive()
			}

			// if we have valid consent, don't render the form and proceed with issuing a token
			if (validConsent != null) {
				logger.debug("Using previously consented token for user $userId and api ${apiInfo.name} and client ${clientInfo.name}")

				// Kong will only allow registered callbacks. pass null if callback is different from registered
				String callback4Kong = canRedirect.exactMatch? authRequest.redirect_uri : null
				if (verboseLogs)
					logger.debug("Found conset, submitting authorization to Kong: for callback '$callback4Kong' and user $userId and api ${apiInfo.name} and client ${clientInfo.name}")
				Map kongResponse = kong.submitAuthorization(userId, callback4Kong, apiInfo, authRequest);

				// If we used Kong's registered callback on previous step, we need to override the response
				// null - do NOT override the final callback (use Kong's response as is
				String callbackReal = canRedirect.exactMatch? null : authRequest.redirect_uri
				if (verboseLogs)
					logger.debug("Making a callback to ${callbackReal?:'default'} for user $userId and api ${apiInfo.name} and client ${clientInfo.name}")
				return makeCallback(kongResponse, authRequest, clientInfo, model, callbackReal)
			}else if (consentTokens.size()>0){
				logger.debug("No valid consent token found in the list of ${consentTokens.size()}, for user $userId and api ${apiInfo.name} and client ${clientInfo.name}")
			}
		}


		URI uri = new URI(authRequest.redirect_uri)
		model.addAttribute("appname", clientInfo.name);
		model.addAttribute("apphost", (uri.getScheme() ? uri.getScheme() + "://" : "") + uri.getHost());
		model.addAttribute("appurl", uri.toString());
		model.addAttribute("apiid", apiId);
		model.addAttribute("debug", development);
		model.addAttribute("rememberme", !hideRememberMe); // depends on flow type and presence of warnings

		return "auth";
	}


	boolean sanitizeRequestParameters(AuthRequest authRequest, String userId, Model model) {
		// do we need to sanitize redirect_uri? its always passed through URI constructor which should do it for us
		if (!validateId(authRequest.client_id)){
			logger.error("Invalid client_id ${authRequest.client_id}, affected user - $userId")
			model.addAttribute("text", "Invalid client_id")
			return false
		}

		authRequest.response_type = authRequest.response_type?authRequest.response_type.toLowerCase():""
		if (!(authRequest.response_type in [AUTHORIZE_CODE_FLOW,AUTHORIZE_IMPLICIT_FLOW])){
			logger.warn("Invalid flow '${authRequest.response_type}' - fallback to $AUTHORIZE_CODE_FLOW")
			authRequest.response_type = AUTHORIZE_CODE_FLOW
		}

		if ((!userId) || userId=="user"){
			if (!development){
				logger.error("SEVERE: Unexpected error (SSO fail)")
				model.addAttribute("text", "Unexpected error (SSO fail)")
				return false
			}else {
				logger.error("No user found in REMOTE_USER header, fallback to '$userId' (application is in dev mode)")
			}
		}

		authRequest.user_id = userId
		return true
	}


	private static boolean validateId(String input){
		return input && (input ==~ /[a-zA-Z0-9\-_]+$/)
	}


	// https://spring.io/guides/gs/handling-form-submission/
	@RequestMapping(value = "/{api_id}/auth/submit", method = RequestMethod.POST)
	// always use POST
	public String authSubmit(@RequestHeader(value = "REMOTE_USER", defaultValue = "user") String userId,
	                         @PathVariable("api_id") String apiId, AuthRequest authRequest, Model model) {
		// temporarily using "actionXXX" param to route requests to allow,deny or debug
		if (authRequest.actionDeny)
			return authDeny(authRequest, model);

		String forUser = (authRequest.user_id && development)?authRequest.user_id : userId
		if (forUser == "user" && !development){
			model.addAttribute("text", "Unexpected error (SSO fail)")
			logger.error("SEVERE: Unexpected error (SSO fail) - REMOTE_USER is $forUser")
			return "uoa-error"
		}

		// fetch api url and oauth2 provision key for given api
		ApiInfo apiInfo = kong.getApiInfo(apiId);
		ClientInfo clientInfo = kong.getClientInfo(authRequest.client_id)

		if ( (!clientInfo) || (!apiInfo) || (!(apiInfo.id)) || !(apiInfo.provisionKey)){
			if (apiInfo && !(apiInfo.provisionKey))
				logger.error("API ${apiInfo.name} ($apiId) is not configured properly - provisionKey is missing")
			else
				logger.error("Not found: clientInfo="+clientInfo?.toString()+"  apiInfo="+apiInfo?.toString()+"  provisionKey="+apiInfo?.provisionKey)
			model.addAttribute("user_id", userId);
			model.addAttribute("map", authRequest);
			return "uoa-error";
		}

		// if different redirectUri is passed, check whether it is allowed for this client. i.e.
		//    if (authRequest.redirect_uri != clientInfo.redirectUri
		//     && !clientInfo.groups.contains(KongContract.GROUP_DYNAMIC_CLIENT)) then error
		def canRedirect = isOkToRedirect(authRequest, clientInfo)
		if (!canRedirect.ok){
			logger.warn("Mismatched callback uri: passed '${authRequest.redirect_uri}' expected '${clientInfo.redirectUris[0]}'")
			model.addAttribute("user_id", userId);
			model.addAttribute("map", authRequest);
			return "uoa-error";
		}  else if (canRedirect.warning) {
			model.addAttribute("clientWarning", canRedirect.warning)
		}


		// Kong will only allow registered callbacks. pass null if callback is different from registered
		String callback4Kong = canRedirect.exactMatch? authRequest.redirect_uri : null
		Map kongResponseObj = kong.submitAuthorization(forUser, callback4Kong, apiInfo, authRequest);

		Map rememberMeResponse = null

		if (kongResponseObj.redirect_uri && !canRedirect.warning && !hideRememberMe
				&& authRequest.remember && authRequest.remember!="none"){
			// create new consent token
			String scopes = authRequest.extractedScopes().join(" ")?:"default"
			Token consentToken = Token.generateConsentToken(getConsentDuration(authRequest.remember),
					forUser, scopes, apiInfo.id, clientInfo.id)
			rememberMeResponse = kong.saveToken(consentToken)
		}

		if (development && authRequest.actionDebug) {
			model.addAttribute("user_id", userId);
			model.addAttribute("map", authRequest);
			model.addAttribute("provision_key", apiInfo.provisionKey);
			model.addAttribute("submitTo", kong.authorizeUrl(apiInfo.selectRequestPath()));
			model.addAttribute("kongResponse", kongResponseObj.toString());
			if (rememberMeResponse)
				model.addAttribute("rememberAction", "New consent token: "+ JsonHelper.serialize(rememberMeResponse))

			return "temp";
		} else if(!(kongResponseObj.redirect_uri)) {
			logger.error("Unexpected error (kong response has no redirect, status ${kongResponseObj?.status})")
			model.addAttribute("user_id", userId);
			model.addAttribute("provision_key", apiInfo.provisionKey);
			model.addAttribute("submitTo", kong.authorizeUrl(apiInfo.selectRequestPath()));
			model.addAttribute("kongResponse", kongResponseObj.toString());

			return "uoa-error";
		} else {
			String returnCallback = makeCallback(kongResponseObj, authRequest, clientInfo, model,
					callbackIsDifferent? authRequest.redirect_uri : null)
			return returnCallback
		}
	}

	private static long getConsentDuration(String inputValue){
		long result = 60*60*24 // 1 day
		switch (inputValue){
			case "one-month" : ONE_MONTH; break;
			case "until-revoked": FOREVER
		}
		return result
	}

	private boolean isOkToAutoGrant(ClientInfo clientInfo, AuthRequest authRequest){
		return (clientInfo.groups.contains(KongContract.GROUP_AUTH_GRANTED) &&
				Arrays.asList(autoGrantFlowsAllowed).contains(authRequest.response_type))
	}

	/**
	 * During auth flow this function is normally called twice:
	 *   - before rendering the main page and
	 *   - before redirecting to consumer app
	 *
	 * Verify whether requested redirect_uri matches the one registered for this client.
	 * If its not, will also check whether overriding redirect is allowed for these client
	 *   and return false if redirect is not allowed, otherwise update model with warning and return true.
	 * The warning is not shown if the client is from the list of trusted clients. For example
	 *   if the client is University API Explorer or Gelato (dev portal) API Explorer
	 *
	 * Redirect override is allowed under next conditions:
	 *   - first, client MUST be in the Kong group system-dynamic-client
	 *   - overridden callback MUST be on the same domain as registered one OR localhost
	 *
	 * @param authRequest what was passed in the request. contains new redirect_uri (if passed)
	 * @param clientInfo whats registered for this client in Kong
	 * @return
	 */
	private def isOkToRedirect(AuthRequest authRequest, ClientInfo clientInfo){
		if ( (!authRequest.redirect_uri) || clientInfo.redirectUris.contains(authRequest.redirect_uri)) {
			authRequest.redirect_uri = authRequest.redirect_uri?:clientInfo.redirectUris[0]
			return [ok:true, exactMatch: true]
		} else {

			if (clientInfo.groups.contains(KongContract.GROUP_AUTH_GRANTED)) {
				return [ok:false,warning: clientInfo.redirectUris[0]]; // should never be reached as this condition must be captured earlier
			}

			if (!clientInfo.groups.contains(KongContract.GROUP_DYNAMIC_CLIENT)) {
				//model.addAttribute("clientError", "callback_match") // will be added outside of this func
				return [ok:false, warning: clientInfo.redirectUris[0]]
			}

			// at this point we know its a "dynamic" client who needs to override url
			// test the host of the new url. the host must be localhost, or must match one of the registered ones
			URI newUri = new URI(authRequest.redirect_uri)
			if (newUri.getHost()?.equalsIgnoreCase("localhost"))
				return [ok:true]

			// search if there is a matching host. show warning if the only match is for non-trusted hosts
			List<String> foundWithWarning = [] // should make it bool?
			List<String> trustedHosts = Arrays.asList(trustedRedirectHosts)
			boolean foundWithoutWarning = false
			clientInfo.redirectUris.each { redirectUri ->
				URI registeredUri = new URI(redirectUri)

				if (KongContract.hostMatch(newUri, registeredUri)) {
					// if its not a trusted host, its still valid but need to show warning to a user
					if (!KongContract.hostMatchAny(newUri, trustedHosts))
						foundWithWarning.add(redirectUri)
					else
						foundWithoutWarning = true
				} else {
					// ignore this host as it doesnt match
				}
			}

			if (foundWithoutWarning)
				return [ok:true]

			// if we are here, there was no perfect match
			return [ok: !foundWithWarning.isEmpty(), warning: clientInfo.redirectUris[0]]
		}
	}

	/**
	 * Complex logic to support Kong 0.8 and above (in 0.8 url fragments didnt work)
	 * This method will be much smaller if we drop support of 0.8
	 * @param kongResponseObject
	 * @param authRequest
	 * @param clientInfo
	 * @param model
	 * @param overrideCallback if set, the url in kongResponseObject will be replaced with this value
	 * @return
	 */
	public static String makeCallback(Map kongResponseObject, AuthRequest authRequest, ClientInfo clientInfo, Model model, String overrideCallback){
		// responses may vary, e.g.
		// {"error_description":"Invalid Kong provision_key","state":"123","error":"invalid_provision_key"}
		// {"redirect_uri":"https://rs.dev.auckland.ac.nz?access_token=1b9..c34&expires_in=7200&state=123&token_type=bearer"}
		// {"redirect_uri":"https://rs.dev.auckland.ac.nz?error=invalid_request&error_description=Invalid%20redirect_uri%20that%20does%20not%20match%20with%20any%20redirect_uri%20created%20with%20the%20application&state=123"}
		if ((!kongResponseObject) || !(kongResponseObject.redirect_uri)){
			// shouldnt happen, will be caught earlier, but just in case...
			model.addAttribute("text", "Unexpected error from API gateway")
			return "uoa-error"
		}

		// https://rs.dev.auckland.ac.nz?code=60077d911907496e83db400d92a82e88&state=123
		// https://rs.dev.auckland.ac.nz#access_token=1c3c6985d1764f76bf12243229909d77&expires_in=7200&state=123&token_type=bearer
		// https://rs.dev.auckland.ac.nz?error=invalid_request&error_description=Invalid%20redirect_uri%20that%20does%20not%20match%20with%20any%20redirect_uri%20created%20with%20the%20application&state=123
		String kongResponse = kongResponseObject.redirect_uri

		URI uri = new URI(kongResponse)
		// if it already has a fragment (this is Kong 0.9+, yay less work to do) or its for code flow...
		if ( (uri.fragment || authRequest.response_type.equalsIgnoreCase(AUTHORIZE_CODE_FLOW)) && !overrideCallback){
			return "redirect:" + kongResponse
		}


		if (authRequest.response_type.equalsIgnoreCase(AUTHORIZE_CODE_FLOW)){ // no fragment replace needed
			if (overrideCallback)
				return "redirect:" + overrideCallback+"?"+uri.query // assuming no fragments on CODE FLOW
			else
				return "redirect:" + kongResponse
		}

		// Implicit flow. May need to take care of fragments
		if (uri.fragment){
			if (overrideCallback)
				return "redirect:"+ overrideCallback+"#"+uri.fragment
			else
				return "redirect:" + kongResponse
		}else{
			Map<String, String> params = KongContract.splitQuery(uri.query)

			if (overrideCallback){
				if (!params.containsKey("error"))
					return "redirect:" + overrideCallback+"#"+uri.query;
				else
					return "redirect:" + overrideCallback+"?"+uri.query
			}else{
				if (!params.containsKey("error"))
					return "redirect:" + new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.query); // fragment
				else
					return "redirect:" + kongResponse
			}
		}
	}


	public String authDeny(AuthRequest authRequest, Model model) {
		ClientInfo clientInfo = kong.getClientInfo(authRequest.client_id)
		if (!clientInfo) {
			logger.error("Unexpected error - unable ot find client info (on deny step) - ${authRequest.client_id}")
			model.addAttribute("text", "Unknown application (client_id)")
			return "uoa-error"
		}

		String callbackUri = authRequest.redirect_uri?:clientInfo.redirectUris[0]
		String clientCallbackUrl = callbackUri+"?error=access_denied&error_description=The+user+has+denied+the+access";

		return "redirect:" + clientCallbackUrl;
	}

	private List<String> processScopes(AuthRequest authRequest, ApiInfo apiInfo, Model model){
		List<String> validScopes = []
		List scopes = []
		String[] requestedScopes = []

		if (!authRequest.scope) {
			if (apiInfo?.scopes?.contains("default"))
				requestedScopes = ["default"]
		} else
			requestedScopes = authRequest.extractedScopes()

		requestedScopes.each {
			if (apiInfo?.scopes?.contains(it)){
				validScopes.add(it)
				String scopeDescription = kong.getScopeDescription(it)
				if (scopeDescription)
					scopes.add([key:it, value: scopeDescription, warn:false])
				else {
					// todo if we cant find description of a scope, what should be do?
					scopes.add([key:it, value:msgNoScopeDescription, warn:true])
				}
			}else{
				// todo if scope is not valid for this endpoint, warn the user right away?
				scopes.add([key:it, value:msgScopeNotAllowed, warn:true])
			}

		}
		model?.addAttribute("scopes", scopes);
		return validScopes
	}

}
