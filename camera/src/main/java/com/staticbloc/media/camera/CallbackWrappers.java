package com.staticbloc.media.camera;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

/*package*/ class CallbackWrappers {
  private CallbackWrappers() {}

  public static class UiThreadCallbacks implements SimpleCamera.Callbacks {
    private final SimpleCamera.Callbacks callbacks;
    private final Handler handler;

    public UiThreadCallbacks(@NonNull SimpleCamera.Callbacks callbacks, @NonNull Handler handler) {
      this.callbacks = callbacks;
      this.handler = handler;
    }

    @Override
    public void onCameraOpened(@SimpleCamera.CameraType final int cameraType, final Throwable t) {
      if(Looper.myLooper() == Looper.getMainLooper()) {
        callbacks.onCameraOpened(cameraType, t);
      }
      else {
        handler.post(new Runnable() {
          @Override public void run() {
            callbacks.onCameraOpened(cameraType, t);
          }
        });
      }
    }

    @Override
    public void onCameraTypeSwitchingChanged(final boolean enabled) {
      if(Looper.myLooper() == Looper.getMainLooper()) {
        callbacks.onCameraTypeSwitchingChanged(enabled);
      }
      else {
        handler.post(new Runnable() {
          @Override public void run() {
            callbacks.onCameraTypeSwitchingChanged(enabled);
          }
        });
      }
    }

    @Override
    public void onFlashModeChanged(@SimpleCamera.FlashMode final String flashMode, final boolean isDefault) {
      if(Looper.myLooper() == Looper.getMainLooper()) {
        callbacks.onFlashModeChanged(flashMode, isDefault);
      }
      else {
        handler.post(new Runnable() {
          @Override public void run() {
            callbacks.onFlashModeChanged(flashMode, isDefault);
          }
        });
      }
    }

    @Override
    public void onFlashModeSwitchingChanged(final boolean enabled) {
      if(Looper.myLooper() == Looper.getMainLooper()) {
        callbacks.onFlashModeSwitchingChanged(enabled);
      }
      else {
        handler.post(new Runnable() {
          @Override public void run() {
            callbacks.onFlashModeSwitchingChanged(enabled);
          }
        });
      }
    }

    @Override
    public void onPhotoCaptureEnabledChanged(final boolean enabled) {
      if(Looper.myLooper() == Looper.getMainLooper()) {
        callbacks.onPhotoCaptureEnabledChanged(enabled);
      }
      else {
        handler.post(new Runnable() {
          @Override public void run() {
            callbacks.onPhotoCaptureEnabledChanged(enabled);
          }
        });
      }
    }

    @Override
    public void onVideoCaptureEnabledChanged(final boolean enabled) {
      if(Looper.myLooper() == Looper.getMainLooper()) {
        callbacks.onVideoCaptureEnabledChanged(enabled);
      }
      else {
        handler.post(new Runnable() {
          @Override public void run() {
            callbacks.onVideoCaptureEnabledChanged(enabled);
          }
        });
      }
    }

    @Override
    public void onZoomEnabledChanged(final boolean enabled) {
      if(Looper.myLooper() == Looper.getMainLooper()) {
        callbacks.onZoomEnabledChanged(enabled);
      }
      else {
        handler.post(new Runnable() {
          @Override public void run() {
            callbacks.onZoomEnabledChanged(enabled);
          }
        });
      }
    }

    @Override
    public void onZoomChanged(final int zoom, final float zoomRatio) {
      if(Looper.myLooper() == Looper.getMainLooper()) {
        callbacks.onZoomChanged(zoom, zoomRatio);
      }
      else {
        handler.post(new Runnable() {
          @Override public void run() {
            callbacks.onZoomChanged(zoom, zoomRatio);
          }
        });
      }
    }

    @Override
    public void onCameraClosed() {
      if(Looper.myLooper() == Looper.getMainLooper()) {
        callbacks.onCameraClosed();
      }
      else {
        handler.post(new Runnable() {
          @Override public void run() {
            callbacks.onCameraClosed();
          }
        });
      }
    }
  }
}
