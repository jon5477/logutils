package net.rcgsoft.logging.util.http.impl;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.apache.hc.core5.concurrent.FutureCallback;

/**
 * The {@link FutureCallback} implementation that forwards the result to a
 * {@link CompletableFuture}.
 * 
 * @author Jon Huang
 *
 */
final class Hc5FutureCallback<T> implements FutureCallback<T> {
	private final CompletableFuture<T> cf;

	Hc5FutureCallback(CompletableFuture<T> cf) {
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