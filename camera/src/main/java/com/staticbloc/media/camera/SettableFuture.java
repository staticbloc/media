package com.staticbloc.media.camera;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/*package*/ class SettableFuture<T> implements Future<T> {
  private final CountDownLatch countDownLatch = new CountDownLatch(1);
  private boolean canceled;
  private boolean done;
  private final AtomicReference<T> value = new AtomicReference<>();

  private volatile Throwable throwable;

  @Override
  public synchronized boolean cancel(boolean mayInterruptIfRunning) {
    if(canceled || done) {
      return false;
    }
    else {
      canceled = true;
      countDownLatch.countDown();
      return true;
    }
  }

  public synchronized boolean cancel(Throwable throwable) {
    if(canceled || done) {
      return false;
    }
    else {
      this.throwable = throwable;
      canceled = true;
      countDownLatch.countDown();
      return true;
    }
  }

  @Override
  public synchronized boolean isCancelled() {
    return canceled;
  }

  @Override
  public synchronized boolean isDone() {
    return done;
  }

  @Nullable
  @Override
  public synchronized T get() throws InterruptedException, ExecutionException {
    countDownLatch.await();
    if(canceled) {
      if(throwable != null) {
        throw new ExecutionException(throwable);
      }
      return null;
    }
    else {
      return value.get();
    }
  }

  @Nullable
  @Override
  public synchronized T get(long timeout, @NonNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    countDownLatch.await(timeout, unit);
    if(canceled) {
      if(throwable != null) {
        throw new ExecutionException(throwable);
      }
      return null;
    }
    else {
      return value.get();
    }
  }

  public synchronized boolean set(@Nullable T value) {
    if(canceled) return false;
    this.value.set(value);
    done = true;
    countDownLatch.countDown();
    return true;
  }
}
