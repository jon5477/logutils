package net.rcgsoft.logging.message;

import org.apache.logging.log4j.message.ParameterizedMessage;

import java.util.*;

/**
 * A {@link ParameterizedMessage} with context information.
 * 
 * @author Jon Huang
 *
 */
public class ContextualMessage extends ParameterizedMessage {
	private static final long serialVersionUID = 1116169611431210422L;
	private final Map<String, Object> context = new HashMap<>();
	private final List<String> tags = new ArrayList<>();

	public ContextualMessage(final String messagePattern, final Object[] arguments, final Throwable throwable) {
		super(messagePattern, arguments, throwable);
	}

	public ContextualMessage(final String messagePattern, final Object... arguments) {
		super(messagePattern, arguments);
	}

	public ContextualMessage(final String messagePattern, final Object arg) {
		super(messagePattern, arg);
	}

	public ContextualMessage(final String messagePattern, final Object arg0, final Object arg1) {
		super(messagePattern, arg0, arg1);
	}

	public Map<String, Object> getContext() {
		return Collections.unmodifiableMap(context);
	}

	public ContextualMessage withContext(Map<String, Object> context) {
		Objects.requireNonNull(context, "context cannot be null");
		this.context.putAll(context);
		return this;
	}

	public List<String> getTags() {
		return Collections.unmodifiableList(this.tags);
	}

	public ContextualMessage addTag(String tag) {
		this.tags.add(tag);
		return this;
	}

	public ContextualMessage addTags(Collection<String> tags) {
		this.tags.addAll(tags);
		return this;
	}
}