/*********************************************************************
* Copyright (c) 2024 Jon Huang
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/

package net.rcgsoft.logging.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON Serialization utility. Provides the {@link ObjectMapper} instance.
 * 
 * @author Jon Huang
 *
 */
public final class SerializationUtil {
	private static final ObjectMapper OBJECT_MAPPER;

	static {
		JsonMapper.Builder b = JsonMapper.builder();
		// Add JSR-310 module
		b.addModule(new JavaTimeModule());
		// Detect is GSON is on the classpath
		try {
			Class.forName("com.google.gson.JsonElement");
			// Add GSON compatibility module
			b.addModule(new GsonInteropModule());
		} catch (ClassNotFoundException e) {
			// no need to handle, GSON doesn't exist on CP
		}
		b.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		OBJECT_MAPPER = b.build();
	}

	private SerializationUtil() {
		// static utility class
	}

	public static ObjectMapper getObjectMapper() {
		return OBJECT_MAPPER;
	}
}