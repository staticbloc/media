package com.staticbloc.media.camera;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class CaptureSession<T> {
  private final SettableFuture<T> future = new SettableFuture<>();

  /*package*/ CaptureSession() {}

  public boolean cancel() {
    return future.cancel(true /*ignored*/);
  }

  /*package*/ boolean cancel(Throwable throwable) {
    return future.cancel(throwable);
  }

  public boolean isCancelled() {
    return future.isCancelled();
  }

  public boolean isDone() {
    return future.isDone();
  }

  @Nullable
  public T get() throws InterruptedException, ExecutionException {
    return future.get();
  }

  @Nullable
  public T get(long timeout, @NonNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return future.get(timeout, unit);
  }

  /*package*/ boolean set(@Nullable T value) {
    return future.set(value);
  }
}
