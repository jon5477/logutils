package net.rcgsoft.logging.slack;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.HttpManager;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;

import net.rcgsoft.logging.util.HttpClient;

/**
 * 
 * @author Jon Huang
 *
 */
public final class SlackManager extends HttpManager {
	private final HttpClient client;
	private final URL url;
	private final String method;
	private final Property[] headers;
	private final boolean blockingHttp;

	protected SlackManager(Configuration configuration, LoggerContext loggerContext, String name, URL url,
			String method, int connectTimeoutMillis, int readTimeoutMillis, Property[] headers,
			SslConfiguration sslConf, boolean verifyHostname, boolean blockingHttp) {
		super(configuration, loggerContext, name);
		this.url = url;
		this.method = method;
		this.headers = headers;
		this.client = new HttpClient(connectTimeoutMillis, readTimeoutMillis, verifyHostname);
		this.blockingHttp = blockingHttp;
	}

	@Override
	public final void close() {
		try {
			this.client.close();
		} catch (IOException e) {
			super.logError(e.getLocalizedMessage(), e);
		} finally {
			super.close();
		}
	}

	@Override
	public final void send(Layout<?> layout, LogEvent event) throws Exception {
		List<Property> headers = new ArrayList<>();
		if (this.headers != null) {
			for (Property header : this.headers) {
				headers.add(header);
			}
		}
		CompletableFuture<?> request = this.client.makeRequest(method, headers, url, layout, event);
		// Wait for HTTP response to arrive (if enabled)
		if (this.blockingHttp) {
			request.get();
		}
	}
}