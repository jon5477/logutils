package net.rcgsoft.logging.util;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.gson.JsonElement;

import net.rcgsoft.logging.util.ser.GsonJsonElementSerializer;

/**
 * GSON interoperability module to bring serialization capabilities of GSON
 * objects to Jackson.
 * 
 * @author Jon Huang
 *
 */
public class GsonInteropModule extends SimpleModule {
	private static final long serialVersionUID = 1L;

	public GsonInteropModule() {
		super("gson-interop");
		addSerializer(JsonElement.class, new GsonJsonElementSerializer());
	}
}