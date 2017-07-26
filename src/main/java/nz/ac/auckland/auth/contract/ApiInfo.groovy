package nz.ac.auckland.auth.contract

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.boot.autoconfigure.web.DefaultErrorAttributes

// API info returned by Kong
@JsonIgnoreProperties(ignoreUnknown = true)
class ApiInfo {
	// Kong 0.9.x
	// this is what is returned by making a call to http://localhost:8001/apis/pcfdev-oauth
	/*
	"upstream_url":"https:\/\/www.dev.auckland.ac.nz\/study-options\/api\/1.23\/search\/results",
	"request_path":"\/pcfdevo",
	"id":"5847fdbe-5ce6-4b63-cbea-f29d88c12bb0",
	"strip_request_path":true,
	"name":"pcfdev-oauth",
	"created_at":1450149205000
	 */

	// Kong 0.10.x
	/*
	{
		"uris": ["\/meta\/v1"],
		"id": "fedbdffa-3268-4834-b392-31e70e8161cc",
		"upstream_read_timeout": 60000,
		"preserve_host": false,
		"created_at": 1477534113000,
		"upstream_connect_timeout": 60000,
		"upstream_url": "http:\/\/apiboxdev01.its.auckland.ac.nz:8001",
		"strip_uri": true,
		"name": "meta-v1",
		"upstream_send_timeout": 60000,
		"retries": 5
	}
	 */
	String id
	String upstream_url

	@Deprecated
	String request_path

	@Deprecated
	String strip_request_path

	List<String> uris
	String strip_uri
	String name
	String created_at
	String preserve_host

	// will be populated after a call to oauth2 plugin
	String provisionKey
	List<String> scopes

	public String selectRequestPath(){
		if (uris)
			return uris[0]
		else
			return request_path
	}

	// plugin info can be retrieved by calling http://localhost:8001/apis/pcfdev-oauth/plugins
	// and the result would be
	// Kong 0.9x
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
				"accept_http_if_already_terminated": true,  <--- in kong 0.10
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
