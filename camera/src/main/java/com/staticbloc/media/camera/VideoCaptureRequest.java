package com.staticbloc.media.camera;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;

public class VideoCaptureRequest {
  private final VideoCaptureSession videoCaptureSession;
  private final Builder builder;

  private VideoCaptureRequest(Builder builder) {
    this.builder = builder;
    videoCaptureSession = new VideoCaptureSession();
  }

  public static class Builder {
    /*package*/ File file;
    /*package*/ VideoCallbacks videoCallbacks;

    public Builder(@NonNull String file, @NonNull VideoCallbacks videoCallbacks) {
      this(new File(file), videoCallbacks);
    }

    public Builder(@NonNull File file, @NonNull VideoCallbacks videoCallbacks) {
      this.videoCallbacks = videoCallbacks;
      this.file = file;
    }

    @NonNull
    public VideoCaptureRequest build() {
      return new VideoCaptureRequest(this);
    }
  }

  @NonNull
  /*package*/ VideoCaptureSession getVideoCaptureSession() {
    return videoCaptureSession;
  }

  @NonNull
  /*package*/ VideoCallbacks getVideoReadyListener() {
    return builder.videoCallbacks;
  }

  @NonNull
  /*package*/ File getFile() {
    return builder.file;
  }

  public interface VideoCallbacks {
    void onRecordingStarted();
    void onVideoReady(@Nullable Throwable t, @Nullable File video, boolean autoStopped);
    void onCancelled();
  }
}
