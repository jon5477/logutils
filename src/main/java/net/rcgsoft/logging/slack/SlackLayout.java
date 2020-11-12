package net.rcgsoft.logging.slack;

import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import io.netty.handler.codec.http.HttpHeaderValues;

/**
 * 
 * @author Jon Huang
 *
 */
@Plugin(name = "SlackLayout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE, printObject = true)
public class SlackLayout extends AbstractStringLayout {
//	private static final Gson gson = new GsonBuilder().create();
	private static final boolean useBrightColors = true;

	protected SlackLayout() {
		super(StandardCharsets.UTF_8);
	}

	@PluginFactory
	public static SlackLayout createLayout() {
		return new SlackLayout();
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
		return HttpHeaderValues.APPLICATION_JSON + "; charset=" + this.getCharset();
	}

	@Override
	public final String toSerializable(LogEvent event) {
		JsonObject payload = new JsonObject();
		JsonArray attachments = new JsonArray();
		JsonObject attachment = new JsonObject();
		// Add Thread name and Class location
		StringBuilder text = new StringBuilder();
		text.append('[').append(event.getThreadName()).append(']');
		if (event.getSource() != null) {
			text.append(' ').append(event.getSource().getClassName());
		}
		attachment.add("text", text.toString());
		// Add Color
		attachment.add("color", getColorByLevel(event.getLevel())); // Based on level
		// Exception fields
		JsonArray fields = new JsonArray();
		// Add Level Field
		JsonObject levelField = new JsonObject();
		levelField.add("title", "Level");
		levelField.add("value", event.getLevel().toString());
		levelField.add("short", false);
		fields.add(levelField);
		// Add Message Field
		JsonObject msgField = new JsonObject();
		msgField.add("title", "Message");
		msgField.add("value", event.getMessage().getFormattedMessage());
		msgField.add("short", false);
		fields.add(msgField);
		// Check if Throwable is proxied
		ThrowableProxy tp = event.getThrownProxy();
		if (tp != null) {
			// Add Stack Trace Field
			JsonObject stField = new JsonObject();
			stField.add("title", "Exception");
			stField.add("value", tp.getCauseStackTraceAsString(""));
			stField.add("short", false);
			fields.add(stField);
		}
		// Add fields
		attachment.add("fields", fields);
		// Add timestamp
		attachment.add("ts", event.getTimeMillis() / 1000);
		// Add attachments
		attachments.add(attachment);
		// Create the payload
		payload.add("attachments", attachments);
		return payload.toString();
	}
}