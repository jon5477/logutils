package net.rcgsoft.logging;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.core.layout.PatternLayout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@Plugin(name = "SlackLayout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE, printObject = true)
public class SlackLayout extends AbstractStringLayout {
	private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private static final String DEFAULT_FOOTER = "]";
	private static final String DEFAULT_HEADER = "[";
	private static final String CONTENT_TYPE = "application/json";
	private static final boolean useBrightColors = true;

	protected SlackLayout(Configuration config, Charset aCharset) {
		super(config,
				aCharset,
				PatternLayout.newSerializerBuilder().setConfiguration(config).setPattern(DEFAULT_HEADER).setDefaultPattern(DEFAULT_HEADER).build(),
				PatternLayout.newSerializerBuilder().setConfiguration(config).setPattern(DEFAULT_FOOTER).setDefaultPattern(DEFAULT_FOOTER).build());
	}

	@PluginFactory
	public static SlackLayout createLayout() {
		return new SlackLayout(new DefaultConfiguration(), StandardCharsets.UTF_8);
	}

	private static final String getColorByLevel(Level level) {
		if (level == Level.FATAL || level == Level.ERROR) {
			return useBrightColors ? "#FF0000" : "#800000";
		} else if (level == Level.WARN) {
			return useBrightColors ? "#FFFF00" : "#808000";
		} else if (level == Level.INFO) {
			return useBrightColors ? "#00FF00" : "#008000";
		} else if (level == Level.DEBUG) {
			return useBrightColors ? "#00FFFF" : "#008080";
		} else if (level == Level.TRACE) {
			return useBrightColors ? "#808080" : "#000000";
		}
		return null; // give no color by default
	}

	/**
	 * @return The content type.
	 */
	@Override
	public final String getContentType() {
		return CONTENT_TYPE + "; charset=" + this.getCharset();
	}

	@Override
	public final String toSerializable(LogEvent event) {
		JsonObject payload = new JsonObject();
		JsonArray attachments = new JsonArray();
		JsonObject attachment = new JsonObject();
		attachment.addProperty("color", getColorByLevel(event.getLevel())); // Based on level
		JsonArray fields = new JsonArray();

		// Add Level Field
		JsonObject levelField = new JsonObject();
		levelField.addProperty("title", "Level");
		levelField.addProperty("value", event.getLevel().toString());
		levelField.addProperty("short", false);
		fields.add(levelField);
		
		// Add Message Field
		JsonObject msgField = new JsonObject();
		msgField.addProperty("title", "Message");
		msgField.addProperty("value", event.getMessage().getFormattedMessage());
		msgField.addProperty("short", false);
		fields.add(msgField);
		
		Throwable t = event.getThrown();
		if (t != null && t.getStackTrace().length > 0) {
			StackTraceElement[] trace = t.getStackTrace();
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < trace.length; i++) {
				sb.append('\n').append(trace[i].toString());
			}
			
			// Add Stack Trace Field
			JsonObject stField = new JsonObject();
			stField.addProperty("title", "Stack Trace");
			stField.addProperty("value", sb.substring(1));
			stField.addProperty("short", false);
			fields.add(stField);
		}
		
		// Add fields
		attachment.add("fields", fields);
		// Add timestamp
		attachment.addProperty("ts", (event.getTimeMillis() / 1000));
		// Add attachments
		attachments.add(attachment);
		// Create the payload
		payload.add("attachments", attachments);
		return gson.toJson(payload);
	}
}