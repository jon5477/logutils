/*********************************************************************
* Copyright (c) 2024 Jon Huang
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/

package net.rcgsoft.logging.util.http.impl;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Property;

import net.rcgsoft.logging.util.http.LogEventHttpClient;

/**
 * Provides the implementation of {@link LogEventHttpClient} using Apache HTTP
 * client 5.x
 * 
 * @author Jon Huang
 *
 */
final class Hc5LogEventHttpClient implements LogEventHttpClient {
	private final Lock mutex = new ReentrantLock();
	private CloseableHttpAsyncClient client;

	@Override
	public final void initialize(int connectTimeoutMillis, int readTimeoutMillis, boolean verifyHostname) {
		mutex.lock();
		try {
			if (client == null) {
				IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
						.setSoTimeout(Timeout.ofMilliseconds(readTimeoutMillis)).build();
				this.client = HttpAsyncClients.custom().setIOReactorConfig(ioReactorConfig).build();
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
			if (httpClient.getStatus() == IOReactorStatus.INACTIVE) {
				httpClient.start();
			}
		} finally {
			mutex.unlock();
		}
		CompletableFuture<SimpleHttpResponse> cf = new CompletableFuture<>();
		try {
			byte[] arrBody = layout.toByteArray(event);
			SimpleRequestBuilder reqBldr = SimpleRequestBuilder.create(httpMethod).setUri(url.toURI())
					.setAbsoluteRequestUri(true);
			if (arrBody != null) {
				reqBldr.setBody(arrBody, ContentType.parse(layout.getContentType()));
			}
			SimpleHttpRequest request = reqBldr.build();
			FutureCallback<SimpleHttpResponse> callback = new Hc5FutureCallback<SimpleHttpResponse>(cf);
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