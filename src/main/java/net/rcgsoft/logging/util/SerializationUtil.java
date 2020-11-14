package net.rcgsoft.logging.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Serialization utility.
 * 
 * @author Jon Huang
 *
 */
public final class SerializationUtil {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private SerializationUtil() {
		// static utility class
	}

	static {
		OBJECT_MAPPER.registerModule(new JavaTimeModule());
	}

	public static final ObjectMapper getObjectMapper() {
		return OBJECT_MAPPER;
	}
}