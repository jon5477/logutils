package net.rcgsoft.logging.sentry;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.environment.SentryEnvironment;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;
import io.sentry.event.interfaces.MessageInterface;
import io.sentry.event.interfaces.StackTraceInterface;
import net.rcgsoft.logging.message.ContextualMessage;

/**
 * Appender for log4j2 in charge of sending the logged events to a Sentry
 * server.
 * 
 * @author David Xu
 *
 */
@Plugin(name = "Sentry", category = "Core", elementType = "appender", printObject = true)
public class SentryAppender extends AbstractAppender {
	/**
	 * Default name for the appender.
	 */
	public static final String APPENDER_NAME = "sentry";
	public static final String LOG4J_NDC = "log4j2-NDC";
	public static final String LOG4J_MARKER = "log4j2-Marker";
	public static final String THREAD_NAME = "Sentry-Threadname";

	/**
	 * Creates an instance of SentryAppender.
	 */
	public SentryAppender() {
		this(APPENDER_NAME, null);
	}

	/**
	 * Creates an instance of SentryAppender.
	 *
	 * @param name   The Appender name.
	 * @param filter The Filter to associate with the Appender.
	 */
	protected SentryAppender(String name, Filter filter) {
		super(name, filter, null, true, Property.EMPTY_ARRAY);
		this.addFilter(new DropSentryFilter());
	}

	/**
	 * Create a Sentry Appender.
	 *
	 * @param name   The name of the Appender.
	 * @param filter The filter, if any, to use.
	 * @return The SentryAppender.
	 */
	@PluginFactory
	public static SentryAppender createAppender(@PluginAttribute("name") final String name,
			@PluginElement("filter") final Filter filter) {
		if (name == null) {
			LOGGER.error("No name provided for SentryAppender");
			return null;
		}
		return new SentryAppender(name, filter);
	}

	/**
	 * Transforms a {@link Level} into an {@link Event.Level}.
	 *
	 * @param level original level as defined in log4j2.
	 * @return log level used within sentry.
	 */
	protected static Event.Level formatLevel(Level level) {
		if (level.isMoreSpecificThan(Level.FATAL)) {
			return Event.Level.FATAL;
		} else if (level.isMoreSpecificThan(Level.ERROR)) {
			return Event.Level.ERROR;
		} else if (level.isMoreSpecificThan(Level.WARN)) {
			return Event.Level.WARNING;
		} else if (level.isMoreSpecificThan(Level.INFO)) {
			return Event.Level.INFO;
		} else {
			return Event.Level.DEBUG;
		}
	}

	/**
	 * Extracts message parameters into a List of Strings.
	 * <p>
	 * null parameters are kept as null.
	 *
	 * @param parameters parameters provided to the logging system.
	 * @return the parameters formatted as Strings in a List.
	 */
	protected static List<String> formatMessageParameters(Object[] parameters) {
		List<String> stringParameters = new ArrayList<>(parameters.length);
		for (Object parameter : parameters) {
			stringParameters.add((parameter != null) ? parameter.toString() : null);
		}
		return stringParameters;
	}

	@Override
	public void append(LogEvent logEvent) {
		// Do not log the event if the current thread is managed by sentry
		if (SentryEnvironment.isManagingThread()) {
			return;
		}
		SentryEnvironment.startManagingThread();
		try {
			EventBuilder eventBuilder = createEventBuilder(logEvent);
			Sentry.captureEvent(event)
			Sentry.capture(eventBuilder);
		} catch (Exception e) {
			error("An exception occurred while creating a new event in Sentry", logEvent, e);
		} finally {
			SentryEnvironment.stopManagingThread();
		}
	}

	/**
	 * Creates a {@code SentryEvent} based on a {@code LogEvent}.
	 *
	 * @param event The log event to map.
	 * @return a {@code SentryEvent} instance containing details provided by the
	 *         logging system.
	 */
	protected SentryEvent createSentryEvent(LogEvent event) {
		Message eventMessage = event.getMessage();
		SentryEvent event = new SentryEvent();
		EventBuilder eventBuilder = new EventBuilder().withSdkIntegration("log4j2")
				.withTimestamp(new Date(event.getTimeMillis())).withMessage(eventMessage.getFormattedMessage())
				.withLogger(event.getLoggerName()).withLevel(formatLevel(event.getLevel()))
				.withExtra(THREAD_NAME, event.getThreadName());

		if (eventMessage.getFormat() != null && !eventMessage.getFormat().equals("")
				&& !eventMessage.getFormattedMessage().equals(eventMessage.getFormat())) {
			eventBuilder.withSentryInterface(new MessageInterface(eventMessage.getFormat(),
					formatMessageParameters(eventMessage.getParameters()), eventMessage.getFormattedMessage()));
		}

		Throwable throwable = event.getThrown();
		if (throwable != null) {
			eventBuilder.withSentryInterface(new ExceptionInterface(throwable));
		} else if (event.getSource() != null) {
			StackTraceElement[] stackTrace = { event.getSource() };
			eventBuilder.withSentryInterface(new StackTraceInterface(stackTrace));
		}

		if (event.getContextStack() != null) {
			eventBuilder.withExtra(LOG4J_NDC, event.getContextStack().asList());
		}

		event.getContextData().forEach((k, v) -> {
			if (Sentry.getStoredClient().getMdcTags().contains(k)) {
				eventBuilder.withTag(k, (String) v);
			} else {
				eventBuilder.withExtra(k, v);
			}
		});

		if (event instanceof ContextualMessage) {
			Map<String, Object> context = ((ContextualMessage) event).getContext();
			if (!context.isEmpty()) {
				context.forEach((k, v) -> {
					if (v instanceof String) {
						if (Sentry.getStoredClient().getMdcTags().contains(k)) {
							eventBuilder.withTag(k, (String) v);
						} else {
							eventBuilder.withExtra(k, v);
						}
					}
				});
			}
		}

		if (event.getMarker() != null) {
			eventBuilder.withTag(LOG4J_MARKER, event.getMarker().toString());
		}

		return eventBuilder;
	}

	@Override
	public void stop() {
		SentryEnvironment.startManagingThread();
		try {
			if (!isStarted()) {
				return;
			}
			super.stop();
			Sentry.close();
		} catch (Exception e) {
			error("An exception occurred while closing the Sentry connection", e);
		} finally {
			SentryEnvironment.stopManagingThread();
		}
	}

	private class DropSentryFilter extends AbstractFilter {
		@Override
		public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
			return filter(logger.getName());
		}

		@Override
		public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
			return filter(logger.getName());
		}

		@Override
		public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
			return filter(logger.getName());
		}

		@Override
		public Result filter(LogEvent event) {
			return filter(event.getLoggerName());
		}

		private Result filter(String loggerName) {
			if (loggerName != null && loggerName.startsWith("io.sentry")) {
				return Result.DENY;
			}
			return Result.NEUTRAL;
		}
	}
}