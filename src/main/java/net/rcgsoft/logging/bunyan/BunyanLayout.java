package net.rcgsoft.logging.bunyan;

import com.google.gson.JsonObject;
import net.rcgsoft.logging.layout.AbstractJsonLayout;
import net.rcgsoft.logging.message.ContextualMessage;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.message.Message;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * A Log4j2 Layout which prints events in Node Bunyan JSON format. The layout
 * takes no options and requires no additional configuration.
 */
@Plugin(name = "BunyanLayout", category = "Core", elementType = "layout", printObject = true)
public class BunyanLayout extends AbstractJsonLayout {

	@PluginFactory
	public static BunyanLayout createLayout(@PluginAttribute("locationInfo") boolean locationInfo,
			@PluginAttribute("properties") boolean properties, @PluginAttribute("complete") boolean complete,
			@PluginAttribute(value = "charset", defaultString = "UTF-8") Charset charset) {
		return new BunyanLayout(charset);
	}

	protected BunyanLayout(Charset charset) {
		super(charset);
	}

	/**
	 * Format the event as a Bunyan style JSON object.
	 */
	@Override
	protected final JsonObject formatJson(LogEvent event) {
		JsonObject jsonEvent = new JsonObject();
		Message msg = event.getMessage();
		if (msg instanceof BunyanMessage) {
			Map<String, Object> context = ((BunyanMessage) msg).getContext();
			if (!context.isEmpty()) {
				context.forEach((k, v) -> jsonEvent.add(k, GSON.toJsonTree(v)));
			}
		}
		if (msg instanceof ContextualMessage) {
			Map<String, Object> context = ((ContextualMessage) msg).getContext();
			if (!context.isEmpty()) {
				context.forEach((k, v) -> jsonEvent.add(k, GSON.toJsonTree(v)));
			}

			jsonEvent.add("tags", listToJsonStringArray(((ContextualMessage) msg).getTags()));
		}
		jsonEvent.addProperty("v", 0);
		jsonEvent.addProperty("level", BUNYAN_LEVEL.get(event.getLevel()));
		jsonEvent.addProperty("levelStr", event.getLevel().toString());
		jsonEvent.addProperty("name", event.getLoggerName());
		try {
			jsonEvent.addProperty("hostname", InetAddress.getLocalHost().getHostName());
		} catch (UnknownHostException e) {
			jsonEvent.addProperty("hostname", "unknown");
		}
		jsonEvent.addProperty("pid", event.getThreadId());
		jsonEvent.addProperty("time", formatAsIsoUTCDateTime(event.getTimeMillis()));
		jsonEvent.addProperty("msg", msg.getFormattedMessage());
		jsonEvent.addProperty("src", event.getSource().getClassName());
		if (event.getLevel().isMoreSpecificThan(Level.WARN) && event.getThrown() != null) {
			JsonObject jsonError = new JsonObject();
			Throwable e = event.getThrown();
			jsonError.addProperty("message", e.getMessage());
			jsonError.addProperty("name", e.getClass().getSimpleName());
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			jsonError.addProperty("stack", sw.toString());
			jsonEvent.add("err", jsonError);
		}
		return jsonEvent;
	}
}