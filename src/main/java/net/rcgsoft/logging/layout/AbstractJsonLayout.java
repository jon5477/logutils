/*********************************************************************
* Copyright (c) 2025 Jon Huang and David Xu
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/

package net.rcgsoft.logging.layout;

import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.netty.handler.codec.http.HttpHeaderValues;

/**
 * Abstract class for all JSON Layouts.
 * 
 * @author David Xu
 * @author Jon Huang
 *
 */
public abstract class AbstractJsonLayout extends AbstractStringLayout {
	protected static final Map<Level, Integer> BUNYAN_LEVEL = new HashMap<>();
	private static final Collector<String, ArrayNode, ArrayNode> ARRAY_NODE_COLLECTOR = Collector.of(
			JsonNodeFactory.instance::arrayNode, ArrayNode::add, ArrayNode::addAll,
			Collector.Characteristics.IDENTITY_FINISH);

	static {
		BUNYAN_LEVEL.put(Level.FATAL, 60);
		BUNYAN_LEVEL.put(Level.ERROR, 50);
		BUNYAN_LEVEL.put(Level.WARN, 40);
		BUNYAN_LEVEL.put(Level.INFO, 30);
		BUNYAN_LEVEL.put(Level.DEBUG, 20);
		BUNYAN_LEVEL.put(Level.TRACE, 10);
	}

	protected static ArrayNode listToJsonStringArray(@NonNull List<String> strs) {
		return strs.stream().collect(ARRAY_NODE_COLLECTOR);
	}

	protected static String formatAsIsoUTCDateTime(long timestamp) {
		Instant instant = Instant.ofEpochMilli(timestamp);
		return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
	}

	protected AbstractJsonLayout(@Nullable Charset charset) {
		super(charset);
	}

	protected abstract ObjectNode formatJson(@NonNull LogEvent event);

	protected final String format(@NonNull LogEvent event) {
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
	public final byte[] toByteArray(@NonNull LogEvent event) {
		return getBytes(format(event));
	}

	@Override
	public final String toSerializable(@NonNull LogEvent event) {
		return format(event);
	}
}