package net.rcgsoft.logging.sentry;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext.ContextStack;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
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
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.message.Message;

import io.sentry.Breadcrumb;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Contexts;
import net.rcgsoft.logging.message.ContextualMessage;

/**
 * Appender for log4j2 in charge of sending the logged events to a Sentry
 * server.
 * 
 * @author David Xu
 * @author Jon Huang
 *
 */
@Plugin(name = "Sentry", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class SentryAppender extends AbstractAppender {
	/**
	 * Default name for the appender.
	 */
	private static final String APPENDER_NAME = "sentry";
	private static final String LOG4J_CTX_DATA = "context_data";
	private static final String LOG4J_CTX_STACK = "context_stack";
	private static final String LOG4J_MARKER = "log_marker";
	private static final String CTX_MSG = "context_message";
	private static final String THREAD_NAME = "thread_name";

	private final String dsn;
	private final IHub hub;

	/**
	 * Creates an instance of SentryAppender.
	 * 
	 * @param hub The Sentry Hub instance.
	 */
	public SentryAppender(IHub hub) {
		this(APPENDER_NAME, "", null, hub);
	}

	/**
	 * Creates an instance of SentryAppender.
	 *
	 * @param name   The Appender name.
	 * @param filter The Filter to associate with the Appender.
	 * @param hub    The Sentry Hub instance.
	 */
	protected SentryAppender(String name, String dsn, Filter filter, IHub hub) {
		super(name, filter, null, true, Property.EMPTY_ARRAY);
		this.addFilter(new DropSentryFilter());
		this.dsn = Objects.requireNonNull(dsn, "dsn cannot be null");
		this.hub = Objects.requireNonNull(hub, "hub cannot be null");
	}

	/**
	 * Creates a new Sentry Appender.
	 * 
	 * @param name   The name of the Appender.
	 * @param dsn    The Sentry DSN.
	 * @param filter The filter, if any, to use.
	 * @return The {@code SentryAppender} instance.
	 */
	@PluginFactory
	public static SentryAppender createAppender(@PluginAttribute("name") final String name,
			@PluginAttribute("dsn") final String dsn, @PluginElement("filter") final Filter filter) {
		if (name == null) {
			LOGGER.error("No name provided for SentryAppender");
			return null;
		}
		return new SentryAppender(name, dsn != null ? dsn : "", filter, HubAdapter.getInstance());
	}

	/**
	 * Transforms a {@link Level} into an {@link SentryLevel}.
	 *
	 * @param level Logging level as defined in Log4J2.
	 * @return The {@code SentryLevel} log level used within sentry.
	 */
	protected static SentryLevel formatLevel(Level level) {
		if (level.isMoreSpecificThan(Level.FATAL)) {
			return SentryLevel.FATAL;
		} else if (level.isMoreSpecificThan(Level.ERROR)) {
			return SentryLevel.ERROR;
		} else if (level.isMoreSpecificThan(Level.WARN)) {
			return SentryLevel.WARNING;
		} else if (level.isMoreSpecificThan(Level.INFO)) {
			return SentryLevel.INFO;
		} else {
			return SentryLevel.DEBUG;
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
	public void start() {
		if (!Sentry.isEnabled()) {
			Sentry.init(opt -> {
				opt.setEnableExternalConfiguration(true);
				opt.setDsn(dsn);
			});
		}
		super.start();
	}

	@Override
	public void append(LogEvent logEvent) {
		if (logEvent.getLevel().isMoreSpecificThan(Level.ERROR)) {
			hub.captureEvent(createSentryEvent(logEvent));
		}
		if (logEvent.getLevel().isMoreSpecificThan(Level.INFO)) {
			hub.addBreadcrumb(createBreadcrumb(logEvent));
		}
	}

	/**
	 * Builds an {@code SentryEvent} based on the {@code LogEvent}.
	 *
	 * @param logEvent The logging event from Log4J2.
	 * @return SentryEvent containing details provided by the logging system.
	 */
	protected SentryEvent createSentryEvent(LogEvent logEvent) {
		SentryEvent evt = new SentryEvent(new Date(logEvent.getTimeMillis()));
		Message logMsg = logEvent.getMessage();
		io.sentry.protocol.Message msg = new io.sentry.protocol.Message();
		msg.setMessage(logMsg.getFormat());
		msg.setFormatted(logMsg.getFormattedMessage());
		Object[] paramsArr = logMsg.getParameters();
		if (paramsArr != null) {
			List<String> params = formatMessageParameters(paramsArr);
			msg.setParams(params);
		}
		evt.setMessage(msg);
		evt.setLogger(logEvent.getLoggerName());
		evt.setLevel(formatLevel(logEvent.getLevel()));
		ThrowableProxy thrownProxy = logEvent.getThrownProxy();
		if (thrownProxy != null) {
			evt.setThrowable(thrownProxy.getThrowable());
		}
		String threadName = logEvent.getThreadName();
		if (threadName != null) {
			evt.setExtra(THREAD_NAME, threadName);
		}
		Contexts evtCtx = evt.getContexts();
		Map<String, String> contextData = new ConcurrentHashMap<>(logEvent.getContextData().toMap());
		if (!contextData.isEmpty()) {
			evtCtx.put(LOG4J_CTX_DATA, contextData);
		}
		ContextStack stack = logEvent.getContextStack();
		List<String> stackList = stack.asList();
		Map<Integer, String> contextStack = new ConcurrentHashMap<>();
		for (int i = 0, n = stackList.size(); i < n; i++) {
			contextStack.put(i, stackList.get(i));
		}
		evtCtx.put(LOG4J_CTX_STACK, contextStack);
		if (logMsg instanceof ContextualMessage) {
			Map<String, Object> context = new ConcurrentHashMap<>(((ContextualMessage) logMsg).getContext());
			if (!context.isEmpty()) {
				evtCtx.put(CTX_MSG, context);
			}
		}
		Marker marker = logEvent.getMarker();
		if (marker != null) {
			evtCtx.put(LOG4J_MARKER, marker.toString());
		}
		return evt;
	}

	private Breadcrumb createBreadcrumb(LogEvent logEvent) {
		Breadcrumb breadcrumb = new Breadcrumb();
		breadcrumb.setLevel(formatLevel(logEvent.getLevel()));
		breadcrumb.setCategory(logEvent.getLoggerName());
		breadcrumb.setMessage(logEvent.getMessage().getFormattedMessage());
		return breadcrumb;
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