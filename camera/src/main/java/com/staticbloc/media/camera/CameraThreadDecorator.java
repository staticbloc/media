package com.staticbloc.media.camera;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import com.staticbloc.media.utils.Size;
import com.staticbloc.media.utils.SizeUnit;

import java.util.List;
import java.util.concurrent.TimeUnit;

public final class CameraThreadDecorator extends SimpleCamera {
  private final SimpleCamera camera;

  private HandlerThread handlerThread;
  private Handler handler;

  public CameraThreadDecorator(SimpleCamera camera) {
    this.camera = camera;
  }

  @Override
  public void init(@NonNull final Callbacks callbacks, @AllowedCameraType final int allowedCameraType) {
    if (handler != null) {
      throw new IllegalStateException("SimpleCamera.init should only be called if STATE=RELEASED");
    }

    handlerThread = new HandlerThread("CameraThreadFacade");
    handlerThread.start();

    Looper cameraLooper = handlerThread.getLooper();
    // sanity check
    if(cameraLooper == null) {
      throw new IllegalStateException("CameraThreadFacade is not alive - please find or file an issue on GitHub, and document the steps taken that resulted in this exception");
    }
    handler = new Handler(cameraLooper);

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.init(callbacks, allowedCameraType);
      }
    });
  }

  @Override
  public void open(@CameraType final int cameraType, @NonNull final CameraPreview cameraPreview) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.open(cameraType, cameraPreview);
      }
    });
  }

  @Override
  public void setPreview(@NonNull final CameraPreview cameraPreview) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.setPreview(cameraPreview);
      }
    });
  }

  @Override
  public boolean isOpened() {
    return camera.isOpened();
  }

  @Override
  public boolean isRecording() {
    return camera.isRecording();
  }

  @Override
  public boolean isError() {
    return camera.isError();
  }

  @Override
  public boolean isClosed() {
    return camera.isClosed();
  }

  @Override
  public boolean isReleased() {
    return camera.isReleased();
  }

  @Override
  public boolean hasPreview() {
    return camera.hasPreview();
  }

  @NonNull
  @Override
  public Size getPreviewSize() {
    throwIfNotInitted();

    return camera.getPreviewSize();
  }

  @Override
  public void updatePreviewTargetSize(@NonNull final Size previewTargetSize) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.updatePreviewTargetSize(previewTargetSize);
      }
    });
  }

  @NonNull
  public Size getPhotoSize() {
    throwIfNotInitted();

    return camera.getPhotoSize();
  }

  @NonNull
  public List<Size> getSupportedPhotoSizes() {
    throwIfNotInitted();

    return camera.getSupportedPhotoSizes();
  }

  public void setPhotoSize(@NonNull final Size pictureSize) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.setPhotoSize(pictureSize);
      }
    });
  }

  @NonNull
  @Override
  public Size getVideoSize() {
    throwIfNotInitted();

    return camera.getVideoSize();
  }

  @NonNull
  @Override
  public List<Size> getSupportedVideoSizes() {
    throwIfNotInitted();

    return camera.getSupportedVideoSizes();
  }

  @Override
  public void setVideoSize(@NonNull final Size videoSize) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.setVideoSize(videoSize);
      }
    });
  }

  @Override
  public void setVideoBitratePerSecond(final long videoBitrate, final SizeUnit sizeUnit) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.setVideoBitratePerSecond(videoBitrate, sizeUnit);
      }
    });
  }

  @Override
  public void setMaxRecordingSize(final long maxRecordingSize, final SizeUnit sizeUnit) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.setMaxRecordingSize(maxRecordingSize, sizeUnit);
      }
    });
  }

  @Override
  public void setMaxRecordingDuration(final int maxRecordingDuration, final TimeUnit timeUnit) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.setMaxRecordingDuration(maxRecordingDuration, timeUnit);
      }
    });
  }

  @Override
  public int getAllowedCameraType() {
    throwIfNotInitted();

    return camera.getAllowedCameraType();
  }

  @Override
  public int getCameraType() {
    throwIfNotInitted();

    return camera.getCameraType();
  }

  @Override
  public boolean isZoomSupported() {
    throwIfNotInitted();

    return camera.isZoomSupported();
  }

  @Override
  public void zoom(final int zoom) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.zoom(zoom);
      }
    });
  }

  @Override
  public void smoothZoom(final int zoom) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.smoothZoom(zoom);
      }
    });
  }

  @Override
  public int getZoom() {
    throwIfNotInitted();

    return camera.getZoom();
  }

  @Override
  public int getMaxZoom() {
    throwIfNotInitted();

    return camera.getMaxZoom();
  }

  @Override
  public float getZoomRatio() {
    throwIfNotInitted();

    return camera.getZoomRatio();
  }

  @Override
  public void nextFlashMode() {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.nextFlashMode();
      }
    });
  }

  @Override
  public void setFlashMode(@NonNull @FlashMode final String flashMode) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.setFlashMode(flashMode);
      }
    });
  }

  @NonNull
  @Override
  public String getFlashMode() {
    throwIfNotInitted();

    return camera.getFlashMode();
  }

  @NonNull
  @Override
  public List<String> getSupportedFlashModes() {
    throwIfNotInitted();

    return camera.getSupportedFlashModes();
  }

  @Override
  public void toggleCameraType() {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.toggleCameraType();
      }
    });
  }

  @Override
  public void setCameraType(@CameraType final int cameraType) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.setCameraType(cameraType);
      }
    });
  }

  @Override
  public void setPreviewEnabled(final boolean enabled) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.setPreviewEnabled(enabled);
      }
    });
  }

  @Override
  public boolean isPreviewEnabled() {
    throwIfNotInitted();

    return camera.isPreviewEnabled();
  }

  @NonNull
  @Override
  public <T> PhotoCaptureSession<T> takePhoto(@NonNull final PhotoCaptureRequest<T> captureRequest) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.takePhoto(captureRequest);
      }
    });

    return captureRequest.getPhotoCaptureSession();
  }

  @Override
  public void close() {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.close();
      }
    });
  }

  @Override
  public void release() {
    throwIfNotInitted();

    Handler tempHandler = handler;
    handler = null;
    tempHandler.post(new Runnable() {
      @Override
      public void run() {
        camera.release();
        handlerThread.quit();
      }
    });
  }

  @Override
  int getCameraId() {
    throwIfNotInitted();

    return camera.getCameraId();
  }

  @Override
  public VideoCaptureSession startVideoRecording(@NonNull final VideoCaptureRequest videoCaptureRequest) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.startVideoRecording(videoCaptureRequest);
      }
    });

    return videoCaptureRequest.getVideoCaptureSession();
  }

  @Override
  public void stopVideoRecording(@NonNull final VideoCaptureSession videoCaptureSession) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.stopVideoRecording(videoCaptureSession);
      }
    });
  }

  @Override
  public void cancelVideoRecording(@NonNull final VideoCaptureSession videoCaptureSession) {
    throwIfNotInitted();

    handler.post(new Runnable() {
      @Override
      public void run() {
        camera.cancelVideoRecording(videoCaptureSession);
      }
    });
  }

  private void throwIfNotInitted() {
    if(handler == null) throw new IllegalStateException("SimpleCamera STATE needs to equal INITTED to make this call");
  }
}
