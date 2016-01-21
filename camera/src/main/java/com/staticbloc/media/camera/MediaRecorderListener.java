package com.staticbloc.media.camera;

import android.media.MediaRecorder;
import android.os.Handler;
import android.support.annotation.NonNull;

import java.lang.ref.WeakReference;

/*package*/ class MediaRecorderListener implements MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener {
  private final WeakReference<SimpleCamera> cameraRef;
  private final VideoCaptureSession videoCaptureSession;
  private final Handler cameraHandler;

  public MediaRecorderListener(@NonNull SimpleCamera camera, @NonNull VideoCaptureSession videoCaptureSession, @NonNull Handler cameraHandler) {
    this.cameraRef = new WeakReference<>(camera);
    this.videoCaptureSession = videoCaptureSession;
    this.cameraHandler = cameraHandler;
  }

  @Override
  public void onError(MediaRecorder mr, int what, int extra) {
    videoCaptureSession.cancel(new RuntimeException("Received a MediaRecorder error (what=" + what + ", extra=" + extra));
    cameraRef.clear();
  }

  @Override
  public void onInfo(MediaRecorder mr, int what, int extra) {
    if(what != MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED && what != MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
      videoCaptureSession.cancel(new RuntimeException("Received a MediaRecorder error (what=" + what + ", extra=" + extra));
      return;
    }

    videoCaptureSession.lock();
    try {
      if (videoCaptureSession.isCancelled() || videoCaptureSession.isDone()) {
        return;
      }
      else {
        videoCaptureSession.setAutoStopped(true);
      }
    }
    finally {
      videoCaptureSession.unlock();
    }

    cameraHandler.post(new Runnable() {
      @Override
      public void run() {
        SimpleCamera camera = cameraRef.get();
        if(camera != null) {
          camera.stopVideoRecording(videoCaptureSession);
          cameraRef.clear();
        }
      }
    });
  }
}
