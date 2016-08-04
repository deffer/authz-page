package nz.ac.auckland.auth.contract

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method

class KongContract {
	public static final String GROUP_PRIVATE = "system-private"
	public static final String GROUP_HASH_CLIENT = "system-hash-client"
	public static final String GROUP_AUTH_GRANTED = "system-auth-granted"
	public static final String GROUP_DYNAMIC_CLIENT = "system-dynamic-client"

	public static final String OP_LIST_TOKENS = "/oauth2_tokens"
	public static final String OP_CLIENT_OAUTH2 = "/oauth2" // /oauth2?client_id=

	// static resources. accessed as normal APIs
	public static final String RES_SCOPES = "/scope"


	public static String joinUrl(String base, String path){
		if (!path)
			return base;

		if (!base)
			return path;

		if (base.endsWith('/')) {
			if (path.startsWith('/'))
				return base[0..base.length()-2] + path
			else
				return base + path
		}else{
			if (!path.startsWith('/'))
				return base+'/'+path
			else
				return base+path
		}
	}

	public static String oauth2ClientQuery(String kongAdminUrl, String clientId){
		return joinUrl(kongAdminUrl, OP_CLIENT_OAUTH2)+"?client_id=$clientId"
	}

	public static String oauth2ClientQueryById(String kongAdminUrl, String id){
		return joinUrl(kongAdminUrl, OP_CLIENT_OAUTH2)+"?id=$id"
	}

	public static String listUserTokensQuery(String kongAdminUrl, String userId){
		return joinUrl(kongAdminUrl, OP_LIST_TOKENS)+"?authenticated_userid=$userId"
	}


	public static hostMatch(URI uri1, URI uri2){
		//URI newUri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), newQuery, uri.getFragment());
		URI host1 = new URI(uri1.getScheme(), uri1.getAuthority(), "/", "", "")
		URI host2 = new URI(uri2.getScheme(), uri2.getAuthority(), "/", "", "")
		return host1.equals(host2)
	}

	// beware of possible DDOS by requesting random scopes.
	//   option is to load all scopes in memory and ignore requests for unknown scopes
	public static final Map scopesCache = [:]
	public static final Map scopesFallback = ["default": "Basic information about you", "identity-read": "Read-only access to identity systems on your behalf.", "identity-write": "Access to identity systems on your behalf."]
	public static String getScopeDescription(String scopeName, String kongProxyUrl, String kongAdminKey){
		if (!scopeName)
			return ""
		scopeName = scopeName.toLowerCase()
		if (!scopesCache[scopeName]){
			String url = joinUrl(joinUrl(kongProxyUrl, RES_SCOPES), scopeName)
			println "Fetching scope $scopeName description from $url"
			def result = getMap(url, kongAdminKey)
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


	public static Map getMap(String fullUrl, String kongAdminKey){
		Map map = null
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
}
