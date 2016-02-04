package com.staticbloc.media.camera;

import android.media.MediaRecorder;
import android.support.annotation.NonNull;
import android.util.Log;

import java.lang.ref.WeakReference;

/*package*/ class MediaRecorderListener implements MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener {
  private final WeakReference<SimpleCamera> cameraRef;
  private final VideoCaptureSession videoCaptureSession;

  public MediaRecorderListener(@NonNull SimpleCamera camera, @NonNull VideoCaptureSession videoCaptureSession) {
    this.cameraRef = new WeakReference<>(camera);
    this.videoCaptureSession = videoCaptureSession;
  }

  @Override
  public void onError(MediaRecorder mr, int what, int extra) {
    videoCaptureSession.cancel(new RuntimeException("Received a MediaRecorder error (what=" + what + ", extra=" + extra));
    cameraRef.clear();
  }

  @Override
  public void onInfo(MediaRecorder mr, int what, int extra) {
    if(what != MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED && what != MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
      Log.w("SimpleCamera", "Received a MediaRecorder error (what=" + what + ", extra=" + extra);
      return;
    }

    videoCaptureSession.lock();
    try {
      if (!videoCaptureSession.isCancelled() && !videoCaptureSession.isDone()) {
        videoCaptureSession.setAutoStopped(true);
        SimpleCamera camera = cameraRef.get();
        if(camera != null) {
          camera.stopVideoRecording(videoCaptureSession);
          cameraRef.clear();
        }
      }
    }
    finally {
      videoCaptureSession.unlock();
      mr.setOnInfoListener(null);
    }
  }
}
