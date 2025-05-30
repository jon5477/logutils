/*********************************************************************
* Copyright (c) 2025 Jon Huang
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/

package net.rcgsoft.logging.netty;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.core.AbstractLifeCycle;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAliases;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.ValidHost;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.ValidPort;
import org.apache.logging.log4j.core.net.AbstractSocketManager;
import org.apache.logging.log4j.core.net.Advertiser;
import org.apache.logging.log4j.core.net.DatagramSocketManager;
import org.apache.logging.log4j.core.net.Protocol;
import org.apache.logging.log4j.core.net.SocketOptions;
import org.apache.logging.log4j.core.net.SslSocketManager;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;

import io.netty.channel.Channel;

/**
 * An Appender that delivers events over netty socket connections. Supports both
 * TCP and UDP.
 * 
 * @author Jon Huang
 *
 */
@Plugin(name = "Netty", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class NettyAppender extends AbstractOutputStreamAppender<AbstractSocketManager> {
	/**
	 * Subclasses can extend this abstract Builder.
	 * <h1>Defaults</h1>
	 * <ul>
	 * <li>host: "localhost"</li>
	 * <li>protocol: "TCP"</li>
	 * </ul>
	 * <h1>Changes</h1>
	 * <ul>
	 * <li>Removed deprecated "delayMillis", use "reconnectionDelayMillis".</li>
	 * <li>Removed deprecated "reconnectionDelay", use
	 * "reconnectionDelayMillis".</li>
	 * </ul>
	 *
	 * @param <B> The type to build.
	 */
	public abstract static class AbstractBuilder<B extends AbstractBuilder<B>>
			extends AbstractOutputStreamAppender.Builder<B> {
		@PluginBuilderAttribute
		private boolean advertise;

		@PluginBuilderAttribute
		private int connectTimeoutMillis;

		@PluginBuilderAttribute
		private int writerTimeoutMillis;

		@PluginBuilderAttribute
		@ValidHost
		private String host = "localhost";

		@PluginBuilderAttribute
		private boolean immediateFail = true;

		@PluginBuilderAttribute
		@ValidPort
		private int port;

		@PluginBuilderAttribute
		private Protocol protocol = Protocol.TCP;

		@PluginBuilderAttribute
		@PluginAliases({ "reconnectDelay", "reconnectionDelay", "delayMillis", "reconnectionDelayMillis" })
		private int reconnectDelayMillis;

		@PluginBuilderAttribute
		private int bufferLowWaterMark;

		@PluginBuilderAttribute
		private int bufferHighWaterMark;

		@PluginElement("SocketOptions")
		private SocketOptions socketOptions;

		@PluginElement("SslConfiguration")
		@PluginAliases({ "SslConfig" })
		private SslConfiguration sslConfiguration;

		@PluginBuilderAttribute
		private String bufFileName;

		public boolean getAdvertise() {
			return advertise;
		}

		public int getConnectTimeoutMillis() {
			return connectTimeoutMillis;
		}

		public int getWriterTimeoutMillis() {
			return writerTimeoutMillis;
		}

		public String getHost() {
			return host;
		}

		public int getPort() {
			return port;
		}

		public Protocol getProtocol() {
			return protocol;
		}

		public SslConfiguration getSslConfiguration() {
			return sslConfiguration;
		}

		public boolean getImmediateFail() {
			return immediateFail;
		}

		public B withAdvertise(final boolean advertise) {
			this.advertise = advertise;
			return asBuilder();
		}

		public B withConnectTimeoutMillis(final int connectTimeoutMillis) {
			this.connectTimeoutMillis = connectTimeoutMillis;
			return asBuilder();
		}

		public B withHost(final String host) {
			this.host = host;
			return asBuilder();
		}

		public B withImmediateFail(final boolean immediateFail) {
			this.immediateFail = immediateFail;
			return asBuilder();
		}

		public B withPort(final int port) {
			this.port = port;
			return asBuilder();
		}

		public B withProtocol(final Protocol protocol) {
			this.protocol = protocol;
			return asBuilder();
		}

		public B withReconnectDelayMillis(final int reconnectDelayMillis) {
			this.reconnectDelayMillis = reconnectDelayMillis;
			return asBuilder();
		}

		public B withBufferLowWaterMark(final int bufferLowWaterMark) {
			this.bufferLowWaterMark = bufferLowWaterMark;
			return asBuilder();
		}

		public B withBufferHighWaterMark(final int bufferHighWaterMark) {
			this.bufferHighWaterMark = bufferHighWaterMark;
			return asBuilder();
		}

		public B withSocketOptions(final SocketOptions socketOptions) {
			this.socketOptions = socketOptions;
			return asBuilder();
		}

		public B withBufFileName(final String bufFileName) {
			this.bufFileName = bufFileName;
			return asBuilder();
		}

		public B withSslConfiguration(final SslConfiguration sslConfiguration) {
			this.sslConfiguration = sslConfiguration;
			return asBuilder();
		}

		public int getReconnectDelayMillis() {
			return reconnectDelayMillis;
		}

		public int getBufferLowWaterMark() {
			return bufferLowWaterMark;
		}

		public int getBufferHighWaterMark() {
			return bufferHighWaterMark;
		}

		public SocketOptions getSocketOptions() {
			return socketOptions;
		}

		public String getBufFileName() {
			return bufFileName;
		}
	}

	/**
	 * Builds a NettyAppender.
	 * <ul>
	 * <li>Removed deprecated "delayMillis", use "reconnectionDelayMillis".</li>
	 * <li>Removed deprecated "reconnectionDelay", use
	 * "reconnectionDelayMillis".</li>
	 * </ul>
	 */
	public static class Builder extends AbstractBuilder<Builder>
			implements org.apache.logging.log4j.core.util.Builder<NettyAppender> {
		@Override
		public NettyAppender build() {
			boolean immediateFlush = isImmediateFlush();
			final boolean bufferedIo = isBufferedIo();
			final Layout<? extends Serializable> layout = getLayout();
			if (layout == null) {
				AbstractLifeCycle.LOGGER.error("No layout provided for NettyAppender");
				return null;
			}
			final String name = getName();
			if (name == null) {
				AbstractLifeCycle.LOGGER.error("No name provided for NettyAppender");
				return null;
			}
			final Protocol protocol = getProtocol();
			final Protocol actualProtocol = protocol != null ? protocol : Protocol.TCP;
			if (actualProtocol == Protocol.UDP) {
				immediateFlush = true;
			}
			final AbstractSocketManager manager = NettyAppender.createSocketManager(name, actualProtocol, getHost(),
					getPort(), getConnectTimeoutMillis(), getWriterTimeoutMillis(), getSslConfiguration(),
					getReconnectDelayMillis(), getImmediateFail(), layout, getBufferSize(), getBufferLowWaterMark(),
					getBufferHighWaterMark(), getSocketOptions(), getBufFileName());
			return new NettyAppender(name, layout, getFilter(), manager, isIgnoreExceptions(),
					!bufferedIo || immediateFlush, getAdvertise() ? getConfiguration().getAdvertiser() : null,
					getPropertyArray());
		}
	}

	@PluginBuilderFactory
	public static Builder newBuilder() {
		return new Builder();
	}

	private final Object advertisement;
	private final Advertiser advertiser;

	protected NettyAppender(final String name, final Layout<? extends Serializable> layout, final Filter filter,
			final AbstractSocketManager manager, final boolean ignoreExceptions, final boolean immediateFlush,
			final Advertiser advertiser, final Property[] properties) {
		super(name, layout, filter, ignoreExceptions, immediateFlush, properties, manager);
		if (advertiser != null) {
			final Map<String, String> configuration = new HashMap<>(layout.getContentFormat());
			configuration.putAll(manager.getContentFormat());
			configuration.put("contentType", layout.getContentType());
			configuration.put("name", name);
			this.advertisement = advertiser.advertise(configuration);
		} else {
			this.advertisement = null;
		}
		this.advertiser = advertiser;
	}

	@Override
	public boolean stop(final long timeout, final TimeUnit timeUnit) {
		setStopping();
		super.stop(timeout, timeUnit, false);
		if (this.advertiser != null) {
			this.advertiser.unadvertise(this.advertisement);
		}
		setStopped();
		return true;
	}

	/**
	 * Creates an {@code AbstractSocketManager} for TCP, UDP, and SSL-based sockets.
	 * 
	 * @param name                 The socket name
	 * @param protocol             The socket protocol
	 * @param host                 The host to connect to
	 * @param port                 The port to connect to
	 * @param connectTimeoutMillis The connection timeout (in milliseconds)
	 * @param writerTimeoutMillis  The writer timeout (in milliseconds)
	 * @param sslConfig            The SSL configuration
	 * @param reconnectDelayMillis The reconnection delay (in milliseconds)
	 * @param immediateFail        {@code true} if the connection should immediately
	 *                             fail during connection
	 * @param layout               The layout of the logging output
	 * @param bufferSize           The socket write buffer size (in bytes)
	 * @param bufLowWaterMark      The low water mark of the writer buffer (if it
	 *                             falls below this {@link Channel#isWritable()}
	 *                             returns {@code true})
	 * @param bufHighWaterMark     The high water mark of the writer buffer (if it
	 *                             goes above this {@link Channel#isWritable()}
	 *                             returns {@code false})
	 * @param socketOptions        The socket options
	 * @param bufFileName          The buffer file name for storing failed writes
	 * @return The {@code AbstractSocketManager} implementation based on the socket
	 *         protocol.
	 */
	protected static AbstractSocketManager createSocketManager(final String name, Protocol protocol, final String host,
			final int port, final int connectTimeoutMillis, final int writerTimeoutMillis,
			final SslConfiguration sslConfig, final int reconnectDelayMillis, final boolean immediateFail,
			final Layout<? extends Serializable> layout, final int bufferSize, final int bufLowWaterMark,
			final int bufHighWaterMark, final SocketOptions socketOptions, final String bufFileName) {
		if (protocol == Protocol.TCP && sslConfig != null) {
			// Upgrade TCP to SSL if an SSL config is specified.
			protocol = Protocol.SSL;
		}
		if (protocol != Protocol.SSL && sslConfig != null) {
			LOGGER.info("Appender {} ignoring SSL configuration for {} protocol", name, protocol);
		}
		return switch (protocol) {
			case TCP -> NettyTcpSocketManager.getSocketManager(name, host, port, connectTimeoutMillis,
					writerTimeoutMillis, reconnectDelayMillis, layout, bufferSize, bufLowWaterMark, bufHighWaterMark,
					socketOptions, bufFileName);
			case UDP -> DatagramSocketManager.getSocketManager(host, port, layout, bufferSize);
			case SSL -> SslSocketManager.getSocketManager(sslConfig, host, port, connectTimeoutMillis,
					reconnectDelayMillis, immediateFail, layout, bufferSize, socketOptions);
			default -> throw new IllegalArgumentException(protocol.toString());
		};
	}

	@Override
	protected void directEncodeEvent(final LogEvent event) {
		// Disable garbage-free logging for now:
		// problem with UDP: 8K buffer size means that largish messages get broken up
		// into chunks
		writeByteArrayToManager(event); // revert to classic (non-garbage free) logging
	}
}