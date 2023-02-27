package net.rcgsoft.logging.sentry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collections;
import java.util.Map;

import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import io.sentry.IHub;
import io.sentry.SentryEvent;
import net.rcgsoft.logging.message.ContextualMessage;

public class SentryAppenderTest {
	private SentryAppender app;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		IHub hub = Mockito.mock(IHub.class);
		this.app = new SentryAppender(hub);
	}

	@After
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