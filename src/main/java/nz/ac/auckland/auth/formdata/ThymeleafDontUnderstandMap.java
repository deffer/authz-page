package nz.ac.auckland.auth.formdata;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by deffer on 14/12/2015.
 */
public class ThymeleafDontUnderstandMap {
	private Map<String,String> properties = new HashMap<>();

	public Map<String,String> getProperties() {
		return properties;
	}

	public void setProperties(Map<String,String> properties) {
		this.properties = properties;
	}
}
