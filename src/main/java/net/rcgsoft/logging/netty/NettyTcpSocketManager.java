package net.rcgsoft.logging.netty;

import java.io.Serializable;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.core.appender.OutputStreamManager;
import org.apache.logging.log4j.core.net.AbstractSocketManager;
import org.apache.logging.log4j.core.net.SocketOptions;
import org.apache.logging.log4j.core.util.Log4jThread;
import org.apache.logging.log4j.util.Strings;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * Manager of TCP Socket connections.
 */
public class NettyTcpSocketManager extends AbstractSocketManager {
	private static final EventLoopGroup workerGroup = new NioEventLoopGroup();
	/**
	 * The default reconnection delay (30000 milliseconds or 30 seconds).
	 */
	public static final int DEFAULT_RECONNECTION_DELAY_MILLIS = 30000;
	/**
	 * The default port number of remote logging server (4560).
	 */
	private static final int DEFAULT_PORT = 4560;
	private static final NettyTcpSocketManagerFactory<NettyTcpSocketManager, FactoryData> FACTORY = new NettyTcpSocketManagerFactory<>();
	private final int reconnectionDelayMillis;
	private Reconnector reconnector;
	private Channel channel;
	private final SocketOptions socketOptions;
	private final boolean retry;
	private final boolean immediateFail;
	private final int connectTimeoutMillis;

	/**
	 * Constructs.
	 *
	 * @param name                    The unique name of this connection.
	 * @param os                      The OutputStream.
	 * @param channel                 The Netty {@link Channel}.
	 * @param inetAddress             The Internet address of the host.
	 * @param host                    The name of the host.
	 * @param port                    The port number on the host.
	 * @param connectTimeoutMillis    the connect timeout in milliseconds.
	 * @param reconnectionDelayMillis Reconnection interval.
	 * @param immediateFail           True if the write should fail if no socket is
	 *                                immediately available.
	 * @param layout                  The Layout.
	 * @param bufferSize              The buffer size.
	 */
	public NettyTcpSocketManager(final String name, final Channel channel, final InetAddress inetAddress,
			final String host, final int port, final int connectTimeoutMillis, final int reconnectionDelayMillis,
			final boolean immediateFail, final Layout<? extends Serializable> layout, final int bufferSize,
			final SocketOptions socketOptions) {
		super(name, null, inetAddress, host, port, layout, true, bufferSize);
		this.connectTimeoutMillis = connectTimeoutMillis;
		this.reconnectionDelayMillis = reconnectionDelayMillis;
		this.channel = channel;
		this.immediateFail = immediateFail;
		this.retry = reconnectionDelayMillis > 0;
		if (channel == null) {
			this.reconnector = createReconnector();
			this.reconnector.start();
		}
		this.socketOptions = socketOptions;
	}

	/**
	 * Obtains a NettyTcpSocketManager.
	 *
	 * @param host                 The host to connect to.
	 * @param port                 The port on the host.
	 * @param connectTimeoutMillis the connect timeout in milliseconds
	 * @param reconnectDelayMillis The interval to pause between retries.
	 * @param bufferSize           The buffer size.
	 * @return A TcpSocketManager.
	 */
	public static NettyTcpSocketManager getSocketManager(final String host, int port, final int connectTimeoutMillis,
			int reconnectDelayMillis, final boolean immediateFail, final Layout<? extends Serializable> layout,
			final int bufferSize, final SocketOptions socketOptions) {
		if (Strings.isEmpty(host)) {
			throw new IllegalArgumentException("A host name is required");
		}
		if (port <= 0) {
			port = DEFAULT_PORT;
		}
		if (reconnectDelayMillis == 0) {
			reconnectDelayMillis = DEFAULT_RECONNECTION_DELAY_MILLIS;
		}
		return (NettyTcpSocketManager) getManager("TCP:" + host + ':' + port, new FactoryData(host, port,
				connectTimeoutMillis, reconnectDelayMillis, immediateFail, layout, bufferSize, socketOptions), FACTORY);
	}

	@Override
	protected void write(final byte[] bytes, final int offset, final int length, final boolean immediateFlush) {
		if (channel == null) {
			if (reconnector != null && !immediateFail) {
				reconnector.latch();
			}
			if (channel == null) {
				throw new AppenderLoggingException("Error writing to " + getName() + ": socket not available");
			}
		}
		try {
			writeAndFlush(bytes, offset, length);
		} catch (Exception causeEx) {
			if (retry && reconnector == null) {
				final String config = inetAddress + ":" + port;
				reconnector = createReconnector();
				try {
					reconnector.reconnect();
				} catch (final Exception reconnEx) {
					LOGGER.debug("Cannot reestablish socket connection to {}: {}; starting reconnector thread {}",
							config, reconnEx.getLocalizedMessage(), reconnector.getName(), reconnEx);
					reconnector.start();
					throw new AppenderLoggingException(String.format("Error sending to %s for %s", getName(), config),
							causeEx);
				}
				try {
					writeAndFlush(bytes, offset, length);
				} catch (final Exception e) {
					throw new AppenderLoggingException(String.format(
							"Error writing to %s after reestablishing connection for %s", getName(), config), causeEx);
				}
			}
		}
	}

	private void writeAndFlush(final byte[] bytes, final int offset, final int length) throws Exception {
		// Allocate a buffer of the length we plan to write
		ByteBuf buffer = channel.alloc().buffer(length);
		try {
			buffer.writeBytes(bytes, offset, length);
			channel.writeAndFlush(buffer);
		} finally {
			buffer.release();
		}
	}

	@Override
	protected synchronized boolean closeOutputStream() {
		final boolean closed = super.closeOutputStream();
		if (reconnector != null) {
			reconnector.shutdown();
			reconnector.interrupt();
			reconnector = null;
		}
		final Channel oldChannel = channel;
		channel = null;
		if (oldChannel != null) {
			try {
				oldChannel.close().sync();
			} catch (final Exception e) {
				LOGGER.error("Could not close socket {}", channel);
				return false;
			}
		}
		return closed;
	}

	public int getConnectTimeoutMillis() {
		return connectTimeoutMillis;
	}

	/**
	 * Gets this TcpSocketManager's content format. Specified by:
	 * <ul>
	 * <li>Key: "protocol" Value: "tcp"</li>
	 * <li>Key: "direction" Value: "out"</li>
	 * </ul>
	 *
	 * @return Map of content format keys supporting TcpSocketManager
	 */
	@Override
	public Map<String, String> getContentFormat() {
		final Map<String, String> result = new HashMap<>(super.getContentFormat());
		result.put("protocol", "tcp");
		result.put("direction", "out");
		return result;
	}

	/**
	 * Handles reconnecting to a Socket on a Thread.
	 */
	private class Reconnector extends Log4jThread {
		private final CountDownLatch latch = new CountDownLatch(1);
		private boolean shutdown = false;
		private final Object owner;

		public Reconnector(final OutputStreamManager owner) {
			super("TcpSocketManager-Reconnector");
			this.owner = owner;
		}

		public void latch() {
			try {
				latch.await();
			} catch (final InterruptedException ex) {
				// Ignore the exception.
			}
		}

		public void shutdown() {
			shutdown = true;
		}

		@Override
		public void run() {
			while (!shutdown) {
				try {
					sleep(reconnectionDelayMillis);
					reconnect();
				} catch (final InterruptedException ie) {
					LOGGER.debug("Reconnection interrupted.");
				} catch (final ConnectException ex) {
					LOGGER.debug("{}:{} refused connection", host, port);
				} catch (final Exception e) {
					LOGGER.debug("Unable to reconnect to {}:{}", host, port);
				} finally {
					latch.countDown();
				}
			}
		}

		void reconnect() throws Exception {
			List<InetSocketAddress> socketAddresses = FACTORY.resolver.resolveHost(host, port);
			if (socketAddresses.size() == 1) {
				LOGGER.debug("Reconnecting " + socketAddresses.get(0));
				connect(socketAddresses.get(0));
			} else {
				Exception ex = null;
				for (InetSocketAddress socketAddress : socketAddresses) {
					try {
						LOGGER.debug("Reconnecting " + socketAddress);
						connect(socketAddress);
						return;
					} catch (Exception e) {
						ex = e;
					}
				}
				throw ex;
			}
		}

		private void connect(InetSocketAddress socketAddress) throws Exception {
			final Channel ch = createSocket(socketAddress);
			InetSocketAddress prev = channel != null ? (InetSocketAddress) channel.remoteAddress() : null;
			synchronized (owner) {
				// Close the old channel
				if (channel.isActive() || channel.isOpen()) {
					channel.close();
				}
				// Set the new channel
				channel = ch;
				reconnector = null;
				shutdown = true;
			}
			String type = prev != null
					&& prev.getAddress().getHostAddress().equals(socketAddress.getAddress().getHostAddress())
							? "reestablished"
							: "established";
			LOGGER.debug("Connection to {}:{} {}: {}", host, port, type, channel);
		}

		@Override
		public String toString() {
			return "Reconnector [latch=" + latch + ", shutdown=" + shutdown + "]";
		}
	}

	private Reconnector createReconnector() {
		final Reconnector recon = new Reconnector(this);
		recon.setDaemon(true);
		recon.setPriority(Thread.MIN_PRIORITY);
		return recon;
	}

	protected Channel createSocket(final InetSocketAddress socketAddress) throws Exception {
		return createSocket(socketAddress, socketOptions, connectTimeoutMillis);
	}

	protected static Channel createSocket(final InetSocketAddress socketAddress, final SocketOptions socketOptions,
			final int connectTimeoutMillis) throws Exception {
		LOGGER.debug("Creating socket {}", socketAddress.toString());
		Bootstrap b = new Bootstrap();
		b.group(workerGroup).channel(NioSocketChannel.class);
		if (socketOptions != null) {
			if (socketOptions.isKeepAlive() != null) {
				b.option(ChannelOption.SO_KEEPALIVE, socketOptions.isKeepAlive());
			}
			// TODO oobInline
			if (socketOptions.isReuseAddress() != null) {
				b.option(ChannelOption.SO_REUSEADDR, socketOptions.isReuseAddress());
			}
			// TODO performancePreferences
			if (socketOptions.getReceiveBufferSize() != null) {
				b.option(ChannelOption.SO_RCVBUF, socketOptions.getReceiveBufferSize());
			}
			if (socketOptions.getSoLinger() != null) {
				b.option(ChannelOption.SO_LINGER, socketOptions.getSoLinger());
			}
			if (socketOptions.getSoTimeout() != null) {
				b.option(ChannelOption.SO_TIMEOUT, socketOptions.getSoTimeout());
			}
			if (socketOptions.isTcpNoDelay() != null) {
				b.option(ChannelOption.TCP_NODELAY, socketOptions.isTcpNoDelay());
			}
			final Integer actualTrafficClass = socketOptions.getActualTrafficClass();
			if (actualTrafficClass != null) {
				b.option(ChannelOption.IP_TOS, actualTrafficClass);
			}
		}
		// TODO Set the Netty handler
		ChannelFuture cf = b.connect(socketAddress.getAddress().getHostAddress(), socketAddress.getPort());
		boolean completed = cf.await(connectTimeoutMillis);
		if (completed) {
			return cf.channel();
		}
		return null;
	}

	/**
	 * Data for the factory.
	 */
	static class FactoryData {
		protected final String host;
		protected final int port;
		protected final int connectTimeoutMillis;
		protected final int reconnectDelayMillis;
		protected final boolean immediateFail;
		protected final Layout<? extends Serializable> layout;
		protected final int bufferSize;
		protected final SocketOptions socketOptions;

		public FactoryData(final String host, final int port, final int connectTimeoutMillis,
				final int reconnectDelayMillis, final boolean immediateFail,
				final Layout<? extends Serializable> layout, final int bufferSize, final SocketOptions socketOptions) {
			this.host = host;
			this.port = port;
			this.connectTimeoutMillis = connectTimeoutMillis;
			this.reconnectDelayMillis = reconnectDelayMillis;
			this.immediateFail = immediateFail;
			this.layout = layout;
			this.bufferSize = bufferSize;
			this.socketOptions = socketOptions;
		}

		@Override
		public String toString() {
			return "FactoryData [host=" + host + ", port=" + port + ", connectTimeoutMillis=" + connectTimeoutMillis
					+ ", reconnectDelayMillis=" + reconnectDelayMillis + ", immediateFail=" + immediateFail
					+ ", layout=" + layout + ", bufferSize=" + bufferSize + ", socketOptions=" + socketOptions + "]";
		}
	}

	/**
	 * Factory to create a TcpSocketManager.
	 *
	 * @param <M> The manager type.
	 * @param <T> The factory data type.
	 */
	protected static class NettyTcpSocketManagerFactory<M extends NettyTcpSocketManager, T extends FactoryData>
			implements ManagerFactory<M, T> {

		static HostResolver resolver = new HostResolver();

		@Override
		public M createManager(final String name, final T data) {
			InetAddress inetAddress;
			try {
				inetAddress = InetAddress.getByName(data.host);
			} catch (final UnknownHostException ex) {
				LOGGER.error("Could not find address of {}: {}", data.host, ex, ex);
				return null;
			}
			Channel channel = null;
			try {
				channel = createSocket(data);
				return createManager(name, channel, inetAddress, data);
			} catch (final Exception ex) {
				LOGGER.error("TcpSocketManager ({}) caught exception and will continue:", name, ex, ex);
			}
			if (data.reconnectDelayMillis == 0) {
				if (channel != null) {
					channel.close();
				}
				return null;
			}
			return createManager(name, null, inetAddress, data);
		}

		@SuppressWarnings("unchecked")
		M createManager(final String name, final Channel channel, final InetAddress inetAddress, final T data) {
			return (M) new NettyTcpSocketManager(name, channel, inetAddress, data.host, data.port,
					data.connectTimeoutMillis, data.reconnectDelayMillis, data.immediateFail, data.layout,
					data.bufferSize, data.socketOptions);
		}

		Channel createSocket(final T data) throws Exception {
			List<InetSocketAddress> socketAddresses = resolver.resolveHost(data.host, data.port);
			Exception e = null;
			for (InetSocketAddress socketAddress : socketAddresses) {
				try {
					return NettyTcpSocketManager.createSocket(socketAddress, data.socketOptions,
							data.connectTimeoutMillis);
				} catch (Exception ex) {
					e = ex;
				}
			}
			throw new Exception(errorMessage(data, socketAddresses), e);
		}

		protected String errorMessage(final T data, List<InetSocketAddress> socketAddresses) {
			StringBuilder sb = new StringBuilder("Unable to create socket for ");
			sb.append(data.host).append(" at port ").append(data.port);
			if (socketAddresses.size() == 1) {
				if (!socketAddresses.get(0).getAddress().getHostAddress().equals(data.host)) {
					sb.append(" using ip address ").append(socketAddresses.get(0).getAddress().getHostAddress());
					sb.append(" and port ").append(socketAddresses.get(0).getPort());
				}
			} else {
				sb.append(" using ip addresses and ports ");
				for (int i = 0; i < socketAddresses.size(); ++i) {
					if (i > 0) {
						sb.append(", ");
						sb.append(socketAddresses.get(i).getAddress().getHostAddress());
						sb.append(":").append(socketAddresses.get(i).getPort());
					}
				}
			}
			return sb.toString();
		}

	}

	/**
	 * This method is only for unit testing. It is not Thread-safe.
	 * 
	 * @param resolver the HostResolver.
	 */
	public static void setHostResolver(HostResolver resolver) {
		NettyTcpSocketManagerFactory.resolver = resolver;
	}

	public static class HostResolver {

		public List<InetSocketAddress> resolveHost(String host, int port) throws UnknownHostException {
			InetAddress[] addresses = InetAddress.getAllByName(host);
			List<InetSocketAddress> socketAddresses = new ArrayList<>(addresses.length);
			for (InetAddress address : addresses) {
				socketAddresses.add(new InetSocketAddress(address, port));
			}
			return socketAddresses;
		}
	}

	public int getReconnectionDelayMillis() {
		return reconnectionDelayMillis;
	}

	@Override
	public String toString() {
		return "NettyTcpSocketManager [reconnectionDelayMillis=" + reconnectionDelayMillis + ", reconnector="
				+ reconnector + ", channel=" + channel + ", socketOptions=" + socketOptions + ", retry=" + retry
				+ ", immediateFail=" + immediateFail + ", connectTimeoutMillis=" + connectTimeoutMillis
				+ ", inetAddress=" + inetAddress + ", host=" + host + ", port=" + port + ", layout=" + layout
				+ ", byteBuffer=" + byteBuffer + ", count=" + count + "]";
	}
}