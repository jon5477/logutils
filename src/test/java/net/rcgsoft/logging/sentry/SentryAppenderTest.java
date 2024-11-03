package net.rcgsoft.logging.sentry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collections;
import java.util.Map;

import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.sentry.IHub;
import io.sentry.SentryEvent;
import net.rcgsoft.logging.message.ContextualMessage;

public class SentryAppenderTest {
	private SentryAppender app;

	@BeforeAll
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterAll
	public static void tearDownAfterClass() throws Exception {
	}

	@BeforeEach
	public void setUp() throws Exception {
		IHub hub = Mockito.mock(IHub.class);
		this.app = new SentryAppender(hub);
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testCreateSentryEvent() {
		Log4jLogEvent.Builder b = Log4jLogEvent.newBuilder();
		ContextualMessage msg = new ContextualMessage("Unit test")
				.withContext(Collections.singletonMap("key", "value"));
		b.setMessage(msg);
		b.setTimeMillis(1663715618107L);
		b.setThrownProxy(new ThrowableProxy(new Exception("Test")));
		SentryEvent se = this.app.createSentryEvent(b.build());
		assertNotNull(se);
		// Check if the context provided is set on the sentry event
		@SuppressWarnings("unchecked")
		Map<String, Object> contextMsg = (Map<String, Object>) se.getContexts().get("context_message");
		assertEquals("value", contextMsg.get("key"));
	}
}