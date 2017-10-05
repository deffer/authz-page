package nz.ac.auckland.auth.endpoints

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

class JsonHelper {
	static ObjectMapper mapper; // mapper is thread-safe

	static {
		JsonFactory factory = new JsonFactory();
		mapper = new ObjectMapper(factory);
		mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		mapper.disable(MapperFeature.AUTO_DETECT_IS_GETTERS);
		mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
	}

	public static String serialize(Object obj) {
		try {
			return mapper.writeValueAsString(obj);
		} catch (Exception var2) {
			throw new Exception(var2);
		}
	}

	public static <T> T deserialize(String text, Class<T> type) {
		try {
			return mapper.readerFor(type).readValue(text);
		} catch (Exception var3) {
			throw new Exception(var3);
		}
	}

	public static <T> T convert(Object from, Class<T> type){
		return mapper.convertValue(from, type)
	}

}
