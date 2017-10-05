package nz.ac.auckland.auth;

import nz.ac.auckland.auth.contract.ClientInfo
import nz.ac.auckland.auth.contract.KongContract
import nz.ac.auckland.auth.endpoints.AuthorizationController
import nz.ac.auckland.auth.formdata.AuthRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.ui.Model;

import java.util.Map;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = AuthRequest.class)
@WebAppConfiguration
public class AuthzPageApplicationTests {

	//@Test
	//public void contextLoads() {
	//}

	@Test
	public void testRedirectOverride(){
		String ATV = '1c3c69856bf1224c69856b3229909d77' //access token value
		Model model = [:] as Model

		AuthRequest request = new AuthRequest(client_id : "client_123", response_type: "code",
				scope: "default", state: "1236", remember : AuthRequest.REMEMBER_FOREVER,
				redirect_uri: "")

		ClientInfo clientInfo = new ClientInfo(clientId: "123", name: "App name", consumerId: "Bob",
				redirectUris: ["https://rs.dev.auckland.ac.nz/callback"], // doesn't matter here
				groups:[])

		// test CODE flow
		Map kongResponse = [redirect_uri: "https://rs.dev.auckland.ac.nz?code=$ATV&state=1236"]


		String redirect
		URI uri
		Map<String, String> query
		Map<String, String> fragment

		// test that its replaced and NOT converted into fragment
		redirect = AuthorizationController.makeCallback(kongResponse, request, clientInfo, model,
			"http://rs.dev.auckland.ac.nz/callback/auth")
		uri = new URI(redirect.substring(9))
		assert uri.host == "rs.dev.auckland.ac.nz"
		assert uri.path == "/callback/auth"
		assert !uri.fragment
		assert uri.query
		query = KongContract.splitQuery(uri.query)
		assert query.containsKey("code")
		assert query.containsKey("state")
		assert query["code"] == ATV

		// and again
		redirect = AuthorizationController.makeCallback(kongResponse, request, clientInfo, model,
				"https://something.else.rs.dev.auckland.ac.nz/auth/callback.html")
		uri = new URI(redirect.substring(9))
		assert uri.host == "something.else.rs.dev.auckland.ac.nz"
		assert uri.path == "/auth/callback.html"
		assert !uri.fragment
		assert uri.query
		query = KongContract.splitQuery(uri.query)
		assert query.containsKey("code")
		assert query.containsKey("state")
		assert query["code"] == ATV

		// and again
		redirect = AuthorizationController.makeCallback(kongResponse, request, clientInfo, model,
				"http://localhost:8080/auth/callback.html")
		uri = new URI(redirect.substring(9))
		assert uri.host == "localhost"
		assert uri.port == 8080
		assert uri.path == "/auth/callback.html"
		assert !uri.fragment
		assert uri.query
		query = KongContract.splitQuery(uri.query)
		assert query.containsKey("code")
		assert query.containsKey("state")
		assert query["code"] == ATV

		// -----------------------
		// test Implicit flow
		// -----------------------
		request.response_type = "token"
		kongResponse = [redirect_uri: "https://rs.dev.auckland.ac.nz#access_token=$ATV&expires_in=7200&state=1236&token_type=bearer"]

		redirect = AuthorizationController.makeCallback(kongResponse, request, clientInfo, model,
				"http://rs.dev.auckland.ac.nz/callback/auth")
		uri = new URI(redirect.substring(9))
		assert uri.host == "rs.dev.auckland.ac.nz"
		assert uri.path == "/callback/auth"
		assert uri.fragment
		assert !uri.query
		fragment = KongContract.splitQuery(uri.fragment)
		assert fragment["state"] == "1236"
		assert fragment["access_token"] == ATV
		assert fragment["token_type"]=="bearer"
		assert fragment["expires_in"] == "7200"

		// test no override and fragment is good
		redirect = AuthorizationController.makeCallback(kongResponse, request, clientInfo, model, null)
		uri = new URI(redirect.substring(9))
		assert uri.host == "rs.dev.auckland.ac.nz"
		assert (uri.path == "/" || !uri.path)
		assert uri.fragment
		assert !uri.query
		fragment = KongContract.splitQuery(uri.fragment)
		assert fragment["state"] == "1236"
		assert fragment["access_token"] == ATV
		assert fragment["token_type"]=="bearer"
		assert fragment["expires_in"] == "7200"


		// and again
		redirect = AuthorizationController.makeCallback(
				[redirect_uri: "https://rs.dev.auckland.ac.nz/oauth2/callback#access_token=$ATV&expires_in=7200&state=1236&token_type=bearer"],
				request, clientInfo, model, null)
		uri = new URI(redirect.substring(9))
		assert uri.host == "rs.dev.auckland.ac.nz"
		assert uri.path == "/oauth2/callback"
		assert uri.fragment
		assert !uri.query
		fragment = KongContract.splitQuery(uri.fragment)
		assert fragment["state"] == "1236"
		assert fragment["access_token"] == ATV
		assert fragment["token_type"]=="bearer"
		assert fragment["expires_in"] == "7200"

		// test no override but need to replace with fragment
		redirect = AuthorizationController.makeCallback(
				[redirect_uri: "https://rs.dev.auckland.ac.nz?access_token=$ATV&expires_in=7200&state=1236&token_type=bearer"],
				request, clientInfo, model, null)
		uri = new URI(redirect.substring(9))
		assert uri.host == "rs.dev.auckland.ac.nz"
		assert (uri.path == "/" || !uri.path)
		assert uri.fragment
		assert !uri.query
		fragment = KongContract.splitQuery(uri.fragment)
		assert fragment["state"] == "1236"
		assert fragment["access_token"] == ATV
		assert fragment["token_type"]=="bearer"
		assert fragment["expires_in"] == "7200"

		// and again (no override but need to replace with fragment)
		redirect = AuthorizationController.makeCallback(
				[redirect_uri: "https://rs.dev.auckland.ac.nz/callback/auth.html?access_token=$ATV&expires_in=7200&state=1236&token_type=bearer"],
				request, clientInfo, model, null)
		uri = new URI(redirect.substring(9))
		assert uri.host == "rs.dev.auckland.ac.nz"
		assert uri.path == "/callback/auth.html"
		assert uri.fragment
		assert !uri.query
		fragment = KongContract.splitQuery(uri.fragment)
		assert fragment["state"] == "1236"
		assert fragment["access_token"] == ATV
		assert fragment["token_type"]=="bearer"
		assert fragment["expires_in"] == "7200"

		// test override AND replace with fragment
		redirect = AuthorizationController.makeCallback(
				[redirect_uri: "https://rs.dev.auckland.ac.nz/callback/auth.html?access_token=$ATV&expires_in=7200&state=1236&token_type=bearer"],
				request, clientInfo, model,
				"http://rs1.rs.dev.auckland.ac.nz/oauth2/callback.html")
		uri = new URI(redirect.substring(9))
		assert uri.host == "rs1.rs.dev.auckland.ac.nz"
		assert uri.path == "/oauth2/callback.html"
		assert uri.fragment
		assert !uri.query
		fragment = KongContract.splitQuery(uri.fragment)
		assert fragment["state"] == "1236"
		assert fragment["access_token"] == ATV
		assert fragment["token_type"]=="bearer"
		assert fragment["expires_in"] == "7200"

		redirect = AuthorizationController.makeCallback(
				[redirect_uri: "https://rs.dev.auckland.ac.nz?access_token=$ATV&expires_in=7200&state=1236&token_type=bearer"],
				request, clientInfo, model,
				"http://rs1.rs.dev.auckland.ac.nz/oauth2/callback.html")
		uri = new URI(redirect.substring(9))
		assert uri.host == "rs1.rs.dev.auckland.ac.nz"
		assert uri.path == "/oauth2/callback.html"
		assert uri.fragment
		assert !uri.query
		fragment = KongContract.splitQuery(uri.fragment)
		assert fragment["state"] == "1236"
		assert fragment["access_token"] == ATV
		assert fragment["token_type"]=="bearer"
		assert fragment["expires_in"] == "7200"

		redirect = AuthorizationController.makeCallback(
				[redirect_uri: "https://rs.dev.auckland.ac.nz/callback/auth.html?access_token=$ATV&expires_in=7200&state=1236&token_type=bearer"],
				request, clientInfo, model,
				"http://rs1.rs.dev.auckland.ac.nz")
		uri = new URI(redirect.substring(9))
		assert uri.host == "rs1.rs.dev.auckland.ac.nz"
		assert (uri.path == "/" || !uri.path)
		assert uri.fragment
		assert !uri.query
		fragment = KongContract.splitQuery(uri.fragment)
		assert fragment["state"] == "1236"
		assert fragment["access_token"] == ATV
		assert fragment["token_type"]=="bearer"
		assert fragment["expires_in"] == "7200"
	}
}
