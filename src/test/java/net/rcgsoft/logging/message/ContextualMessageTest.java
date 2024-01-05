package net.rcgsoft.logging.message;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

public class ContextualMessageTest {
	private static final String MESSAGE_PATTERN = "Test Message with Parameter {}";
	private static final Object[] ARGUMENTS = new Object[] { Integer.valueOf(1) };
	private static final Exception EXCEPTION = new Exception("Test");
	private ContextualMessage ctxMsg;

	@Before
	public void setUp() throws Exception {
		ctxMsg = new ContextualMessage(MESSAGE_PATTERN, ARGUMENTS.clone(), EXCEPTION);
	}

	@Test
	public void testContextualMessageStringObjectArrayThrowable() {
		ContextualMessage cm = new ContextualMessage(MESSAGE_PATTERN, ARGUMENTS.clone(), EXCEPTION);
		assertEquals(MESSAGE_PATTERN, cm.getFormat());
		assertArrayEquals(ARGUMENTS, cm.getParameters());
		assertEquals(EXCEPTION, cm.getThrowable());
		cm = new ContextualMessage(null, ARGUMENTS.clone(), EXCEPTION);
		assertNull(cm.getFormat());
		assertArrayEquals(ARGUMENTS, cm.getParameters());
		assertEquals(EXCEPTION, cm.getThrowable());
		cm = new ContextualMessage(MESSAGE_PATTERN, null, EXCEPTION);
		assertEquals(MESSAGE_PATTERN, cm.getFormat());
		assertNull(cm.getParameters());
		assertEquals(EXCEPTION, cm.getThrowable());
		cm = new ContextualMessage(MESSAGE_PATTERN, ARGUMENTS.clone(), null);
		assertEquals(MESSAGE_PATTERN, cm.getFormat());
		assertArrayEquals(ARGUMENTS, cm.getParameters());
		assertNull(cm.getThrowable());
	}

	@Test
	public void testContextualMessageStringObjectArray() {
		ContextualMessage cm = new ContextualMessage(MESSAGE_PATTERN, ARGUMENTS.clone());
		assertEquals(MESSAGE_PATTERN, cm.getFormat());
		assertArrayEquals(ARGUMENTS, cm.getParameters());
		assertNull(cm.getThrowable());
		cm = new ContextualMessage(null, ARGUMENTS.clone());
		assertNull(cm.getFormat());
		assertArrayEquals(ARGUMENTS, cm.getParameters());
		assertNull(cm.getThrowable());
		cm = new ContextualMessage(MESSAGE_PATTERN, (Object[]) null);
		assertEquals(MESSAGE_PATTERN, cm.getFormat());
		assertNull(cm.getParameters());
		assertNull(cm.getThrowable());
	}

	@Test
	public void testContextualMessageStringObject() {
		ContextualMessage cm = new ContextualMessage(MESSAGE_PATTERN, ARGUMENTS[0]);
		assertEquals(MESSAGE_PATTERN, cm.getFormat());
		assertArrayEquals(new Object[] { ARGUMENTS[0] }, cm.getParameters());
		assertNull(cm.getThrowable());
		cm = new ContextualMessage(null, ARGUMENTS[0]);
		assertNull(cm.getFormat());
		assertArrayEquals(new Object[] { ARGUMENTS[0] }, cm.getParameters());
		assertNull(cm.getThrowable());
		cm = new ContextualMessage(MESSAGE_PATTERN, (Object) null);
		assertEquals(MESSAGE_PATTERN, cm.getFormat());
		assertArrayEquals(new Object[] { null }, cm.getParameters());
		assertNull(cm.getThrowable());
	}

	@Test
	public void testContextualMessageStringObjectObject() {
		String msg = "Test Message 2 Parameters {} {}";
		Integer arg1 = Integer.valueOf(1);
		Integer arg2 = Integer.valueOf(2);
		ContextualMessage cm = new ContextualMessage(msg, arg1, arg2);
		assertEquals(msg, cm.getFormat());
		assertArrayEquals(new Object[] { arg1, arg2 }, cm.getParameters());
		assertNull(cm.getThrowable());
		cm = new ContextualMessage(null, arg1, arg2);
		assertNull(cm.getFormat());
		assertArrayEquals(new Object[] { arg1, arg2 }, cm.getParameters());
		assertNull(cm.getThrowable());
		cm = new ContextualMessage(msg, null, arg2);
		assertEquals(msg, cm.getFormat());
		assertArrayEquals(new Object[] { null, arg2 }, cm.getParameters());
		assertNull(cm.getThrowable());
		cm = new ContextualMessage(msg, arg1, null);
		assertEquals(msg, cm.getFormat());
		assertArrayEquals(new Object[] { arg1, null }, cm.getParameters());
		assertNull(cm.getThrowable());
	}

	@Test
	public void testGetContext() {
		Map<String, Object> ctx = ctxMsg.getContext();
		assertNotNull(ctx);
		assertTrue(ctx.isEmpty());
		// Modification of returned map should not be allowed
		assertThrows(UnsupportedOperationException.class, () -> ctx.put("Key", "Value"));
	}

	@Test
	public void testWithContext() {
		// Check that adding information to the context works
		Map<String, Object> ctx = new HashMap<>();
		ctx.put("IntKey", Integer.valueOf(1));
		ctx.put("StrKey", "TestStr");
		ctx.put("ObjKey", new Object());
		assertTrue(ctxMsg.getContext().isEmpty());
		ctxMsg.withContext(ctx);
		assertFalse(ctxMsg.getContext().isEmpty());
		assertEquals(3, ctxMsg.getContext().size());
		assertNotSame(ctx, ctxMsg.getContext());
	}

	@Test
	public void testGetTags() {
		List<String> tags = ctxMsg.getTags();
		assertNotNull(tags);
		assertTrue(tags.isEmpty());
		// Modification of returned list should not be allowed
		assertThrows(UnsupportedOperationException.class, () -> tags.add("Value"));
	}

	@Test
	public void testAddTag() {
		String tag = "TestTag";
		ctxMsg.addTag(tag);
		assertTrue(ctxMsg.getTags().contains(tag));
	}

	@Test
	public void testAddTags() {
		List<String> tags = Arrays.asList("Tag1", "Tag2", "Tag3");
		ctxMsg.addTags(tags);
		Set<String> expectedTags = new HashSet<>(tags);
		Set<String> actualTags = new HashSet<>(ctxMsg.getTags());
		actualTags.retainAll(tags);
		assertEquals(expectedTags, actualTags);
	}
}