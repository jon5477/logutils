package net.rcgsoft.logging.logstash;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.message.Message;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import net.rcgsoft.logging.layout.AbstractJsonLayout;
import net.rcgsoft.logging.message.ContextualMessage;
import net.rcgsoft.logging.util.SerializationUtil;

/**
 * A Log4j2 Layout which prints events in Logstash JSON format. The layout takes
 * no options and requires no additional configuration.
 */
@Plugin(name = "LogstashLayout", category = "Core", elementType = "layout", printObject = true)
public class LogstashLayout extends AbstractJsonLayout {
	@PluginFactory
	public static LogstashLayout createLayout(
			@PluginAttribute(value = "charset", defaultString = "UTF-8") Charset charset) {
		return new LogstashLayout(charset);
	}

	protected LogstashLayout(Charset charset) {
		super(charset);
	}

	/**
	 * Format the event as a Logstash style JSON object.
	 */
	@Override
	protected final JsonObject formatJson(LogEvent event) {
		JsonObject jsonEvent = new JsonObject();
		Message msg = event.getMessage();
		if (msg instanceof ContextualMessage) {
			Map<String, Object> context = ((ContextualMessage) msg).getContext();
			if (!context.isEmpty()) {
				for (Map.Entry<String, Object> entry : context.entrySet()) {
					jsonEvent.add(entry.getKey(), SerializationUtil.toJsonObject(entry.getValue()));
				}
			}
			JsonArray tags = listToJsonStringArray(((ContextualMessage) msg).getTags());
			tags.add("bunyan");
			jsonEvent.add("tags", tags);
		}
		jsonEvent.add("v", 0);
		jsonEvent.add("level", BUNYAN_LEVEL.get(event.getLevel()));
		jsonEvent.add("name", event.getLoggerName());
		try {
			jsonEvent.add("hostname", InetAddress.getLocalHost().getHostName());
		} catch (UnknownHostException e) {
			jsonEvent.add("hostname", "unknown");
		}
		jsonEvent.add("pid", event.getThreadId());
		jsonEvent.add("@timestamp", formatAsIsoUTCDateTime(event.getTimeMillis()));
		jsonEvent.add("message", msg.getFormattedMessage());
		jsonEvent.add("source", event.getSource().getClassName());
		if (event.getLevel().isMoreSpecificThan(Level.WARN) && event.getThrown() != null) {
			JsonObject jsonError = new JsonObject();
			Throwable e = event.getThrown();
			jsonError.add("message", e.getMessage());
			jsonError.add("name", e.getClass().getSimpleName());
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			jsonError.add("stack", sw.toString());
			jsonEvent.add("err", jsonError);
		}
		return jsonEvent;
	}
}