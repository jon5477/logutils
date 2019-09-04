package net.rcgsoft.logging.logstash;

import java.nio.charset.Charset;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;

import net.rcgsoft.logging.layout.AbstractJsonLayout;

@Plugin(name = "LogstashLayout", category = "Core", elementType = "layout", printObject = true)
public class LogstashLayout extends AbstractJsonLayout {
	protected LogstashLayout(Charset charset) {
		super(charset);
	}

	@Override
	protected String format(LogEvent event) {
		// TODO Auto-generated method stub
		return null;
	}
}