package nz.ac.auckland.auth.endpoints

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import nz.ac.auckland.auth.formdata.ApiInfo;
import nz.ac.auckland.auth.formdata.AuthRequest
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.client.RestTemplate

@Controller
// to do:  add csrf protection to prevent user from unknowingly submitting approval by following specially crafted link
//         (using POST should prevent it, but its better to have more protection in place)
// do NOT enable CORS on any of these method
public class AuthorizationController {

	// options for reponse_type when calling oauth/authorize endpoint
	public static final String AUTHORIZE_CODE_FLOW = "code"
	public static final String AUTHORIZE_IMPLICIT_FLOW = "token"


	// to do take from properties
	private String kongAdminUrl = "https://admin.api.dev.auckland.ac.nz/";
	private String kongProxyUrl = "https://proxy.api.dev.auckland.ac.nz";


	@RequestMapping("/{api_id}/oauth2/authorize")
	public String authForm(@RequestHeader(value = "REMOTE_USER", defaultValue = "NULL") String userId,
	                       @RequestHeader(value = "HTTP_DISPLAYNAME", defaultValue = "NULL") String userName,
	                       @PathVariable("api_id") String apiId, AuthRequest authRequest, Model model) {

		return renderAuthForm(userId, userName, apiId, authRequest, model);
	}

	// http://localhost:8090/pcfdev-oauth/auth?client_id=irina_oauth2_pluto&response_type=code&scope=read,write
	@Deprecated
	@RequestMapping("/{api_id}/auth")
	public String authFormDeprecated(@RequestHeader(value = "REMOTE_USER", defaultValue = "NULL") String userId,
						   @RequestHeader(value = "HTTP_DISPLAYNAME", defaultValue = "NULL") String userName,
	                       @PathVariable("api_id") String apiId, AuthRequest authRequest, Model model) {
		return renderAuthForm(userId, userName, apiId, authRequest, model);
	}


	private String renderAuthForm(String userId, String userName, String apiId, AuthRequest authRequest, Model model){
		// todo get scopes from request
		// todo get scopes description from ?<TBD>?
		// todo if userId is NULL, show error (user is not authenticated, SSO failed??)
		Map<String, String> scopes = new HashMap<>();
		scopes.put("person-read", "Allows application to read person information on your behalf.");
		scopes.put("person-write", "Allows application to update person information on your behalf. Your current role-based authorization will apply.");
		model.addAttribute("scopes", scopes); // to do scopes description

		// extract data from parameters and pass to the view in hidden fields
		authRequest.client_id = sanitize(authRequest.client_id) ?: "irina_oauth2_pluto";
		authRequest.response_type = sanitize(authRequest.response_type) ?: AUTHORIZE_CODE_FLOW;
		authRequest.user_id = userId != "NULL" ? userId : "";
		model.addAttribute("map", authRequest);

		// greetings
		String displayName = (userName != "NULL"? userName :  ("Unknown ("+(authRequest.user_id ?: "user")+")"))
		model.addAttribute("name", displayName);

		// find out application name
		// call Kong http://localhost:8001/oauth2?client_id=irina_oauth2_pluto
		String appName = "unknown" // todo if app not found, show error page
		String clientCallbackUrl = "unknown"
		Map clientInfo = new RestTemplate().getForObject(kongAdminUrl + "/oauth2?client_id=" + authRequest.client_id, Map.class);
		if (clientInfo["data"] != null && clientInfo["data"] instanceof List && clientInfo["data"].size() > 0) {
			appName = (String) clientInfo["data"][0]["name"];
			def callbackFromKong = clientInfo["data"][0]["redirect_uri"];
			// was string, now its an array since Kong > 0.6
			if (callbackFromKong != null && !(callbackFromKong instanceof String))
				clientCallbackUrl = (String) callbackFromKong[0];
			else
				clientCallbackUrl = (String) callbackFromKong;
		}

		URI uri = new URI(clientCallbackUrl)
		model.addAttribute("appname", appName);
		model.addAttribute("appurl", (uri.getScheme() ? uri.getScheme() + "://" : "") + uri.getHost());
		model.addAttribute("apiid", apiId);

		return "auth";
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
			return authDeny(authRequest);

		// fetch api url and oauth2 provision key for given api
		String provisionKey = null;
		RestTemplate rest = new RestTemplate();
		ApiInfo apiInfo = rest.getForObject(kongAdminUrl + "/apis/" + apiId, ApiInfo.class);
		Map pluginInfo = rest.getForObject(kongAdminUrl + "/apis/" + apiId + "/plugins", Map.class);
		pluginInfo.data.each { plugin ->
			if (plugin.name == "oauth2")
				provisionKey = plugin.config.provision_key;
		}

		// todo if api (or provision key) not found, show error page

		// now we need to inform Kong that user authenticatedUserId grants authorization to application cliend_id
		String submitTo = apiInfo.request_path + "/oauth2/authorize"

		// todo if result is not response_uri, then show error page
		String kongResponse = submitAuthorization(userId, submitTo, provisionKey, authRequest);

		if (authRequest.actionDebug || !(kongResponse)) {
			model.addAttribute("user_id", userId);
			model.addAttribute("map", authRequest);
			model.addAttribute("provision_key", provisionKey);
			model.addAttribute("submitTo", kongProxyUrl + submitTo);
			model.addAttribute("kongResponse", kongResponse);

			return "temp";
		} else
			return "redirect:" + kongResponse
	}

	private String submitAuthorization(String authenticatedUserId, String submitTo, String provisionKey,
	                                   AuthRequest authRequest) {
		/*$ curl https://your.api.com/oauth2/authorize \
			--header "Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW" \
			--data "client_id=XXX" \
			--data "response_type=XXX" \
			--data "scope=XXX" \
			--data "provision_key=XXX" \
			--data "authenticated_userid=XXX"
		*/

		def redirect_uri = null;

		// perform a POST request, expecting JSON response (redirect url)
		def http = new HTTPBuilder(kongProxyUrl + submitTo)
		http.request(Method.POST, ContentType.JSON) {
			requestContentType = ContentType.URLENC
			body = [client_id: authRequest.client_id, response_type: authRequest.response_type,
			        scope    : authRequest.scope, provision_key: provisionKey,
			        authenticated_userid: authenticatedUserId] // do NOT use authRequest.user_id as it can be spoofed
			// response handler for a success response code
			response.success = { resp, reader ->
				println "response status: ${resp.statusLine}"
				println 'Headers: -----------'
				resp.headers.each { h ->
					println " ${h.name} : ${h.value}"
				}

				if (reader instanceof Map && reader.containsKey("redirect_uri"))
					redirect_uri = reader.get("redirect_uri")
				else {
					println 'Response data: -----'
					println reader
					println '--------------------'
				}
			}
			response.failure = { resp, reader ->
				println "ERROR response status: ${resp.statusLine}"
				println 'Headers: -----------'
				resp.headers.each { h ->
					println " ${h.name} : ${h.value}"
				}
				println 'Response data: -----'
				println reader
				println '--------------------'
				redirect_uri = reader.toString()
			}
		}
		return redirect_uri
	}

	public String authDeny(AuthRequest authRequest) {
		String clientCallbackUrl = "unknown"; // todo if app not found, show error page
		RestTemplate restTemplate = new RestTemplate();
		Map clientInfo = restTemplate.getForObject(kongAdminUrl + "/oauth2?client_id=" + authRequest.client_id, Map.class);
		if (clientInfo["data"] != null && clientInfo["data"] instanceof List && clientInfo["data"].size() > 0)
			clientCallbackUrl = (String) clientInfo["data"][0]["redirect_uri"];

		// todo build redirect url in a smart way (assuming there could be other parameters already)
		clientCallbackUrl += "/?error=access_denied&error_description=The+user+denied+access+to+your+application";

		return "redirect:" + clientCallbackUrl;
	}
}
