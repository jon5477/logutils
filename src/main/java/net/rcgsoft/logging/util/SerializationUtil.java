package net.rcgsoft.logging.util;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

/**
 * Serialization utility for serializing POJOs into JSON objects.
 * 
 * @author Jon Huang
 *
 */
public final class SerializationUtil {
	private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private SerializationUtil() {
		// static utility class
	}

	public static final JsonValue toJsonValue(Class<?> type, Object value) {
		if (value == null) {
			return Json.NULL;
		}
		if (type == Boolean.class || type == Boolean.TYPE) {
			return Json.value(((Boolean) value).booleanValue());
		} else if (type == Double.class || type == Double.TYPE) {
			return Json.value(((Double) value).doubleValue());
		} else if (type == Float.class || type == Float.TYPE) {
			return Json.value(((Float) value).floatValue());
		} else if (type == Byte.class || type == Byte.TYPE || type == Short.class || type == Short.TYPE
				|| type == Integer.class || type == Integer.TYPE) {
			return Json.value(((Number) value).intValue());
		} else if (type == Long.class || type == Long.TYPE) {
			return Json.value(((Long) value).longValue());
		} else if (type == Character.class || type == Character.TYPE) {
			return Json.value(((Character) value).toString());
		} else if (type == String.class) {
			return Json.value((String) value);
		} else if (value instanceof Calendar) {
			Calendar cal = ((Calendar) value);
			ZonedDateTime zdt = ZonedDateTime.ofInstant(cal.toInstant(), cal.getTimeZone().toZoneId());
			return Json.value(ISO_DATE_FORMAT.format(zdt));
		} else if (value instanceof TemporalAccessor) {
			return Json.value(ISO_DATE_FORMAT.format((TemporalAccessor) value));
		}
		return null;
	}

	public static final JsonObject toJsonObject(Object obj) {
		JsonObject jobj = new JsonObject();
		Field[] fields = obj.getClass().getDeclaredFields();
		for (Field field : fields) {
			String name = field.getName();
			Class<?> type = field.getType();
			try {
				Object value = field.get(obj);
				// Attempt to convert the value to a JSON value
				JsonValue jsonVal = toJsonValue(type, value);
				if (jsonVal != null) {
					jobj.add(name, jsonVal);
				} else {
					jobj.add(name, value != null ? toJsonObject(value) : Json.NULL);
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				// failed to serialize field
			}
		}
		return jobj;
	}
}