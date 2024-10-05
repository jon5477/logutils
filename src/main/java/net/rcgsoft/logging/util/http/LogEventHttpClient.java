package net.rcgsoft.logging.util.http;

import java.io.Closeable;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Property;

/**
 * The interface for sending a {@link LogEvent} over HTTP/S.
 * 
 * @author Jon Huang
 *
 */
public interface LogEventHttpClient extends Closeable {
	/**
	 * Initializes the HTTP client with the given parameters. This call must be made
	 * before requests can be sent.
	 * 
	 * @param connectTimeoutMillis The connect timeout (in milliseconds).
	 * @param readTimeoutMillis    The read timeout (in milliseconds).
	 * @param verifyHostname       {@code true} if the hostname should be verified
	 *                             in the TLS handshake, {@code false} to disable
	 *                             hostname checking
	 */
	void initialize(int connectTimeoutMillis, int readTimeoutMillis, boolean verifyHostname);

	/**
	 * Creates and sends a request to the {@link URL} with using the given HTTP
	 * method and headers. The {@link Layout} will be used to format the
	 * {@link LogEvent} that will be sent as the body of the HTTP request.
	 * 
	 * @param httpMethod  The HTTP method to use.
	 * @param httpHeaders The HTTP headers to send as part of this request.
	 * @param url         The {@link URL} where the request will be made.
	 * @param layout      The {@link Layout} that will be used to format the
	 *                    {@link LogEvent}.
	 * @param event       The {@link LogEvent} to serialize into the HTTP body.
	 * @return A {@link CompletableFuture} detailing the progress of this HTTP
	 *         request.
	 */
	CompletableFuture<?> sendRequest(String httpMethod, List<Property> httpHeaders, URL url, Layout<?> layout,
			LogEvent event);
}