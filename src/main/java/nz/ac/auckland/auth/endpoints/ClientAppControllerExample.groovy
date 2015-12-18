package nz.ac.auckland.auth.endpoints

import nz.ac.auckland.auth.formdata.AuthRequest
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.RestTemplate

@Controller
class ClientAppControllerExample {
	// to do take from properties
	private String kongProxyUrl = "https://rs.dev.auckland.ac.nz/";
	private String personApiPath = "/person/api/person/me"

	@RequestMapping("/lily")
	public String authForm(Model model) {

		model.addAttribute("somefield", "somevalue");

		return "lily";
	}
}
