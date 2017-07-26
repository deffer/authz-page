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