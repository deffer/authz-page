package nz.ac.auckland.auth.endpoints

import groovyx.net.http.ContentType
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
    private String kongAdminUrl = "http://localhost:8001";
    private String kongProxyUrl = "https://rs.dev.auckland.ac.nz/";

    @RequestMapping("/{api_id}/auth")
    public String authForm(@PathVariable("api_id") String apiId, AuthRequest authRequest, Model model) {
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
        Map clientInfo = restTemplate.getForObject(kongAdminUrl+"/oauth2?client_id="+authRequest.client_id, Map.class);
        if (clientInfo["data"]!=null && clientInfo["data"] instanceof List && clientInfo["data"].size()>0)
            appName = (String) clientInfo["data"][0]["name"]; // ((Map)elements.get(0)).get("name");

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
    @RequestMapping(value="/{api_id}/auth/submit", method= RequestMethod.POST) // always use POST
    public String authSubmit(@PathVariable("api_id") String apiId, AuthRequest authRequest, Model model) {
        if (authRequest.actionDeny)
            return authDeny(authRequest);

        // fetch api url and oauth2 provision key for given api
        String provisionKey = null;
        RestTemplate rest = new RestTemplate();
        ApiInfo apiInfo = rest.getForObject(kongAdminUrl+"/apis/"+apiId, ApiInfo.class);
        Map pluginInfo = rest.getForObject(kongAdminUrl+"/apis/"+apiId+"/plugins", Map.class);
        pluginInfo.data.each {plugin ->
            if (plugin.name== "oauth2")
                provisionKey = plugin.config.provision_key;
        }

        // todo if api (or provision key) not found, show error page
        String authenticatedUserId = authRequest.user_id; // todo take authenticated user from session and save into authRequest
        authRequest.provision_key = provisionKey;
        authRequest.submitTo = apiInfo.request_path+"/oauth2/authorize"

        // now we need to inform Kong that user authenticatedUserId grants authorization to application cliend_id
        // todo if result is not response_uri, then show error page
        authRequest.kongResponse = submitAuthorization(authRequest);

        if (authRequest.actionDebug || !(authRequest.kongResponse)) {
            model.addAttribute("user_id", authenticatedUserId);
            model.addAttribute("map", authRequest);
            return "temp";
        }else
            return "redirect:"+authRequest.kongResponse
    }

    private String submitAuthorization(AuthRequest authRequest){
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
        def http = new HTTPBuilder(kongProxyUrl+authRequest.submitTo)
        http.request(Method.POST, ContentType.JSON) {
            requestContentType = ContentType.URLENC
            body = [client_id: authRequest.client_id, response_type: authRequest.response_type,
                    scope: authRequest.scope,provision_key: authRequest.provision_key, authenticated_userid:authRequest.user_id ]
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
            response.failure = {resp, reader ->
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
        Map clientInfo = restTemplate.getForObject(kongAdminUrl+"/oauth2?client_id="+authRequest.client_id, Map.class);
        if (clientInfo["data"]!=null && clientInfo["data"] instanceof List && clientInfo["data"].size()>0)
            clientCallbackUrl = (String) clientInfo["data"][0]["redirect_uri"];

        // todo append parameters to redirect url in a smart way (assuming there could be other parameters already)
        clientCallbackUrl += "/?error=access_denied&error_description=The+user+denied+access+to+your+application";

        return "redirect:"+clientCallbackUrl;
    }
}
