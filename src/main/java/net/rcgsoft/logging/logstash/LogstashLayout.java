/*********************************************************************
* Copyright (c) 2025 Jon Huang and David Xu
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.rcgsoft.logging.layout.AbstractJsonLayout;
import net.rcgsoft.logging.message.ContextualMessage;
import net.rcgsoft.logging.util.SerializationUtil;

/**
 * A Log4j2 Layout which prints events in Logstash JSON format. The layout takes
 * no options and requires no additional configuration.
 * 
 * @author David Xu
 *
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
	protected final ObjectNode formatJson(LogEvent event) {
		ObjectNode jsonEvent = JsonNodeFactory.instance.objectNode();
		Message msg = event.getMessage();
		if (msg instanceof ContextualMessage ctxMsg) {
			ObjectMapper objMapper = SerializationUtil.getObjectMapper();
			Map<String, Object> context = ctxMsg.getContext();
			if (!context.isEmpty()) {
				for (Map.Entry<String, Object> entry : context.entrySet()) {
					jsonEvent.set(entry.getKey(), objMapper.valueToTree(entry.getValue()));
				}
			}
			ArrayNode tags = listToJsonStringArray(ctxMsg.getTags());
			tags.add("bunyan");
			jsonEvent.set("tags", tags);
		}
		jsonEvent.put("v", 0);
		jsonEvent.put("level", BUNYAN_LEVEL.get(event.getLevel()));
		jsonEvent.put("name", event.getLoggerName());
		try {
			jsonEvent.put("hostname", InetAddress.getLocalHost().getHostName());
		} catch (UnknownHostException e) {
			jsonEvent.put("hostname", "unknown");
		}
		jsonEvent.put("pid", event.getThreadId());
		jsonEvent.put("@timestamp", formatAsIsoUTCDateTime(event.getTimeMillis()));
		jsonEvent.put("message", msg.getFormattedMessage());
		jsonEvent.put("source", event.getSource().getClassName());
		if (event.getLevel().isMoreSpecificThan(Level.WARN) && event.getThrown() != null) {
			ObjectNode jsonError = JsonNodeFactory.instance.objectNode();
			Throwable e = event.getThrown();
			jsonError.put("message", e.getMessage());
			jsonError.put("name", e.getClass().getSimpleName());
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			jsonError.put("stack", sw.toString());
			jsonEvent.set("err", jsonError);
		}
		return jsonEvent;
	}
}