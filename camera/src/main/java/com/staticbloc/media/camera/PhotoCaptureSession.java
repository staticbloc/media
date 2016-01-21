package com.staticbloc.media.camera;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public final class PhotoCaptureSession<T> extends CaptureSession<T> {
  private PhotoCaptureRequest.PhotoCapturedListener<T> photoCapturedListener;
  private Handler callbackHandler;

  private final Runnable callCancelCallback = new Runnable() {
    @Override
    public void run() {
      if(photoCapturedListener != null) photoCapturedListener.onCancelled();
    }
  };

  /*package*/ synchronized void init(@Nullable  final PhotoCaptureRequest.PhotoCapturedListener<T> photoCapturedListener, @NonNull Handler callbackHandler) {
    this.photoCapturedListener = photoCapturedListener;
    this.callbackHandler = callbackHandler;

    if(isCancelled() && photoCapturedListener != null) {
      callbackHandler.post(callCancelCallback);
    }
  }

  @Override
  public synchronized boolean cancel() {
    return cancel(null);
  }

  @Override
  synchronized boolean cancel(@Nullable Throwable throwable) {
    boolean notCanceledYet = throwable == null ? super.cancel() : super.cancel(throwable);
    if(notCanceledYet && callbackHandler != null) callbackHandler.post(callCancelCallback);
    return notCanceledYet;
  }
}
