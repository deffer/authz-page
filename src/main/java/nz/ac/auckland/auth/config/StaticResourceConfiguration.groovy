package nz.ac.auckland.auth.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter

@Configuration
@PropertySource(value = ['file:${HOME}/.webdev/as.properties', 'file:${HOMEPATH}/.webdev/as.properties'], ignoreResourceNotFound = true)
public class StaticResourceConfiguration extends WebMvcConfigurerAdapter {
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		if (!registry.hasMappingForPattern("/webjars/**")) {
			registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
		}
		registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/");
	}
}