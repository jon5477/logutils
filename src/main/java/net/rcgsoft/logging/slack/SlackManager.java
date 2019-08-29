package net.rcgsoft.logging.slack;

import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.HttpManager;
import org.apache.logging.log4j.core.config.Configuration;

public final class SlackManager extends HttpManager {
	private final URL url;
	private final boolean verifyHostname;

	protected SlackManager(Configuration configuration, LoggerContext loggerContext, String name, URL url, boolean verifyHostname) {
		super(configuration, loggerContext, name);
		this.url = url;
		this.verifyHostname = verifyHostname;
	}

	@Override
	public void send(Layout<?> layout, LogEvent event) throws Exception {
		HttpClientBuilder builder = HttpClients.custom();
		if (!verifyHostname) {
			builder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
		}
		try (CloseableHttpClient httpclient = builder.build();) {
			HttpPost httpPost = new HttpPost(url.toURI());
			String jsonContent = (String) layout.toSerializable(event);
			StringEntity se = new StringEntity(jsonContent);
			if (layout.getContentType() != null) {
				se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, layout.getContentType()));
			}
			httpPost.setEntity(se);
			try (CloseableHttpResponse response = httpclient.execute(httpPost);) {
				// TODO Internally log response status
//				System.out.println(response.getStatusLine());
				HttpEntity entity2 = response.getEntity();
				// do something useful with the response body
				// and ensure it is fully consumed
				EntityUtils.consume(entity2);
			}
		}
	}
}