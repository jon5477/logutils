/*********************************************************************
* Copyright (c) 2024 Jon Huang
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/

package net.rcgsoft.logging.util.ser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Jackson {@link JsonSerializer} for GSON's implementation of JSON values that
 * inherit from {@link JsonElement}.
 * 
 * @author Jon Huang
 *
 */
public final class GsonJsonElementSerializer extends JsonSerializer<JsonElement> {
	@Override
	public final void serialize(JsonElement value, JsonGenerator gen, SerializerProvider serializers)
			throws IOException {
		serializeJsonElement(gen, value);
	}

	private void serializeJsonElement(JsonGenerator gen, JsonElement elem) throws IOException {
		Objects.requireNonNull(gen, "json generator cannot be null");
		Objects.requireNonNull(elem, "json element cannot be null");
		if (elem.isJsonNull()) {
			gen.writeNull();
		} else if (elem.isJsonPrimitive()) {
			JsonPrimitive jp = elem.getAsJsonPrimitive();
			if (jp.isBoolean()) {
				gen.writeBoolean(jp.getAsBoolean());
			} else if (jp.isNumber()) {
				Number num = jp.getAsNumber();
				if (num instanceof Short s) {
					gen.writeNumber(s.shortValue());
				} else if (num instanceof Integer i) {
					gen.writeNumber(i.intValue());
				} else if (num instanceof Long l) {
					gen.writeNumber(l.longValue());
				} else if (num instanceof Float f) {
					gen.writeNumber(f.floatValue());
				} else if (num instanceof Double d) {
					gen.writeNumber(d.doubleValue());
				} else if (num instanceof BigDecimal bd) {
					gen.writeNumber(bd);
				} else if (num instanceof BigInteger bi) {
					gen.writeNumber(bi);
				} else {
					gen.writeNumber(num.toString());
				}
			} else if (jp.isString()) {
				gen.writeString(jp.getAsString());
			} else {
				throw new UnsupportedOperationException("Unhandled JsonPrimitive: " + elem.getClass().getSimpleName());
			}
		} else if (elem.isJsonArray()) {
			JsonArray arr = elem.getAsJsonArray();
			gen.writeStartArray();
			for (JsonElement arrElem : arr) {
				serializeJsonElement(gen, arrElem);
			}
			gen.writeEndArray();
		} else if (elem.isJsonObject()) {
			JsonObject obj = elem.getAsJsonObject();
			gen.writeStartObject();
			for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
				gen.writeFieldName(e.getKey());
				serializeJsonElement(gen, e.getValue());
			}
			gen.writeEndObject();
		} else {
			throw new UnsupportedOperationException("Unhandled JsonElement: " + elem.getClass().getSimpleName());
		}
	}
}