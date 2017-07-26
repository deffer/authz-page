package nz.ac.auckland.auth.config

import nz.ac.auckland.auth.endpoints.AuthorizationController
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

import javax.annotation.PostConstruct
import javax.servlet.*
import javax.servlet.http.HttpServletResponse
import java.util.jar.Attributes
import java.util.jar.Manifest

@Service
public class CustomAPIFilter implements Filter {


	private static final Logger logger = LoggerFactory.getLogger(CustomAPIFilter.class)

	String revision = "unknown"

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
	public void doFilter(ServletRequest request, ServletResponse response,
	                     FilterChain chain) throws IOException, ServletException {
		HttpServletResponse res = (HttpServletResponse) response;
		res.addHeader("X-API-Implementation-Version", revision);
		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}
}