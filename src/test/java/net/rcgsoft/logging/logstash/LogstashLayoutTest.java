package net.rcgsoft.logging.logstash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonObject;

import net.rcgsoft.logging.message.ContextualMessage;

public class LogstashLayoutTest {
	@Test
	public void testFormatJson() {
		LogstashLayout layout = LogstashLayout.createLayout(StandardCharsets.UTF_8);
		Map<String, Object> logMeta = new HashMap<>();
		logMeta.put("clientId", "1");
		logMeta.put("ip", null);
		logMeta.put("stok", "cddae6178d39c46e3f35");
		logMeta.put("parent_request_id", "16e71eb47a06e3236ed0");
		logMeta.put("ua", null);
		logMeta.put("user_id", "1");
		logMeta.put("subdomain", "test");
		logMeta.put("request_id", "fa9976c4628bef1be731");
		logMeta.put("email", "user@example.com");
		Map<String, Object> serviceMap = new HashMap<>();
		serviceMap.put("name", "junit");
		serviceMap.put("service", "unittest");
		serviceMap.put("action", "test");
		JsonObject params = new JsonObject();
		params.addProperty("returnContextID", true);
		serviceMap.put("params", params); // Gson's JSON Object
		Map<String, Object> serviceMeta = new HashMap<>();
		serviceMeta.put("service", serviceMap);
		serviceMeta.put("environment", "local");
		ContextualMessage msg = new ContextualMessage("unit_test").withContext(logMeta).withContext(serviceMeta)
				.withContext(Collections.singletonMap("scope", "unittest"));
		Log4jLogEvent.Builder evt = new Log4jLogEvent.Builder();
		evt.setMessage(msg);
		evt.setLevel(Level.DEBUG);
		evt.setLoggerFqcn("org.apache.logging.log4j.spi.AbstractLogger");
		evt.setLoggerName("net.rcgsoft.logging.logstash.LogstashLayoutTestLogger");
		evt.setThreadId(0);
		evt.setTimeMillis(1663715618107L);
		evt.setSource(new StackTraceElement(LogstashLayoutTest.class.getSimpleName(), "testFormatJson", null, -1));
		evt.setThrown(null);
		ObjectNode jsObj = layout.formatJson(evt.build());
		assertNotNull(jsObj);
		// Verify the JSON object
		assertEquals("1", jsObj.get("clientId").asText());
		assertTrue(jsObj.get("ip").isNull());
		assertEquals("cddae6178d39c46e3f35", jsObj.get("stok").asText());
		assertEquals("16e71eb47a06e3236ed0", jsObj.get("parent_request_id").asText());
		assertTrue(jsObj.get("ua").isNull());
		assertEquals("1", jsObj.get("user_id").asText());
		assertEquals("test", jsObj.get("subdomain").asText());
		assertEquals("fa9976c4628bef1be731", jsObj.get("request_id").asText());
		assertEquals("user@example.com", jsObj.get("email").asText());
		// Verify the service node
		ObjectNode svc = (ObjectNode) jsObj.get("service");
		assertNotNull(svc);
		assertEquals("junit", svc.get("name").asText());
		assertEquals("unittest", svc.get("service").asText());
		assertEquals("test", svc.get("action").asText());
		assertTrue(svc.get("params").get("returnContextID").asBoolean());
		// Back to verifying the rest of the fields
		assertEquals("local", jsObj.get("environment").asText());
		assertEquals("bunyan", jsObj.get("tags").get(0).asText());
		assertEquals(0, jsObj.get("v").asInt());
		assertEquals(20, jsObj.get("level").asInt()); // level for DEBUG
		assertEquals("net.rcgsoft.logging.logstash.LogstashLayoutTestLogger", jsObj.get("name").asText());
		assertFalse(jsObj.get("hostname").isNull());
		assertEquals(1, jsObj.get("pid").asInt());
		assertEquals("2022-09-20T23:13:38.107Z", jsObj.get("@timestamp").asText());
		assertEquals("unit_test", jsObj.get("message").asText());
		assertEquals(LogstashLayoutTest.class.getSimpleName(), jsObj.get("source").asText());
	}
}