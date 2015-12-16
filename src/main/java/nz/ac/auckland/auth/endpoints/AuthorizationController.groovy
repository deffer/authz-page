package nz.ac.auckland.auth.endpoints

import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import nz.ac.auckland.auth.formdata.ApiInfo;
import nz.ac.auckland.auth.formdata.AuthRequest
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.client.RestTemplate

@Controller
// to do:  add csrf protection to prevent user unknowingly submitting approval by following specially crafted link
// do NOT enable CORS on any of these method
public class AuthorizationController {

    // to do take from properties
    private String kongUrl = "http://localhost:8001";

    @RequestMapping("/{api_id}/auth")
    public String authForm(@PathVariable("api_id") String apiId, AuthRequest authRequest, Model model) {
        // to do save auth request in session.
        Map<String, String> scopes = new HashMap<>();
        scopes.put("person-read", "Allows application to read person information on your behalf.");
        scopes.put("person-write", "Allows application to update person information on your behalf. Your current role-based authorization will apply.");
        model.addAttribute("name", "user");
        model.addAttribute("scopes", scopes); // to do scopes description

        // extract data from parameters and store in session(to do) also pass in hidden fields
        authRequest.client_id = sanitize(authRequest.client_id) ?: "irina_oauth2_pluto";
        authRequest.response_type = sanitize(authRequest.response_type) ?: "code";
        authRequest.user_id = "";
        model.addAttribute("map", authRequest);

        // find out application name
        // call Kong http://localhost:8001/oauth2?client_id=irina_oauth2_pluto
        String appName = "unknown"; // todo if app not found, show error page
        RestTemplate restTemplate = new RestTemplate();
        Map clientInfo = restTemplate.getForObject(kongUrl+"/oauth2?client_id="+authRequest.client_id, Map.class);
        if (clientInfo["data"]!=null && clientInfo["data"] instanceof List && clientInfo["data"].size()>0)
            appName = (String) clientInfo["data"][0]["name"]; // ((Map)elements.get(0)).get("name");

        // todo also remember

        model.addAttribute("appname", appName);
        model.addAttribute("apiid", apiId);

        return "auth";
    }

    // never trust any request that didnt come from trusted services
    private String sanitize(String input){
        // implement
        return input;
    }

    // https://spring.io/guides/gs/handling-form-submission/
    @RequestMapping(value="/{api_id}/auth/submit", method= RequestMethod.POST)
    public String authSubmit(@PathVariable("api_id") String apiId, @ModelAttribute AuthRequest authRequest, Model model) {
        if (authRequest.actionDeny)
            return authDeny(authRequest);

        // we could call this method to find out unique api id first, however api name works just fine, so skip
        // ApiInfo apiInfo = new RestTemplate().getForObject(kongUrl+"/apis/"+apiId, ApiInfo.class);

        // fetch provision_key for given api
        Map pluginInfo = new RestTemplate().getForObject(kongUrl+"/apis/"+apiId+"/plugins", Map.class);
        String provisionKey = null;
        pluginInfo.data.each {plugin ->
            if (plugin.name== "oauth2")
                provisionKey = plugin.config.provision_key;
        }

        // todo if api (provision key) not found, show error page
        String authenticatedUserId = authRequest.user_id; // authenticated user from session
        String clientId = authRequest.client_id; // or take from authRequest (stored in session)
        String responseType = authRequest.response_type; // take from authRequest (stored in session)
        authRequest.provision_key = provisionKey;

        // now we need to inform Kong that user authenticatedUserId grants authorization to application cliend_id

        /*$ curl https://your.api.com/oauth2/authorize \
        --header "Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW" \
        --data "client_id=XXX" \
        --data "response_type=XXX" \
        --data "scope=XXX" \
        --data "provision_key=XXX" \
        --data "authenticated_userid=XXX"*/
        model.addAttribute("user_id", authenticatedUserId);
        model.addAttribute("map", authRequest);
        return "temp";
    }

    public String authDeny(AuthRequest authRequest) {
        String clientCallbackUrl = "unknown"; // todo if app not found, show error page
        RestTemplate restTemplate = new RestTemplate();
        Map clientInfo = restTemplate.getForObject(kongUrl+"/oauth2?client_id="+authRequest.client_id, Map.class);
        if (clientInfo["data"]!=null && clientInfo["data"] instanceof List && clientInfo["data"].size()>0)
            clientCallbackUrl = (String) clientInfo["data"][0]["redirect_uri"];

        // todo append parameters to redirect url in a smart way (assuming there could be other parameters already)
        clientCallbackUrl += "/?error=access_denied&error_description=The+user+denied+access+to+your+application";

        return "redirect:"+clientCallbackUrl;
    }
}
