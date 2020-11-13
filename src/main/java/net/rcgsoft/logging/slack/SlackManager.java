package net.rcgsoft.logging.slack;

import java.io.IOException;
import java.net.URL;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.HttpManager;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;

import io.netty.handler.codec.http.HttpHeaderNames;
import net.rcgsoft.logging.util.DaemonThreadFactory;

/**
 * 
 * @author Jon Huang
 *
 */
public final class SlackManager extends HttpManager {
	private final AsyncHttpClient client;
	private final URL url;
	private final String method;
	private final Property[] headers;

	protected SlackManager(Configuration configuration, LoggerContext loggerContext, String name, URL url,
			String method, int connectTimeoutMillis, int readTimeoutMillis, Property[] headers,
			SslConfiguration sslConf, boolean verifyHostname) {
		super(configuration, loggerContext, name);
		DefaultAsyncHttpClientConfig.Builder cfgBuilder = new DefaultAsyncHttpClientConfig.Builder();
		this.url = url;
		this.method = method;
		this.headers = headers;
		if (connectTimeoutMillis > 0) {
			cfgBuilder.setConnectTimeout(connectTimeoutMillis);
		}
		if (readTimeoutMillis > 0) {
			cfgBuilder.setReadTimeout(readTimeoutMillis);
		}
		cfgBuilder.setDisableHttpsEndpointIdentificationAlgorithm(!verifyHostname);
		cfgBuilder.setThreadFactory(new DaemonThreadFactory());
		this.client = Dsl.asyncHttpClient(cfgBuilder.build());
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
		RequestBuilder reqBuilder = Dsl.request(method, url.toString()).addHeader(HttpHeaderNames.CONTENT_TYPE,
				layout.getContentType());
		if (this.headers != null) {
			for (Property header : this.headers) {
				reqBuilder.addHeader(header.getName(), header.getValue());
			}
		}
		reqBuilder.setBody(layout.toByteArray(event));
		ListenableFuture<Response> respFuture = client.executeRequest(reqBuilder);
		// TODO Find an alternative to blocking here
		// if we don't block here the JVM will exit because the Timer is a daemon thread
		respFuture.get();
	}
}