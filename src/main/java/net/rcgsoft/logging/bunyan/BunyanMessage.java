package net.rcgsoft.logging.bunyan;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.message.ParameterizedMessage;

public class BunyanMessage extends ParameterizedMessage {
	private static final long serialVersionUID = 1116169611431210422L;
	private final Map<String, Object> context = new HashMap<>();

	public BunyanMessage(final String messagePattern, final Object[] arguments, final Throwable throwable) {
		super(messagePattern, arguments, throwable);
	}

	public BunyanMessage(final String messagePattern, final Object... arguments) {
		super(messagePattern, arguments);
	}

	public BunyanMessage(final String messagePattern, final Object arg) {
		super(messagePattern, arg);
	}

	public BunyanMessage(final String messagePattern, final Object arg0, final Object arg1) {
		super(messagePattern, arg0, arg1);
	}

	public Map<String, Object> getContext() {
		return Collections.unmodifiableMap(context);
	}

	public BunyanMessage withContext(Map<String, Object> context) {
		Objects.requireNonNull(context, "context cannot be null");
		this.context.putAll(context);
		return this;
	}
}