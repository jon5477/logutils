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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Property;

import net.rcgsoft.logging.util.http.LogEventHttpClient;

/**
 * Provides the implementation of {@link LogEventHttpClient} using Java's
 * {@link HttpClient} (available since Java 11).
 * 
 * @author Jon Huang
 *
 */
final class JavaLogEventHttpClient implements LogEventHttpClient {
	private final Lock mutex = new ReentrantLock();
	private Duration readTimeout;
	private HttpClient client;

	@Override
	public final void initialize(int connectTimeoutMillis, int readTimeoutMillis, boolean verifyHostname) {
		mutex.lock();
		try {
			this.readTimeout = Duration.ofMillis(readTimeoutMillis);
			if (client == null) {
				this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMillis)).build();
			}
		} finally {
			mutex.unlock();
		}
	}

	@Override
	public final CompletableFuture<?> sendRequest(String httpMethod, List<Property> httpHeaders, URL url,
			Layout<?> layout, LogEvent event) {
		HttpClient httpClient;
		mutex.lock();
		try {
			httpClient = this.client;
		} finally {
			mutex.unlock();
		}
		if (httpClient == null) {
			throw new IllegalStateException("client must be initialized prior to use");
		}
		URI uri;
		try {
			uri = url.toURI();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(uri);
		if (!readTimeout.isNegative() && !readTimeout.isZero()) {
			reqBuilder.timeout(readTimeout);
		}

		reqBuilder.header("Content-Type", layout.getContentType());
		for (Property header : httpHeaders) {
			reqBuilder.header(header.getName(), header.getValue());
		}
		byte[] arrBody = layout.toByteArray(event);
		reqBuilder.method(httpMethod, arrBody != null ? BodyPublishers.ofByteArray(arrBody) : BodyPublishers.noBody());
		CompletableFuture<HttpResponse<Void>> respFuture = httpClient.sendAsync(reqBuilder.build(),
				BodyHandlers.discarding());
		return respFuture;
	}

	@Override
	public final void close() throws IOException {
		mutex.lock();
		try {
			if (this.client != null) {
				this.client = null;
			}
		} finally {
			mutex.unlock();
		}
	}
}