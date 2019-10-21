package net.rcgsoft.logging.netty;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.core.net.AbstractSocketManager;
import org.apache.logging.log4j.core.net.SocketOptions;
import org.apache.logging.log4j.util.Strings;

import com.squareup.tape2.ObjectQueue;
import com.squareup.tape2.ObjectQueue.Converter;
import com.squareup.tape2.QueueFile;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.PromiseCombiner;

/**
 * Manager of TCP Socket connections.
 */
public class NettyTcpSocketManager extends AbstractSocketManager {
	private static final int MEGABYTE = 1024 * 1024;
	private static final int DEFAULT_LOW_WATER_MARK = 4 * MEGABYTE;
	private static final int DEFAULT_HIGH_WATER_MARK = 8 * MEGABYTE;
	private static final EventLoopGroup workerGroup = new NioEventLoopGroup(2);
	private static final ExecutorService executor = Executors.newSingleThreadExecutor();
	/**
	 * The default reconnection delay (1000 milliseconds or 1 second).
	 */
	public static final int DEFAULT_RECONNECTION_DELAY_MILLIS = 1000;
	/**
	 * The default port number of remote logging server (4560).
	 */
	private static final int DEFAULT_PORT = 4560;
	private static final NettyTcpSocketManagerFactory<NettyTcpSocketManager, FactoryData> FACTORY = new NettyTcpSocketManagerFactory<>();
	private final Lock mutex = new ReentrantLock();
	private final int reconnectionDelayMillis;
	private final Reconnector reconnector = new Reconnector();
	private Future<?> reconFuture;
	private final AtomicBoolean socketInitialized = new AtomicBoolean();
	private final AtomicReference<Channel> channelRef = new AtomicReference<>();
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
		super(name, null, inetAddress, host, port, layout, true, 0);
		this.connectTimeoutMillis = connectTimeoutMillis;
		this.reconnectionDelayMillis = reconnectionDelayMillis;
		this.channelRef.set(channel);
		this.immediateFail = immediateFail;
		this.retry = reconnectionDelayMillis > 0;
		this.socketInitialized.set(channel != null);
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

	@SuppressWarnings("sync-override")
	@Override
	protected void write(final byte[] bytes, final int offset, final int length, final boolean immediateFlush) {
		Channel channel = channelRef.get();
		if (channel == null) {
			if (!immediateFail) {
				reconnector.latch();
			}
			if (channel == null && socketInitialized.get()) {
				throw new AppenderLoggingException("Error writing to " + getName() + ": socket not available");
			}
		}
		try {
			Channel ch = channelRef.get();
			if (ch != null && ch.isActive()) {
				writeAndFlush(ch, bytes, offset, length)
						.addListener(new CheckConnectionListener(bytes, offset, length, immediateFlush));
			} else {
				handleWriteException(bytes, offset, length, immediateFlush, null);
			}
		} catch (Exception causeEx) {
			handleWriteException(bytes, offset, length, immediateFlush, causeEx);
		}
	}

	private boolean isReconnectorRunning() {
		mutex.lock();
		try {
			return reconFuture != null && !reconFuture.isDone();
		} finally {
			mutex.unlock();
		}
	}

	private void handleWriteException(final byte[] bytes, final int offset, final int length,
			final boolean immediateFlush, Throwable causeEx) {
		if (retry && !isReconnectorRunning()) {
			final String config = inetAddress + ":" + port;
			try {
				reconnector.reconnect().sync();
			} catch (Exception reconnEx) {
				// We failed to reconnect, we now need to start the reconnector thread
				mutex.lock();
				try {
					// We are only creating a future if the current one is null or old one has
					// completed
					if (reconFuture == null || reconFuture.isDone()) {
						reconFuture = executor.submit(reconnector);
					}
				} finally {
					mutex.unlock();
				}
				LOGGER.debug("Cannot reestablish socket connection to {}: {}; starting reconnector thread", config,
						reconnEx.getLocalizedMessage(), reconnEx);
				throw new AppenderLoggingException(String.format("Error sending to %s for %s", getName(), config),
						causeEx);
			}
			try {
				Channel ch = channelRef.get();
				writeAndFlush(ch, bytes, offset, length);
			} catch (final Exception e) {
				throw new AppenderLoggingException(
						String.format("Error writing to %s after reestablishing connection for %s", getName(), config),
						causeEx);
			}
		} else {
			reconnector.addMessage(bytes, offset, length, immediateFlush);
		}
	}

	private ChannelFuture writeAndFlush(Channel ch, final byte[] bytes, final int offset, final int length) {
		// Allocate a buffer of the length we plan to write
		ByteBuf buffer = ch.alloc().buffer(length, length);
		buffer.writeBytes(bytes, offset, length);
		return ch.writeAndFlush(buffer);
	}

	private class CheckConnectionListener implements ChannelFutureListener {
		private final byte[] bytes;
		private final int offset;
		private final int length;
		private final boolean immediateFlush;

		private CheckConnectionListener(byte[] bytes, int offset, int length, boolean immediateFlush) {
			this.bytes = bytes;
			this.offset = offset;
			this.length = length;
			this.immediateFlush = immediateFlush;
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			if (!future.isSuccess()) {
				Throwable t = future.cause();
				NettyTcpSocketManager.this.handleWriteException(bytes, offset, length, immediateFlush, t);
			}
		}
	}

	@SuppressWarnings("sync-override")
	@Override
	protected boolean closeOutputStream() {
		reconnector.shutdown();
		mutex.lock();
		try {
			if (reconFuture != null) {
				reconFuture.cancel(true);
			}
			reconFuture = null;
		} finally {
			mutex.unlock();
		}
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
	private class Reconnector implements Runnable {
		private final Lock lock = new ReentrantLock();
		private final CountDownLatch latch = new CountDownLatch(1);
		private final AtomicBoolean shutdown = new AtomicBoolean();
		private final QueueFile queueFile;
		private final ObjectQueue<ByteBuf> messages;

		public Reconnector() {
			try {
				UUID uuid = UUID.randomUUID();
				File bufFile = File.createTempFile("netty-log4j-buf-", uuid.toString() + ".tmp", new File("."));
				bufFile.deleteOnExit();
				queueFile = new QueueFile.Builder(bufFile).build();
				messages = ObjectQueue.create(queueFile, new Converter<ByteBuf>() {
					@Override
					public ByteBuf from(byte[] source) throws IOException {
						return Unpooled.wrappedBuffer(source);
					}

					@Override
					public void toStream(ByteBuf value, OutputStream sink) throws IOException {
						for (int i = value.readerIndex(), n = value.writerIndex(); i < n; i++) {
							sink.write(value.getByte(i));
						}
						sink.flush();
					}
				});
			} catch (IOException e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}

		public void latch() {
			try {
				latch.await();
			} catch (final InterruptedException ex) {
				// Bubble up the interrupted status
				Thread.currentThread().interrupt();
			}
		}

		public void shutdown() {
			shutdown.set(true);
		}

		public void addMessage(byte[] bytes, int offset, int length, boolean immediateFlush) {
			ByteBuf buf = Unpooled.buffer(length, length);
			buf.writeBytes(bytes, offset, length);
			// Handle the off case when I add a message AFTER the channel has reconnected
			Channel ch = channelRef.get();
			if (ch != null && ch.isActive()) {
				// We reconnected BEFORE this message was added, just perform a flush now
				try {
					ch.writeAndFlush(buf).addListener((ChannelFuture future) -> {
						// If the write failed to complete, requeue the message since we are
						// reconnecting
						if (future.isDone() && !future.isSuccess()) {
							ByteBuf newbuf = Unpooled.buffer(length, length);
							newbuf.writeBytes(bytes, offset, length);
							addMessage(newbuf);
						}
					});
				} catch (Exception e) {
					LOGGER.error("Unable to write message: {}", e.getMessage(), e);
					addMessage(buf);
				}
			} else {
				addMessage(buf);
			}
		}

		private void addMessage(ByteBuf buf) {
			Objects.requireNonNull(buf);
			lock.lock();
			try {
				messages.add(buf);
			} catch (IOException e) {
				LOGGER.error("Failed to add message to buffer: {}", e.getMessage(), e);
			} finally {
				lock.unlock();
			}
		}

		@Override
		public void run() {
			// When this thread is started, default to false
			shutdown.set(false);
			// While we're not alerted of a shutdown, keep looping
			while (!shutdown.get()) {
				try {
					Thread.sleep(reconnectionDelayMillis);
					ChannelFuture cf = reconnect();
					// Wait for the future to complete
					cf.sync();
					// Flush out all the queued messages
					Channel ch = cf.channel();
					lock.lock();
					try {
						LOGGER.debug("Flushing {} log messages from queue.", messages.size());
						PromiseCombiner all = new PromiseCombiner(workerGroup.next());
						for (Iterator<ByteBuf> i = messages.iterator(); i.hasNext();) {
							// Make sure we are not writing too fast
							if (!ch.isWritable()) {
								// Message is not writable...we need to slow down
								// Flush the write buffers to make new space for writes
								ch.flush();
								// Create an aggregation of all write promises
								ChannelPromise aggregatePromise = ch.newPromise();
								all.finish(aggregatePromise);
								// Wait on all the write promises, once they are complete we can hopefully keep writing again
								aggregatePromise.await();
							} else {
								ByteBuf msg = i.next();
								all.add(ch.write(msg));
							}
						}
						LOGGER.debug("Successfully flushed {} log messages from queue.", messages.size());
						messages.clear();
					} finally {
						lock.unlock();
					}
				} catch (final InterruptedException ie) {
					LOGGER.debug("Reconnection interrupted.");
					Thread.currentThread().interrupt();
				} catch (final ConnectException ex) {
					LOGGER.debug("{}:{} refused connection", host, port);
				} catch (final Exception e) {
					LOGGER.error("Unable to reconnect to {}:{}", host, port, e);
				} finally {
					socketInitialized.set(true);
					latch.countDown();
				}
			}
		}

		ChannelFuture reconnect() throws Exception {
			List<InetSocketAddress> socketAddresses = NettyTcpSocketManagerFactory.resolver.resolveHost(host, port);
			if (socketAddresses.size() == 1) {
				LOGGER.debug("Reconnecting " + socketAddresses.get(0));
				return connect(socketAddresses.get(0));
			} else {
				Exception ex = null;
				for (InetSocketAddress socketAddress : socketAddresses) {
					try {
						LOGGER.debug("Reconnecting " + socketAddress);
						return connect(socketAddress);
					} catch (Exception e) {
						ex = e;
					}
				}
				throw ex;
			}
		}

		private ChannelFuture connect(InetSocketAddress socketAddress) throws Exception {
			ChannelFuture future = createSocket(socketAddress);
			future.addListener((ChannelFuture cf) -> {
				if (cf.isSuccess()) {
					Channel channel = cf.channel();
					Channel oldChannel = NettyTcpSocketManager.this.channelRef.get();
					// Close the old channel (if it exists)
					if (oldChannel != null && (oldChannel.isActive() || oldChannel.isOpen())) {
						oldChannel.close();
					}
					// Set the new channel
					NettyTcpSocketManager.this.channelRef.set(channel);
					// When we reconnect, go ahead and terminate the thread
					this.shutdown.set(true);
					String type = oldChannel != null ? "reestablished" : "established";
					LOGGER.debug("Connection to {}:{} {}: {}", host, port, type, channel);
				}
			});
			return future;
		}

		@Override
		public String toString() {
			return "Reconnector [latch=" + latch + ", shutdown=" + shutdown + "]";
		}
	}

	protected ChannelFuture createSocket(final InetSocketAddress socketAddress) throws InterruptedException {
		return createSocket(socketAddress, socketOptions, connectTimeoutMillis);
	}

	protected static ChannelFuture createSocket(final InetSocketAddress socketAddress,
			final SocketOptions socketOptions, final int connectTimeoutMillis) throws InterruptedException {
		LOGGER.debug("Creating socket {}", socketAddress.toString());
		Bootstrap b = new Bootstrap();
		b.group(workerGroup).channel(NioSocketChannel.class).option(ChannelOption.ALLOCATOR,
				PooledByteBufAllocator.DEFAULT);
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
		int lowWaterMark;
		try {
			lowWaterMark = Integer.parseInt(System.getProperty("writeBufferLowWaterMark", String.valueOf(4 * 1024 * 1024)));
		} catch (NumberFormatException e) {
			lowWaterMark = DEFAULT_LOW_WATER_MARK;
			LOGGER.debug("Error reading system property \"writeBufferLowWaterMark\", defaulting to {} MB", (lowWaterMark / MEGABYTE));
		}
		int highWaterMark;
		try {
			highWaterMark = Integer.parseInt(System.getProperty("writeBufferHighWaterMark", String.valueOf(8 * 1024 * 1024)));
		} catch (NumberFormatException e) {
			highWaterMark = DEFAULT_HIGH_WATER_MARK;
			LOGGER.debug("Error reading system property \"writeBufferHighWaterMark\", defaulting to {} MB", (highWaterMark / MEGABYTE));
		}
		b.option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(1 * 1024 * 1024, 2 * 1024 * 1024)); // 1 MB (low) 2 MB (high)
		// Set the Netty handler
		b.handler(new ChannelInitializer<SocketChannel>() {
			@Override
			protected void initChannel(SocketChannel ch) throws Exception {
				ch.pipeline().addLast(new AppenderInboundHandler());
				ch.pipeline().addLast(new ChannelOutboundHandlerAdapter());
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
					return NettyTcpSocketManager
							.createSocket(socketAddress, data.socketOptions, data.connectTimeoutMillis)
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
				+ reconnector + ", channel=" + channelRef.get() + ", socketOptions=" + socketOptions + ", retry="
				+ retry + ", immediateFail=" + immediateFail + ", connectTimeoutMillis=" + connectTimeoutMillis
				+ ", inetAddress=" + inetAddress + ", host=" + host + ", port=" + port + ", layout=" + layout
				+ ", byteBuffer=" + byteBuffer + ", count=" + count + "]";
	}

	private static class AppenderInboundHandler extends ChannelInboundHandlerAdapter {
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			ReferenceCountUtil.release(msg);
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			LOGGER.error(cause.getMessage(), cause);
		}
	}
}