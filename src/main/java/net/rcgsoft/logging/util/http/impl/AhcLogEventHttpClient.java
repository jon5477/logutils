/*********************************************************************
* Copyright (c) 2025 Jon Huang
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/

package net.rcgsoft.logging.util.http.impl;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Property;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;

import io.netty.handler.codec.http.HttpHeaderNames;
import net.rcgsoft.logging.util.DaemonThreadFactory;
import net.rcgsoft.logging.util.http.LogEventHttpClient;

/**
 * Provides the implementation of {@link LogEventHttpClient} using Async HTTP
 * Client.
 * 
 * @author Jon Huang
 *
 */
final class AhcLogEventHttpClient implements LogEventHttpClient {
	private final Lock mutex = new ReentrantLock();
	private AsyncHttpClient client;

	@Override
	public final void initialize(int connectTimeoutMillis, int readTimeoutMillis, boolean verifyHostname) {
		mutex.lock();
		try {
			if (client != null) {
				return;
			}
		} finally {
			mutex.unlock();
		}
		DefaultAsyncHttpClientConfig.Builder cfg = new DefaultAsyncHttpClientConfig.Builder();
		if (connectTimeoutMillis > 0) {
			cfg.setConnectTimeout(Duration.ofMillis(connectTimeoutMillis));
		}
		if (readTimeoutMillis > 0) {
			cfg.setReadTimeout(Duration.ofMillis(readTimeoutMillis));
		}
		cfg.setDisableHttpsEndpointIdentificationAlgorithm(!verifyHostname);
		cfg.setThreadFactory(new DaemonThreadFactory());
		mutex.lock();
		try {
			if (this.client == null) {
				this.client = Dsl.asyncHttpClient(cfg);
			}
		} finally {
			mutex.unlock();
		}
	}

	@Override
	public final CompletableFuture<?> sendRequest(String httpMethod, List<Property> httpHeaders, URL url,
			Layout<?> layout, LogEvent event) {
		AsyncHttpClient httpClient;
		mutex.lock();
		try {
			httpClient = this.client;
		} finally {
			mutex.unlock();
		}
		if (httpClient == null) {
			throw new IllegalStateException("client must be initialized prior to use");
		}
		RequestBuilder reqBuilder = Dsl.request(httpMethod, url.toString()).addHeader(HttpHeaderNames.CONTENT_TYPE,
				layout.getContentType());
		if (httpHeaders != null) {
			for (Property header : httpHeaders) {
				reqBuilder.addHeader(header.getName(), header.getValue());
			}
		}
		reqBuilder.setBody(layout.toByteArray(event));
		ListenableFuture<Response> respFuture = httpClient.executeRequest(reqBuilder);
		return respFuture.toCompletableFuture();
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