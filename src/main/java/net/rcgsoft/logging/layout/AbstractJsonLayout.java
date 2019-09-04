package net.rcgsoft.logging.layout;

import java.nio.charset.Charset;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;

public abstract class AbstractJsonLayout extends AbstractStringLayout {
	protected AbstractJsonLayout(Charset charset) {
		super(charset);
	}

	protected abstract String format(LogEvent event);

	@Override
	public final String getContentType() {
		return "application/json";
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