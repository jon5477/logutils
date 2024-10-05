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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.apache.http.concurrent.FutureCallback;

/**
 * The {@link FutureCallback} implementation that forwards the result to a
 * {@link CompletableFuture}.
 * 
 * @author Jon Huang
 *
 */
final class Hc4FutureCallback<T> implements FutureCallback<T> {
	private final CompletableFuture<T> cf;

	Hc4FutureCallback(CompletableFuture<T> cf) {
		this.cf = Objects.requireNonNull(cf, "completable future cannot be null");
	}

	@Override
	public final void completed(T result) {
		cf.complete(result);
	}

	@Override
	public final void failed(Exception ex) {
		cf.completeExceptionally(ex);
	}

	@Override
	public final void cancelled() {
		cf.cancel(false);
	}
}