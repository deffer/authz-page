package nz.ac.auckland.auth.endpoints;

import nz.ac.auckland.auth.formdata.AuthRequest;
import nz.ac.auckland.auth.formdata.ClientInfo;
import nz.ac.auckland.auth.formdata.ThymeleafDontUnderstandMap;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Controller
// to do:  add csrf protection to prevent user unknowingly submitting approval by following specially crafted link
// do NOT enable CORS on any of these method
public class AuthorizationController {

    // to do take from properties
    private String kongUrl = "http://localhost:8001";

    @RequestMapping("/auth")
    public String authForm(AuthRequest authRequest, Model model) {
        // to do save auth request in session.
        Map<String, String> scopes = new HashMap<>();
        scopes.put("person-read", "Allows application to read person information on your behalf.");
        scopes.put("person-write", "Allows application to update person information on your behalf. Your current role-based authorization will apply.");
        model.addAttribute("name", "user");
        model.addAttribute("scopes", scopes); // to do scopes description

        // extract data from parameters
        String clientId = authRequest.getClient_id();
        if (clientId == null || clientId.equals(""))
            clientId = "irina_oauth2_pluto";
        else
            clientId = sanitize(clientId);

        String responseType = authRequest.getResponse_type();
        if (responseType == null || responseType.equals(""))
            responseType = "code";
        else
            responseType = sanitize(responseType);

        // parameters to hidden fields
        ThymeleafDontUnderstandMap map = new ThymeleafDontUnderstandMap();
        map.getProperties().put("client_id", clientId);
        map.getProperties().put("response_type", responseType);
        model.addAttribute("map", map);

        // find aout application names
        String appName = "unknown";
        // call Kong http://localhost:8001/oauth2?client_id=irina_oauth2_pluto
        RestTemplate restTemplate = new RestTemplate();
        Map clientInfo = restTemplate.getForObject(kongUrl+"/oauth2?client_id="+clientId, Map.class);
        if (clientInfo.get("data")!=null && clientInfo.get("data") instanceof List){
            List elements = (List)clientInfo.get("data");
            if (elements.size()>0)
                appName = (String)  ((Map)elements.get(0)).get("name");
        }
        model.addAttribute("appname", appName);

        return "auth";
    }

    // never trust any request that didnt come from trusted services
    private String sanitize(String input){
        // implement
        return input;
    }

    @RequestMapping(value="/auth/submit", method= RequestMethod.POST)
    public String authSubmit(@ModelAttribute ThymeleafDontUnderstandMap map, Model model) {
        // to do fetch authRequest from session and ensure that submitted values match

        // https://spring.io/guides/gs/handling-form-submission/
        String authenticatedUserId = map.getProperties().get("textA"); // authenticated user from session

        // here we need to inform Kong that user authenticatedUserId grants authorization to application cliend_id


        /*$ curl https://your.api.com/oauth2/authorize \
        --header "Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW" \
        --data "client_id=XXX" \
        --data "response_type=XXX" \
        --data "scope=XXX" \
        --data "provision_key=XXX" \
        --data "authenticated_userid=XXX"*/
        model.addAttribute("textA", authenticatedUserId);
        return "temp";
    }
}
