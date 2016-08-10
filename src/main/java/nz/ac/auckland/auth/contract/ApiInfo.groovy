package nz.ac.auckland.auth.contract

// API info returned by Kong
class ApiInfo {
	// this is what is returned by making a call to http://localhost:8001/apis/pcfdev-oauth
	/*
	"upstream_url":"https:\/\/www.dev.auckland.ac.nz\/study-options\/api\/1.23\/search\/results",
	"request_path":"\/pcfdevo",
	"id":"5847fdbe-5ce6-4b63-cbea-f29d88c12bb0",
	"strip_request_path":true,
	"name":"pcfdev-oauth",
	"created_at":1450149205000
	 */

	String id
	String upstream_url
	String request_path
	String strip_request_path
	String name
	String created_at

	// will be populated after a call to oauth2 plugin
	String provisionKey
	List<String> scopes

	// plugin info can be retrieved by calling http://localhost:8001/apis/pcfdev-oauth/plugins
	// and the result would be

	/*
	{
		"data": [{
			"api_id": "5847fdbe-5ce6-4b63-cbea-f29d88c12bb0",
			"id": "ed112742-03f2-463b-c624-eaf102064491",
			"created_at": 1450150227000,
			"enabled": true,
			"name": "oauth2",
			"config": {
				"mandatory_scope": false,
				"token_expiration": 7200,
				"enable_implicit_grant": false,
				"hide_credentials": false,
				"enable_password_grant": false,
				"enable_authorization_code": true,
				"provision_key": "pcfdev-api-key",
				"enable_client_credentials": false,
				"scopes": ["pcfread", "pcfwrite"]
			}
		}]
	}
	 */
}
