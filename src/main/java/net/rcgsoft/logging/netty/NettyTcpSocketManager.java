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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.Logger;
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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
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
	private static final String BUFFER_NOT_NULL_MSG = "buffer cannot be null";
	private static final int MEGABYTE = 1024 * 1024;
	private static final int DEFAULT_LOW_WATER_MARK = 4 * MEGABYTE;
	private static final int DEFAULT_HIGH_WATER_MARK = 8 * MEGABYTE;
	private static final EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
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
	 * The {@link QueueFile} that contains the failed and unwritten messages. This
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
	/**
	 * Should the footer be skipped when writing?
	 */
	private final AtomicBoolean skipFooter = new AtomicBoolean(false);
	private final AtomicReference<Channel> channelRef = new AtomicReference<>();
	private final SocketOptions socketOptions;
	private final int bufLowWaterMark;
	private final int bufHighWaterMark;
	private final boolean retry;
	private final int connectTimeoutMillis;
	private final int writerTimeoutMillis;

	/**
	 * Constructor for a Netty-based TCP socket manager.
	 * 
	 * @param name                    The unique name of this appender.
	 * @param channel                 The Netty {@code Channel}.
	 * @param inetAddress             The Internet address of the host.
	 * @param host                    The hostname of the host.
	 * @param port                    The port number on the host.
	 * @param connectTimeoutMillis    The connect timeout (in milliseconds)
	 * @param writerTimeoutMillis     The writer timeout (in milliseconds)
	 * @param reconnectionDelayMillis The reconnection interval (in milliseconds)
	 * @param layout                  The logging {@code Layout}
	 * @param bufferSize              The socket write buffer size (in bytes)
	 * @param bufLowWaterMark         The low water mark of the writer buffer (if it
	 *                                falls below this {@link Channel#isWritable()}
	 *                                returns {@code true})
	 * @param bufHighWaterMark        The high water mark of the writer buffer (if
	 *                                it goes above this
	 *                                {@link Channel#isWritable()} returns
	 *                                {@code false})
	 * @param socketOptions           The socket options
	 * @param tapeBufFileName         The buffer file name for storing failed writes
	 */
	public NettyTcpSocketManager(String name, Channel channel, InetAddress inetAddress, String host, int port,
			int connectTimeoutMillis, int writerTimeoutMillis, int reconnectionDelayMillis,
			Layout<? extends Serializable> layout, int bufferSize, int bufLowWaterMark, int bufHighWaterMark,
			SocketOptions socketOptions, String tapeBufFileName) throws IOException {
		super(name, null, inetAddress, host, port, layout, false, 0);
		this.connectTimeoutMillis = connectTimeoutMillis;
		this.writerTimeoutMillis = writerTimeoutMillis;
		this.reconnectionDelayMillis = reconnectionDelayMillis;
		this.channelRef.set(channel);
		this.retry = reconnectionDelayMillis > 0;
		this.bufLowWaterMark = bufLowWaterMark;
		this.bufHighWaterMark = bufHighWaterMark;
		this.socketOptions = socketOptions;
		String bufFileName = Optional.ofNullable(tapeBufFileName).orElse("netty-log4j-buf-" + name + "-tmp.bin");
		File bufFile = new File(bufFileName);
		this.queueFile = new QueueFile.Builder(bufFile).build();
		// Start the writer thread even if the queue is empty
		this.startWriter();
	}

	public static Logger getLogger() {
		return LOGGER;
	}

	/**
	 * Creates and fetches a Netty-based TCP socket manager.
	 * 
	 * @param name                 The unique name of this appender.
	 * @param host                 The hostname of the host.
	 * @param port                 The port number on the host.
	 * @param connectTimeoutMillis The connect timeout (in milliseconds)
	 * @param writerTimeoutMillis  The writer timeout (in milliseconds)
	 * @param reconnectDelayMillis The reconnection interval (in milliseconds)
	 * @param layout               The logging {@code Layout}
	 * @param bufferSize           The socket write buffer size (in bytes)
	 * @param bufLowWaterMark      The low water mark of the writer buffer (if it
	 *                             falls below this {@link Channel#isWritable()}
	 *                             returns {@code true})
	 * @param bufHighWaterMark     The high water mark of the writer buffer (if it
	 *                             goes above this {@link Channel#isWritable()}
	 *                             returns {@code false})
	 * @param socketOptions        The socket options
	 * @param bufFileName          The buffer file name for storing failed writes
	 * @return The created Netty-based TCP Socket Manager.
	 */
	public static NettyTcpSocketManager getSocketManager(String name, String host, int port, int connectTimeoutMillis,
			int writerTimeoutMillis, int reconnectDelayMillis, Layout<? extends Serializable> layout, int bufferSize,
			int bufLowWaterMark, int bufHighWaterMark, SocketOptions socketOptions, String bufFileName) {
		if (Strings.isEmpty(host)) {
			throw new IllegalArgumentException("A host name is required");
		}
		if (port <= 0) {
			port = DEFAULT_PORT;
		}
		if (reconnectDelayMillis == 0) {
			reconnectDelayMillis = DEFAULT_RECONNECTION_DELAY_MILLIS;
		}
		if (writerTimeoutMillis < 0) {
			writerTimeoutMillis = 0;
		}
		return (NettyTcpSocketManager) getManager(name,
				new FactoryData(host, port, connectTimeoutMillis, reconnectDelayMillis, writerTimeoutMillis, layout,
						bufferSize, bufLowWaterMark, bufHighWaterMark, socketOptions, bufFileName),
				FACTORY);
	}

	/**
	 * Checks if the writer is running and starts it if it is not running.
	 */
	private void startWriter() {
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
	private void startReconnector() {
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
	private void stopReconnector() {
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
	private void stopWriter() {
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
	 * Fetches the active {@link Channel} as an {@link Optional}. This will return
	 * an {@link Optional#empty()} if the {@link Channel} is null or inactive.
	 * 
	 * @return The active {@link Channel} as an {@link Optional} or
	 *         {@link Optional#empty()}.
	 */
	private Optional<Channel> getActiveChannel() {
		Channel ch = channelRef.get();
		if (ch != null && ch.isActive()) {
			return Optional.of(ch);
		}
		channelRef.set(null);
		return Optional.empty();
	}

	/**
	 * Indicate whether the footer should be skipped or not.
	 * 
	 * @param skipFooter {@code true} if the footer should be skipped.
	 */
	@Override
	public final void skipFooter(boolean skipFooter) {
		this.skipFooter.set(skipFooter);
	}

	@Override
	protected final void writeHeader(OutputStream os) {
		// Fetch the channel for performing the write
		Optional<Channel> ch = getActiveChannel();
		if (layout != null && ch.isPresent()) {
			byte[] header = layout.getHeader();
			if (header != null) {
				writeToChannel(ch.get(), header, 0, header.length, false);
			}
		}
	}

	/**
	 * Writes the footer.
	 */
	@Override
	protected final void writeFooter() {
		if (layout == null || skipFooter.get()) {
			return;
		}
		byte[] footer = layout.getFooter();
		if (footer != null) {
			write(footer);
		}
	}

	/**
	 * Performs a write to the underlying socket. If the write is unsuccessful, the
	 * data will be preserved and persisted to disk. This method call does NOT block
	 * but does obtain a monitor in cases where the socket is not available and the
	 * reconnector thread needs to be started.
	 */
	@SuppressWarnings({ "sync-override", "java:S3551", "UnsynchronizedOverridesSynchronized" })
	@Override
	protected final void write(byte[] bytes, int offset, int length, boolean immediateFlush) {
		// Fetch the channel for performing the write
		Optional<Channel> ch = getActiveChannel();
		if (ch.isPresent()) {
			// check if the channel is active
			writeToChannel(ch.get(), bytes, offset, length, immediateFlush);
		} else {
			// cannot write since the channel is null OR no longer active
			handleFailedWrite(bytes, offset, length);
		}
	}

	@SuppressWarnings({ "sync-override", "java:S3551", "UnsynchronizedOverridesSynchronized" })
	@Override
	protected final void writeToDestination(byte[] bytes, int offset, int length) {
		// Fetch the channel for performing the write
		Optional<Channel> ch = getActiveChannel();
		if (ch.isPresent()) {
			// check if the channel is active
			writeToChannel(ch.get(), bytes, offset, length, true);
		} else {
			// cannot write since the channel is null OR no longer active
			handleFailedWrite(bytes, offset, length);
		}
	}

	/**
	 * Performs a flush to the underlying socket.
	 */
	@SuppressWarnings({ "sync-override", "java:S3551", "UnsynchronizedOverridesSynchronized" })
	@Override
	protected final void flushDestination() {
		getActiveChannel().ifPresent(Channel::flush);
	}

	/**
	 * Writes the following buffer to the {@link Channel} and enqueues the write on
	 * failure.
	 * 
	 * @param ch             The {@link Channel} to write to
	 * @param bytes          The buffer of bytes to read from
	 * @param offset         The starting index of the buffer to read from
	 * @param length         The length of bytes to read
	 * @param immediateFlush {@code true} if flushing should occur immediately after
	 *                       writing
	 * @return the {@link ChannelFuture} that contains the status of the write
	 */
	private ChannelFuture writeToChannel(Channel ch, byte[] bytes, int offset, int length, boolean immediateFlush) {
		Objects.requireNonNull(ch, "channel cannot be null");
		Objects.requireNonNull(bytes, "bytes buffer cannot be null");
		// Wrap the existing buffer to save memory
		ByteBuf buffer = Unpooled.wrappedBuffer(bytes, offset, length);
		ChannelFuture cf = immediateFlush ? ch.writeAndFlush(buffer) : ch.write(buffer);
		return cf.addListener(f -> {
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
	private void handleFailedWrite(byte[] bytes, int offset, int length) {
		Objects.requireNonNull(bytes, BUFFER_NOT_NULL_MSG);
		// the connection was lost, start up the reconnector
		startReconnector();
		// start up the writer
		startWriter();
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
	private void handleFailedWrite(byte[] bytes, int offset, int length, Throwable cause) {
		Objects.requireNonNull(bytes, BUFFER_NOT_NULL_MSG);
		handleFailedWrite(bytes, offset, length);
		LOGGER.debug("Could not successfully write to socket: {}", cause.getLocalizedMessage(), cause);
	}

	@SuppressWarnings({ "sync-override", "java:S3551", "UnsynchronizedOverridesSynchronized" })
	@Override
	protected final boolean closeOutputStream() {
		this.stopReconnector();
		this.stopWriter();
		Channel oldChannel = channelRef.getAndSet(null);
		if (oldChannel != null) {
			if (oldChannel.isActive()) {
				oldChannel.flush();
			}
			try {
				ChannelFuture closeFuture = oldChannel.close().sync();
				return closeFuture.isSuccess();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			} catch (Exception e) {
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
	private void enqueueMessage(byte[] bytes, int offset, int length) {
		Objects.requireNonNull(bytes, BUFFER_NOT_NULL_MSG);
		queueMutex.lock();
		try {
			this.queueFile.add(bytes, offset, length);
		} catch (IOException e) {
			LOGGER.error("Failed to add message to buffer: {}", e.getMessage(), e);
		} finally {
			queueMutex.unlock();
		}
	}

	/**
	 * Gets this NettyTcpSocketManager's content format. Specified by:
	 * <ul>
	 * <li>Key: "protocol" Value: "tcp"</li>
	 * <li>Key: "direction" Value: "out"</li>
	 * </ul>
	 *
	 * @return Map of content format keys supporting NettyTcpSocketManager
	 */
	@Override
	public final Map<String, String> getContentFormat() {
		Map<String, String> result = new HashMap<>(super.getContentFormat());
		result.put("protocol", "tcp");
		result.put("direction", "out");
		return result;
	}

	/**
	 * Handles reconnecting a {@link Channel} in a {@link Runnable}.
	 * 
	 * @author Jon Huang
	 *
	 */
	private final class Reconnector implements Runnable {
		@Override
		public final void run() {
			boolean stop = false;
			try {
				List<InetSocketAddress> resolvedHosts = getResolvedHosts();
				if (resolvedHosts.size() == 1) {
					// single host, connect once synchronously
					stop = tryConnect(resolvedHosts.get(0));
				} else {
					// multiple hosts, try each one synchronously
					for (InetSocketAddress host : resolvedHosts) {
						if (Thread.currentThread().isInterrupted()) {
							throw new InterruptedException();
						}
						stop = tryConnect(host);
						if (stop) {
							break;
						}
					}
				}
			} catch (InterruptedException e) {
				// interrupted - used as a signal to abort this thread
				stop = true;
				LOGGER.debug("Reconnection interrupted.");
				Thread.currentThread().interrupt();
			} catch (UnknownHostException e) {
				// failed to resolve host, we can try again on the next scheduled time
				LOGGER.debug("Failed to resolve host: {}", e.getLocalizedMessage(), e);
			}
			if (stop) {
				NettyTcpSocketManager.this.stopReconnector();
			}
		}

		private List<InetSocketAddress> getResolvedHosts() throws UnknownHostException {
			return NettyTcpSocketManager.resolveHost(host, port);
		}

		private ChannelFuture createSocket(InetSocketAddress socketAddress) {
			return NettyTcpSocketManager.createSocket(socketAddress, socketOptions, connectTimeoutMillis,
					writerTimeoutMillis, bufLowWaterMark, bufHighWaterMark);
		}

		private CompletableFuture<Channel> connect(InetSocketAddress sockAddr) {
			CompletableFuture<Channel> cf = new CompletableFuture<>();
			createSocket(sockAddr).addListener((ChannelFuture f) -> {
				// Check the status of the reconnection
				if (f.isSuccess()) {
					Channel ch = f.channel();
					if (ch.isActive()) {
						LOGGER.debug("Successfully connected: {}", ch);
						cf.complete(ch);
					} else {
						LOGGER.debug("Failed to connect. Channel not active");
						cf.completeExceptionally(new IOException("channel is not active"));
					}
				} else if (f.isCancelled()) {
					cf.cancel(false);
				} else {
					Throwable cause = f.cause();
					LOGGER.debug("Failed to connect: {}", cause.getLocalizedMessage(), cause);
					cf.completeExceptionally(cause);
				}
			});
			return cf;
		}

		private boolean tryConnect(InetSocketAddress sockAddr) throws InterruptedException {
			try {
				Channel ch = connect(sockAddr).get();
				channelRef.set(ch);
				// successfully reconnected, now we need to abort the reconnector thread
				return true;
			} catch (ExecutionException e) {
				// failed to connect, try again on the next scheduled time
				Throwable cause = e.getCause();
				LOGGER.debug("Failed to connect to host {}: {}", sockAddr, cause.getLocalizedMessage(), cause);
			}
			return false;
		}
	}

	private final class QueueWriter implements Runnable {
		@Override
		public final void run() {
			Optional<Channel> oc = getActiveChannel();
			if (oc.isEmpty()) {
				return;
			}
			Channel ch = oc.get();
			boolean shutdown = false;
			try {
				// Flush out all the enqueued messages
				queueMutex.lockInterruptibly();
				try {
					if (queueFile.isEmpty()) {
						// nothing in the queue, this is the only legitimate time we should ever
						// shutdown the writer thread
						shutdown = true;
						return;
					}
					int elemCount = queueFile.size();
					int writeCount = 0;
					LOGGER.debug("Flushing {} log messages from queue.", elemCount);
					byte[] bytes;
					while ((bytes = queueFile.peek()) != null) {
						if (Thread.currentThread().isInterrupted()) {
							// thread interrupted, stop immediately
							throw new InterruptedException();
						}
						if (!ch.isActive() || // connection broke, we should stop attempting further writes
								!ch.isWritable()) { // channel write buffer is filling up, back off
							break;
						}
						// Write and flush to the socket
						ChannelFuture wf = ch.writeAndFlush(Unpooled.wrappedBuffer(bytes)).await();
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
				} finally {
					queueMutex.unlock();
				}
			} catch (IOException e) {
				LOGGER.error(e.getLocalizedMessage(), e);
			} catch (InterruptedException e) {
				// interrupted, we just terminate the loop
				Thread.currentThread().interrupt(); // mark the thread as interrupted again
			} finally {
				if (shutdown) {
					NettyTcpSocketManager.this.stopWriter();
				}
			}
		}
	}

	private static ChannelFuture createSocket(InetSocketAddress socketAddress, SocketOptions socketOptions,
			int connectTimeoutMillis, int writerTimeoutMillis, int bufLowWaterMark, int bufHighWaterMark) {
		LOGGER.debug("Creating socket {}", socketAddress);
		Bootstrap b = new Bootstrap();
		b.group(workerGroup).channel(NioSocketChannel.class)
				.option(ChannelOption.ALLOCATOR, new UnpooledByteBufAllocator(false))
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Integer.valueOf(connectTimeoutMillis));
		if (socketOptions != null) {
			if (socketOptions.isKeepAlive() != null) {
				b.option(ChannelOption.SO_KEEPALIVE, socketOptions.isKeepAlive());
			}
			if (socketOptions.isReuseAddress() != null) {
				b.option(ChannelOption.SO_REUSEADDR, socketOptions.isReuseAddress());
			}
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
			Integer actualTrafficClass = socketOptions.getActualTrafficClass();
			if (actualTrafficClass != null) {
				b.option(ChannelOption.IP_TOS, actualTrafficClass);
			}
		}
		int bufLow = bufLowWaterMark > 0 ? bufLowWaterMark : DEFAULT_LOW_WATER_MARK; // 4 MB (low default)
		int bufHigh = bufHighWaterMark > 0 ? bufHighWaterMark : DEFAULT_HIGH_WATER_MARK; // 8 MB (high default)
		LOGGER.debug("WRITE_BUFFER_LOW_WATER_MARK size: {}", bufLow);
		LOGGER.debug("WRITE_BUFFER_HIGH_WATER_MARK size: {}", bufHigh);
		b.option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(bufLow, bufHigh));
		// Set the Netty handler
		b.handler(new ChannelInitializer<SocketChannel>() {
			@Override
			protected void initChannel(SocketChannel ch) throws Exception {
				if (writerTimeoutMillis > 0) {
					ch.pipeline().addLast(new IdleStateHandler(0, writerTimeoutMillis, 0, TimeUnit.MILLISECONDS));
				}
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

		public FactoryData(String host, int port, int connectTimeoutMillis, int reconnectDelayMillis,
				int writerTimeoutMillis, Layout<? extends Serializable> layout, int bufferSize, int bufLowWaterMark,
				int bufHighWaterMark, SocketOptions socketOptions, String bufFileName) {
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
	 * Factory to create a NettyTcpSocketManager.
	 *
	 * @param <M> The manager type.
	 * @param <T> The factory data type.
	 */
	protected static class NettyTcpSocketManagerFactory<M extends NettyTcpSocketManager, T extends FactoryData>
			implements ManagerFactory<M, T> {
		@Override
		public final M createManager(String name, T data) {
			try {
				InetAddress inetAddress = InetAddress.getByName(data.host);
				Channel ch = createSocket(data);
				return createManager(name, ch, inetAddress, data);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (UnknownHostException e) {
				LOGGER.error("Could not find address of {}: {}", data.host, e.getLocalizedMessage(), e);
			} catch (IOException e) {
				LOGGER.error("Failed to create manager: {}", e.getLocalizedMessage(), e);
			}
			return null;
		}

		@SuppressWarnings("unchecked")
		final M createManager(String name, Channel channel, InetAddress inetAddress, T data) throws IOException {
			return (M) new NettyTcpSocketManager(name, channel, inetAddress, data.host, data.port,
					data.connectTimeoutMillis, data.writerTimeoutMillis, data.reconnectDelayMillis, data.layout,
					data.bufferSize, data.bufLowWaterMark, data.bufHighWaterMark, data.socketOptions, data.bufFileName);
		}

		final Channel createSocket(T data) throws InterruptedException, IOException {
			List<InetSocketAddress> socketAddresses = resolveHost(data.host, data.port);
			Throwable cause = null;
			for (InetSocketAddress socketAddress : socketAddresses) {
				try {
					ChannelFuture cf = NettyTcpSocketManager.createSocket(socketAddress, data.socketOptions,
							data.connectTimeoutMillis, data.writerTimeoutMillis, data.bufLowWaterMark,
							data.bufHighWaterMark);
					return cf.sync().channel();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					cause = e;
				} catch (Exception e) {
					cause = e;
				}
			}
			throw new IOException(errorMessage(data, socketAddresses), cause);
		}

		private String errorMessage(T data, List<InetSocketAddress> socketAddresses) {
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

	private static List<InetSocketAddress> resolveHost(String host, int port) throws UnknownHostException {
		InetAddress[] addresses = InetAddress.getAllByName(host);
		List<InetSocketAddress> socketAddresses = new ArrayList<>(addresses.length);
		for (InetAddress address : addresses) {
			socketAddresses.add(new InetSocketAddress(address, port));
		}
		return socketAddresses;
	}

	/**
	 * Called to signify that this Manager is no longer required by an Appender.
	 */
	@Override
	public final void close() {
		try {
			workerGroup.shutdownGracefully();
			executor.shutdownGracefully();
		} finally {
			super.close();
		}
	}

	@Override
	public final String toString() {
		return "NettyTcpSocketManager [reconnectionDelayMillis=" + reconnectionDelayMillis + ", channel="
				+ channelRef.get() + ", socketOptions=" + socketOptions + ", retry=" + retry + ", connectTimeoutMillis="
				+ connectTimeoutMillis + ", writerTimeoutMillis=" + writerTimeoutMillis + ", inetAddress=" + inetAddress
				+ ", host=" + host + ", port=" + port + ", layout=" + layout + ", byteBuffer=" + byteBuffer + ", count="
				+ count + "]";
	}
}