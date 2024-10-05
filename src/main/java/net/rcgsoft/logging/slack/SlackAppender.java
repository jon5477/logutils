/*********************************************************************
* Copyright (c) 2024 Jon Huang
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/

package net.rcgsoft.logging.slack;

import java.io.Serializable;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.HttpManager;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;

/**
 * Slack Appender for sending log events to a Slack webhook.
 * 
 * @author Jon Huang
 *
 */
@Plugin(name = "Slack", category = Node.CATEGORY, elementType = Appender.ELEMENT_TYPE, printObject = true)
public final class SlackAppender extends AbstractAppender {

	/**
	 * Builds HttpAppender instances.
	 * 
	 * @param <B> The type to build
	 */
	public static class Builder<B extends Builder<B>> extends AbstractAppender.Builder<B>
			implements org.apache.logging.log4j.core.util.Builder<SlackAppender> {

		@PluginBuilderAttribute
		@Required(message = "No URL provided for HttpAppender")
		private URL url;

		@PluginBuilderAttribute
		private String method = "POST";

		@PluginBuilderAttribute
		private int connectTimeoutMillis = 0;

		@PluginBuilderAttribute
		private int readTimeoutMillis = 0;

		@PluginElement("Headers")
		private Property[] headers;

		@PluginElement("SslConfiguration")
		private SslConfiguration sslConfiguration;

		@PluginBuilderAttribute
		private boolean verifyHostname = true;

		@PluginBuilderAttribute
		private int fieldSizeLimit = 0;

		@PluginBuilderAttribute
		private boolean blockingHttp = false;

		@Override
		public SlackAppender build() {
			final HttpManager httpManager = new SlackManager(getConfiguration(), getConfiguration().getLoggerContext(),
					getName(), url, method, connectTimeoutMillis, readTimeoutMillis, headers, sslConfiguration,
					verifyHostname, blockingHttp);
			return new SlackAppender(getName(), SlackLayout.createLayout(fieldSizeLimit), getFilter(),
					isIgnoreExceptions(), httpManager);
		}

		public URL getUrl() {
			return url;
		}

		public String getMethod() {
			return method;
		}

		public int getConnectTimeoutMillis() {
			return connectTimeoutMillis;
		}

		public int getReadTimeoutMillis() {
			return readTimeoutMillis;
		}

		public Property[] getHeaders() {
			return headers;
		}

		public SslConfiguration getSslConfiguration() {
			return sslConfiguration;
		}

		public boolean isVerifyHostname() {
			return verifyHostname;
		}

		public int getFieldSizeLimit() {
			return fieldSizeLimit;
		}

		public B setUrl(final URL url) {
			this.url = url;
			return asBuilder();
		}

		public B setMethod(final String method) {
			this.method = method;
			return asBuilder();
		}

		public B setConnectTimeoutMillis(final int connectTimeoutMillis) {
			this.connectTimeoutMillis = connectTimeoutMillis;
			return asBuilder();
		}

		public B setReadTimeoutMillis(final int readTimeoutMillis) {
			this.readTimeoutMillis = readTimeoutMillis;
			return asBuilder();
		}

		public B setHeaders(final Property[] headers) {
			this.headers = headers;
			return asBuilder();
		}

		public B setSslConfiguration(final SslConfiguration sslConfiguration) {
			this.sslConfiguration = sslConfiguration;
			return asBuilder();
		}

		public B setVerifyHostname(final boolean verifyHostname) {
			this.verifyHostname = verifyHostname;
			return asBuilder();
		}

		public B setFieldSizeLimit(final int fieldSizeLimit) {
			this.fieldSizeLimit = fieldSizeLimit;
			return asBuilder();
		}

		public B setBlockingHttp(final boolean blockingHttp) {
			this.blockingHttp = blockingHttp;
			return asBuilder();
		}
	}

	/**
	 * Creates a new builder for HttpAppender
	 * 
	 * @param <B> The type of builder
	 * @return a builder for a HttpAppender.
	 */
	@PluginBuilderFactory
	public static <B extends Builder<B>> B newBuilder() {
		return new Builder<B>().asBuilder();
	}

	private final HttpManager manager;

	private SlackAppender(final String name, final Layout<? extends Serializable> layout, final Filter filter,
			final boolean ignoreExceptions, final HttpManager manager) {
		super(name, filter, layout, ignoreExceptions, Property.EMPTY_ARRAY);
		Objects.requireNonNull(layout, "layout");
		this.manager = Objects.requireNonNull(manager, "manager");
	}

	@Override
	public void start() {
		super.start();
		manager.startup();
	}

	@Override
	public void append(final LogEvent event) {
		try {
			manager.send(getLayout(), event);
		} catch (final Exception e) {
			error("Unable to send HTTP in appender [" + getName() + "]", event, e);
		}
	}

	@Override
	public boolean stop(final long timeout, final TimeUnit timeUnit) {
		setStopping();
		boolean stopped = super.stop(timeout, timeUnit, false);
		stopped &= manager.stop(timeout, timeUnit);
		setStopped();
		return stopped;
	}

	@Override
	public String toString() {
		return "SlackAppender{" + "name=" + getName() + ", state=" + getState() + '}';
	}
}