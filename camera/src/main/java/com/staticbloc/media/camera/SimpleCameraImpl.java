package com.staticbloc.media.camera;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.WindowManager;
import com.staticbloc.media.utils.FileUtils;
import com.staticbloc.media.utils.Size;
import com.staticbloc.media.utils.SizeUnit;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK;
import static android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT;
import static com.staticbloc.media.camera.CameraState.CLOSE;
import static com.staticbloc.media.camera.CameraState.ERROR;
import static com.staticbloc.media.camera.CameraState.INIT;
import static com.staticbloc.media.camera.CameraState.OPEN;
import static com.staticbloc.media.camera.CameraState.RELEASE;

/*package*/ final class SimpleCameraImpl extends SimpleCamera {
  private static final int BAD_ID = -1;
  private static final long UNSET_ZOOM_DELAY = 1000;

  @AllowedCameraType private volatile int allowedCameraType = CAMERA_TYPE_ALL;
  @CameraType private volatile int currentCameraType = CAMERA_TYPE_ALL;

  private volatile int cameraId = BAD_ID;

  private final Display display;
  private final SizeStrategy sizeStrategy;

  private Handler callbackHandler;

  private final boolean willRecordVideo;

  private Camera.CameraInfo cameraInfo;
  private Camera camera;

  private Callbacks callbacks;
  private CameraPreview cameraPreview;

  private CameraFlashChanger flashChanger;

  private final CameraState state = new CameraState();

  private volatile boolean previewEnabled;

  private int videoBitrate = NOT_SET;
  private long maxRecordingSize = NOT_SET;
  private int maxRecordingDuration = NOT_SET;

  private final boolean videoCaptureSessionCancelledOnClose;
  private VideoCaptureSession videoCaptureSession;

  private Device device = new Device();
  private final Set<String> nonAllowedFlashModes;

  private final boolean shutterSoundMute;
  private final String shutterSoundOverridePath;
  private final int shutterSoundOverrideRes;
  private ShutterSoundOverride shutterSoundOverride;

  private final OrientationEventListener orientationEventListener;
  private int currentCameraRotation = OrientationEventListener.ORIENTATION_UNKNOWN;

  private Handler myHandler;

  private final Object zoomLock = new Object();
  private AtomicBoolean smoothZooming = new AtomicBoolean();
  private final Camera.OnZoomChangeListener onZoomChangeListener = new Camera.OnZoomChangeListener() {
    @Override
    public void onZoomChange(int zoomValue, boolean stopped, Camera camera) {
      smoothZooming.set(zoomValue != device.getZoom());
      if(callbacks != null) callbacks.onZoomChanged(zoomValue, device.getZoomRatio());
    }
  };

  private final Runnable unsetSmoothingZoomRunnable = new Runnable() {
    @Override
    public void run() {
      smoothZooming.set(false);
    }
  };

  private CountDownLatch takePictureLatch;
  private final Object takePictureLock = new Object();

  /*package*/ SimpleCameraImpl(@NonNull Builder builder) {
    Context context = builder.context.getApplicationContext();
    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    this.display = windowManager.getDefaultDisplay();
    this.callbackHandler = builder.callbackHandler;
    this.sizeStrategy = builder.sizeStrategy;
    this.nonAllowedFlashModes = builder.nonAllowedFlashModes;
    this.willRecordVideo = builder.willRecordVideo;
    this.videoBitrate = builder.videoBitrate;
    this.maxRecordingSize = builder.maxRecordingSize;
    this.maxRecordingDuration = builder.maxRecordingDuration;
    this.videoCaptureSessionCancelledOnClose = builder.videoCaptureSessionCancelledOnClose;
    this.shutterSoundMute = builder.shutterSoundMute;
    this.shutterSoundOverridePath = builder.shutterSoundOverridePath;
    this.shutterSoundOverrideRes = builder.shutterSoundOverrideRes;

    if(shutterSoundMute || shutterSoundOverridePath != null || shutterSoundOverrideRes != NOT_SET) {
      this.shutterSoundOverride = new ShutterSoundOverride(context);
    }
    else {
      this.shutterSoundOverride = null;
    }

    this.orientationEventListener = new OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
      @Override
      public void onOrientationChanged(final int orientation) {
        if(myHandler != null) {
          myHandler.post(new Runnable() {
            @Override
            public void run() {
              if(state.compare(OPEN)) {
                setCameraOrientation(orientation);
              }
            }
          });
        }
      }
    };
  }

  @Override
  public void init(@NonNull Callbacks callbacks, @CameraType int allowedCameraType) {
    if (state.compareThenSet(OPEN | CLOSE | ERROR, INIT)) {
      throw new IllegalStateException("Camera state should be PRE_INIT, INIT, or RELEASE when calling init (was " + state + ")");
    }

    if(callbackHandler == null) {
      callbackHandler = new Handler(Looper.myLooper());
    }

    myHandler = new Handler(Looper.myLooper());

    this.callbacks = new CallbackWrappers.UiThreadCallbacks(callbacks, callbackHandler);
    this.allowedCameraType = allowedCameraType;
  }

  @WorkerThread
  @Override
  public void open(final @CameraType int cameraType, @NonNull CameraPreview cameraPreview) {
    if(!state.compare(INIT | CLOSE)) {
      throw new IllegalStateException("Camera state should be INIT or CLOSE when calling open (was " + state + ")");
    }

    Throwable t = null;

    // maybe we should use compareAndSwap to make sure the state is still INIT before we set it to ERROR or OPEN?
    if(!findCamera(cameraType)) {
      state.set(ERROR);
      t = new RuntimeException("Could not find camera");
    }
    else {
      state.set(OPEN);
    }

    if(!state.compare(ERROR)) {
      device = new Device(cameraId, camera.getParameters(), sizeStrategy, nonAllowedFlashModes);

      setPreview(cameraPreview);

      setUpCamera();

      orientationEventListener.enable();

      if(shutterSoundOverride != null) {
        if(shutterSoundMute) {
          shutterSoundOverride.open();
        }
        else if(shutterSoundOverridePath != null) {
          shutterSoundOverride.open(shutterSoundOverridePath);
        }
        else if(shutterSoundOverrideRes != NOT_SET) {
          shutterSoundOverride.open(shutterSoundOverrideRes);
        }
      }
    }

    if(callbacks != null) {
      callbacks.onCameraOpened(currentCameraType, t);
      callbacks.onVideoCaptureEnabledChanged(true);
    }
  }

  @Override
  public void setPreview(@NonNull CameraPreview cameraPreview) {
    if(!state.compare(OPEN)) {
      return;
    }

    if(previewEnabled) {
      setPreviewEnabled(false);
      if(this.cameraPreview != cameraPreview) {
        this.cameraPreview.release();
      }
    }

    try {
      Camera.Parameters parameters = camera.getParameters();
      parameters.setRecordingHint(willRecordVideo);
      camera.setParameters(parameters);
    }
    catch(Exception e) {
      Log.e("SimpleCamera", "There was an issue setting the recording hint", e);
    }

    this.cameraPreview = cameraPreview;

    try {
      if(cameraPreview.hasSurfaceHolder()) {
        camera.setPreviewDisplay(cameraPreview.getSurfaceHolder());
      }
      else if(cameraPreview.hasSurfaceTexture()) {
        camera.setPreviewTexture(cameraPreview.getSurfaceTexture());
      }
    }
    catch (IOException e) {
      return;
    }

    setOptimalPreviewSize(cameraPreview.getPreviewSize());
    setPreviewEnabled(true);
  }

  @Override
  public boolean isError() {
    return state.compare(ERROR);
  }

  @NonNull
  @Override
  public Size getPreviewSize() {
    return device.getPreviewSize();
  }

  @Override
  public void updatePreviewTargetSize(@NonNull Size previewTargetSize) {
    if(!state.compare(OPEN)) {
      throw new IllegalStateException("Camera state should be OPEN when calling updatePreviewTargetSize (was " + state + ")");
    }

    if(cameraPreview == null) {
      throw new IllegalStateException("A CameraPreview must be set before calling updatePreviewTargetSize");
    }

    setPreviewEnabled(false);

    setOptimalPreviewSize(previewTargetSize);
    setPreviewEnabled(true);
  }

  @NonNull
  @Override
  public Size getPhotoSize() {
    return device.getPhotoSize();
  }

  @NonNull
  @Override
  public List<Size> getSupportedPhotoSizes() {
    return device.getSupportedPhotoSizes();
  }

  @Override
  public void setPhotoSize(@NonNull Size photoSize) {
    if(!state.compare(OPEN)) {
      throw new IllegalStateException("Camera state should be OPEN when calling setPhotoSize (was " + state + ")");
    }

    if(!device.setPhotoSize(photoSize)) {
      throw new IllegalArgumentException(photoSize + " is not a supported photo size");
    }

    try {
      Camera.Parameters parameters = camera.getParameters();
      parameters.setPictureSize(photoSize.width, photoSize.height);
      camera.setParameters(parameters);
    }
    catch(Exception e) {
      Log.e("SimpleCamera", "Exception while setting photo size", e);
    }
  }

  @NonNull
  @Override
  public Size getVideoSize() {
    return device.getVideoSize();
  }

  @NonNull
  @Override
  public List<Size> getSupportedVideoSizes() {
    return device.getSupportedVideoSizes();
  }

  @Override
  public void setVideoSize(@NonNull Size videoSize) {
    if(!state.compare(OPEN)) {
      throw new IllegalStateException("Camera state should be OPEN when calling setVideoSize (was " + state + ")");
    }

    if(!device.setVideoSize(videoSize)) {
      throw new IllegalArgumentException(videoSize + " is not a supported video size");
    }
  }

  @Override
  public void setVideoBitratePerSecond(long videoBitrate, SizeUnit sizeUnit) {
    long bitrate = sizeUnit.toBits(videoBitrate);
    if(bitrate > Integer.MAX_VALUE) {
      bitrate = Integer.MAX_VALUE;
    }
    this.videoBitrate = (int) bitrate;
  }

  @Override
  public void setMaxRecordingSize(long maxRecordingSize, SizeUnit sizeUnit) {
    this.maxRecordingSize = sizeUnit.toBytes(maxRecordingSize);
  }

  @Override
  public void setMaxRecordingDuration(int maxRecordingDuration, TimeUnit timeUnit) {
    long duration = timeUnit.toMillis(maxRecordingDuration);
    if(duration > Integer.MAX_VALUE) {
      duration = Integer.MAX_VALUE;
    }
    this.maxRecordingDuration = (int) duration;
  }

  @Override
  public int getAllowedCameraType() {
    return allowedCameraType;
  }

  @Override
  public int getCameraType() {
    return currentCameraType;
  }

  @Override
  public boolean isZoomSupported() {
    return device.isZoomSupported();
  }

  @Override
  public void zoom(int zoom) {
    if(!state.compare(OPEN)) {
      throw new IllegalStateException("Camera state should be OPEN when calling zoom (was " + state + ")");
    }

    synchronized (zoomLock) {
      if(smoothZooming.get()) {
        return;
      }

      if(!device.setZoom(zoom)) {
        throw new IllegalArgumentException(zoom + " is not a supported zoom value");
      }

      Camera.Parameters parameters = camera.getParameters();
      parameters.setZoom(zoom);
      camera.setParameters(parameters);

      if(callbacks != null) callbacks.onZoomChanged(device.getZoom(), device.getZoomRatio());
    }
  }

  @Override
  public void smoothZoom(int zoom) {
    if(!state.compare(OPEN)) {
      throw new IllegalStateException("Camera state should be OPEN when calling zoom (was " + state + ")");
    }

    synchronized (zoomLock) {
      if(device.isSmoothZoomSupported()) {
        if(!device.setZoom(zoom)) {
          throw new IllegalArgumentException(zoom + " is not a supported zoom value");
        }

        if(!smoothZooming.getAndSet(true)) {
          myHandler.removeCallbacks(unsetSmoothingZoomRunnable);
          camera.startSmoothZoom(zoom);
          myHandler.postDelayed(unsetSmoothingZoomRunnable, UNSET_ZOOM_DELAY);
        }
      }
      else {
        zoom(zoom);
      }
    }
  }

  @Override
  public int getZoom() {
    return device.getZoom();
  }

  @Override
  public int getMaxZoom() {
    return device.getMaxZoom();
  }

  @Override
  public float getZoomRatio() {
    return device.getZoomRatio();
  }

  @Override
  int getCameraId() {
    return cameraId;
  }

  @Override
  public VideoCaptureSession startVideoRecording(@NonNull final VideoCaptureRequest videoCaptureRequest) {
    final VideoCaptureSession videoCaptureSession = videoCaptureRequest.getVideoCaptureSession();
    videoCaptureSession.init(videoCaptureRequest.getVideoReadyListener(), callbackHandler);

    videoCaptureSession.lock();

    try {
      if (videoCaptureSession.isCancelled()) {
        return videoCaptureSession;
      }

      if (!state.compare(OPEN)) {
        videoCaptureSession.cancel(new IllegalStateException("Camera state should be OPEN when calling startVideoRecording (was " + state + ")"));
        return videoCaptureSession;
      }
    }
    finally {
      videoCaptureSession.unlock();
    }

    setFocusForVideoOrPhoto(true);

    if(callbacks != null) callbacks.onPhotoCaptureEnabledChanged(false);

    boolean error = false;

    // carefully following the steps in the order prescribed at https://developer.android.com/guide/topics/media/camera.html#capture-video
    MediaRecorder mediaRecorder = new MediaRecorder();

    try {
      camera.unlock();
      mediaRecorder.setCamera(camera);

      mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
      mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

      mediaRecorder.setProfile(device.getCamcorderProfile());

      if(videoBitrate != NOT_SET) mediaRecorder.setVideoEncodingBitRate(videoBitrate);
      mediaRecorder.setMaxFileSize(maxRecordingSize);
      mediaRecorder.setMaxDuration(maxRecordingDuration);

      mediaRecorder.setOrientationHint(getCameraOrientation());

      FileUtils.mkdirs(videoCaptureRequest.getFile());
      mediaRecorder.setOutputFile(videoCaptureRequest.getFile().getAbsolutePath());

      mediaRecorder.setPreviewDisplay(cameraPreview.getSurface());

      MediaRecorderListener mediaRecorderListener = new MediaRecorderListener(this, videoCaptureSession);
      mediaRecorder.setOnErrorListener(mediaRecorderListener);
      mediaRecorder.setOnInfoListener(mediaRecorderListener);

      videoCaptureSession.lock();
      try {
        if(videoCaptureSession.isCancelled()) {
          mediaRecorder.reset();
          mediaRecorder.release();
          return videoCaptureSession;
        }

        mediaRecorder.prepare();

        mediaRecorder.start();

        videoCaptureSession.setRecordingObjects(mediaRecorder, videoCaptureRequest.getFile());
        this.videoCaptureSession = videoCaptureSession;

        callbackHandler.post(new Runnable() {
          @Override
          public void run() {
            videoCaptureRequest.getVideoReadyListener().onRecordingStarted();
          }
        });
      }
      catch (IOException | IllegalStateException e) {
        mediaRecorder.reset();
        mediaRecorder.release();

        videoCaptureRequest.getFile().delete();

        throw e;
      }
      finally {
        videoCaptureSession.unlock();
      }
    }
    catch(Throwable t) {
      setFocusForVideoOrPhoto(false);

      // TODO: try to figure out what went wrong, and explain it in the RuntimeException
      videoCaptureSession.cancel(new RuntimeException("Exception while starting video recording", t));

      error = true;
    }
    finally {
      try {
        camera.lock();
      }
      catch(Throwable t) {
        Log.e("SimpleCamera", "Could not re-lock camera after error", t);
      }

      if(error || device.isVideoSnapshotSupported()) {
        if(callbacks != null) callbacks.onPhotoCaptureEnabledChanged(true);
      }
    }

    return videoCaptureSession;
  }

  @Override
  public void stopVideoRecording(@NonNull VideoCaptureSession videoCaptureSession) {
    stopVideoRecording(videoCaptureSession, false);
  }

  @Override
  public void cancelVideoRecording(@NonNull VideoCaptureSession videoCaptureSession) {
    stopVideoRecording(videoCaptureSession, true);
  }

  private void stopVideoRecording(@NonNull VideoCaptureSession videoCaptureSession, boolean cancel) {
    if(this.videoCaptureSession == null) {
      throw new IllegalStateException(new IllegalStateException("Tried to stop a recording, but no video was being recorded"));
    }

    videoCaptureSession.lock();

    try {
      if(videoCaptureSession.isCancelled() || videoCaptureSession.isDone()) {
        return;
      }

      MediaRecorder mediaRecorder = videoCaptureSession.getMediaRecorder();
      if(mediaRecorder == null) {
        return;
      }

      boolean stoppedTooSoon = false;
      try {
        mediaRecorder.stop();
      }
      catch(RuntimeException e) {
        Log.w("SimpleCamera", "Stopped recording too soon after starting", e);
        stoppedTooSoon = true;
      }

      mediaRecorder.reset();
      mediaRecorder.release();

      setFocusForVideoOrPhoto(false);

      if(cancel) {
        videoCaptureSession.cancel();
      }
      else {
        videoCaptureSession.stop(stoppedTooSoon);
      }
    }
    finally {
      videoCaptureSession.unlock();
      this.videoCaptureSession = null;
      if(!device.isVideoSnapshotSupported()) {
        if(callbacks != null) callbacks.onPhotoCaptureEnabledChanged(true);
      }
    }
  }

  @Override
  public boolean isOpened() {
    return state.compare(OPEN);
  }

  @Override
  public boolean isRecording() {
    return videoCaptureSession != null;
  }

  @Override
  public boolean isClosed() {
    return state.compare(CLOSE);
  }

  @Override
  public boolean isReleased() {
    return state.compare(RELEASE);
  }

  @Override
  public boolean hasPreview() {
    return cameraPreview != null;
  }

  @Override
  public void nextFlashMode() {
    if(!state.compare(OPEN)) return;

    flashChanger.cycleCameraFlashMode();
  }

  @Override
  public void setFlashMode(@NonNull @FlashMode String flashMode) {
    if(!state.compare(OPEN)) return;

    flashChanger.setCameraFlashMode(flashMode);
  }

  @NonNull
  @FlashMode
  @Override
  public String getFlashMode() {
    if(!state.compare(OPEN)) return FLASH_MODE_AUTO;
    return flashChanger.currentFlashMode;
  }

  @NonNull
  @Override
  public List<String> getSupportedFlashModes() {
    return device.getSupportedFlashModes();
  }

  @Override
  public void toggleCameraType() {
    @CameraType int nextCameraType = (currentCameraType == CAMERA_TYPE_FRONT ? CAMERA_TYPE_BACK : CAMERA_TYPE_FRONT);
    setCameraType(nextCameraType);
  }

  @Override
  public void setCameraType(@CameraType final int cameraType) {
    if(!state.compare(OPEN)) return;

    if(allowedCameraType != CAMERA_TYPE_ALL || currentCameraType == cameraType) {
      return;
    }

    if(callbacks != null) {
      callbacks.onPhotoCaptureEnabledChanged(false);
      callbacks.onVideoCaptureEnabledChanged(false);
    }

    // HACK: do a weird little dance to make sure close() doesn't close the CameraPreview and Callbacks
    CameraPreview cameraPreviewHolder = cameraPreview;
    Callbacks callbacksHolder = callbacks;
    ShutterSoundOverride overrideHolder = shutterSoundOverride;
    cameraPreview = null;
    callbacks = null;
    shutterSoundOverride = null;
    close();
    cameraPreview = cameraPreviewHolder;
    callbacks = callbacksHolder;
    shutterSoundOverride = overrideHolder;

    open(cameraType, cameraPreview);
  }

  @Override
  public void setPreviewEnabled(boolean enabled) {
    synchronized (takePictureLock) {
      while(takePictureLatch != null) {
        try {
          takePictureLatch.await();
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }

      if(!state.compare(OPEN)) return;

      if(camera != null) {
        if (enabled) {
          camera.startPreview();
          previewEnabled = true;

          if(callbacks != null) callbacks.onPhotoCaptureEnabledChanged(true);
        }
        else {
          if(callbacks != null) callbacks.onPhotoCaptureEnabledChanged(false);

          previewEnabled = false;
          camera.stopPreview();
        }
      }
    }
  }

  @Override
  public boolean isPreviewEnabled() {
    return previewEnabled;
  }

  @NonNull
  @Override
  public <T> PhotoCaptureSession<T> takePhoto(@NonNull final PhotoCaptureRequest<T> captureRequest) {
    final PhotoCaptureSession<T> photoCaptureSession = captureRequest.getPhotoCaptureSession();
    photoCaptureSession.init(captureRequest.getPhotoCapturedListener(), callbackHandler);

    if(photoCaptureSession.isCancelled()) {
      return photoCaptureSession;
    }

    if(!state.compare(OPEN)) {
      photoCaptureSession.cancel();
      return photoCaptureSession;
    }

    if(camera != null) {
      // disable taking a photo while the camera is taking a photo
      if(callbacks != null) callbacks.onPhotoCaptureEnabledChanged(false);

      Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
        @Override
        public void onShutter() {
          callbackHandler.post(new Runnable() {
            @Override
            public void run() {
              if (photoCaptureSession.isCancelled()) return;
              if(shutterSoundOverride != null) shutterSoundOverride.play();
              if (captureRequest.getShutterActionListener() != null) captureRequest.getShutterActionListener().onShutterAction();
            }
          });
        }
      };

      @CameraType final int cameraType = currentCameraType;
      Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(final byte[] data, Camera camera) {
          synchronized (takePictureLock) {
            if(takePictureLatch != null) {
              takePictureLatch.countDown();
              takePictureLatch = null;
            }
          }

          if(shutterSoundOverride != null) {
            shutterSoundOverride.restoreVolume();
          }

          if(photoCaptureSession.isCancelled()) {
            setPreviewEnabled(true);
            return;
          }

          boolean cancel = false;

          if(!state.compare(OPEN)) {
            cancel = true;
          }

          if(data == null || cancel) {
            setPreviewEnabled(true);
            photoCaptureSession.cancel();
          }
          else {
            captureRequest.onCapture(data, callbackHandler, cameraType);

            if(captureRequest.shouldRestartPreview()) {
              setPreviewEnabled(true);
            }
          }
        }
      };

      synchronized (takePictureLock) {
        while(takePictureLatch != null) {
          try {
            takePictureLatch.await();
          }
          catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return photoCaptureSession;
          }
        }

        takePictureLatch = new CountDownLatch(1);

        if(shutterSoundOverride != null) {
          shutterSoundOverride.overrideVolume();
        }

        camera.takePicture(shutterCallback, null, pictureCallback);
      }
    }
    else {
      photoCaptureSession.cancel();
    }

    return photoCaptureSession;
  }

  @Override
  public void close() {
    if(videoCaptureSession != null) {
      stopVideoRecording(videoCaptureSession, videoCaptureSessionCancelledOnClose);
    }

    orientationEventListener.disable();
    currentCameraRotation = OrientationEventListener.ORIENTATION_UNKNOWN;

    if (camera != null) {
      camera.release();
      camera = null;
    }

    if(callbacks != null) {
      callbacks.onCameraClosed();
    }

    if(cameraPreview != null) {
      cameraPreview.release();
    }

    if(shutterSoundOverride != null) {
      shutterSoundOverride.close();
    }

    state.set(CLOSE);

    smoothZooming.set(false);
  }

  @Override
  public void release() {
    synchronized (state) {
      if(state.compare(CLOSE)) {
        close();
        state.set(RELEASE);
      }
    }

    callbacks = null;
  }

  private void setUpCamera() {
    Camera.Parameters parameters = camera.getParameters();

    Size optimalPhotoSize = device.getPhotoSize();
    if(optimalPhotoSize != null) {
      parameters.setPictureSize(optimalPhotoSize.width, optimalPhotoSize.height);
    }

    List<String> supportedFocusModes = parameters.getSupportedFocusModes();
    if(supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
      parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
    }

    List<String> supportedFlashModes = device.getSupportedFlashModes();
    if(supportedFlashModes.isEmpty()) {
      if(callbacks != null) callbacks.onFlashModeSwitchingChanged(false);
    }
    else {
      if(supportedFlashModes.contains(FLASH_MODE_AUTO)) {
        parameters.setFlashMode(FLASH_MODE_AUTO);
      }
      else {
        parameters.setFlashMode(supportedFlashModes.get(0));
      }
      if(callbacks != null) callbacks.onFlashModeSwitchingChanged(true);
    }
    flashChanger = new CameraFlashChanger(supportedFlashModes, parameters.getFlashMode());

    if(device.isZoomSupported()) {
      camera.setZoomChangeListener(onZoomChangeListener);
      if(callbacks != null) callbacks.onZoomEnabledChanged(true);
    }
    else {
      if(callbacks != null) callbacks.onZoomEnabledChanged(false);
    }

    try {
      camera.setParameters(parameters);
    }
    catch(Exception ignore) {/* this barfs on some devices*/}
  }

  private boolean setOptimalPreviewSize(Size previewTargetSize) {
    Camera.Parameters parameters = camera.getParameters();

    device.setPreviewSize(sizeStrategy, previewTargetSize);
    Size previewSize = device.getPreviewSize();
    try {
      parameters.setPreviewSize(previewSize.width, previewSize.height);
      camera.setParameters(parameters);

      if(parameters.isZoomSupported()) {
        device.resetZoom(parameters.getZoom(), parameters.getMaxZoom(), parameters.isZoomSupported(), parameters.isSmoothZoomSupported(), parameters.getZoomRatios());
        if(callbacks != null) callbacks.onZoomEnabledChanged(true);
      }
      else {
        device.resetZoom(0, 0, false, false, Collections.<Integer>emptyList());
        if(callbacks != null) callbacks.onZoomEnabledChanged(false);
      }
      return true;
    }
    catch(Exception e) {
      Log.e("SimpleCamera", "Exception while updating preview size", e);
      return false;
    }
  }

  @WorkerThread
  private boolean findCamera(@CameraType int cameraType) {
    int cameraCount = Camera.getNumberOfCameras();
    if(cameraCount == 1 || allowedCameraType == CAMERA_TYPE_ALL) {
      camera = cameraType == CAMERA_TYPE_FRONT ? getCamera(CAMERA_FACING_FRONT) : getCamera(CAMERA_FACING_BACK);
      if(camera == null) {
        camera = cameraType == CAMERA_TYPE_FRONT ? getCamera(CAMERA_FACING_BACK) : getCamera(CAMERA_FACING_FRONT);
        currentCameraType = cameraType == CAMERA_TYPE_FRONT ? CAMERA_TYPE_BACK : CAMERA_TYPE_FRONT;
      }
      else {
        currentCameraType = cameraType == CAMERA_TYPE_FRONT ? CAMERA_TYPE_FRONT : CAMERA_TYPE_BACK;
      }
    }
    else if(cameraCount > 1) {
      if(allowedCameraType == CAMERA_TYPE_BACK) {
        camera = getCamera(CAMERA_FACING_BACK);
        currentCameraType = CAMERA_TYPE_BACK;
      }
      else if(allowedCameraType == CAMERA_TYPE_FRONT) {
        camera = getCamera(CAMERA_FACING_FRONT);
        currentCameraType = CAMERA_TYPE_FRONT;
      }
    }

    if(camera == null) {
      return false;
    }

    if(callbacks != null) {
      if(cameraCount == 1 || (allowedCameraType != CAMERA_TYPE_ALL)) {
        callbacks.onCameraTypeSwitchingChanged(false);
      }
      else {
        callbacks.onCameraTypeSwitchingChanged(true);
      }
    }

    setDisplayOrientation();
    setCameraOrientation(0);

    return true;
  }

  private void setDisplayOrientation() {
    final int rotation = display.getRotation();
    int degrees = 0;
    switch (rotation) {
      case Surface.ROTATION_0: degrees = 0; break;
      case Surface.ROTATION_90: degrees = 90; break;
      case Surface.ROTATION_180: degrees = 180; break;
      case Surface.ROTATION_270: degrees = 270; break;
    }

    int result;
    if (cameraInfo.facing == CAMERA_FACING_FRONT) {
      result = (cameraInfo.orientation + degrees) % 360;
      result = (360 - result) % 360;  // compensate the mirror
    }
    else {  // back-facing
      result = (cameraInfo.orientation - degrees + 360) % 360;
    }
    camera.setDisplayOrientation(result);
  }

  private synchronized void setCameraOrientation(int orientation) {
    if(orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
      orientation = 0;
    }

    int degrees = 0;
    if(orientation > 315 || orientation <= 45) {
      degrees = 0;
    }
    else if(orientation > 45 && orientation <= 135) {
      degrees = 90;
    }
    else if(orientation > 135 && orientation <= 225) {
      degrees = 180;
    }
    else if(orientation > 225 && orientation <= 315) {
      degrees = 270;
    }

    if(currentCameraRotation == degrees) {
      return;
    }

    currentCameraRotation = degrees;

    int result = getCameraOrientation();
    try {
      Camera.Parameters parameters = camera.getParameters();
      parameters.setRotation(result);
      camera.setParameters(parameters);
    }
    catch(Exception e) {
      Log.w("SimpleCamera", "Exception while setting camera orientation", e);
    }
  }

  private int getCameraOrientation() {
    int orientation = (currentCameraRotation + 45) / 90 * 90;
    if (cameraInfo.facing == CAMERA_FACING_FRONT) {
      return (cameraInfo.orientation - orientation + 360) % 360;
    } else {  // back-facing camera
      return (cameraInfo.orientation + orientation) % 360;
    }
  }

  @IntDef({ CAMERA_FACING_BACK, CAMERA_FACING_FRONT })
  @Retention(RetentionPolicy.SOURCE)
  private @interface CameraFacing {}

  private Camera getCamera(@CameraFacing int facing) {
    try {
      for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(i, cameraInfo);

        if (cameraInfo.facing == facing) {
          cameraId = i;
          this.cameraInfo = cameraInfo;
          return Camera.open(i);
        }
      }
    }
    catch(Exception e) {
      cameraId = BAD_ID;
      Log.e("SimpleCamera", "Could not open " + (facing == CAMERA_FACING_BACK ? "back" : "front") + " camera", e);
    }

    return null;
  }

  private void setFocusForVideoOrPhoto(boolean video) {
    // always good to use a try-catch with Camera.Parameters
    try {
      Camera.Parameters params = camera.getParameters();
      String focusParam = video ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO : Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
      if(params.getSupportedFocusModes().contains(focusParam)) {
        params.setFocusMode(focusParam);
        camera.setParameters(params);
      }
    }
    catch(Throwable t) {
      Log.e("SimpleCamera", "Couldn't set focus for " + (video ? "video" : "photo"), t);
    }
  }

  private class CameraFlashChanger {
    private final List<String> supportedFlashModes;
    private String currentFlashMode;

    public CameraFlashChanger(@NonNull List<String> supportedFlashModes, @Nullable String currentFlashMode) {
      if(currentFlashMode == null) {
        currentFlashMode = FLASH_MODE_OFF;
      }

      this.supportedFlashModes = supportedFlashModes;
      this.currentFlashMode = currentFlashMode;

      if(!supportedFlashModes.isEmpty()) {
        invokeCallback(true);
      }
    }

    public void setCameraFlashMode(@FlashMode String flashMode) {
      if(!supportedFlashModes.contains(flashMode)) {
        throw new IllegalStateException("Flash mode " + flashMode + " is not supported");
      }

      currentFlashMode = flashMode;

      Camera.Parameters parameters = camera.getParameters();
      parameters.setFlashMode(currentFlashMode);
      camera.setParameters(parameters);

      invokeCallback(false);
    }

    public void cycleCameraFlashMode() {
      if(supportedFlashModes.size() <= 1) {
        return;
      }

      int currentIndex = supportedFlashModes.indexOf(currentFlashMode);
      if(currentIndex == supportedFlashModes.size() - 1) {
        currentIndex = 0;
      }
      else {
        currentIndex++;
      }

      currentFlashMode = supportedFlashModes.get(currentIndex);

      Camera.Parameters parameters = camera.getParameters();
      parameters.setFlashMode(currentFlashMode);
      camera.setParameters(parameters);

      invokeCallback(false);
    }

    private void invokeCallback(boolean isDefault) {
      switch (currentFlashMode) {
        case FLASH_MODE_AUTO:
          if(callbacks != null) callbacks.onFlashModeChanged(FLASH_MODE_AUTO, isDefault);
          break;
        case FLASH_MODE_ON:
          if(callbacks != null) callbacks.onFlashModeChanged(FLASH_MODE_ON, isDefault);
          break;
        case FLASH_MODE_OFF:
          if(callbacks != null) callbacks.onFlashModeChanged(FLASH_MODE_OFF, isDefault);
          break;
        case FLASH_MODE_RED_EYE:
          if(callbacks != null) callbacks.onFlashModeChanged(FLASH_MODE_RED_EYE, isDefault);
          break;
        case FLASH_MODE_TORCH:
          if(callbacks != null) callbacks.onFlashModeChanged(FLASH_MODE_TORCH, isDefault);
          break;
      }
    }
  }
}

