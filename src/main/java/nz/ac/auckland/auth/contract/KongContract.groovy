package nz.ac.auckland.auth.contract

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import nz.ac.auckland.auth.formdata.AuthRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class KongContract {
	public static final String GROUP_PRIVATE = "system-private"
	public static final String GROUP_HASH_CLIENT = "system-hash-client"
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
	private String kongAdminUrl = "https://admin.api.dev.auckland.ac.nz/"; // needs trailing /

	@Value('${kong.proxy.url}')
	private String kongProxyUrl = "https://proxy.api.dev.auckland.ac.nz"; // does NOT need trailing /   ?????

	@Value('${kong.admin.key}')
	private String kongAdminKey = "none";

	@Value('${as.debug}')
	private boolean debug = false

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
		return joinUrls(kongAdminUrl, OP_LIST_TOKENS)+"?authenticated_userid=$userId"
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
			println "Fetching scope $scopeName description from $url"
			def result = getMap(url)
			if (result.message){
				scopesCache.put(scopeName, result.message)
			}else{
				println "ERROR: scope $scopeName description not found"
				if (scopesFallback.get(scopeName))
					scopesCache.put(scopeName, scopesFallback.get(scopeName))
			}
		}
		return scopesCache[scopeName]
	}

	static def handler = {resp, reader, func ->
		println "response status: ${resp.statusLine}"
		if (resp.status !=200) {
			println 'Headers: -----------'
			resp.headers.each { h ->
				println " ${h.name} : ${h.value}"
			}
			println 'Response data: -----'
			println reader
			println '--------------------'
		}
		func(resp, reader);
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
		def http = new HTTPBuilder(fullUrl)
		println "Calling "+http.uri
		http.request(Method.GET, ContentType.JSON) {
			requestContentType = ContentType.JSON
			//body = queryData
			if (kongAdminKey && kongAdminKey!="none")
				headers = [apikey: kongAdminKey]

			response.success = handler.rcurry({resp, reader->
				if (reader instanceof Map)
					map = reader
			})
			response.failure = handler.rcurry({resp, reader->
				println "ERROR:\r$reader"
			})
		}

		return map
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

}
