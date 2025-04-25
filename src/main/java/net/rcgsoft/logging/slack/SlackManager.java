/*********************************************************************
* Copyright (c) 2025 Jon Huang
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/

package net.rcgsoft.logging.slack;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.HttpManager;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;

import net.rcgsoft.logging.util.http.LogEventHttpClient;
import net.rcgsoft.logging.util.http.impl.LogEventHttpClientLoader;

/**
 * 
 * @author Jon Huang
 *
 */
public final class SlackManager extends HttpManager {
	private final LogEventHttpClient client;
	private final URL url;
	private final String method;
	private final Property[] headers;
	private final boolean blockingHttp;

	SlackManager(Configuration configuration, LoggerContext loggerContext, String name, URL url,
			String method, int connectTimeoutMillis, int readTimeoutMillis, Property[] headers,
			SslConfiguration sslConf, boolean verifyHostname, boolean blockingHttp) {
		super(configuration, loggerContext, name);
		this.url = url;
		this.method = method;
		this.headers = headers;
		this.client = LogEventHttpClientLoader.load();
		this.client.initialize(connectTimeoutMillis, readTimeoutMillis, verifyHostname);
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
		List<Property> headers;
		if (this.headers != null) {
			headers = Arrays.asList(this.headers);
		} else {
			headers = new ArrayList<>();
		}
		CompletableFuture<?> request = this.client.sendRequest(method, headers, url, layout, event);
		// Wait for HTTP response to arrive (if enabled)
		if (this.blockingHttp) {
			request.get();
		}
	}
}