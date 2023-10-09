package net.rcgsoft.logging.util.http;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.apache.http.concurrent.FutureCallback;

/**
 * 
 * @author Jon Huang
 *
 */
public final class Hc4FutureCallback<T> implements FutureCallback<T> {
	private final CompletableFuture<T> cf;

	public Hc4FutureCallback(CompletableFuture<T> cf) {
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