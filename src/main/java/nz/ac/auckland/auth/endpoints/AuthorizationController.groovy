package nz.ac.auckland.auth.endpoints


import nz.ac.auckland.auth.contract.KongContract
import nz.ac.auckland.auth.contract.ApiInfo;
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

	@Value('${as.debug}')
	private boolean debug = false

	@Value('${as.trustedRedirectHosts}')
	private String[] trustedRedirectHosts

	@Value('${as.autogrant.flows}')
	private String[] autoGrantFlowsAllowed = ["token"]

	private static final Logger logger = LoggerFactory.getLogger(AuthorizationController.class);

	@Autowired
	KongContract kong

	// http://localhost:8090/identity/oauth2/authorize?client_id=my_clientid&response_type=code&scope=identity-read,identity-write
	@RequestMapping("/{api_id}/oauth2/authorize")
	public String authForm(@RequestHeader(value = "REMOTE_USER", defaultValue = "NULL") String userId,
	                       @RequestHeader(value = "displayName", defaultValue = "NULL") String userName,
	                       @PathVariable("api_id") String apiId, AuthRequest authRequest, Model model) {

		return renderAuthForm(userId, userName, apiId, authRequest, model);
	}

	// http://localhost:8090/pcfdev-oauth/auth?client_id=irina_oauth2_pluto&response_type=code&scope=read,write
	private String renderAuthForm(String userId, String userName, String apiId, AuthRequest authRequest, Model model){

		if (!sanitizeRequestParameters(authRequest, userId, model)) {
			return "uoa-error"
		}

		// defined greetings value
		String displayName = userName != "NULL"? userName :  "Unknown (${authRequest.user_id})"
		model.addAttribute("name", displayName);

		// pass request input values to the view in hidden fields
		model.addAttribute("map", authRequest);

		ApiInfo apiInfo = kong.getApiInfo(apiId)
		if ((!apiInfo) || !(apiInfo.id)){
			model.addAttribute("clientError", "unknown_api")
			return "auth";
		}

		if (!(apiInfo.provisionKey)){
			model.addAttribute("clientError", "noauth_api")
			return "auth";
		}

		List<String> validScopes = processScopes(authRequest, apiInfo, model)
		authRequest.scope = validScopes.join(" ")


		// find out application and consumer details
		ClientInfo clientInfo = kong.getClientInfo(authRequest.client_id)
		if (clientInfo){
			if (isOkToAutoGrant(clientInfo, authRequest)){
				// that's our internal university client application.
				// we trust it (and so does the user) as long as the app location is registered and not overridden in request
				Map kongResponse = kong.submitAuthorization(userId, clientInfo.redirectUri,apiInfo, authRequest);
				return makeCallback(kongResponse, authRequest, clientInfo, model, null) // null - do NOT override callbackUri
			}

			if (!isOkToRedirect(authRequest, clientInfo, model)){
				model.addAttribute("clientError", "callback_match")
			}


			URI uri = new URI(authRequest.redirect_uri)
			model.addAttribute("appname", clientInfo.name);
			model.addAttribute("apphost", (uri.getScheme() ? uri.getScheme() + "://" : "") + uri.getHost());
			model.addAttribute("appurl", uri.toString());
			model.addAttribute("apiid", apiId);
		}else{
			model.addAttribute("clientError", "unknown_client")
		}

		return "auth";
	}


	boolean sanitizeRequestParameters(AuthRequest authRequest, String userId, Model model) {
		// do we need to sanitize redirect_uri? its always passed through URI constructor which should do it for us
		if (!validateId(authRequest.client_id)){
			logger.error("Invalid client_id "+authRequest.client_id)
			model.addAttribute("text", "Invalid client_id")
			return false
		}

		authRequest.response_type = authRequest.response_type?authRequest.response_type.toLowerCase():""
		if (!(authRequest.response_type in [AUTHORIZE_CODE_FLOW,AUTHORIZE_IMPLICIT_FLOW])){
			logger.warn("Invalid flow '${authRequest.response_type}' - fallback to $AUTHORIZE_CODE_FLOW")
			authRequest.response_type = AUTHORIZE_CODE_FLOW
		}

		if ((!userId) || userId=="NULL"){
			if (!debug){
				logger.error("Unexpected error (SSO fail)")
				model.addAttribute("text", "Unexpected error (SSO fail)")
				return false
			}else {
				logger.error("No user found in REMOTE_USER header, fallback to 'user' (application is in debug mode)")
				authRequest.user_id = "user";
				return true
			}
		}else
			authRequest.user_id = userId
		return true
	}


	private boolean validateId(String input){
		return input && (input ==~ /[a-zA-Z0-9\-_]+$/)
	}


	// https://spring.io/guides/gs/handling-form-submission/
	@RequestMapping(value = "/{api_id}/auth/submit", method = RequestMethod.POST)
	// always use POST
	public String authSubmit(@RequestHeader(value = "REMOTE_USER", defaultValue = "NULL") String userId,
	                         @PathVariable("api_id") String apiId, AuthRequest authRequest, Model model) {
		// temporarily using "actionXXX" param to route requests to allow,deny or debug
		if (authRequest.actionDeny)
			return authDeny(authRequest, model);

		String forUser = (authRequest.user_id && debug)?authRequest.user_id : userId
		if (forUser == "NULL" && !debug){
			model.addAttribute("text", "Unexpected error (SSO fail)")
			logger.error("Unexpected error (SSO fail) - REMOTE_USER is $forUser")
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
		//     && !clientInfo.groups.contains(KongContract.GROUP_DYNAMIC_CLIENT)){
		if (!isOkToRedirect(authRequest, clientInfo, model)){
			logger.warn("Mismatched callback uri: passed '${authRequest.redirect_uri}' expected '${clientInfo.redirectUri}'")
			model.addAttribute("user_id", userId);
			model.addAttribute("map", authRequest);
			return "uoa-error";
		}

		// if redirectUri is overridden and it is allowed, set it here
		String redirectUri = authRequest.redirect_uri

		// now we need to inform Kong that user authenticatedUserId grants authorization to application cliend_id
		Map kongResponseObj = kong.submitAuthorization(forUser, redirectUri, apiInfo, authRequest);

		if (debug && authRequest.actionDebug) {
			model.addAttribute("user_id", userId);
			model.addAttribute("map", authRequest);
			model.addAttribute("provision_key", apiInfo.provisionKey);
			model.addAttribute("submitTo", kong.authorizeUrl(apiInfo.request_path));
			model.addAttribute("kongResponse", kongResponseObj.toString());

			return "temp";
		} else if(!(kongResponseObj.redirect_uri)) {
			logger.error("Unexpected error (kong response has no redirect, status ${kongResponseObj?.status})")
			model.addAttribute("user_id", userId);
			model.addAttribute("provision_key", apiInfo.provisionKey);
			model.addAttribute("submitTo", kong.authorizeUrl(apiInfo.request_path));
			model.addAttribute("kongResponse", kongResponseObj.toString());

			return "uoa-error";
		} else {
			String overrideCallback = authRequest.redirect_uri != clientInfo.redirectUri? authRequest.redirect_uri:null;
			String returnCallback = makeCallback(kongResponseObj, authRequest, clientInfo, model, overrideCallback)
			return returnCallback
		}
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
	 *   - overridden callback MUST be on the same domain as registered one OR if we are in DEV it could be localhost
	 *
	 * @param authRequest what was passed in teh request. contains new redirect_uri (if passed)
	 * @param clientInfo whats registered for this client in Kong
	 * @param model can be empty, as in the case of calling this method before issuing a final redirect.
	 * @return
	 */
	private boolean isOkToRedirect(AuthRequest authRequest, ClientInfo clientInfo, Model model){
		if ( (!authRequest.redirect_uri) || authRequest.redirect_uri==clientInfo.redirectUri) {
			authRequest.redirect_uri = clientInfo.redirectUri
			return true
		} else {

			if (clientInfo.groups.contains(KongContract.GROUP_AUTH_GRANTED)) {
				model?.addAttribute("clientWarning", clientInfo.redirectUri)
				return false; // should never be reached as this condition must be captured earlier
			}

			if (!clientInfo.groups.contains(KongContract.GROUP_DYNAMIC_CLIENT)) {
				model?.addAttribute("clientWarning", clientInfo.redirectUri)
				//model.addAttribute("clientError", "callback_match") // will be added outside of this func
				return false
			}

			// test url itself. the host must be localhost, or must match the registered one
			URI newUri = new URI(authRequest.redirect_uri)
			if (newUri.getHost()?.equalsIgnoreCase("localhost"))
				return true;

			URI registeredUri = new URI(clientInfo.redirectUri)

			if (KongContract.hostMatch(newUri, registeredUri)){
				//decide if need to show warning
				if (!KongContract.hostMatchAny(newUri, Arrays.asList(trustedRedirectHosts)))
					model?.addAttribute("clientWarning", clientInfo.redirectUri)
				return true
			}else {
				model?.addAttribute("clientWarning", clientInfo.redirectUri)
				return false
			}
		}
	}

	/**
	 * Complex logic to support Kong 0.8 and above (in 0.8 url fragments didnt work)ю
	 * This method will be much smaller if we drop support of 0.8
	 * @param kongResponseObject
	 * @param authRequest
	 * @param clientInfo
	 * @param model
	 * @param overrideCallback
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
		// if it has a fragment (this is Kong 0.9+, yay less work to do) or its for code flow...
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

		String clientCallbackUrl = clientInfo.redirectUri+"?error=access_denied&error_description=The+user+has+denied+the+access";

		return "redirect:" + clientCallbackUrl;
	}

	private List<String> processScopes(AuthRequest authRequest, ApiInfo apiInfo, Model model){
		List<String> validScopes = []
		Map<String, String> scopes = new HashMap<>();
		if (!authRequest.scope)
			return validScopes

		String[] requestedScopes = authRequest.scope?.split(" ")
		if (requestedScopes.length==1 && requestedScopes[0].contains(','))
			requestedScopes = authRequest.scope?.split(",")

		requestedScopes.each {
			if (apiInfo?.scopes?.contains(it)){
				validScopes.add(it)
				String scopeDescription = kong.getScopeDescription(it)
				if (scopeDescription)
					scopes.put(it, scopeDescription)
				else {
					// todo if we cant find description of a scope, what should be do
				}
			}else{
				// todo if scope is not valid for this endpoint, warn the user right away?
			}

		}
		model?.addAttribute("scopes", scopes);
		return validScopes
	}

}
