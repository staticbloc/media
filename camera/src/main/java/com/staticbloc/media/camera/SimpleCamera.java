package com.staticbloc.media.camera;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.StringDef;
import com.staticbloc.media.utils.Size;
import com.staticbloc.media.utils.SizeUnit;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public abstract class SimpleCamera {
  @IntDef({CAMERA_TYPE_ALL, CAMERA_TYPE_FRONT, CAMERA_TYPE_BACK})
  @Retention(RetentionPolicy.SOURCE)
  public @interface AllowedCameraType {}

  @IntDef({CAMERA_TYPE_FRONT, CAMERA_TYPE_BACK, CAMERA_TYPE_NONE})
  @Retention(RetentionPolicy.SOURCE)
  public @interface CameraType {}

  @StringDef({FLASH_MODE_AUTO, FLASH_MODE_ON, FLASH_MODE_OFF, FLASH_MODE_RED_EYE, FLASH_MODE_TORCH})
  @Retention(RetentionPolicy.SOURCE)
  public @interface FlashMode {}

  public static final int CAMERA_TYPE_ALL = 0;
  public static final int CAMERA_TYPE_FRONT = 1;
  public static final int CAMERA_TYPE_BACK = 2;
  public static final int CAMERA_TYPE_NONE = Integer.MIN_VALUE;

  public static final String FLASH_MODE_AUTO = "auto";
  public static final String FLASH_MODE_ON = "on";
  public static final String FLASH_MODE_OFF = "off";
  public static final String FLASH_MODE_RED_EYE = "red-eye";
  public static final String FLASH_MODE_TORCH = "torch";

  protected static final int NOT_SET = -1;

  public static final class Builder {
    /*package*/ Context context;

    /*package*/ Handler callbackHandler;
    /*package*/ SizeStrategy sizeStrategy;

    /*package*/ Set<String> nonAllowedFlashModes;
    /*package*/ boolean willRecordVideo = false;
    /*package*/ int videoBitrate = NOT_SET;
    /*package*/ long maxRecordingSize = NOT_SET;
    /*package*/ int maxRecordingDuration = NOT_SET;
    /*package*/ boolean videoCaptureSessionCancelledOnClose = true;
    
    /*package*/ boolean shutterSoundMute = false;
    /*package*/ String shutterSoundOverridePath = null;
    /*package*/ int shutterSoundOverrideRes = NOT_SET;

    public Builder(Context context) {
      this.context = context;
    }

    @NonNull
    public Builder callbacksOnMainThread() {
      if(callbackHandler != null) {
        throw new IllegalStateException("Cannot call callbacksOnMainThread if callbacksHandler was already set");
      }
      callbackHandler = new Handler(Looper.getMainLooper());
      return this;
    }

    @NonNull
    public Builder callbacksHandler(@NonNull Handler handler) {
      if(this.callbackHandler != null) {
        throw new IllegalStateException("Cannot set callbacksHandler if callbacksOnMainThread was already called");
      }
      this.callbackHandler = handler;
      return this;
    }

    @NonNull
    public Builder sizeStrategy(@NonNull SizeStrategy sizeStrategy) {
      this.sizeStrategy = sizeStrategy;
      return this;
    }

    @NonNull
    public Builder nonAllowedFlashModes(@NonNull String... nonAllowedFlashModes) {
      this.nonAllowedFlashModes = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(nonAllowedFlashModes)));
      return this;
    }

    @NonNull
    public Builder willRecordVideo(boolean willRecordVideo) {
      this.willRecordVideo = willRecordVideo;
      return this;
    }

    @NonNull
    public Builder videoBitratePerSecond(int videoBitrate, SizeUnit sizeUnit) {
      long bitrate = sizeUnit.toBits(videoBitrate);
      if(bitrate > Integer.MAX_VALUE) {
        bitrate = Integer.MAX_VALUE;
      }
      this.videoBitrate = (int) bitrate;
      return this;
    }

    @NonNull
    public Builder maxRecordingSize(long maxRecordingSize, SizeUnit sizeUnit) {
      this.maxRecordingSize = sizeUnit.toBytes(maxRecordingSize);
      return this;
    }

    @NonNull
    public Builder maxRecordingDuration(int maxRecordingDuration, TimeUnit timeUnit) {
      long duration = timeUnit.toMillis(maxRecordingDuration);
      if(duration > Integer.MAX_VALUE) {
        duration = Integer.MAX_VALUE;
      }
      this.maxRecordingDuration = (int) duration;
      return this;
    }

    @NonNull
    public Builder cancelVideoCaptureOnClose(boolean cancelVideoCaptureOnClose) {
      this.videoCaptureSessionCancelledOnClose = cancelVideoCaptureOnClose;
      return this;
    }

    @NonNull
    public Builder muteShutterSound() {
      this.shutterSoundOverridePath = null;
      this.shutterSoundOverrideRes = NOT_SET;
      this.shutterSoundMute = true;
      return this;
    }

    @NonNull
    public Builder overrideShutterSound(@NonNull String shutterSoundOverridePath) {
      this.shutterSoundOverridePath = shutterSoundOverridePath;
      this.shutterSoundOverrideRes = NOT_SET;
      this.shutterSoundMute = false;
      return this;
    }

    @NonNull
    public Builder overrideShutterSound(int shutterSoundOverrideRes) {
      this.shutterSoundOverridePath = null;
      this.shutterSoundOverrideRes = shutterSoundOverrideRes;
      this.shutterSoundMute = false;
      return this;
    }

    @NonNull
    public SimpleCamera build() {
      if(sizeStrategy == null) {
        sizeStrategy = new SizeStrategy.DefaultSizeStrategy();
      }

      if(nonAllowedFlashModes == null) {
        nonAllowedFlashModes = Collections.emptySet();
      }

      return new CameraThreadDecorator(new SimpleCameraImpl(this));
    }
  }

  public abstract void init(@NonNull Callbacks callbacks, @AllowedCameraType int allowedCameraType);
  public abstract void open(@CameraType int cameraType, @NonNull CameraPreview cameraPreview);
  public abstract void setPreview(@NonNull CameraPreview cameraPreview);

  public abstract boolean isOpened();
  public abstract boolean isRecording();
  public abstract boolean isError();
  public abstract boolean isClosed();
  public abstract boolean isReleased();

  public abstract boolean hasPreview();
  @NonNull public abstract Size getPreviewSize();
  public abstract void updatePreviewTargetSize(@NonNull Size previewTargetSize);

  @NonNull public abstract Size getPhotoSize();
  @NonNull public abstract List<Size> getSupportedPhotoSizes();
  public abstract void setPhotoSize(@NonNull Size photoSize);

  @NonNull public abstract Size getVideoSize();
  @NonNull public abstract List<Size> getSupportedVideoSizes();
  public abstract void setVideoSize(@NonNull Size videoSize);
  public abstract void setVideoBitratePerSecond(long videoBitrate, SizeUnit sizeUnit);
  public abstract void setMaxRecordingSize(long maxRecordingSize, SizeUnit sizeUnit);
  public abstract void setMaxRecordingDuration(int maxRecordingDuration, TimeUnit timeUnit);

  @AllowedCameraType public abstract int getAllowedCameraType();
  @CameraType public abstract int getCameraType();

  public abstract boolean isZoomSupported();
  public abstract void zoom(int zoom);
  public abstract void smoothZoom(int zoom);
  public abstract int getZoom();
  public abstract int getMaxZoom();
  public abstract float getZoomRatio();

  public abstract void nextFlashMode();
  public abstract void setFlashMode(@NonNull @FlashMode String flashMode);
  @NonNull @FlashMode public abstract String getFlashMode();
  @NonNull public abstract List<String> getSupportedFlashModes();
  public abstract void toggleCameraType();
  public abstract void setCameraType(@CameraType int cameraType);
  public abstract void setPreviewEnabled(boolean enabled);
  public abstract boolean isPreviewEnabled();
  @NonNull public abstract <T> PhotoCaptureSession<T> takePhoto(@NonNull PhotoCaptureRequest<T> captureRequest);
  public abstract void close();
  public abstract void release();

  /*package*/ abstract int getCameraId();
  public abstract VideoCaptureSession startVideoRecording(@NonNull VideoCaptureRequest videoCaptureRequest);
  public abstract void stopVideoRecording(@NonNull VideoCaptureSession videoCaptureSession);
  public abstract void cancelVideoRecording(@NonNull VideoCaptureSession videoCaptureSession);

  public interface Callbacks {
    void onCameraOpened(@CameraType int cameraType, Throwable t);
    void onCameraTypeSwitchingChanged(boolean enabled);
    void onFlashModeChanged(@FlashMode String flashMode, boolean isDefault);
    void onFlashModeSwitchingChanged(boolean enabled);
    void onPhotoCaptureEnabledChanged(boolean enabled);
    void onVideoCaptureEnabledChanged(boolean enabled);
    void onZoomEnabledChanged(boolean enabled);
    void onZoomChanged(int zoom, float zoomRatio);
    void onCameraClosed();
  }

  public static class CallbacksAdapter implements Callbacks {
    @Override public void onCameraOpened(@CameraType int cameraType, Throwable t) {}
    @Override public void onCameraTypeSwitchingChanged(boolean enabled) {}
    @Override public void onFlashModeChanged(@FlashMode String flashMode, boolean isDefault) {}
    @Override public void onFlashModeSwitchingChanged(boolean enabled) {}
    @Override public void onPhotoCaptureEnabledChanged(boolean enabled) {}
    @Override public void onVideoCaptureEnabledChanged(boolean enabled) {}
    @Override public void onZoomEnabledChanged(boolean enabled) {}
    @Override public void onZoomChanged(int zoom, float zoomRatio) {}
    @Override public void onCameraClosed() {}
  }
}
