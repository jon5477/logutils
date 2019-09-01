package net.rcgsoft.logging.bunyan;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.message.Message;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * A Log4j2 Layout which prints events in Node Bunyan JSON format. The layout
 * takes no options and requires no additional configuration.
 */
@Plugin(name = "BunyanLayout", category = "Core", elementType = "layout", printObject = true)
public class BunyanLayout extends AbstractStringLayout {
	private static final Map<Level, Integer> BUNYAN_LEVEL;
	private static final Gson GSON = new GsonBuilder().create();

	static {
		BUNYAN_LEVEL = new HashMap<>();
		BUNYAN_LEVEL.put(Level.FATAL, 60);
		BUNYAN_LEVEL.put(Level.ERROR, 50);
		BUNYAN_LEVEL.put(Level.WARN, 40);
		BUNYAN_LEVEL.put(Level.INFO, 30);
		BUNYAN_LEVEL.put(Level.DEBUG, 20);
		BUNYAN_LEVEL.put(Level.TRACE, 10);
	}

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
	private String format(LogEvent event) {
		JsonObject jsonEvent = new JsonObject();
		Message msg = event.getMessage();
		if (msg instanceof BunyanMessage) {
			Map<String, Object> context = ((BunyanMessage) msg).getContext();
			if (!context.isEmpty()) {
				context.forEach((k, v) -> jsonEvent.add(k, GSON.toJsonTree(v)));
			}
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
		return GSON.toJson(jsonEvent) + "\n";
	}

	private static String formatAsIsoUTCDateTime(long timeStamp) {
		final Instant instant = Instant.ofEpochMilli(timeStamp);
		return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
	}

	/**
	 * This Layout renders JSON objects, hence we use application/json. This is in a
	 * strict sense untrue, since the entire stream is not proper JSON.
	 */
	@Override
	public String getContentType() {
		return "application/json";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] toByteArray(LogEvent event) {
		return format(event).getBytes();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toSerializable(LogEvent event) {
		return format(event);
	}
}