package nz.ac.auckland.auth.endpoints

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import nz.ac.auckland.auth.contract.KongContract
import nz.ac.auckland.auth.formdata.ApiInfo;
import nz.ac.auckland.auth.formdata.AuthRequest
import nz.ac.auckland.auth.formdata.ClientInfo
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.client.RestTemplate


@Controller
// to do:  add csrf protection. to prevent user from unknowingly submitting approval by following specially crafted link
//         (using POST should prevent it, but its better to have more protection in place)
// do NOT enable CORS on any of these method
public class AuthorizationController {

	// options for response_type when calling oauth/authorize endpoint
	public static final String AUTHORIZE_CODE_FLOW = "code"
	public static final String AUTHORIZE_IMPLICIT_FLOW = "token"



	@Value('${kong.admin.url}')
	private String kongAdminUrl = "https://admin.api.dev.auckland.ac.nz/"; // needs trailing /

	@Value('${kong.proxy.url}')
	private String kongProxyUrl = "https://proxy.api.dev.auckland.ac.nz"; // does NOT need trailing /   ?????


	@Value('${as.debug}')
	private boolean debug = false


	@RequestMapping("/{api_id}/oauth2/authorize")
	public String authForm(@RequestHeader(value = "REMOTE_USER", defaultValue = "NULL") String userId,
	                       @RequestHeader(value = "displayName", defaultValue = "NULL") String userName,
	                       @PathVariable("api_id") String apiId, AuthRequest authRequest, Model model) {

		return renderAuthForm(userId, userName, apiId, authRequest, model);
	}

	// http://localhost:8090/pcfdev-oauth/auth?client_id=irina_oauth2_pluto&response_type=code&scope=read,write
	@Deprecated
	@RequestMapping("/{api_id}/auth")
	public String authFormDeprecated(@RequestHeader(value = "REMOTE_USER", defaultValue = "NULL") String userId,
						   @RequestHeader(value = "displayName", defaultValue = "NULL") String userName,
	                       @PathVariable("api_id") String apiId, AuthRequest authRequest, Model model) {
		return renderAuthForm(userId, userName, apiId, authRequest, model);
	}


	private String renderAuthForm(String userId, String userName, String apiId, AuthRequest authRequest, Model model){

		if (!sanitizeRequestParameters(authRequest, userId, model))
			return "generic_response" // todo show ERROR page

		// pass request input values to the view in hidden fields
		model.addAttribute("map", authRequest);
		processScopes(authRequest, model)

		// defined greetings value
		String displayName = userName != "NULL"? userName :  "Unknown (${authRequest.user_id})"
		model.addAttribute("name", displayName);

		ApiInfo apiInfo = getApiInfo(apiId)
		if (!apiInfo){
			model.addAttribute("clientError", "unknown_api")
			return "auth";
		}

		if (!(apiInfo.provisionKey)){
			model.addAttribute("clientError", "noauth_api")
			return "auth";
		}

		// find out application and consumer details
		ClientInfo clientInfo = getClientInfo(authRequest)
		if (clientInfo){
			if (clientInfo.groups.contains(KongContract.GROUP_AUTH_GRANTED) && authRequest.response_type==AUTHORIZE_IMPLICIT_FLOW){
				// that's our internal university client application. we trust it and so does the user.
				String kongResponse = submitAuthorization(userId, clientInfo.redirectUri, apiInfo.request_path + "/oauth2/authorize", apiInfo.provisionKey, authRequest);
				return makeCallback(kongResponse, authRequest, clientInfo, model, null) // do NOT override callbackUri, huge security risk
			}

			if ( (!authRequest.redirect_uri) || authRequest.redirect_uri==clientInfo.redirectUri)
				authRequest.redirect_uri = clientInfo.redirectUri
			else {
				model.addAttribute("clientWarning", clientInfo.redirectUri)
				if (!clientInfo.groups.contains(KongContract.GROUP_DYNAMIC_CLIENT))
					model.addAttribute("clientError", "callback_match")

			}

			URI uri = new URI(authRequest.redirect_uri)
			model.addAttribute("appname", clientInfo.name);
			model.addAttribute("appurl", (uri.getScheme() ? uri.getScheme() + "://" : "") + uri.getHost());
			model.addAttribute("apiid", apiId);
		}else{
			model.addAttribute("clientError", "unknown_client")
		}

		return "auth";
	}


	boolean sanitizeRequestParameters(AuthRequest authRequest, String userId, Model model) {
		// todo if userId is NULL, show error (user is not authenticated, SSO failed??)
		authRequest.client_id = sanitize(authRequest.client_id) ?: "irina_oauth2_pluto";
		authRequest.response_type = sanitize(authRequest.response_type) ?: AUTHORIZE_CODE_FLOW;
		if ((!userId) || userId=="NULL"){
			if (!debug){
				model.addAttribute("Unexpected error (SSO fail)")
				return false
			}else {
				authRequest.user_id = "user";
				return true
			}
		}else
			authRequest.user_id = userId
		return true
	}

	// never trust any data that didnt come from trusted services
	private String sanitize(String input) {
		// implement
		return input;
	}

	// https://spring.io/guides/gs/handling-form-submission/
	@RequestMapping(value = "/{api_id}/auth/submit", method = RequestMethod.POST)
	// always use POST
	public String authSubmit(@RequestHeader(value = "REMOTE_USER", defaultValue = "NULL") String userId,
	                         @PathVariable("api_id") String apiId, AuthRequest authRequest, Model model) {
		// temporarily using "actionXXX" param to route requests to allow,deny or debug
		if (authRequest.actionDeny)
			return authDeny(authRequest, model);

		// fetch api url and oauth2 provision key for given api
		ApiInfo apiInfo = getApiInfo(apiId);
		ClientInfo clientInfo = getClientInfo(authRequest)

		if ( (!clientInfo) || (!apiInfo) || !(apiInfo.provisionKey)){
			// todo show error page
			println("Not found: clientInfo="+clientInfo?.toString()+"  apiInfo="+apiInfo?.toString()+"  provisionKey="+apiInfo?.provisionKey)
			model.addAttribute("user_id", userId);
			model.addAttribute("map", authRequest);
			model.addAttribute("provision_key", apiInfo?.provisionKey);
			return "temp";
		}

		// if redirectUri is overridden and it is allowed, set it here
		String redirectUri = authRequest.redirect_uri
		if (authRequest.redirect_uri != clientInfo.redirectUri && !clientInfo.groups.contains(KongContract.GROUP_DYNAMIC_CLIENT)){
			// todo show error page
			println("Mismatched callback uri: passed '${authRequest.redirect_uri}' expected '${clientInfo.redirectUri}'")
			model.addAttribute("user_id", userId);
			model.addAttribute("map", authRequest);
			model.addAttribute("provision_key", apiInfo.provisionKey);
			return "temp";
		}

		// now we need to inform Kong that user authenticatedUserId grants authorization to application cliend_id
		String submitTo = apiInfo.request_path + "/oauth2/authorize"
		String kongResponse = submitAuthorization(userId, redirectUri, submitTo, apiInfo.provisionKey, authRequest);

		if (authRequest.actionDebug || !(kongResponse)) {
			model.addAttribute("user_id", userId);
			model.addAttribute("map", authRequest);
			model.addAttribute("provision_key", apiInfo.provisionKey);
			model.addAttribute("submitTo", kongProxyUrl + submitTo);
			model.addAttribute("kongResponse", kongResponse);

			return "temp";
		} else {
			String overrideCallback = authRequest.redirect_uri != clientInfo.redirectUri? authRequest.redirect_uri:null;
			return makeCallback(kongResponse, authRequest, clientInfo, model, overrideCallback)
		}
	}

	private String makeCallback(String kongResponse, AuthRequest authRequest, ClientInfo clientInfo, Model model, String overrideCallback){
		if (!kongResponse){
			model.addAttribute("text", "Unexpected error (kong response has no redirect)")
			return "generic_response" // todo show ERROR page
		}
		URI uri = new URI(kongResponse)
		Map<String, String> params = splitQuery(uri.query)

		// add state parameter if requested
		if (authRequest.state && !params.containsKey("state")) {
			String newQuery = "state=${authRequest.state}"
			if (uri.query)
				newQuery = uri.query + "&" + newQuery;

			URI newUri = new URI(uri.getScheme(), uri.getAuthority(),
					uri.getPath(), newQuery, uri.getFragment());

			kongResponse = newUri.toString()
			uri = newUri
		}

		if (!params.containsKey("error")) {
			if (overrideCallback)
				kongResponse = overrideCallback + "?" + uri.query

			// assumes that neither overrideCallback nor registered callback would have a '#' in them
			if (authRequest.use_fragment || (authRequest.response_type?.equals(AUTHORIZE_IMPLICIT_FLOW) && clientInfo.groups.contains(KongContract.GROUP_HASH_CLIENT)))
				kongResponse = kongResponse.replace("?", "#")
		}

		return "redirect:" + kongResponse
	}

	// common closure to print http response
	def printResponse = {resp, reader ->
		println "ERROR response status: ${resp.statusLine}"
		println 'Headers: -----------'
		resp.headers.each { h ->
			println " ${h.name} : ${h.value}"
		}
		println 'Response data: -----'
		println reader
		println '--------------------'
	}

	// copied from stackoverflow
	public static Map<String, String> splitQuery(String query) throws UnsupportedEncodingException {
		Map<String, String> query_pairs = new LinkedHashMap<String, String>();
		if (!query)
			return query_pairs
		String[] pairs = query.split("&");
		for (String pair : pairs) {
			int idx = pair.indexOf("=");
			query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
		}
		return query_pairs;
	}

	private ApiInfo getApiInfo(String apiId){
		try {
			RestTemplate rest = new RestTemplate();
			ApiInfo apiInfo = rest.getForObject(kongAdminUrl + "/apis/" + apiId, ApiInfo.class);
			Map pluginInfo = rest.getForObject(kongAdminUrl + "/apis/" + apiId + "/plugins", Map.class);
			pluginInfo?.data?.each { plugin ->
				if (plugin.name == "oauth2")
					apiInfo.provisionKey = plugin.config.provision_key;
			}
			return apiInfo
		}catch (Exception e){
			e.printStackTrace()
			return null;
		}
	}
	private String submitAuthorization(String authenticatedUserId, String redirectUri,
	                                   String submitTo, String provisionKey, AuthRequest authRequest) {
		/*$ curl https://your.api.com/oauth2/authorize \
			--header "Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW" \
			--data "client_id=XXX" \
			--data "response_type=XXX" \
			--data "scope=XXX" \
			--data "provision_key=XXX" \
			--data "authenticated_userid=XXX"
		*/

		// todo when Mashape fixes the error with redirectUri, use the one passed in here

		def redirect_uri = null;
		String scopes = ""
		if (authRequest.scope)
			scopes = authRequest.scope.replaceAll(',', ' ')

		// perform a POST request, expecting JSON response (redirect url)
		def http = new HTTPBuilder(kongProxyUrl + submitTo)
		http.request(Method.POST, ContentType.JSON) {
			requestContentType = ContentType.URLENC
			body = [client_id: authRequest.client_id, response_type: authRequest.response_type,
			        scope    : scopes, provision_key: provisionKey,
			        authenticated_userid: authenticatedUserId] // MUST use authenticatedUserId of SSO user

			// response handler for a success response code
			response.success = { resp, reader ->
				if (reader instanceof Map && reader.containsKey("redirect_uri"))
					redirect_uri = reader.get("redirect_uri")
				else
					printResponse(resp, reader)

			}
			response.failure = { resp, reader ->
				printResponse(resp, reader)
				//redirect_uri = reader.toString()
			}
		}
		return redirect_uri
	}

	public String authDeny(AuthRequest authRequest, Model model) {
		ClientInfo clientInfo = getClientInfo(authRequest)
		if (!clientInfo) {
			model.addAttribute("text", "Unknown application (client_id)")
			return "generic_response" // todo show ERROR page
		}

		String clientCallbackUrl = clientInfo.redirectUri+"?error=access_denied&error_description=The+user+denied+access+to+your+application";

		return "redirect:" + clientCallbackUrl;
	}

	private ClientInfo getClientInfo(AuthRequest authRequest){
		ClientInfo result = new ClientInfo()
		Map map = new RestTemplate().getForObject(KongContract.oauth2ClientQuery(kongAdminUrl,authRequest.client_id), Map.class);

		if (ClientInfo.loadFromClientResponse(map, result)){
			map = new RestTemplate().getForObject("$kongAdminUrl/consumers/${result.consumerId}/acls", Map.class);
			ClientInfo.loadFromConsumerResponse(map, result)
			return result
		}else
			return null

	}

	private  boolean processScopes(AuthRequest authRequest, Model model){
		// todo get scopes from request
		// todo get scopes description from ?<TBD>?
		Map<String, String> scopes = new HashMap<>();
		scopes.put("person-read", "Allows application to read person information on your behalf. This is just an example scope to test the layout of the displayed page, please ignore it.");
		scopes.put("person-write", "Allows application to update person information on your behalf. Your current role-based privileges will apply. This is just an example scope to test the layout of the displayed page, please ignore it.");
		scopes.put("enrollment-read", "Allows application to read student enrollments on your behalf. Your current role-based privileges will apply. This is just an example scope to test the layout of the displayed page, please ignore it.");
		model.addAttribute("scopes", scopes); // to do scopes description
		return true
	}


}
