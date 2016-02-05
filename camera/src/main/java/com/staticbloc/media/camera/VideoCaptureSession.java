package com.staticbloc.media.camera;

import android.media.MediaRecorder;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class VideoCaptureSession {
  private final CaptureSession<File> session = new CaptureSession<File>(){};
  private final Lock lock;
  private MediaRecorder mediaRecorder;
  private File file;
  private VideoCaptureRequest.VideoCallbacks videoCallbacks;
  private Handler callbackHandler;

  private final AtomicBoolean autoStopped = new AtomicBoolean();

  /*package*/ VideoCaptureSession() {
    this.lock = new ReentrantLock();
  }

  private final Runnable callCancelCallback = new Runnable() {
    @Override
    public void run() {
      if(videoCallbacks != null) videoCallbacks.onCancelled();
    }
  };

  private class ErrorCallbackRunnable implements Runnable {
    private final Throwable t;

    public ErrorCallbackRunnable(@NonNull Throwable t) {
      this.t = t;
    }

    @Override
    public void run() {
      if(videoCallbacks != null) videoCallbacks.onVideoReady(t, null, false);
    }
  }

  /*package*/ void lock() {
    lock.lock();
  }

  /*package*/ void unlock() {
    lock.unlock();
  }

  /*package*/ void init(@Nullable final VideoCaptureRequest.VideoCallbacks videoCallbacks, @NonNull Handler callbackHandler) {
    this.videoCallbacks = videoCallbacks;
    this.callbackHandler = callbackHandler;

    if(session.isCancelled() && videoCallbacks != null) {
      callbackHandler.post(callCancelCallback);
    }
  }

  /*package*/ void setRecordingObjects(@NonNull MediaRecorder mediaRecorder, @NonNull File file) {
    this.mediaRecorder = mediaRecorder;
    this.file = file;
  }

  /*package*/ MediaRecorder getMediaRecorder() {
    return mediaRecorder;
  }

  /*package*/ void setAutoStopped(boolean autoStopped) {
    this.autoStopped.set(autoStopped);
  }

  /*package*/ boolean cancel() {
    return cancel(null);
  }

  /*package*/ boolean cancel(@Nullable Throwable throwable) {
    lock.lock();
    try {
      boolean notCanceledYet = throwable == null ? session.cancel() : session.cancel(throwable);
      if(notCanceledYet) {
        if(file != null) {
          file.delete();
          file = null;
        }

        if(callbackHandler != null) {
          if(throwable == null) callbackHandler.post(callCancelCallback);
          else callbackHandler.post(new ErrorCallbackRunnable(throwable));
        }

        mediaRecorder = null;
      }
      return notCanceledYet;
    }
    finally {
      lock.unlock();
    }
  }

  /*package*/ void stop(boolean deleteFile) {
    if(deleteFile && file != null) {
      file.delete();
      file = null;
    }
    session.set(file);

    if(callbackHandler != null) {
      callbackHandler.post(new Runnable() {
        @Override
        public void run() {
          if(videoCallbacks != null) videoCallbacks.onVideoReady(null, file, autoStopped.get());
        }
      });
    }

    mediaRecorder = null;
  }

  public boolean isCancelled() {
    return session.isCancelled();
  }

  public boolean isDone() {
    return session.isDone();
  }

  @Nullable
  public File get() throws InterruptedException, ExecutionException {
    return session.get();
  }

  @Nullable
  public File get(long timeout, @NonNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return session.get(timeout, unit);
  }
}