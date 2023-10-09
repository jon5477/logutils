package net.rcgsoft.logging.util;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Property;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.http.HttpHeaderNames;
import net.rcgsoft.logging.util.http.Hc4FutureCallback;
import net.rcgsoft.logging.util.http.Hc5FutureCallback;

/**
 * @author Jon Huang
 */
public class HttpClient implements Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpClient.class);
	private AsyncHttpClient client;
	private CloseableHttpAsyncClient httpClient4;
	private org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient httpClient5;

	/**
	 * Attempts to instantiate the Netty-based AsyncHttpClient (AHC) via reflection.
	 * 
	 * @param connectTimeoutMillis The connect timeout in milliseconds
	 * @param readTimeoutMillis    The read timeout in milliseconds
	 * @return the {@link AsyncHttpClient} instance or {@code null}
	 */
	private AsyncHttpClient buildAsyncHttpClient(int connectTimeoutMillis, int readTimeoutMillis) {
		try {
			Class.forName("org.asynchttpclient.AsyncHttpClient");
			Class<?> dslClass = Class.forName("org.asynchttpclient.Dsl");
			Object cfgBuilder = Class.forName("org.asynchttpclient.DefaultAsyncHttpClientConfig.Builder")
					.getConstructor().newInstance();
			java.lang.reflect.Method asyncHttpClientMethod = dslClass.getMethod("asyncHttpClient",
					DefaultAsyncHttpClientConfig.Builder.class);
			return (AsyncHttpClient) asyncHttpClientMethod.invoke(cfgBuilder);
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException
				| IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			// Not loaded on the classpath
			return null;
		}
	}

	/**
	 * Attempts to instantiate the Apache Asynchronous HTTP 5.x client via
	 * reflection.
	 * 
	 * @param connectTimeoutMillis The connect timeout in milliseconds
	 * @param readTimeoutMillis    The read timeout in milliseconds
	 * @return the
	 *         {@link org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient}
	 *         instance or {@code null}
	 */
	private org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient buildApacheHC5Client(
			int connectTimeoutMillis, int readTimeoutMillis) {
		try {
			Class.forName("org.apache.hc.core5.reactor.IOReactorConfig");
			Class.forName("org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient");
			Class.forName("org.apache.hc.client5.http.impl.async.HttpAsyncClients");
			IOReactorConfig ioReactorConfig = IOReactorConfig.custom().setIoThreadCount(4)
					.setSoTimeout(Timeout.ofSeconds(5)).build();
			org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient client = org.apache.hc.client5.http.impl.async.HttpAsyncClients
					.custom().setIOReactorConfig(ioReactorConfig).build();
			return client;
		} catch (ClassNotFoundException e) {
			// Not loaded on the classpath
			return null;
		}
	}

	/**
	 * Attempts to instantiate the Apache Asynchronous HTTP 4.x client via
	 * reflection.
	 * 
	 * @param connectTimeoutMillis The connect timeout in milliseconds
	 * @param readTimeoutMillis    The read timeout in milliseconds
	 * @return the {@link CloseableHttpAsyncClient} instance or {@code null}
	 */
	private CloseableHttpAsyncClient buildApacheHCClient(int connectTimeoutMillis, int readTimeoutMillis) {
		try {
			Class.forName("org.apache.http.impl.nio.client.CloseableHttpAsyncClient");
			Class.forName("org.apache.http.impl.nio.client.HttpAsyncClients");
			CloseableHttpAsyncClient client = HttpAsyncClients.createDefault();
			return client;
		} catch (ClassNotFoundException e) {
			// Not loaded on the classpath
			e.printStackTrace();
			return null;
		}
	}

	public HttpClient(int connectTimeoutMillis, int readTimeoutMillis) {
		// Determine the underlying HTTP client to use based on what classes we have
		// loaded
		// Prefer AHC since this is non-blocking and uses Netty
		this.client = buildAsyncHttpClient(connectTimeoutMillis, readTimeoutMillis);
		// Fallback to Apache Async HTTP Client 5.x
		if (this.client == null) {
			org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient client = buildApacheHC5Client(
					connectTimeoutMillis, readTimeoutMillis);
			if (client != null) {
				LOGGER.debug("Loaded Apache Async HTTP Client 5.x");
			}
			this.httpClient5 = client;
		} else {
			LOGGER.debug("Loaded Netty Async HTTP Client");
		}
		// Fallback to Apache Async HTTP Client 4.x
		if (this.client == null && this.httpClient5 == null) {
			CloseableHttpAsyncClient client = buildApacheHCClient(connectTimeoutMillis, readTimeoutMillis);
			if (client != null) {
				LOGGER.debug("Loaded Apache Async HTTP Client 4.x");
			}
			this.httpClient4 = client;
		}
	}

	public CompletableFuture<?> makeRequest(String httpMethod, List<Property> httpHeaders, URL url, Layout<?> layout,
			LogEvent event) {
		if (this.client != null) {
			return this.makeAHCRequest(httpMethod, httpHeaders, url, layout, event);
		}
		if (this.httpClient5 != null) {
			return this.makeApacheHC5Request(httpMethod, httpHeaders, url, layout, event);
		}
		return this.makeApacheHCRequest(httpMethod, httpHeaders, url, layout, event);
	}

	private CompletableFuture<Response> makeAHCRequest(String httpMethod, List<Property> httpHeaders, URL url,
			Layout<?> layout, LogEvent event) {
		RequestBuilder reqBuilder = Dsl.request(httpMethod, url.toString()).addHeader(HttpHeaderNames.CONTENT_TYPE,
				layout.getContentType());
		if (httpHeaders != null) {
			for (Property header : httpHeaders) {
				reqBuilder.addHeader(header.getName(), header.getValue());
			}
		}
		reqBuilder.setBody(layout.toByteArray(event));
		ListenableFuture<Response> respFuture = client.executeRequest(reqBuilder);
		return respFuture.toCompletableFuture();
	}

	private CompletableFuture<SimpleHttpResponse> makeApacheHC5Request(String httpMethod, List<Property> httpHeaders,
			URL url, Layout<?> layout, LogEvent event) {
		if (this.httpClient5.getStatus() == IOReactorStatus.INACTIVE) {
			this.httpClient5.start();
		}
		CompletableFuture<SimpleHttpResponse> cf = new CompletableFuture<>();
		try {
			SimpleHttpRequest request = SimpleRequestBuilder.create(httpMethod).setUri(url.toURI())
					.setAbsoluteRequestUri(true)
					.setBody(layout.toByteArray(event), ContentType.parse(layout.getContentType())).build();
			this.httpClient5.execute(request, new Hc5FutureCallback<>(cf));
		} catch (URISyntaxException e) {
			cf.completeExceptionally(e);
		}
		return cf;
	}

	private CompletableFuture<HttpResponse> makeApacheHCRequest(String httpMethod, List<Property> httpHeaders, URL url,
			Layout<?> layout, LogEvent event) {
		if (!this.httpClient4.isRunning()) {
			this.httpClient4.start();
		}
		CompletableFuture<HttpResponse> cf = new CompletableFuture<>();
		try {
			HttpPost request = new HttpPost(url.toURI());
			this.httpClient4.execute(request, new Hc4FutureCallback<>(cf));
		} catch (URISyntaxException e) {
			cf.completeExceptionally(e);
		}
		return cf;
	}

	/**
	 * Closes the underlying HTTP client.
	 * 
	 * @throws IOException
	 */
	@Override
	public final void close() throws IOException {
		if (this.client != null && !this.client.isClosed()) {
			this.client.close();
			this.client = null;
		}
		if (this.httpClient5 != null) {
			this.httpClient5.close();
			this.httpClient5 = null;
		}
		if (this.httpClient4 != null) {
			this.httpClient4.close();
			this.httpClient4 = null;
		}
	}
}