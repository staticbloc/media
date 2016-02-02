package com.staticbloc.media.camera.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.staticbloc.media.camera.CameraPreview;
import com.staticbloc.media.camera.CameraZoomTouchHandler;
import com.staticbloc.media.camera.PhotoCaptureRequest;
import com.staticbloc.media.camera.SimpleCamera;
import com.staticbloc.media.camera.SizeStrategy;
import com.staticbloc.media.camera.VideoCaptureRequest;
import com.staticbloc.media.camera.VideoCaptureSession;
import com.staticbloc.media.ui.ChildCroppingLayout;
import com.staticbloc.media.utils.Size;
import com.staticbloc.media.utils.SizeUnit;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public abstract class SimpleCameraFragment extends Fragment {
  private static final int REQUEST_CAMERA_PERMISSION = 1;
  private static final int REQUEST_VIDEO_PERMISSION = 2;

  private static final int NOT_SET = -1;

  protected static final String CUSTOM_BUNDLE_KEY = "customBundle";

  private boolean allowZooming;
  private boolean mirrorFrontCameraImages;
  private boolean willRecordVideo;
  @SimpleCamera.AllowedCameraType private int allowedCameraType;
  @SimpleCamera.CameraType private int initialCameraType;

  private boolean mockFrontFlash;
  private ShutterActionFlashScreen mockFrontFlashCallback;

  private boolean cameraPermission;
  private boolean videoPermission;

  private CameraPreviewWrapper cameraPreviewWrapper;
  private FrameLayout previewContainerView;

  private File outputDirectory;

  private CameraZoomTouchHandler zoomTouchHandler;

  private SimpleCamera camera;

  private PhotoCaptureRequest.OnShutterActionListener shutterActionListener;

  private View capturePhotoView;
  private View recordVideoView;
  private View stopRecordingVideoView;
  private TextView zoomStateView;
  private View toggleCameraTypeView;
  private View toggleFlashTypeView;

  private VideoCaptureSession videoCaptureSession;

  private final ClickListener clickListener = new ClickListener();

  private boolean longPressCaptureToRecordVideo;
  private final CaptureGestureListener captureGestureListener = new CaptureGestureListener();
  private GestureDetector gestureDetector;

  private DecimalFormat zoomRatioFormat;

  private SimpleCamera.Callbacks callbacks = new SimpleCamera.Callbacks() {
    @Override
    public void onCameraOpened(@SimpleCamera.CameraType int cameraType, Throwable t) {
      if(t != null) {
        onCameraOpenError(t);
      }

      cameraPreviewWrapper.setPreviewViewAspectRatio(camera.getPreviewSize());

      if(allowZooming) {
        final View cameraPreviewView = cameraPreviewWrapper.getPreviewView();
        cameraPreviewView.post(new Runnable() {
          @Override
          public void run() {
            zoomTouchHandler = new CameraZoomTouchHandler(camera, cameraPreviewView.getMeasuredHeight() / 4, new CameraZoomTouchHandler.OnNewZoomValueListener() {
              @Override
              public void onZoomingStarted() {
                if(zoomStateView != null) {
                  zoomStateView.setVisibility(View.VISIBLE);
                }
              }

              @Override
              public void onNewZoomValue(int newZoom) {
                camera.zoom(newZoom);
              }

              @Override
              public void onZoomingEnded() {
                if(zoomStateView != null) {
                  zoomStateView.setVisibility(View.GONE);
                }
              }
            });
            cameraPreviewView.setOnTouchListener(zoomTouchHandler);
          }
        });
      }

      SimpleCameraFragment.this.onCameraOpened(cameraType);
    }

    @Override
    public void onCameraTypeSwitchingChanged(boolean enabled) {
      if(toggleCameraTypeView != null) {
        float alpha;
        if(enabled) {
          alpha = 1;
          toggleCameraTypeView.setOnClickListener(clickListener);
        }
        else {
          alpha = 0;
          toggleCameraTypeView.setOnClickListener(null);
        }

        toggleCameraTypeView.animate().alpha(alpha).start();
      }
    }

    @Override
    public void onFlashModeChanged(@SimpleCamera.FlashMode String flashMode, boolean isDefault) {
      SimpleCameraFragment.this.onFlashModeChanged(flashMode, isDefault);
    }

    @Override
    public void onFlashModeSwitchingChanged(boolean enabled) {
      if(toggleFlashTypeView != null) {
        float alpha;
        if(enabled) {
          alpha = 1;
          toggleFlashTypeView.setOnClickListener(clickListener);
        }
        else {
          alpha = 0;
          toggleFlashTypeView.setOnClickListener(null);
        }

        toggleFlashTypeView.animate().alpha(alpha).start();
      }
    }

    @Override
    public void onPhotoCaptureEnabledChanged(boolean enabled) {
      if(longPressCaptureToRecordVideo) {
        captureGestureListener.photoCaptureEnabled = enabled;
      }
      else if(capturePhotoView != null) {
        float alpha;
        if(enabled) {
          alpha = 1;
          capturePhotoView.setOnClickListener(clickListener);
        }
        else {
          alpha = 0;
          capturePhotoView.setOnClickListener(null);
        }

        capturePhotoView.animate().alpha(alpha).start();
      }
    }

    @Override
    public void onVideoCaptureEnabledChanged(boolean enabled) {
      if(longPressCaptureToRecordVideo) {
        captureGestureListener.videoCaptureEnabled = enabled;
      }
      else {
        float alpha = enabled ? 1 : 0;
        if(recordVideoView != null) {
          if(enabled) {
            recordVideoView.setOnClickListener(clickListener);
          }
          else {
            recordVideoView.setOnClickListener(null);
          }

          recordVideoView.animate().alpha(alpha).start();
        }
        if(stopRecordingVideoView != null) {
          if(enabled) {
            stopRecordingVideoView.setOnClickListener(clickListener);
          }
          else {
            stopRecordingVideoView.setOnClickListener(null);
          }
        }
      }
    }

    @Override
    public void onZoomEnabledChanged(boolean enabled) {
      if(zoomTouchHandler != null) {
        zoomTouchHandler.onZoomParametersChanged(camera);
      }
    }

    @Override
    public void onZoomChanged(int zoom, float zoomRatio) {
      if(zoomStateView != null && zoomRatioFormat != null) {
        String zoomRatioStr = zoomRatioFormat.format(zoomRatio) + "x";
        zoomStateView.setText(zoomRatioStr);
      }
    }

    @Override
    public void onCameraClosed() {
      if(recordVideoView != null) recordVideoView.setOnClickListener(null);
      if(capturePhotoView != null) capturePhotoView.setOnClickListener(null);
      if(toggleFlashTypeView != null) toggleFlashTypeView.setOnClickListener(null);
      if(toggleCameraTypeView != null) toggleCameraTypeView.setOnClickListener(null);

      if(cameraPreviewWrapper != null) {
        View cameraPreviewView = cameraPreviewWrapper.getPreviewView();
        if(cameraPreviewView != null) {
          cameraPreviewView.setOnTouchListener(null);
        }
      }
    }
  };

  public SimpleCameraFragment() {}

  @SuppressLint("ValidFragment")
  protected SimpleCameraFragment(Builder builder) {
    populateArguments(builder);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Bundle args = getArguments();
    if(args == null) {
      throw new NullPointerException("SimpleCameraFragment.getArguments() returns null. Please use SimpleCameraFragment.Builder instead of the default constructor");
    }

    allowZooming = args.getBoolean("allowZooming");
    mirrorFrontCameraImages = args.getBoolean("mirrorFrontCameraImages");
    boolean shutterSoundMute = args.getBoolean("shutterSoundMute");
    String shutterSoundOverridePath = args.getString("shutterSoundOverridePath");
    int shutterSoundOverrideRes = args.getInt("shutterSoundOverrideRes");
    willRecordVideo = args.getBoolean("willRecordVideo");
    int videoBitrate = args.getInt("videoBitrate");
    long maxRecordingSize = args.getLong("maxRecordingSize");
    int maxRecordingDuration = args.getInt("maxRecordingDuration");
    boolean videoCaptureSessionCancelledOnClose = args.getBoolean("videoCaptureSessionCancelledOnClose");
    //noinspection ResourceType
    allowedCameraType = args.getInt("allowedCameraType");
    //noinspection ResourceType
    initialCameraType = args.getInt("initialCameraType");
    String[] nonAllowedFlashModes = args.getStringArray("nonAllowedFlashModes");
    if(nonAllowedFlashModes == null) {
      nonAllowedFlashModes = new String[0];
    }
    mockFrontFlash = args.getBoolean("mockFrontFlash");
    int previewType = args.getInt("previewType");

    String outputDirectoryPath = args.getString("outputDirectory");
    if(TextUtils.isEmpty(outputDirectoryPath)) {
      outputDirectory = getContext().getFilesDir();
    }
    else {
      outputDirectory = new File(outputDirectoryPath);
    }

    longPressCaptureToRecordVideo = args.getBoolean("longPressCaptureToRecordVideo");

    SizeStrategy.DefaultSizeStrategy.Builder sizeStrategyBuilder = new SizeStrategy.DefaultSizeStrategy.Builder();
    Size maxPreviewSize = args.getParcelable("maxPreviewSize");
    if(maxPreviewSize != null) {
      sizeStrategyBuilder.maxPreviewSize(maxPreviewSize);
    }
    Size targetPhotoSize = args.getParcelable("targetPhotoSize");
    if(targetPhotoSize != null) {
      sizeStrategyBuilder.targetPhotoSize(targetPhotoSize);
    }
    Size targetVideoSize = args.getParcelable("targetVideoSize");
    if(targetVideoSize != null) {
      sizeStrategyBuilder.targetVideoSize(targetVideoSize);
    }

    if(allowZooming) {
      zoomRatioFormat = new DecimalFormat("#.#");
      zoomRatioFormat.setMinimumFractionDigits(1);
      zoomRatioFormat.setMaximumFractionDigits(1);
    }

    SimpleCamera.Builder cameraBuilder = new SimpleCamera.Builder(getContext())
        .callbacksOnMainThread()
        .sizeStrategy(sizeStrategyBuilder.build())
        .willRecordVideo(willRecordVideo)
        .cancelVideoCaptureOnClose(videoCaptureSessionCancelledOnClose)
        .nonAllowedFlashModes(nonAllowedFlashModes);

    if(videoBitrate != NOT_SET) {
      cameraBuilder.videoBitratePerSecond(videoBitrate, SizeUnit.BITS);
    }
    if(maxRecordingSize != NOT_SET) {
      cameraBuilder.maxRecordingSize(maxRecordingSize, SizeUnit.BYTES);
    }
    if(maxRecordingDuration != NOT_SET) {
      cameraBuilder.maxRecordingDuration(maxRecordingDuration, TimeUnit.MILLISECONDS);
    }

    if(shutterSoundMute) {
      cameraBuilder.muteShutterSound();
    }
    else if(shutterSoundOverridePath != null) {
      cameraBuilder.overrideShutterSound(shutterSoundOverridePath);
    }
    else if(shutterSoundOverrideRes != NOT_SET) {
      cameraBuilder.overrideShutterSound(shutterSoundOverrideRes);
    }

    camera = cameraBuilder.build();
    camera.init(callbacks, allowedCameraType);
    cameraPreviewWrapper = CameraPreviewWrapper.newInstance(getContext(), previewType);
  }

  @Nullable
  @Override
  public final View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    ChildCroppingLayout childCroppingLayout = new ChildCroppingLayout(getContext());
    childCroppingLayout.addView(cameraPreviewWrapper.getPreviewView());

    previewContainerView = new FrameLayout(getContext());
    previewContainerView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    View cameraOverlayView = onCreateCameraControlsView(inflater, previewContainerView);

    if(cameraOverlayView != null) {
      previewContainerView.addView(cameraOverlayView);
    }

    previewContainerView.addView(childCroppingLayout, 0);

    //noinspection ResourceType
    @Builder.ShutterAction int shutterAction = getArguments().getInt("shutterAction");
    if(shutterAction == Builder.SHUTTER_ACTION_FLASH_SCREEN) {
      shutterActionListener = new ShutterActionFlashScreen(previewContainerView);
    }
    else if(shutterAction == Builder.SHUTTER_ACTION_SCREEN_SHUTTER) {
      shutterActionListener = new ShutterActionFlashScreen(previewContainerView);
    }
    else {
      shutterActionListener = new NoOpShutterAction();
    }

    if(mockFrontFlash) {
      mockFrontFlashCallback = new ShutterActionFlashScreen(previewContainerView, 1500);
    }

    return previewContainerView;
  }

  @Override
  public void onResume() {
    super.onResume();

    cameraPermission = ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    videoPermission = ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

    if (cameraPermission) {
      openCamera();
    }
    else {
      requestCameraPermission();
    }
  }

  private void openCamera() {
    previewContainerView.post(new Runnable() {
      @Override
      public void run() {
        Size targetSize = new Size(previewContainerView.getMeasuredWidth(), previewContainerView.getMeasuredHeight());
        cameraPreviewWrapper.registerCameraPreviewListener(new CameraPreviewWrapper.CameraPreviewListener() {
          @Override
          public void onPreviewReady(CameraPreview cameraPreview) {
            camera.open(initialCameraType, cameraPreview);
          }

          @Override
          public void onPreviewViewSizeChanged(int width, int height) {
            if (camera.hasPreview()) {
              camera.updatePreviewTargetSize(new Size(width, height));
              cameraPreviewWrapper.setPreviewViewAspectRatio(camera.getPreviewSize());
            }
          }
        }, targetSize);
      }
    });
  }

  @Override
  public void onPause() {
    super.onPause();

    if(camera.isOpened()) {
      camera.close();
    }

    cameraPreviewWrapper.unregisterCameraPreviewListener();
  }

  @Override
  public void onDetach() {
    super.onDetach();

    camera.release();
    cameraPreviewWrapper.destroy();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    switch(requestCode) {
      case REQUEST_CAMERA_PERMISSION:
        cameraPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!cameraPermission) {
          onCameraPermissionDenied();
          return;
        }

        openCamera();
        break;
      case REQUEST_VIDEO_PERMISSION:
        cameraPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        videoPermission = grantResults[1] == PackageManager.PERMISSION_GRANTED;

        if (!cameraPermission) {
          onCameraPermissionDenied();
          return;
        }
        else if (!videoPermission) {
          onVideoPermissionDenied();
        }

        openCamera();
        break;
      default:
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }

  protected final void setCapturePhotoView(View capturePhotoView) {
    this.capturePhotoView = capturePhotoView;
    if(longPressCaptureToRecordVideo) {
      gestureDetector = new GestureDetector(getContext(), captureGestureListener, new Handler(Looper.getMainLooper()));
      capturePhotoView.setOnTouchListener(captureTouchListener);
    }
  }

  protected final void setRecordVideoView(View recordVideoView) {
    if(longPressCaptureToRecordVideo) {
      throw new IllegalStateException("Cannot call setRecordVideoView if longPressCaptureToRecordVideo is true");
    }
    this.recordVideoView = recordVideoView;
  }

  protected final void setStopRecordingVideoView(View stopRecordingVideoView) {
    if(longPressCaptureToRecordVideo) {
      throw new IllegalStateException("Cannot call setStopRecordingVideoView if longPressCaptureToRecordVideo is true");
    }
    this.stopRecordingVideoView = stopRecordingVideoView;
  }

  protected final void setZoomStateView(TextView zoomStateView) {
    if(!allowZooming) {
      throw new IllegalStateException("Cannot set a zoomStateView if zooming is not allowed (check the Builder)");
    }
    this.zoomStateView = zoomStateView;
  }

  protected final void setToggleCameraTypeView(View toggleCameraTypeView) {
    this.toggleCameraTypeView = toggleCameraTypeView;
  }

  protected final void setToggleFlashTypeView(View toggleFlashTypeView) {
    this.toggleFlashTypeView = toggleFlashTypeView;
  }

  protected final void requestCameraPermission() {
    if(!cameraPermission) {
      if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.CAMERA)) {
        onExplainCameraPermission();
        return;
      }
    }
    else if(willRecordVideo && !videoPermission) {
      if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.RECORD_AUDIO)) {
        onExplainVideoPermission();
        return;
      }
    }

    if(willRecordVideo) {
      ActivityCompat.requestPermissions(getActivity(), new String[] { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO }, REQUEST_VIDEO_PERMISSION);
    }
    else {
      ActivityCompat.requestPermissions(getActivity(), new String[] { Manifest.permission.CAMERA }, REQUEST_CAMERA_PERMISSION);
    }
  }

  protected abstract void onCameraOpened(@SimpleCamera.CameraType int cameraType);
  protected abstract void onCameraOpenError(@NonNull Throwable t);
  protected abstract void onCameraPermissionDenied();
  protected abstract void onExplainCameraPermission();
  protected abstract void onVideoPermissionDenied();
  protected abstract void onExplainVideoPermission();
  @Nullable protected abstract View onCreateCameraControlsView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container);
  protected abstract void onPhotoCaptured(@NonNull File photo);
  protected abstract void onPhotoCaptureError(@NonNull Throwable t);
  protected abstract void onVideoStartedRecording();
  protected abstract void onVideoAlreadyRecording();
  protected abstract void onVideoRecordingError(@NonNull Throwable t);
  protected abstract void onVideoRecordingCancelled();
  protected abstract void onVideoRecordingFinished(boolean autoStopped);
  protected abstract void onFlashModeChanged(@NonNull @SimpleCamera.FlashMode String flashMode, boolean isDefault);

  protected void animateCameraTypeToggle(ViewPropertyAnimator animator) {}

  @NonNull
  private File getFileForPhoto() {
    String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
    return new File(outputDirectory, fileName);
  }

  @NonNull
  private File getFileForVideo() {
    String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";
    return new File(outputDirectory, fileName);
  }

  private void takePhoto() {
    if(!cameraPermission) {
      requestCameraPermission();
      return;
    }

    if(camera.isOpened()) {
      if(camera.getCameraType() == SimpleCamera.CAMERA_TYPE_FRONT && camera.getFlashMode().equals(SimpleCamera.FLASH_MODE_OFF) &&
          mockFrontFlashCallback != null) {
        mockFrontFlashCallback.onShutterAction();
      }

      camera.takePhoto(PhotoCaptureRequest.asFile(getFileForPhoto())
              .restartPreview(true)
              .mirrorFrontCameraImage(mirrorFrontCameraImages)
              .shutterActionListener(shutterActionListener)
              .photoReadyListener(new PhotoCaptureRequest.PhotoCapturedListener<File>() {
                @Override
                public void onPhotoCaptured(@Nullable Throwable t, @NonNull File photo) {
                  if (t == null) {
                    SimpleCameraFragment.this.onPhotoCaptured(photo);
                  }
                  else {
                    onPhotoCaptureError(t);
                  }
                }

                @Override
                public void onCancelled() { /* we don't allow capture requests to be cancelled */ }
              })
      );
    }
  }

  private void startRecordingVideo() {
    if(!videoPermission) {
      requestCameraPermission();
      return;
    }

    if(camera.isRecording()) {
      onVideoAlreadyRecording();
    }
    else {
      if(videoCaptureSession != null) {
        camera.cancelVideoRecording(videoCaptureSession);
      }

      videoCaptureSession = camera.startVideoRecording(new VideoCaptureRequest.Builder(getFileForVideo(), new VideoCaptureRequest.VideoCallbacks() {
        @Override
        public void onRecordingStarted() {
          if(recordVideoView != null) recordVideoView.animate().alpha(0).start();
          if(stopRecordingVideoView != null) stopRecordingVideoView.animate().alpha(1).start();
          captureGestureListener.recordingVideo = true;

          onVideoStartedRecording();
        }

        @Override
        public void onVideoReady(@Nullable Throwable e, @Nullable File video, boolean autoStopped) {
          videoCaptureSession = null;

          if(recordVideoView != null) recordVideoView.animate().alpha(0).start();
          if(stopRecordingVideoView != null) stopRecordingVideoView.animate().alpha(1).start();

          captureGestureListener.recordingVideo = false;

          if(e != null) {
            onVideoRecordingError(e);
          }
          else if(video == null) {
            onVideoRecordingCancelled();
          }
          else {
            onVideoRecordingFinished(autoStopped);
          }
        }

        @Override
        public void onCancelled() {
          videoCaptureSession = null;

          if(recordVideoView != null) recordVideoView.animate().alpha(0).start();
          if(stopRecordingVideoView != null) stopRecordingVideoView.animate().alpha(1).start();

          captureGestureListener.recordingVideo = false;

          onVideoRecordingCancelled();
        }
      }).build());
    }
  }

  private void stopRecordingVideo() {
    captureGestureListener.recordingVideo = false;

    if(videoCaptureSession == null || videoCaptureSession.isDone() || videoCaptureSession.isCancelled()) {
      videoCaptureSession = null;
      if(recordVideoView != null) recordVideoView.animate().alpha(0).start();
      if(stopRecordingVideoView != null) stopRecordingVideoView.animate().alpha(1).start();
    }
    else {
      camera.stopVideoRecording(videoCaptureSession);
    }
  }

  private void populateArguments(Builder builder) {
    Bundle args = new Bundle();
    args.putBoolean("allowZooming", builder.allowZooming);
    args.putBoolean("mirrorFrontCameraImages", builder.mirrorFrontCameraImages);
    args.putInt("shutterAction", builder.shutterAction);
    args.putBoolean("shutterSoundMute", builder.shutterSoundMute);
    args.putString("shutterSoundOverridePath", builder.shutterSoundOverridePath);
    args.putInt("shutterSoundOverrideRes", builder.shutterSoundOverrideRes);
    args.putBoolean("willRecordVideo", builder.willRecordVideo);
    args.putInt("videoBitrate", builder.video.bitrate);
    args.putLong("maxRecordingSize", builder.video.maxRecordingSize);
    args.putInt("maxRecordingDuration", builder.video.maxRecordingDuration);
    args.putBoolean("videoCaptureSessionCancelledOnClose", builder.video.videoCaptureSessionCancelledOnClose);
    args.putInt("allowedCameraType", builder.allowedCameraType);
    args.putInt("initialCameraType", builder.initialCameraType);
    args.putStringArray("nonAllowedFlashModes", builder.nonAllowedFlashModes);
    args.putBoolean("mockFrontFlash", builder.mockFrontFlash);
    args.putInt("previewType", builder.previewType);
    args.putString("outputDirectory", builder.outputDirectory.getAbsolutePath());
    args.putBoolean("longPressCaptureToRecordVideo", builder.longPressCaptureToRecordVideo);
    args.putParcelable("maxPreviewSize", builder.maxPreviewSize);
    args.putParcelable("targetPhotoSize", builder.targetPhotoSize);
    args.putParcelable("targetVideoSize", builder.video.targetSize);
    if(builder.customBundle != null) {
      args.putBundle(CUSTOM_BUNDLE_KEY, builder.customBundle);
    }
    setArguments(args);
  }

  private class ClickListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      if(v == capturePhotoView) {
        takePhoto();
      }
      else if(v == recordVideoView) {
        startRecordingVideo();
      }
      else if(v == stopRecordingVideoView) {
        stopRecordingVideo();
      }
      else if(v == toggleCameraTypeView) {
        if(!cameraPermission) {
          requestCameraPermission();
          return;
        }

        camera.toggleCameraType();
        animateCameraTypeToggle(cameraPreviewWrapper.getPreviewView().animate());
      }
      else if(v == toggleFlashTypeView) {
        if(!cameraPermission) {
          requestCameraPermission();
          return;
        }

        camera.nextFlashMode();
      }
    }
  }

  private View.OnTouchListener captureTouchListener = new View.OnTouchListener() {
    @Override
    public boolean onTouch(View v, MotionEvent event) {
      if(captureGestureListener.recordingVideo && event.getAction() == MotionEvent.ACTION_UP && event.getPointerCount() <= 1) {
        stopRecordingVideo();
        return true;
      }
      else {
        return gestureDetector.onTouchEvent(event);
      }
    }
  };

  private class CaptureGestureListener extends GestureDetector.SimpleOnGestureListener {
    public boolean photoCaptureEnabled;
    public boolean videoCaptureEnabled;
    public boolean recordingVideo;

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
      if(photoCaptureEnabled) {
        takePhoto();
        return true;
      }
      else {
        return false;
      }
    }

    @Override
    public void onLongPress(MotionEvent e) {
      if(videoCaptureEnabled) {
        capturePhotoView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        startRecordingVideo();
      }
    }

    @Override
    public boolean onDown(MotionEvent e) {
      return photoCaptureEnabled || videoCaptureEnabled;
    }
  }

  public abstract static class Builder<T> {
    @IntDef({ SHUTTER_ACTION_NONE, SHUTTER_ACTION_FLASH_SCREEN, SHUTTER_ACTION_SCREEN_SHUTTER })
    @Retention(RetentionPolicy.SOURCE)
    /*package*/ @interface ShutterAction {}

    /*package*/ static final int SHUTTER_ACTION_NONE = 0;
    /*package*/ static final int SHUTTER_ACTION_FLASH_SCREEN = 1;
    /*package*/ static final int SHUTTER_ACTION_SCREEN_SHUTTER = 2;

    private boolean allowZooming = true;
    private boolean mirrorFrontCameraImages = true;
    @ShutterAction private int shutterAction = SHUTTER_ACTION_NONE;
    private boolean shutterSoundMute = false;
    private String shutterSoundOverridePath = null;
    private int shutterSoundOverrideRes = NOT_SET;
    private boolean willRecordVideo = false;
    private Video video = new Video();
    private int allowedCameraType = SimpleCamera.CAMERA_TYPE_ALL;
    private int initialCameraType = SimpleCamera.CAMERA_TYPE_BACK;
    private boolean mockFrontFlash = false;
    private String[] nonAllowedFlashModes = new String[0];
    private int previewType = CameraPreviewWrapper.SURFACE_VIEW_PREVIEW;
    private File outputDirectory = null;
    private boolean longPressCaptureToRecordVideo = false;

    private Size maxPreviewSize = new Size(Integer.MAX_VALUE, Integer.MAX_VALUE);
    private Size targetPhotoSize = new Size(1920, 1080);

    private Bundle customBundle = null;

    @NonNull
    public Builder<T> allowZooming(boolean allowZooming) {
      this.allowZooming = allowZooming;
      return this;
    }

    @NonNull
    public Builder<T> mirrorFrontCameraImages(boolean mirrorFrontCameraImages) {
      this.mirrorFrontCameraImages = mirrorFrontCameraImages;
      return this;
    }

    @NonNull
    public Builder<T> withFlashScreenShutterAction() {
      this.shutterAction = SHUTTER_ACTION_FLASH_SCREEN;
      return this;
    }

    @NonNull
    public Builder<T> withScreenShutterShutterAction() {
      this.shutterAction = SHUTTER_ACTION_SCREEN_SHUTTER;
      return this;
    }

    @NonNull
    public Builder<T> muteShutterSound() {
      this.shutterSoundMute = true;
      return this;
    }

    @NonNull
    public Builder<T> overrideShutterSound(@NonNull String shutterSoundOverridePath) {
      this.shutterSoundOverrideRes = 0;
      this.shutterSoundOverridePath = shutterSoundOverridePath;
      return this;
    }

    @NonNull
    public Builder<T> overrideShutterSound(int shutterSoundOverrideRes) {
      this.shutterSoundOverridePath = null;
      this.shutterSoundOverrideRes = shutterSoundOverrideRes;
      return this;
    }

    @NonNull
    public Builder<T> video(@NonNull Video video) {
      this.willRecordVideo = true;
      this.video = video;
      return this;
    }

    @NonNull
    public Builder<T> allowedCameraType(@SimpleCamera.AllowedCameraType int allowedCameraType) {
      this.allowedCameraType = allowedCameraType;
      return this;
    }

    @NonNull
    public Builder<T> initalCameraType(@SimpleCamera.CameraType int initialCameraType) {
      this.initialCameraType = initialCameraType;
      return this;
    }

    @NonNull
    public Builder<T> nonAllowedFlashModes(@NonNull String... nonAllowedFlashModes) {
      this.nonAllowedFlashModes = nonAllowedFlashModes;
      return this;
    }

    @NonNull
    public Builder<T> mockFrontFlash(boolean mockFrontFlash) {
      this.mockFrontFlash = mockFrontFlash;
      return this;
    }

    @NonNull
    public Builder<T> surfaceViewPreview() {
      this.previewType = CameraPreviewWrapper.SURFACE_VIEW_PREVIEW;
      return this;
    }

    @NonNull
    public Builder<T> textureViewPreview() {
      this.previewType = CameraPreviewWrapper.TEXTURE_VIEW_PREVIEW;
      return this;
    }

    @NonNull
    public Builder<T> outputDirectory(@NonNull String outputDirectory) {
      this.outputDirectory = new File(outputDirectory);
      return this;
    }

    @NonNull
    public Builder<T> outputDirectory(@NonNull File outputDirectory) {
      this.outputDirectory = outputDirectory;
      return this;
    }

    @NonNull
    public Builder<T> longPressCaptureToRecordVideo(boolean longPressCaptureToRecordVideo) {
      this.longPressCaptureToRecordVideo = longPressCaptureToRecordVideo;
      return this;
    }

    @NonNull
    public Builder<T> maxPreviewSize(@NonNull Size maxPreviewSize) {
      this.maxPreviewSize = maxPreviewSize;
      return this;
    }

    @NonNull
    public Builder<T> targetPhotoSize(@NonNull Size targetPhotoSize) {
      this.targetPhotoSize = targetPhotoSize;
      return this;
    }

    @NonNull
    public Builder<T> customBundle(@NonNull Bundle customBundle) {
      this.customBundle = customBundle;
      return this;
    }

    @NonNull
    public abstract T build();
  }

  public static class Video {
    private final int bitrate;
    private final long maxRecordingSize;
    private final int maxRecordingDuration;
    private final boolean videoCaptureSessionCancelledOnClose;
    private Size targetSize;

    private Video() {
      this(new Builder());
    }

    private Video(Builder builder) {
      this.bitrate = builder.bitrate;
      this.maxRecordingSize = builder.maxRecordingSize;
      this.maxRecordingDuration = builder.maxRecordingDuration;
      this.videoCaptureSessionCancelledOnClose = builder.videoCaptureSessionCancelledOnClose;
      this.targetSize = builder.targetSize;
    }

    public static class Builder {
      private int bitrate = NOT_SET;
      private long maxRecordingSize = NOT_SET;
      private int maxRecordingDuration = NOT_SET;
      private boolean videoCaptureSessionCancelledOnClose = true;
      private Size targetSize = new Size(1920, 1080);

      @NonNull
      public Builder bitrate(int videoBitrate, SizeUnit sizeUnit) {
        long bitrate = sizeUnit.toBits(videoBitrate);
        if(bitrate > Integer.MAX_VALUE) {
          bitrate = Integer.MAX_VALUE;
        }
        this.bitrate = (int) bitrate;
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
      public Builder targetSize(@NonNull Size targetSize) {
        this.targetSize = targetSize;
        return this;
      }

      @NonNull
      public Video build() {
        return new Video(this);
      }
    }
  }
}
