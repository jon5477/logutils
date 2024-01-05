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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.netty.handler.codec.http.HttpHeaderValues;

/**
 * 
 * @author Jon Huang
 *
 */
@Plugin(name = "SlackLayout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE, printObject = true)
public class SlackLayout extends AbstractStringLayout {
	private static final String TITLE = "title";
	private static final String VALUE = "value";
	private static final String SHORT = "short";
	private static final boolean USE_BRIGHT_COLORS = true;
	private int fieldSizeLimit = 0;

	protected SlackLayout(int fieldSizeLimit) {
		super(StandardCharsets.UTF_8);
		this.fieldSizeLimit = fieldSizeLimit;
	}

	@PluginFactory
	public static SlackLayout createLayout(int payloadSizeLimit) {
		return new SlackLayout(payloadSizeLimit);
	}

	private static final String getColorByLevel(Level level) {
		if (level == Level.FATAL || level == Level.ERROR) {
			return USE_BRIGHT_COLORS ? "#FF0000" : "#800000";
		} else if (level == Level.WARN) {
			return USE_BRIGHT_COLORS ? "#FFFF00" : "#808000";
		} else if (level == Level.INFO) {
			return USE_BRIGHT_COLORS ? "#00FF00" : "#008000";
		} else if (level == Level.DEBUG) {
			return USE_BRIGHT_COLORS ? "#00FFFF" : "#008080";
		} else if (level == Level.TRACE) {
			return USE_BRIGHT_COLORS ? "#808080" : "#000000";
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
		ObjectNode payload = JsonNodeFactory.instance.objectNode();
		ArrayNode attachments = JsonNodeFactory.instance.arrayNode();
		ObjectNode attachment = JsonNodeFactory.instance.objectNode();
		// Add Thread name and Class location
		StringBuilder text = new StringBuilder();
		text.append('[').append(event.getThreadName()).append(']');
		if (event.getSource() != null) {
			text.append(' ').append(event.getSource().getClassName());
		}
		attachment.put("text", text.toString());
		// Add Color
		attachment.put("color", getColorByLevel(event.getLevel())); // Based on level
		// Exception fields
		ArrayNode fields = JsonNodeFactory.instance.arrayNode();
		// Add Level Field
		ObjectNode levelField = JsonNodeFactory.instance.objectNode();
		levelField.put(TITLE, "Level");
		levelField.put(VALUE, event.getLevel().toString());
		levelField.put(SHORT, false);
		fields.add(levelField);
		// Add Message Field
		ObjectNode msgField = JsonNodeFactory.instance.objectNode();
		msgField.put(TITLE, "Message");
		String fmtMsg = event.getMessage().getFormattedMessage();
		if (this.fieldSizeLimit > 0) {
			int endIdx = Math.min(fmtMsg.length(), this.fieldSizeLimit);
			msgField.put(VALUE, fmtMsg.substring(0, endIdx));
		} else {
			msgField.put(VALUE, fmtMsg);
		}
		msgField.put(SHORT, false);
		fields.add(msgField);
		// Check if Throwable is proxied
		ThrowableProxy tp = event.getThrownProxy();
		if (tp != null) {
			// Add Stack Trace Field
			ObjectNode stField = JsonNodeFactory.instance.objectNode();
			stField.put(TITLE, "Exception");
			String stackTraceStr = tp.getCauseStackTraceAsString("");
			if (this.fieldSizeLimit > 0) {
				int endIdx = Math.min(stackTraceStr.length(), this.fieldSizeLimit);
				stField.put(VALUE, stackTraceStr.substring(0, endIdx));
			} else {
				stField.put(VALUE, stackTraceStr);
			}
			stField.put(SHORT, false);
			fields.add(stField);
		}
		// Add fields
		attachment.set("fields", fields);
		// Add timestamp
		attachment.put("ts", event.getTimeMillis() / 1000);
		// Add attachments
		attachments.add(attachment);
		// Create the payload
		payload.set("attachments", attachments);
		return payload.toString();
	}
}