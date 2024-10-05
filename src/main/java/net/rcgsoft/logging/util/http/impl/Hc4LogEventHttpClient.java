package net.rcgsoft.logging.util.http.impl;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Property;

import net.rcgsoft.logging.util.http.LogEventHttpClient;

/**
 * Provides the implementation of {@link LogEventHttpClient} using Apache HTTP
 * client 4.x
 * 
 * @author Jon Huang
 *
 */
final class Hc4LogEventHttpClient implements LogEventHttpClient {
	private final Lock mutex = new ReentrantLock();
	private CloseableHttpAsyncClient client;

	@Override
	public final void initialize(int connectTimeoutMillis, int readTimeoutMillis, boolean verifyHostname) {
		mutex.lock();
		try {
			if (client == null) {
				this.client = HttpAsyncClients.createDefault();
			}
		} finally {
			mutex.unlock();
		}
	}

	@Override
	public final CompletableFuture<?> sendRequest(String httpMethod, List<Property> httpHeaders, URL url,
			Layout<?> layout, LogEvent event) {
		CloseableHttpAsyncClient httpClient;
		mutex.lock();
		try {
			httpClient = this.client;
			if (httpClient == null) {
				throw new IllegalStateException("client must be initialized prior to use");
			}
			if (!httpClient.isRunning()) {
				httpClient.start();
			}
		} finally {
			mutex.unlock();
		}
		CompletableFuture<HttpResponse> cf = new CompletableFuture<>();
		try {
			HttpPost request = new HttpPost(url.toURI());
			byte[] arrBody = layout.toByteArray(event);
			if (arrBody != null) {
				ByteArrayEntity entity = new ByteArrayEntity(arrBody);
				String ctype = layout.getContentType();
				if (ctype != null) {
					entity.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, ctype));
				}
				request.setEntity(entity);
			}
			FutureCallback<HttpResponse> callback = new Hc4FutureCallback<HttpResponse>(cf);
			httpClient.execute(request, callback);
		} catch (URISyntaxException e) {
			cf.completeExceptionally(e);
		}
		return cf;
	}

	@Override
	public final void close() throws IOException {
		mutex.lock();
		try {
			if (this.client != null) {
				this.client.close();
				this.client = null;
			}
		} finally {
			mutex.unlock();
		}
	}
}