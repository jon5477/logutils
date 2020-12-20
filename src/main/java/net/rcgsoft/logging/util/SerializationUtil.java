package net.rcgsoft.logging.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON Serialization utility. Provides the {@code ObjectMapper} instance.
 * 
 * @author Jon Huang
 *
 */
public final class SerializationUtil {
	private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().addModule(new JavaTimeModule())
			.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false).build();

	private SerializationUtil() {
		// static utility class
	}

	public static final ObjectMapper getObjectMapper() {
		return OBJECT_MAPPER;
	}
}