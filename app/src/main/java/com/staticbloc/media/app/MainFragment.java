package com.staticbloc.media.app;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.widget.ImageView;
import com.staticbloc.media.camera.SimpleCamera;
import com.staticbloc.media.camera.fragment.CameraFileFactory;
import com.staticbloc.media.camera.fragment.SimpleCameraFragment;
import com.staticbloc.media.utils.Size;
import com.staticbloc.media.utils.SizeUnit;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class MainFragment extends SimpleCameraFragment {
  private static final int REQUEST_WRITE_EXTERNAL_PERMISSION = 3;
  private ImageView imageView;
  private View capturePhotoView;
  private ImageView toggleFlashTypeView;

  private boolean writeExternalPermission;

  public MainFragment() {}

  @SuppressLint("ValidFragment")
  protected MainFragment(Builder builder) {
    super(builder);
  }

  public static MainFragment newInstance() {
    return new Builder<MainFragment>() {
      @NonNull
      @Override
      public MainFragment build() {
        return new MainFragment(this);
      }
    }
        .initalCameraType(SimpleCamera.CAMERA_TYPE_FRONT)
        .allowedCameraType(SimpleCamera.CAMERA_TYPE_ALL)
        .textureViewPreview()
        .mirrorFrontCameraImages(true)
        .muteShutterSound()
        .nonAllowedFlashModes(SimpleCamera.FLASH_MODE_RED_EYE)
        .targetPhotoSize(new Size(1920, 1080))
        .withFlashScreenShutterAction()
        .fileFactory(new CameraFileFactory.Default(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SimpleCamera")))
        .video(new Video.Builder()
            .bitrate(750, SizeUnit.KB)
            .maxRecordingDuration(10, TimeUnit.SECONDS)
            .targetSize(new Size(1920, 1080))
            .cancelVideoCaptureOnClose(true)
            .longPressCaptureToRecordVideo(true)
            .build())
        .allowZooming(true)
        .build();
  }

  @Override
  public void onResume() {
    super.onResume();

    writeExternalPermission = ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

    if(capturePhotoView != null) capturePhotoView.setEnabled(writeExternalPermission);

    if(!writeExternalPermission) {
      requestWriteExternalPermission();
    }
  }

  private void requestWriteExternalPermission() {
    if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
      onExplainWriteExternalPermission();
    }
    else {
      ActivityCompat.requestPermissions(getActivity(), new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_WRITE_EXTERNAL_PERMISSION);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    switch(requestCode) {
      case REQUEST_WRITE_EXTERNAL_PERMISSION:
        writeExternalPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!writeExternalPermission) {
          onWriteExternalPermissionDenied();
        }

        if(capturePhotoView != null) capturePhotoView.setEnabled(writeExternalPermission);

        break;
      default:
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }

  protected void onWriteExternalPermissionDenied() {
    getActivity().finish();
  }

  protected void onExplainWriteExternalPermission() {
    // TODO: use material dialog to explain and in the positive callback request permissions again
    requestWriteExternalPermission();
  }

  @Override
  protected void onCameraOpened(@SimpleCamera.CameraType int cameraType) {

  }

  @Override
  protected void onCameraOpenError(@NonNull Throwable t) {
    Snackbar.make(capturePhotoView, "Error starting camera", Snackbar.LENGTH_LONG).show();
    Log.e("SimpleCamera", "There was an error opening the camera", t);
  }

  @Override
  protected void onCameraPermissionDenied() {
    getActivity().finish();
  }

  @Override
  protected void onExplainCameraPermission() {
    // TODO: use material dialog to explain and in the positive callback request permissions again
    requestCameraPermission();
  }

  @Override
  protected void onVideoPermissionDenied() {
    Snackbar.make(capturePhotoView, "Cannot record video; permission denied", Snackbar.LENGTH_LONG).show();
  }

  @Override
  protected void onExplainVideoPermission() {
    // TODO: use material dialog to explain and in the positive callback request permissions again
    requestCameraPermission();
  }

  @Nullable
  @Override
  protected View onCreateCameraControlsView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
    View overlay = inflater.inflate(R.layout.camera_controls_overlay, container, false);
    capturePhotoView = overlay.findViewById(R.id.take_photo);
    setCapturePhotoView(capturePhotoView);
    setToggleCameraTypeView(overlay.findViewById(R.id.toggle_camera_type));
    toggleFlashTypeView = (ImageView) overlay.findViewById(R.id.toggle_flash_type);
    setToggleFlashTypeView(toggleFlashTypeView);
    imageView = (ImageView) overlay.findViewById(R.id.image);
    return overlay;
  }

  @Override
  protected void onPhotoCaptured(@NonNull File photo) {
    imageView.setImageURI(Uri.fromFile(photo));
    imageView.animate()
        .alpha(1)
        .translationX(0)
        .translationY(0)
        .scaleX(1)
        .scaleY(1)
        .setListener(new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            imageView.postDelayed(new Runnable() {
              @Override
              public void run() {
                imageView.animate()
                    .alpha(0)
                    .translationX(imageView.getWidth())
                    .translationY(imageView.getHeight())
                    .scaleX(0)
                    .scaleY(0)
                    .setListener(null)
                    .start();
              }
            }, 2500);

            animation.removeListener(this);
          }
        })
        .start();
  }

  @Override
  protected void onPhotoCaptureError(@NonNull Throwable t) {
    Snackbar.make(capturePhotoView, "Error taking photo", Snackbar.LENGTH_LONG).show();
    Log.e("SimpleCamera", "There was an error capturing a photo", t);
  }

  @Override
  protected void onVideoRecordingStart() {
    Snackbar.make(capturePhotoView, "Started recording video", Snackbar.LENGTH_LONG).show();
  }

  @Override
  protected void onVideoAlreadyRecording() {
    Snackbar.make(capturePhotoView, "Cannot record video; already recording", Snackbar.LENGTH_LONG).show();
  }

  @Override
  protected  void onVideoRecordingEnd(@Nullable Throwable t, @Nullable File video, boolean wasCancelled, boolean wasAutoStopped) {
    if(t != null) {
      Snackbar.make(capturePhotoView, "There was an error while recording a video", Snackbar.LENGTH_LONG).show();
      Log.e("SimpleCamera", "There was an error while recording a video", t);
    }
    else if(wasCancelled) {
      Snackbar.make(capturePhotoView, "Video recording cancelled", Snackbar.LENGTH_LONG).show();
    }
    else {
      if(wasAutoStopped) {
        Snackbar.make(capturePhotoView, "Recorded video for max duration", Snackbar.LENGTH_LONG).show();
      }
      else {
        Snackbar.make(capturePhotoView, "Stopped recording video", Snackbar.LENGTH_LONG).show();
      }
    }
  }

  @Override
  protected void onFlashModeChanged(@NonNull @SimpleCamera.FlashMode String flashMode, boolean isDefault) {
    switch(flashMode) {
      case SimpleCamera.FLASH_MODE_ON:
        toggleFlashTypeView.setImageResource(R.drawable.ic_image_flash_on);
        break;
      case SimpleCamera.FLASH_MODE_OFF:
        toggleFlashTypeView.setImageResource(R.drawable.ic_image_flash_off);
        break;
      case SimpleCamera.FLASH_MODE_TORCH:
        toggleFlashTypeView.setImageResource(R.drawable.ic_image_flash_torch);
        break;
      case SimpleCamera.FLASH_MODE_AUTO:
        toggleFlashTypeView.setImageResource(R.drawable.ic_image_flash_auto);
        break;
    }
  }

  @Override
  protected void animatePreviewViewDuringCameraTypeToggle(@NonNull ViewPropertyAnimator animator) {
    animator.rotationYBy(360)
        .setDuration(1500)
        .setInterpolator(new AnticipateOvershootInterpolator())
        .start();
  }
}
