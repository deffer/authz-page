package nz.ac.auckland.auth.endpoints;

import nz.ac.auckland.auth.ThymeleafDontUnderstandMap;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;

@Controller
public class AuthorizationController {

    @RequestMapping("/auth")
    public String authForm(@RequestParam(value="name", required=false, defaultValue="Irina") String name, Model model) {
        ThymeleafDontUnderstandMap map = new ThymeleafDontUnderstandMap();
        model.addAttribute("name", name);
        model.addAttribute("map", map);
        return "auth";
    }

    @RequestMapping(value="/auth/submit", method= RequestMethod.POST)
    public String authSubmit(@ModelAttribute ThymeleafDontUnderstandMap map, Model model) {
        // https://spring.io/guides/gs/handling-form-submission/
        model.addAttribute("scopeA", map.getProperties().get("scopeA"));
        return "temp";
    }
}
