package nz.ac.auckland.auth.endpoints;

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
public class AuthorizationController {

    @RequestMapping("/auth")
    public String authForm(@RequestParam(value="name", required=false, defaultValue="Irina") String name, Model model) {
        ThymeleafDontUnderstandMap map = new ThymeleafDontUnderstandMap();
        Map<String, String> scopes = new HashMap<>();
        scopes.put("person-read", "Allows application to read person information on your behalf.");
        scopes.put("person-write", "Allows application to update person information on your behalf. Your current role-based authorization will apply.");
        model.addAttribute("name", name);
        model.addAttribute("map", map);
        model.addAttribute("scopes", scopes);

        String appName = "unknown";
        // call Kong http://localhost:8001/oauth2?client_id=irina_oauth2_pluto
        RestTemplate restTemplate = new RestTemplate();
        //ClientInfo clientInfo = restTemplate.getForObject("http://localhost:8001/oauth2?client_id=irina_oauth2_pluto", ClientInfo.class);
        Map clientInfo = restTemplate.getForObject("http://localhost:8001/oauth2?client_id=irina_oauth2_pluto", Map.class);
        if (clientInfo.get("data")!=null && clientInfo.get("data") instanceof List){
            List elements = (List)clientInfo.get("data");
            if (elements.size()>0)
                appName = (String)  ((Map)elements.get(0)).get("name");
        }
        model.addAttribute("appname", appName);
        return "auth";
    }

    @RequestMapping(value="/auth/submit", method= RequestMethod.POST)
    public String authSubmit(@ModelAttribute ThymeleafDontUnderstandMap map, Model model) {
        // https://spring.io/guides/gs/handling-form-submission/
        model.addAttribute("textA", map.getProperties().get("textA"));
        return "temp";
    }
}
