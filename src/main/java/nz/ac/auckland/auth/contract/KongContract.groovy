package nz.ac.auckland.auth.contract

class KongContract {
	public static final String GROUP_PRIVATE = "system-private"
	public static final String GROUP_HASH_CLIENT = "system-hash-client"
	public static final String GROUP_AUTH_GRANTED = "system-auth-granted"
	public static final String GROUP_DYNAMIC_CLIENT = "system-dynamic-client"

	public static final String OP_LIST_TOKENS = "/oauth2_tokens"
	public static final String OP_CLIENT_OAUTH2 = "/oauth2" // /oauth2?client_id=

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

}
