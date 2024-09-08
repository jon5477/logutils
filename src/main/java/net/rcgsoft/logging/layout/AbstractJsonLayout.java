package net.rcgsoft.logging.layout;

import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.netty.handler.codec.http.HttpHeaderValues;

/**
 * Abstract class for all JSON Layouts.
 * 
 * @author David Xu
 *
 */
public abstract class AbstractJsonLayout extends AbstractStringLayout {
	protected static final Map<Level, Integer> BUNYAN_LEVEL = new HashMap<>();

	static {
		BUNYAN_LEVEL.put(Level.FATAL, 60);
		BUNYAN_LEVEL.put(Level.ERROR, 50);
		BUNYAN_LEVEL.put(Level.WARN, 40);
		BUNYAN_LEVEL.put(Level.INFO, 30);
		BUNYAN_LEVEL.put(Level.DEBUG, 20);
		BUNYAN_LEVEL.put(Level.TRACE, 10);
	}

	public static final ArrayNode listToJsonStringArray(List<String> strs) {
		int listSize = strs.size();
		ArrayNode arr = JsonNodeFactory.instance.arrayNode(listSize);
		for (String str : strs) {
			arr.add(str);
		}
		return arr;
	}

	public static final String formatAsIsoUTCDateTime(long timeStamp) {
		final Instant instant = Instant.ofEpochMilli(timeStamp);
		return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
	}

	protected AbstractJsonLayout(Charset charset) {
		super(charset);
	}

	protected abstract ObjectNode formatJson(LogEvent event);

	protected final String format(LogEvent event) {
		StringBuilder sb = new StringBuilder();
		sb.append(this.formatJson(event));
		sb.append('\n');
		return sb.toString();
	}

	@Override
	public final String getContentType() {
		return HttpHeaderValues.APPLICATION_JSON.toString();
	}

	@Override
	public final byte[] toByteArray(LogEvent event) {
		return format(event).getBytes();
	}

	@Override
	public final String toSerializable(LogEvent event) {
		return format(event);
	}
}