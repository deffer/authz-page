package nz.ac.auckland.auth.contract

import com.fasterxml.jackson.databind.ObjectMapper
import groovyx.net.http.ContentType
import groovyx.net.http.EncoderRegistry
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import nz.ac.auckland.auth.endpoints.JsonHelper
import nz.ac.auckland.auth.formdata.AuthRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class KongContract {

	private static final Logger logger = LoggerFactory.getLogger(KongContract.class);

	public static final String GROUP_PRIVATE = "system-private"
	public static final String GROUP_AUTH_GRANTED = "system-auth-granted"
	public static final String GROUP_DYNAMIC_CLIENT = "system-dynamic-client"

	// these are endpoints on admin interface (port 8001)
	public static final String OP_LIST_TOKENS = "/oauth2_tokens"
	public static final String OP_CLIENT_OAUTH2 = "/oauth2" // /oauth2?client_id=

	// these are endpoints on public interface (8000)
	static String OP_AUTHORIZE = '/oauth2/authorize'


	// static resources. accessed as normal APIs
	public static final String RES_SCOPES = "/scope"


	@Value('${kong.admin.url}')
	private String kongAdminUrl //= "https://api.dev.auckland.ac.nz/service/kong-loopback-api";

	@Value('${kong.proxy.url}')
	private String kongProxyUrl // ="https://api.dev.auckland.ac.nz/service";

	@Value('${kong.admin.key:}')
	private String kongAdminKey

	@Value('${as.log.verbose:false}')
	private static boolean verboseLogs

	public int kongVersion = 9;

	public HTTPBuilder newBuilder(String url){
		HTTPBuilder http = new HTTPBuilder(url)

		// This is required on Windows since the default encoder will be forced to Win1251 otherwise
		EncoderRegistry encoder = new EncoderRegistry( )
		encoder.setCharset('utf-8')
		http.encoderRegistry = encoder


		if (kongAdminKey && kongAdminKey != "none")
			http.setHeaders(["apikey": kongAdminKey])

		return http
	}

	public static String joinUrls(String base, String... parts){
		if ((!parts) || parts.length==0)
			return base;

		String current = base?:""

		parts.each {part->
			if (current.endsWith('/')) {
				if (part.startsWith('/'))
					current = current[0..current.length()-2] + part
				else
					current =  current + part
			}else{
				if (part && current && !part.startsWith('/'))
					current = current+'/'+part
				else
					current = current+part
			}
		}
		return current
	}

	public String oauth2ClientQuery(String clientId){
		return joinUrls(kongAdminUrl, OP_CLIENT_OAUTH2)+"?client_id=$clientId"
	}

	public String oauth2ClientQueryById(String id){
		return joinUrls(kongAdminUrl, OP_CLIENT_OAUTH2)+"?id=$id"
	}

	public String listUserTokensQuery(String userId){
		return joinUrls(kongAdminUrl, OP_LIST_TOKENS)+"?authenticated_userid=$userId&size=400"
	}

	public String listSpecialConsentTokensQuery(String userFormatted, String clientAppId=null){
		long expiresIn = 0
		if (clientAppId)
			return joinUrls(kongAdminUrl, OP_LIST_TOKENS)+"?authenticated_userid=$userFormatted&credential_id=$clientAppId&expires_in=$expiresIn"
		else
			return joinUrls(kongAdminUrl, OP_LIST_TOKENS)+"?authenticated_userid=$userFormatted&expires_in=$expiresIn"
	}


	public String getTokenQuery(String id){
		return joinUrls(kongAdminUrl, OP_LIST_TOKENS, id)
	}


	public String listConsumerAclsQuery(String consumerId){
		return joinUrls(kongAdminUrl, "/consumers/$consumerId/acls")
	}

	public String apiInfoQuery(String apiId){
		return joinUrls(kongAdminUrl, "/apis/" + apiId);
	}

	public String listApiPluginsQuery(String apiId){
		return joinUrls(kongAdminUrl, "/apis/" + apiId + "/plugins");
	}


	public String authorizeUrl(String apiPath){
		return joinUrls(kongProxyUrl, apiPath, OP_AUTHORIZE)
	}

	public static boolean hostMatch(URI uri1, URI uri2){
		//URI newUri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), newQuery, uri.getFragment());
		URI host1 = new URI(uri1.getScheme(), uri1.getAuthority(), "/", "", "")
		URI host2 = new URI(uri2.getScheme(), uri2.getAuthority(), "/", "", "")
		return host1.equals(host2)
	}

	public static boolean hostMatchAny(URI uri, List<String> hosts){
		String current = uri.getHost()?.toLowerCase() // unicode????
		if ((!current) || !hosts)
			return false;

		for (String host: hosts){
			if (host && current.endsWith(host.toLowerCase()))
				return true;
		}

		return false;
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

	// beware of possible DDOS by requesting random scopes.
	//   option is to load all scopes in memory and ignore requests for unknown scopes
	public static final Map scopesCache = [:]
	public static final Map scopesFallback = ["default": "Basic information about you", "identity-read": "Read-only access to identity systems on your behalf.", "identity-write": "Access to identity systems on your behalf."]
	public String getScopeDescription(String scopeName){
		if (!scopeName)
			return ""
		scopeName = scopeName.toLowerCase()
		if (!scopesCache[scopeName]){
			String url = joinUrls(kongProxyUrl, RES_SCOPES, scopeName)
			logger.info("Fetching scope $scopeName description from $url")
			def result = getMap(url)
			if (result.message){
				scopesCache.put(scopeName, result.message)
			}else{
				logger.error("ERROR: scope $scopeName description not found")
				if (scopesFallback.get(scopeName))
					scopesCache.put(scopeName, scopesFallback.get(scopeName))
			}
		}
		return scopesCache[scopeName]
	}

	static def handler = {resp, reader, func ->
		def msg = "response status: ${resp.statusLine}\n"
		if (resp.status !=200) {
			msg += 'Headers: -----------\n'
			resp.headers.each { h ->
				msg += " ${h.name} : ${h.value}\n"
			}
			msg += 'Response data: -----\n'
			msg += "$reader\n"
			msg += '--------------------\n'
		}
		if (verboseLogs)
			logger.debug(msg)
		func(resp, reader)
	}

	/**
	 * Calls GET endpoint on Kong and returns result as Map (if there was no errors)
	 * @param fullUrl
	 * @param kongAdminKey
	 * @return empty map if there was an error. The error message will be logged.
	 *   This is not an error we can recover from or display it to a user.
	 */
	public Map getMap(String fullUrl){
		Map map = [:]
		HTTPBuilder http = newBuilder(fullUrl)
		if (verboseLogs) logger.info("Calling "+http.uri)
		try {
			http.request(Method.GET, ContentType.JSON) {
				requestContentType = ContentType.JSON

				response.success = handler.rcurry({ resp, reader ->
					if (reader instanceof Map)
						map = reader
				})
				response.failure = handler.rcurry({ resp, reader ->
					logger.error("Error calling ${http.uri}:" + reader)
				})
			}
		}catch (Throwable e){
			logger.error(e.getMessage(), e)
			return [:]
		}
		return map
	}

	public Map getConsentTokens(String userId, String clientAppId=null){
		return getMap(listSpecialConsentTokensQuery(userId+Token.CONSENT_USER_SUFFIX, clientAppId))
	}

	public List<Token> getTokens4User(String userId){
		List<Token> result = []
		Map accessTokens = getMap(listUserTokensQuery(userId))
		Map consentTokens = getMap(listSpecialConsentTokensQuery(userId+Token.CONSENT_USER_SUFFIX))
		accessTokens?.data?.each {Map tokenData->
			Token token = JsonHelper.convert(tokenData, Token.class)
			token.consentToken = Boolean.FALSE
			result.add(token)
		}
		consentTokens?.data?.each {Map tokenData->
			Token token = JsonHelper.convert(tokenData, Token.class)
			token.init()
			if (token.isConsentToken())
				result.add(token)
			else {
				logger.warn("Consent token integrity error for token ${token.id}, adding to normal set")
				result.add(token)
			}
		}
		return result
	}

	public int deleteResource(String resource){
		int result = 200
		HTTPBuilder http = newBuilder(resource)
		if (verboseLogs)
			logger.info("Calling DELETE on ${http.uri}")
		try {
			http.request(Method.DELETE, ContentType.TEXT, { req ->

				response.success = handler.rcurry({ resp, reader ->
					result = resp.status ? (resp.status as Integer) : 400
				})

				response.failure = handler.rcurry({ resp, reader ->
					result = resp.status ? (resp.status as Integer) : 400
					logger.error("Error calling DELETE on ${http.uri}: code " + result)
				})
			})
			return result;
		}catch (Throwable e){
			logger.error(e.getMessage(), e)
			return 502
		}
	}

	public int getKongVersion(){
		try {
			Map rootResponse = getMap(kongAdminUrl)
			if (rootResponse && rootResponse.version && rootResponse.version.matches(/0\.\d+\.\d+/)) {
				String ver = ((String) rootResponse.version).tokenize('.')[1]
				logger.debug("Extracted version $ver from Kong response\n"+rootResponse.toString())
				kongVersion = Integer.valueOf(ver)
			}
		}catch (Throwable t){
			logger.error(t.getMessage(), t)
		}
		return kongVersion
	}

	/**
	 * Returns information about client application (Application name, groups consumer belongs to, etc)
	 * @param clientId client_id as it was passed by a client application
	 * @return
	 */
	ClientInfo getClientInfo(String clientId){
		Map map = getMap(oauth2ClientQuery(clientId));
		return buildClientInfo(map)
	}

	/**
	 * Returns information about client application (Application name, groups consumer belongs to, etc)
	 * @param credentialsId internal id of the client credentials record (only for oauth2 credentials)
	 * @return
	 */
	ClientInfo getClientFromCredentialsId(String credentialsId){
		Map map = getMap(oauth2ClientQueryById(credentialsId))
		return buildClientInfo(map)
	}

	private ClientInfo buildClientInfo(Map fromMap){
		ClientInfo result = new ClientInfo()

		if (fromMap && ClientInfo.loadFromClientResponse(fromMap, result)){
			fromMap = getMap(listConsumerAclsQuery(result.consumerId));
			ClientInfo.loadFromConsumerResponse(fromMap, result)
			return result
		}else
			return null
	}


	ApiInfo getApiInfo(String apiId){
		ApiInfo result
		try {
			result = JsonHelper.convert(getMap(apiInfoQuery(apiId)), ApiInfo.class)
		}catch (Throwable e){
			logger.error("Exception while connecting to API Gateway: ${e.getMessage()}", e)
			return null
		}
		if ((!result) || !(result.id))
			return result
		Map pluginInfo = getMap(listApiPluginsQuery(apiId))
		pluginInfo?.data?.each { plugin ->
			if (plugin.name == "oauth2") {
				result.provisionKey = plugin.config.provision_key;
				result.scopes = plugin.config.scopes
			}
		}
		return result;
	}

	Map submitAuthorization(String authenticatedUserId, String redirectUri, ApiInfo apiInfo, AuthRequest authRequest) {
		/*$ curl https://your.api.com/oauth2/authorize \
			--header "Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW" \
			--data "client_id=XXX" \
			--data "response_type=XXX" \
			--data "scope=abc cds xcv" \
			--data "provision_key=XXX" \
			--data "authenticated_userid=XXX"
		*/

		String submitTo = authorizeUrl(apiInfo.selectRequestPath())

		if (authRequest.state)
			submitTo += "?state=${URLEncoder.encode(authRequest.state, 'UTF-8')}"

		// as of Kong 0.9.9 the request MUST contain scope parameter, but it can be empty
		//   we should assume 'default' scope if nothing else is passed and api accepts default
		Map result = [:]
		String scopes = ""
		if (authRequest.scope)
			scopes = authRequest.extractedScopes().join(" ")
		else if (apiInfo?.scopes?.contains("default"))
			scopes = "default"

		// perform a POST request, expecting JSON response (redirect url)
		HTTPBuilder http = newBuilder(submitTo)
		logger.info("User $authenticatedUserId has authorized ${authRequest.client_id} to access ${apiInfo.selectRequestPath()}")
		if (verboseLogs) logger.info("Calling ${http.uri} with scopes $scopes and user $authenticatedUserId for client ${authRequest.client_id}")
		def theBody = [client_id: authRequest.client_id, response_type: authRequest.response_type,
		            scope    : scopes, provision_key: apiInfo.provisionKey,
		            authenticated_userid: authenticatedUserId] // MUST use authenticatedUserId of SSO user

		if (redirectUri) // note, this should match one of the registered ones exactly, or Kong will reject it
			theBody.redirect_uri = redirectUri

		http.request(Method.POST, ContentType.JSON) {
			requestContentType = ContentType.URLENC
			body = theBody

			// response handler for a success response code
			response.success = handler.rcurry({resp, reader->
				result.put("status", resp.status)
				if (reader instanceof Map)
					result.putAll(reader)
			})
			response.failure = handler.rcurry({resp, reader->
				result.put("status", resp.status)
				if (reader instanceof Map)
					result.putAll(reader)

				logger.error(reader.toString())
			})
		}
		return result
	}

	Map saveToken(Token token) {
		HTTPBuilder http = newBuilder(joinUrls(kongAdminUrl, OP_LIST_TOKENS))
		logger.info("Saving consent for ${token.authenticated_userid} client ${token.credential_id} as access_token ${token.access_token}")

		def theBody = JsonHelper.serialize(token)

		Map result = [:]

		http.request(Method.POST, ContentType.JSON) {
			requestContentType = ContentType.JSON
			body = theBody

			// response handler for a success response code
			response.success = handler.rcurry({resp, reader->
				result.put("status", resp.status)
				if (reader instanceof Map)
					result.putAll(reader)
			})
			response.failure = handler.rcurry({resp, reader->
				result.put("status", resp.status)
				if (reader instanceof Map)
					result.putAll(reader)

				logger.error(reader.toString())
			})
		}
		return result
	}
}
