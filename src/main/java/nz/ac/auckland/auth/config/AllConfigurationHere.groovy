package nz.ac.auckland.auth.config

import nz.ac.auckland.auth.endpoints.AuthorizationController
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler

import javax.annotation.PostConstruct
import java.util.jar.Attributes
import java.util.jar.Manifest

@Configuration
@PropertySource(value = ['file:${HOME}/.webdev/as.properties', 'file:${HOMEPATH}/.webdev/as.properties'], ignoreResourceNotFound = true)
public class AllConfigurationHere extends WebMvcConfigurerAdapter {

	public static String revision = "unknown"

	private static final Logger logger = LoggerFactory.getLogger(AllConfigurationHere.class);

	@PostConstruct
	public void init(){
		// code below assumes that at least one of the 2 ways to read revision from manifest will work
		// (no idea which one is correct, but they both work under different circumstances)
		String manifestURL = locateManifest()
		String revision2 = AuthorizationController.class.getPackage().getImplementationVersion()

		if (manifestURL) {
			try {
				Manifest manifest = new Manifest(new URL(manifestURL).openStream());
				Attributes attributes = manifest.getMainAttributes();
				revision = attributes.getValue("Implementation-Version");

				if (!revision)
					revision = "unknown"
				logger.info("Implementation-Version is $revision")
			} catch (IOException ex) {
				logger.error(ex.getMessage());
			}

			if (revision2 && revision2 != revision){
				if (revision != "unknown")
					revision = revision+"($revision2)"
				else
					revision = revision2
			}
		}else{
			logger.warn("Unable to locate manifest - no jar")
			revision = revision2
		}


	}

	public String locateManifest(){
		//InputStream is = this.getClass().getClassLoader().getResourceAsStream("META-INF/MANIFEST.MF"); // does not work
		String className = this.getClass().getSimpleName() + ".class";
		String classPath = this.getClass().getResource(className).toString();
		if (!classPath.startsWith("jar")) {
			// Class not from JAR
			return null;
		}
		String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF";
		return manifestPath
	}


	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		if (!registry.hasMappingForPattern("/webjars/**")) {
			registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
		}
		registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/");
		//registry.addResourceHandler("/favicon.ico").addResourceLocations("classpath:/static/images/");
		//registry.addRedirectViewController("/ui/", "/ui/swagger-ui.html");
	}

	// none of below works, leaving it here for reference
	@Bean
	public SimpleUrlHandlerMapping myFaviconHandlerMapping()
	{
		SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
		mapping.setOrder(Integer.MIN_VALUE);
		mapping.setUrlMap(Collections.singletonMap("/favicon.ico",  myFaviconRequestHandler()));
		return mapping;
	}

	@Autowired
	ApplicationContext applicationContext;

	@Bean
	protected ResourceHttpRequestHandler myFaviconRequestHandler()
	{
		ResourceHttpRequestHandler requestHandler =  new ResourceHttpRequestHandler();
		requestHandler.setLocations(Arrays.asList(applicationContext.getResource("static/images/")));
		requestHandler.setCacheSeconds(0);
		return requestHandler;
	}


}