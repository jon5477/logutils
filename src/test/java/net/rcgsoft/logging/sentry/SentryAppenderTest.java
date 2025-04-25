package net.rcgsoft.logging.sentry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.sentry.IScopes;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import net.rcgsoft.logging.message.ContextualMessage;

class SentryAppenderTest {
	private SentryAppender app;

	@BeforeEach
	void setUp() {
		IScopes scopes = Mockito.mock(IScopes.class);
		this.app = new SentryAppender(scopes);
	}

	@AfterEach
	void tearDown() {
		this.app = null;
	}

	@Test
	void testCreateSentryEvent() {
		long timeMillis = 1663715618107L;
		Log4jLogEvent.Builder b = Log4jLogEvent.newBuilder();
		ContextualMessage msg = new ContextualMessage(
				"Parameterized log message running inside a unit test. timeMillis: {} time: {}", timeMillis,
				Instant.ofEpochMilli(timeMillis)).withContext(Collections.singletonMap("key", "value"));
		b.setMessage(msg);
		b.setTimeMillis(timeMillis);
		b.setThrownProxy(new ThrowableProxy(new Exception("Test")));
		SentryEvent se = this.app.createSentryEvent(b.build());
		assertNotNull(se);
		// Verify the contents of the sentry event
		Message sMsg = se.getMessage();
		assertNotNull(sMsg.getMessage());
		assertNotNull(sMsg.getFormatted());
		assertFalse(sMsg.getParams().isEmpty());
		// Check if the context provided is set on the sentry event
		@SuppressWarnings("unchecked")
		Map<String, Object> contextMsg = (Map<String, Object>) se.getContexts().get("context_message");
		assertEquals("value", contextMsg.get("key"));
	}
}