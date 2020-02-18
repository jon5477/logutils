package net.rcgsoft.logging.netty;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.core.net.AbstractSocketManager;
import org.apache.logging.log4j.core.net.SocketOptions;
import org.apache.logging.log4j.util.Strings;

import com.squareup.tape2.QueueFile;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;

/**
 * Netty TCP Socket connection manager and implementation. Allows reconnection
 * of broken sockets and enqueuing failed writes to disk to be written later.
 * 
 * @author Jon Huang
 *
 */
public class NettyTcpSocketManager extends AbstractSocketManager {
	private static final int MEGABYTE = 1024 * 1024;
	private static final int DEFAULT_LOW_WATER_MARK = 4 * MEGABYTE;
	private static final int DEFAULT_HIGH_WATER_MARK = 8 * MEGABYTE;
	private static final EventLoopGroup workerGroup = new NioEventLoopGroup(2);
	private static final EventExecutor executor = new DefaultEventExecutor();
	/**
	 * The default reconnection delay (1000 milliseconds or 1 second).
	 */
	public static final int DEFAULT_RECONNECTION_DELAY_MILLIS = 1000;
	/**
	 * The default port number of remote logging server (4560).
	 */
	private static final int DEFAULT_PORT = 4560;
	private static final NettyTcpSocketManagerFactory<NettyTcpSocketManager, FactoryData> FACTORY = new NettyTcpSocketManagerFactory<>();
	private final Lock queueMutex = new ReentrantLock();
	private final Lock futureMutex = new ReentrantLock();
	private final int reconnectionDelayMillis;
	/**
	 * The {@code QueueFile} that contains the failed and unwritten messages. This
	 * queue will be consumed while the connection is active.
	 */
	private final QueueFile queueFile;
	/**
	 * The {@code Thread} that handles the re-connection process.
	 */
	private final Reconnector reconnector = new Reconnector();
	/**
	 * The {@code Thread} that handles the writes after the connection is
	 * re-established.
	 */
	private final QueueWriter writer = new QueueWriter();
	/**
	 * The reference to the {@code Future} executing the {@code Reconnector}.
	 */
	private ScheduledFuture<?> reconFuture;
	/**
	 * The reference to the {@code Future} executing the {@code ReconnectorWriter}.
	 */
	private Future<?> writerFuture;
	private final AtomicReference<Channel> channelRef = new AtomicReference<>();
	private final SocketOptions socketOptions;
	private final int bufLowWaterMark;
	private final int bufHighWaterMark;
	private final boolean retry;
	private final int connectTimeoutMillis;
	private final int writerTimeoutMillis;

	/**
	 * Constructs.
	 *
	 * @param name                    The unique name of this connection.
	 * @param os                      The OutputStream.
	 * @param channel                 The Netty {@link Channel}.
	 * @param inetAddress             The Internet address of the host.
	 * @param host                    The name of the host.
	 * @param port                    The port number on the host.
	 * @param connectTimeoutMillis    The connect timeout in milliseconds.
	 * @param writerTimeoutMillis     The writer timeout in milliseconds.
	 * @param reconnectionDelayMillis Reconnection interval.
	 * @param layout                  The Layout.
	 * @param bufferSize              The buffer size.
	 * @param bufLowWaterMark         The buffer low water mark
	 * @param bufHighWaterMark        The buffer high water mark
	 * @param socketOptions           The socket options
	 * @param tapeBufFileName         The tape buffer file name
	 */
	public NettyTcpSocketManager(final String name, final Channel channel, final InetAddress inetAddress,
			final String host, final int port, final int connectTimeoutMillis, final int writerTimeoutMillis,
			final int reconnectionDelayMillis, final Layout<? extends Serializable> layout, final int bufferSize,
			final int bufLowWaterMark, final int bufHighWaterMark, final SocketOptions socketOptions,
			final String tapeBufFileName) {
		super(name, null, inetAddress, host, port, layout, true, 0);
		this.connectTimeoutMillis = connectTimeoutMillis;
		this.writerTimeoutMillis = writerTimeoutMillis;
		this.reconnectionDelayMillis = reconnectionDelayMillis;
		this.channelRef.set(channel);
		this.retry = reconnectionDelayMillis > 0;
		this.bufLowWaterMark = bufLowWaterMark;
		this.bufHighWaterMark = bufHighWaterMark;
		this.socketOptions = socketOptions;
		try {
			File bufFile = new File(tapeBufFileName != null ? tapeBufFileName : "netty-log4j-buf-" + name + "-tmp.bin");
			queueFile = new QueueFile.Builder(bufFile).build();
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
		// Start the writer thread even if the queue is empty
		this.fireWriter();
	}

	/**
	 * Obtains a NettyTcpSocketManager.
	 *
	 * @param name                 The name of the appender
	 * @param host                 The host to connect to.
	 * @param port                 The port on the host.
	 * @param connectTimeoutMillis The connect timeout in milliseconds
	 * @param writerTimeoutMillis  The writer timeout in milliseconds
	 * @param reconnectDelayMillis The interval to pause between retries.
	 * @param bufferSize           The buffer size.
	 * @param bufLowWaterMark      The buffer low water mark
	 * @param bufHighWaterMark     The buffer high water mark
	 * @param bufFileName          The tape buffer file name
	 * @return A TcpSocketManager.
	 */
	public static NettyTcpSocketManager getSocketManager(final String name, final String host, int port,
			final int connectTimeoutMillis, int writerTimeoutMillis, int reconnectDelayMillis,
			final Layout<? extends Serializable> layout, final int bufferSize, final int bufLowWaterMark,
			final int bufHighWaterMark, final SocketOptions socketOptions, final String bufFileName) {
		if (Strings.isEmpty(host)) {
			throw new IllegalArgumentException("A host name is required");
		}
		if (port <= 0) {
			port = DEFAULT_PORT;
		}
		if (reconnectDelayMillis == 0) {
			reconnectDelayMillis = DEFAULT_RECONNECTION_DELAY_MILLIS;
		}
		return (NettyTcpSocketManager) getManager(name,
				new FactoryData(host, port, connectTimeoutMillis, writerTimeoutMillis, reconnectDelayMillis, layout,
						bufferSize, bufLowWaterMark, bufHighWaterMark, socketOptions, bufFileName),
				FACTORY);
	}

	/**
	 * Checks if the writer is running and starts it if it is not running.
	 */
	private final void fireWriter() {
		// Check if the writer thread is running (has
		// to be done in a critical section to
		// avoid race conditions)
		futureMutex.lock();
		try {
			// writer is null OR is no longer running
			if (writerFuture == null || writerFuture.isDone()) {
				writerFuture = executor.scheduleWithFixedDelay(writer, 10, 10, TimeUnit.SECONDS);
			}
		} finally {
			futureMutex.unlock();
		}
	}

	/**
	 * Checks if the reconnector is running and starts it if it is not running.
	 */
	private final void fireReconnector() {
		// Check if the reconnector is running (has to be done in a critical section to
		// avoid race conditions)
		futureMutex.lock();
		try {
			// reconnector is null OR is no longer running
			if (reconFuture == null || reconFuture.isDone()) {
				reconFuture = executor.scheduleAtFixedRate(reconnector, 0, reconnectionDelayMillis,
						TimeUnit.MILLISECONDS);
			}
		} finally {
			futureMutex.unlock();
		}
	}

	/**
	 * Stops the reconnector thread if it is running.
	 */
	private final void stopReconnector() {
		futureMutex.lock();
		try {
			if (reconFuture != null && reconFuture.cancel(true)) {
				reconFuture = null;
			}
		} finally {
			futureMutex.unlock();
		}
	}

	/**
	 * Stops the writer thread if it is running.
	 */
	private final void stopWriter() {
		futureMutex.lock();
		try {
			if (writerFuture != null && writerFuture.cancel(true)) {
				writerFuture = null;
			}
		} finally {
			futureMutex.unlock();
		}
	}

	/**
	 * Performs a write to the underlying socket. If the write is unsuccessful, the
	 * data will be preserved and persisted to disk. This method call does NOT block
	 * but does obtain a monitor in cases where the socket is not available and the
	 * reconnector thread needs to be started.
	 */
	@SuppressWarnings("sync-override")
	@Override
	protected final void write(byte[] bytes, int offset, int length, boolean immediateFlush) {
		// Fetch the channel for performing the write
		Channel ch = channelRef.get();
		if (ch != null && ch.isActive()) {
			// check if the channel is active
			writeToChannel(ch, bytes, offset, length);
		} else {
			channelRef.set(null);
			// cannot write since the channel is null OR no longer active
			handleFailedWrite(bytes, offset, length);
		}
	}

	/**
	 * Writes the following buffer to the {@link Channel} and enqueues the write on
	 * failure.
	 * 
	 * @param ch     the {@link Channel} to write to
	 * @param bytes  the buffer of bytes to read from
	 * @param offset the starting index of the buffer to read from
	 * @param length the length of bytes to read
	 * @return the {@link ChannelFuture} that contains the status of the write
	 */
	private final ChannelFuture writeToChannel(Channel ch, byte[] bytes, int offset, int length) {
		Objects.requireNonNull(ch, "channel cannot be null");
		Objects.requireNonNull(bytes, "bytes buffer cannot be null");
		return writeAndFlush(ch, bytes, offset, length).addListener(f -> {
			if (!f.isSuccess()) {
				handleFailedWrite(bytes, offset, length, f.cause());
			}
		});
	}

	/**
	 * Handles when a write failure occurs by starting the reconnector thread and
	 * enqueuing the failed write.
	 * 
	 * @param bytes  the buffer of bytes to read from
	 * @param offset the starting index of the buffer to read from
	 * @param length the length of bytes to read
	 */
	private final void handleFailedWrite(byte[] bytes, final int offset, final int length) {
		Objects.requireNonNull(bytes, "buffer cannot be null");
		// the connection was lost, fire up the reconnector
		fireReconnector();
		// fire up the writer
		fireWriter();
		// enqueue the message to write later
		enqueueMessage(bytes, offset, length);
		LOGGER.debug("Added failed write to queue.");
	}

	/**
	 * Handles when a write failure occurs due to an exception. This will also start
	 * the reconnector thread, enqueue the failed write, and log the exception.
	 * 
	 * @param bytes  the buffer of bytes to read from
	 * @param offset the starting index of the buffer to read from
	 * @param length the length of bytes to read
	 */
	private final void handleFailedWrite(byte[] bytes, int offset, int length, Throwable cause) {
		Objects.requireNonNull(bytes, "buffer cannot be null");
		handleFailedWrite(bytes, offset, length);
		LOGGER.debug("Could not successfully write to socket: {}", cause.getLocalizedMessage(), cause);
	}

	/**
	 * Writes the following buffer to the {@link Channel}.
	 * 
	 * @param ch     the {@link Channel} to write to
	 * @param bytes  the buffer of bytes to read from
	 * @param offset the starting index of the buffer to read from
	 * @param length the length of bytes to read
	 * @return the {@link ChannelFuture} that contains the status of the write
	 */
	private final ChannelFuture writeAndFlush(Channel ch, byte[] bytes, int offset, int length) {
		// Wrap the existing buffer to save memory
		ByteBuf buffer = Unpooled.wrappedBuffer(bytes, offset, length);
		return ch.writeAndFlush(buffer);
	}

	@SuppressWarnings("sync-override")
	@Override
	protected boolean closeOutputStream() {
		this.stopReconnector();
		this.stopWriter();
		final Channel oldChannel = channelRef.getAndSet(null);
		if (oldChannel != null) {
			try {
				oldChannel.close();
			} catch (final Exception e) {
				LOGGER.error("Could not close socket {}", oldChannel);
				return false;
			}
		}
		return true;
	}

	/**
	 * Enqueues the provided message buffer to be written later.
	 * 
	 * @param bytes  the buffer of bytes
	 * @param offset the index to start reading from
	 * @param length the length of bytes to read
	 */
	private final void enqueueMessage(byte[] bytes, int offset, int length) {
		Objects.requireNonNull(bytes, "buffer cannot be null");
		queueMutex.lock();
		try {
			queueFile.add(bytes, offset, length);
		} catch (IOException e) {
			LOGGER.error("Failed to add message to buffer: {}", e.getMessage(), e);
		} finally {
			queueMutex.unlock();
		}
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
	public final Map<String, String> getContentFormat() {
		final Map<String, String> result = new HashMap<>(super.getContentFormat());
		result.put("protocol", "tcp");
		result.put("direction", "out");
		return result;
	}

	/**
	 * Handles reconnecting a {@code Channel} in a {@code Runnable}.
	 * 
	 * @author Jon Huang
	 *
	 */
	private final class Reconnector implements Runnable {
		@Override
		public final void run() {
			boolean shutdown = false;
			try {
				List<InetSocketAddress> resolvedHosts = getResolvedHosts();
				if (resolvedHosts.size() == 1) {
					// single host, connect once synchronously
					ChannelFuture cf = createSocket(resolvedHosts.get(0));
					cf.await();
					// Check the status of the reconnection
					if (cf.isSuccess()) {
						// successfully reconnected, now we need to abort the reconnector thread
						Channel ch = cf.channel();
						if (ch.isActive()) {
							LOGGER.debug("Successfully connected: {}", cf.channel());
							channelRef.set(ch);
							shutdown = true;
						} else {
							LOGGER.debug("Failed to connect. Channel not active");
						}
					} else {
						Throwable cause = cf.cause();
						LOGGER.debug("Failed to connect: {}", cause.getLocalizedMessage(), cause);
					}
				} else {
					// multiple hosts, try each one synchronously
					for (InetSocketAddress host : resolvedHosts) {
						ChannelFuture cf = createSocket(host).await();
						// Check the status of the reconnection
						if (cf.isSuccess()) {
							// successfully reconnected, now we need to abort the reconnector thread
							Channel ch = cf.channel();
							if (ch.isActive()) {
								LOGGER.debug("Successfully connected: {}", cf.channel());
								channelRef.set(ch);
								shutdown = true;
								break;
							} else {
								LOGGER.debug("Failed to connect. Channel not active");
							}
						} else {
							Throwable cause = cf.cause();
							LOGGER.debug("Failed to connect: {}", cause.getLocalizedMessage(), cause);
						}
					}
				}
			} catch (InterruptedException e) {
				// interrupted - used as a signal to abort this thread
				shutdown = true;
				LOGGER.debug("Reconnection interrupted.");
				Thread.currentThread().interrupt();
			} catch (UnknownHostException e) {
				// failed to resolve host, we can try again on the next scheduled time
				LOGGER.debug("Failed to resolve host: {}", e.getLocalizedMessage(), e);
			}
			if (shutdown) {
				NettyTcpSocketManager.this.stopReconnector();
			}
		}

		private final List<InetSocketAddress> getResolvedHosts() throws UnknownHostException {
			return NettyTcpSocketManager.resolveHost(host, port);
		}
	}

	private final class QueueWriter implements Runnable {
		@Override
		public final void run() {
			Channel ch = channelRef.get();
			// Check if the channel is active
			if (ch == null || !ch.isActive()) {
				channelRef.set(null);
				return;
			}
			boolean shutdown = false;
			try {
				// Flush out all the enqueued messages
				queueMutex.lockInterruptibly();
				try {
					int elemCount = queueFile.size();
					if (elemCount == 0) {
						// nothing in the queue, this is the only legitimate time we should ever
						// shutdown the writer thread
						shutdown = true;
						return;
					}
					int writeCount = 0;
					LOGGER.debug("Flushing {} log messages from queue.", elemCount);
					byte[] bytes;
					while ((bytes = queueFile.peek()) != null) {
						if (!ch.isActive()) {
							// connection broke, we should stop attempting further writes
							break;
						}
						if (!ch.isWritable()) {
							// this indicates the write buffer is filling up, we need to back off
							break;
						}
						// Write and flush to the socket
						ChannelFuture wf = ch.writeAndFlush(Unpooled.wrappedBuffer(bytes));
						wf.await();
						if (wf.isSuccess()) {
							queueFile.remove(); // Remove the element after the write
							writeCount++;
						} else {
							// we encountered a problem writing, possibly a broken connection so we break
							// this loop
							break;
						}
					}
					LOGGER.debug("Successfully flushed {} log messages from queue.", writeCount);
				} catch (IOException e) {
					LOGGER.error(e.getLocalizedMessage(), e);
				} finally {
					queueMutex.unlock();
				}
			} catch (InterruptedException e) {
				// interrupted, we just terminate the loop
				Thread.interrupted(); // mark the thread as interrupted again
			} finally {
				if (shutdown) {
					NettyTcpSocketManager.this.stopWriter();
				}
			}
		}
	}

	private final ChannelFuture createSocket(final InetSocketAddress socketAddress) throws InterruptedException {
		return createSocket(socketAddress, socketOptions, connectTimeoutMillis, writerTimeoutMillis, bufLowWaterMark,
				bufHighWaterMark);
	}

	private static final ChannelFuture createSocket(final InetSocketAddress socketAddress,
			final SocketOptions socketOptions, final int connectTimeoutMillis, final int writerTimeoutMillis,
			final int bufLowWaterMark, final int bufHighWaterMark) throws InterruptedException {
		LOGGER.debug("Creating socket {}", socketAddress.toString());
		Bootstrap b = new Bootstrap();
		b.group(workerGroup).channel(NioSocketChannel.class)
				.option(ChannelOption.ALLOCATOR, new UnpooledByteBufAllocator(false))
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Integer.valueOf(connectTimeoutMillis));
		if (socketOptions != null) {
			if (socketOptions.isKeepAlive() != null) {
				b.option(ChannelOption.SO_KEEPALIVE, socketOptions.isKeepAlive());
			}
			// TODO oobInline
			if (socketOptions.isReuseAddress() != null) {
				b.option(ChannelOption.SO_REUSEADDR, socketOptions.isReuseAddress());
			}
			// TODO performancePreferences
			if (socketOptions.getSendBufferSize() != null) {
				b.option(ChannelOption.SO_SNDBUF, socketOptions.getSendBufferSize());
			}
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
		int bufLow = bufLowWaterMark > 0 ? bufLowWaterMark : DEFAULT_LOW_WATER_MARK; // 4 MB (low default)
		int bufHigh = bufHighWaterMark > 0 ? bufHighWaterMark : DEFAULT_HIGH_WATER_MARK; // 8 MB (high default)
		LOGGER.debug("WRITE_BUFFER_LOW_WATER_MARK size: " + bufLow);
		LOGGER.debug("WRITE_BUFFER_HIGH_WATER_MARK size: " + bufHigh);
		b.option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(bufLow, bufHigh));
		// Set the Netty handler
		int writerIdleTimeSeconds = writerTimeoutMillis / 1000;
		b.handler(new ChannelInitializer<SocketChannel>() {
			@Override
			protected void initChannel(SocketChannel ch) throws Exception {
				ch.pipeline().addFirst("idle", new IdleStateHandler(0, writerIdleTimeSeconds, 0));
				ch.pipeline().addLast(new AppenderDuplexHandler());
			}
		});
		return b.connect(socketAddress.getAddress().getHostAddress(), socketAddress.getPort());
	}

	/**
	 * Data for the factory.
	 */
	static class FactoryData {
		protected final String host;
		protected final int port;
		protected final int connectTimeoutMillis;
		protected final int writerTimeoutMillis;
		protected final int reconnectDelayMillis;
		protected final Layout<? extends Serializable> layout;
		protected final int bufferSize;
		protected final int bufLowWaterMark;
		protected final int bufHighWaterMark;
		protected final SocketOptions socketOptions;
		protected final String bufFileName;

		public FactoryData(final String host, final int port, final int connectTimeoutMillis,
				final int reconnectDelayMillis, final int writerTimeoutMillis,
				final Layout<? extends Serializable> layout, final int bufferSize, final int bufLowWaterMark,
				final int bufHighWaterMark, final SocketOptions socketOptions, final String bufFileName) {
			this.host = host;
			this.port = port;
			this.connectTimeoutMillis = connectTimeoutMillis;
			this.writerTimeoutMillis = writerTimeoutMillis;
			this.reconnectDelayMillis = reconnectDelayMillis;
			this.layout = layout;
			this.bufferSize = bufferSize;
			this.bufLowWaterMark = bufLowWaterMark;
			this.bufHighWaterMark = bufHighWaterMark;
			this.socketOptions = socketOptions;
			this.bufFileName = bufFileName;
		}

		@Override
		public final String toString() {
			return "FactoryData [host=" + host + ", port=" + port + ", connectTimeoutMillis=" + connectTimeoutMillis
					+ ", writerTimeoutMillis=" + writerTimeoutMillis + ", reconnectDelayMillis=" + reconnectDelayMillis
					+ ", layout=" + layout + ", bufferSize=" + bufferSize + ", bufLowWaterMark=" + bufLowWaterMark
					+ ", bufHighWaterMark=" + bufHighWaterMark + ", socketOptions=" + socketOptions + ", bufFileName="
					+ bufFileName + "]";
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
		@Override
		public M createManager(final String name, final T data) {
			InetAddress inetAddress;
			try {
				inetAddress = InetAddress.getByName(data.host);
			} catch (final UnknownHostException ex) {
				LOGGER.error("Could not find address of {}: {}", data.host, ex, ex);
				return null;
			}
			return createManager(name, null, inetAddress, data);
		}

		@SuppressWarnings("unchecked")
		M createManager(final String name, final Channel channel, final InetAddress inetAddress, final T data) {
			return (M) new NettyTcpSocketManager(name, channel, inetAddress, data.host, data.port,
					data.connectTimeoutMillis, data.reconnectDelayMillis, data.writerTimeoutMillis, data.layout,
					data.bufferSize, data.bufLowWaterMark, data.bufHighWaterMark, data.socketOptions, data.bufFileName);
		}

		Channel createSocket(final T data) throws Exception {
			List<InetSocketAddress> socketAddresses = resolveHost(data.host, data.port);
			Exception e = null;
			for (InetSocketAddress socketAddress : socketAddresses) {
				try {
					return NettyTcpSocketManager
							.createSocket(socketAddress, data.socketOptions, data.connectTimeoutMillis,
									data.writerTimeoutMillis, data.bufLowWaterMark, data.bufHighWaterMark)
							.syncUninterruptibly().channel();
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

	private static final List<InetSocketAddress> resolveHost(String host, int port) throws UnknownHostException {
		InetAddress[] addresses = InetAddress.getAllByName(host);
		List<InetSocketAddress> socketAddresses = new ArrayList<>(addresses.length);
		for (InetAddress address : addresses) {
			socketAddresses.add(new InetSocketAddress(address, port));
		}
		return socketAddresses;
	}

	private static final class AppenderDuplexHandler extends ChannelDuplexHandler {
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			ReferenceCountUtil.release(msg);
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			LOGGER.error(cause.getMessage(), cause);
		}

		@Override
		public final void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
			if (event instanceof IdleStateEvent && ((IdleStateEvent) event).state() == IdleState.WRITER_IDLE) {
				// Just close the connection if the idle event is triggered
				ctx.close();
			}
		}
	}

	@Override
	public final String toString() {
		return "NettyTcpSocketManager [reconnectionDelayMillis=" + reconnectionDelayMillis + ", reconnector="
				+ reconnector + ", channel=" + channelRef.get() + ", socketOptions=" + socketOptions + ", retry="
				+ retry + ", connectTimeoutMillis=" + connectTimeoutMillis + ", inetAddress=" + inetAddress + ", host="
				+ host + ", port=" + port + ", layout=" + layout + ", byteBuffer=" + byteBuffer + ", count=" + count
				+ "]";
	}
}