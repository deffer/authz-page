package nz.ac.auckland.auth.endpoints

import nz.ac.auckland.auth.contract.ClientInfo
import nz.ac.auckland.auth.contract.KongContract
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping

@Controller
// do NOT enable CORS on any of these method
class DeveloperController {

	private static final Logger logger = LoggerFactory.getLogger(DeveloperController.class);

	@Value('${as.log.verbose}')
	private boolean verboseLogs = false

	@Value('${as.development}')
	private boolean development = false

	@Autowired
	KongContract kong

	@RequestMapping("/developer")
	public String authForm(@RequestHeader(value = "REMOTE_USER", defaultValue = "user") String userId,
	                       @RequestHeader(value = "displayName", defaultValue = "NULL") String userName, Model model) {

		String displayName = userName != "NULL"? userName :  "Unknown (${userId})"
		model.addAttribute("name", displayName);

		if ((!userId || userId == "NULL") && development)
			userId = "user"


		prepareView(userId, model)
		model.addAttribute("canRegister", (userId ==~ /[a-zA-Z]{1,4}\d{3}$/))
		return "developer"
	}

	private void prepareView(String userId, Model model) {
		String consumerId = kong.getDeveloperConsumerId(userId)
		List<ClientInfo> allApplications =  kong.getDeveloperApplications(consumerId)

		List result = allApplications.collect { ClientInfo clientInfo ->
			def displayApp = JsonHelper.convert(clientInfo, HashMap.class)
			long issued = clientInfo.created_at
			displayApp.issuedStr = new Date(issued).format("yyyy-MM-dd")
			displayApp.issuedHint = new Date(issued).format("HH:mm:ss")

			displayApp.callbacks = clientInfo.displayRedirects()
			return displayApp
		}

		/*if (result.isEmpty()){
			// temporary add few rows
			result.add([id: "bd26948c-c2af-4984-bc59-3a049d84d406", issuedStr: "2017-10-23", issuedHint: "12:01:02",
				callbacks: "https://api.test.auckland.ac.nz/explore/employment-v1-docs/ https://localhost:8080/callback",
				clientId: "myapp-application-iben883", name: "MyApp application"
			])
			result.add([id: "ad26948c-c2af-4984-bc59-3a049d84d406", issuedStr: "2017-10-23", issuedHint: "12:01:02",
			            callbacks: "https://api.test.auckland.ac.nz/explore/employment-v1-docs/ https://localhost:8080/callback",
			            clientId: "mygroup-application-iben883", name: "MyGroup application"
			])
		}*/
		model.addAttribute("apps", result)
	}

}
