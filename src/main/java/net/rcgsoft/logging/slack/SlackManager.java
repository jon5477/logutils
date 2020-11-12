package net.rcgsoft.logging.slack;

import java.net.URL;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.HttpManager;
import org.apache.logging.log4j.core.config.Configuration;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;

import io.netty.handler.codec.http.HttpHeaderNames;

/**
 * 
 * @author Jon Huang
 *
 */
public final class SlackManager extends HttpManager {
	private final AsyncHttpClient client;
	private final URL url;

	protected SlackManager(Configuration configuration, LoggerContext loggerContext, String name, URL url,
			boolean verifyHostname) {
		super(configuration, loggerContext, name);
		DefaultAsyncHttpClientConfig cfg = new DefaultAsyncHttpClientConfig.Builder()
				.setDisableHttpsEndpointIdentificationAlgorithm(!verifyHostname).build();
		this.url = url;
		this.client = Dsl.asyncHttpClient(cfg);
	}

	@Override
	public void send(Layout<?> layout, LogEvent event) throws Exception {
		RequestBuilder reqBuilder = Dsl.post(url.toString())
				.addHeader(HttpHeaderNames.CONTENT_TYPE, layout.getContentType()).setBody(layout.toByteArray(event));
		ListenableFuture<Response> respFuture = client.executeRequest(reqBuilder);
	}
}