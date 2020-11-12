package net.rcgsoft.logging.util;

import java.lang.reflect.Field;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

/**
 * Serialization utility for serializing POJOs into JSON objects.
 * 
 * @author Jon Huang
 *
 */
public class SerializationUtil {
	private SerializationUtil() {
		// static utility class
	}

	public static final JsonObject toJsonObject(Object obj) {
		JsonObject jobj = new JsonObject();
		Field[] fields = obj.getClass().getDeclaredFields();
		for (Field field : fields) {
			String name = field.getName();
			Class<?> type = field.getType();
			try {
				Object value = field.get(obj);
				if (type == Boolean.class || type == Boolean.TYPE) {
					jobj.add(name, ((Boolean) value).booleanValue());
				} else if (type == Double.class || type == Double.TYPE) {
					jobj.add(name, ((Double) value).doubleValue());
				} else if (type == Float.class || type == Float.TYPE) {
					jobj.add(name, ((Float) value).floatValue());
				} else if (type == Byte.class || type == Byte.TYPE || type == Short.class || type == Short.TYPE
						|| type == Integer.class || type == Integer.TYPE) {
					jobj.add(name, ((Number) value).intValue());
				} else if (type == Long.class || type == Long.TYPE) {
					jobj.add(name, ((Long) value).longValue());
				} else if (type == Character.class || type == Character.TYPE) {
					jobj.add(name, ((Character) value).toString());
				} else if (type == String.class) {
					jobj.add(name, (String) value);
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