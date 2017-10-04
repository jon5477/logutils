package net.rcgsoft.logging;

import java.nio.charset.Charset;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class SlackPayloadFormat extends AbstractStringLayout {
	private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	protected SlackPayloadFormat(Configuration config, Charset aCharset, Serializer headerSerializer,
			Serializer footerSerializer) {
		super(config, aCharset, headerSerializer, footerSerializer);
		// TODO Auto-generated constructor stub
	}

	@Override
	public String toSerializable(LogEvent event) {
		System.out.println("Fqcn: " + event.getLoggerFqcn());
		JsonObject obj = new JsonObject();
		obj.addProperty("level", event.getLevel().toString());
		obj.addProperty("message", event.getMessage().getFormattedMessage());
		Throwable t = event.getThrown();
		if (t != null) {
			StringBuilder sb = new StringBuilder();
//			event.getContextStack().forEach(e -> sb.append('\n').append(e));
//			obj.addProperty("stackTrace", sb.substring(1));
		}
		obj.addProperty("ts", event.getTimeMillis());
		return gson.toJson(obj);
	}
}