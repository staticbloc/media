package com.staticbloc.media.camera;

import android.hardware.Camera;
import android.media.CamcorderProfile;
import com.staticbloc.media.utils.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/*package*/ class Device {
  private final List<Size> supportedPreviewSizes;
  private volatile Size previewSize;

  private final List<Size> supportedPhotoSizes;
  private volatile Size photoSize;

  private final VideoQualities videoQualities;
  private final List<Size> supportedVideoSizes;
  private volatile Size videoSize;

  private final List<String> supportedFocusModes;

  private final List<String> supportedFlashModes;

  private volatile boolean zoomSupported;
  private volatile int zoom;
  private volatile int maxZoom;
  private volatile boolean smoothZoomSupported;
  private volatile List<Integer> zoomRatios;

  private final Object zoomLock = new Object();

  private volatile boolean videoSnapshotSupported;

  public Device() {
    supportedPreviewSizes = Collections.emptyList();
    previewSize = new Size(0, 0);

    supportedPhotoSizes = Collections.emptyList();
    photoSize = new Size(0, 0);

    videoQualities = null;
    supportedVideoSizes = Collections.emptyList();
    videoSize = new Size(0, 0);

    supportedFocusModes = Collections.emptyList();
    supportedFlashModes = Collections.emptyList();

    zoomSupported = false;
    zoom = 0;
    maxZoom = 0;
    smoothZoomSupported = false;
    zoomRatios = Collections.emptyList();

    videoSnapshotSupported = false;
  }

  public Device(int cameraId, Camera.Parameters parameters, SizeStrategy sizeStrategy, Set<String> nonAllowedFlashModes) {
    supportedPreviewSizes = Collections.unmodifiableList(Size.convertSizes(parameters.getSupportedPreviewSizes()));

    supportedPhotoSizes = Collections.unmodifiableList(Size.convertSizes(parameters.getSupportedPictureSizes()));
    photoSize = sizeStrategy.getOptimalPhotoSize(supportedPhotoSizes);

    videoQualities = VideoQualities.get(cameraId);
    supportedVideoSizes = Collections.unmodifiableList(videoQualities.getSupportedVideoSizes());
    videoSize = sizeStrategy.getOptimalVideoSize(supportedVideoSizes);

    supportedFocusModes = Collections.unmodifiableList(new ArrayList<>(parameters.getSupportedFocusModes()));

    List<String> supportedFlashModes = parameters.getSupportedFlashModes();
    if(supportedFlashModes == null) {
      this.supportedFlashModes = Collections.unmodifiableList(new ArrayList<String>());
    }
    else {
      List<String> supportedFlashModesTmp = new ArrayList<>(supportedFlashModes);
      Iterator<String> flashModeIter = supportedFlashModesTmp.iterator();
      while(flashModeIter.hasNext()) {
        if(nonAllowedFlashModes.contains(flashModeIter.next())) {
          flashModeIter.remove();
        }
      }
      this.supportedFlashModes = Collections.unmodifiableList(supportedFlashModesTmp);
    }

    zoomSupported = parameters.isZoomSupported();
    if(zoomSupported) {
      zoom = parameters.getZoom();
      maxZoom = parameters.getMaxZoom();
      smoothZoomSupported = parameters.isSmoothZoomSupported();
      zoomRatios = parameters.getZoomRatios();
    }
    else {
      zoom = 0;
      maxZoom = 0;
      smoothZoomSupported = false;
      zoomRatios = Collections.emptyList();
    }

    videoSnapshotSupported = parameters.isVideoSnapshotSupported();
  }

  public List<Size> getSupportedPreviewSizes() {
    return supportedPreviewSizes;
  }

  public Size getPreviewSize() {
    return previewSize;
  }

  public void setPreviewSize(SizeStrategy sizeStrategy, Size previewTargetSize) {
    previewSize = sizeStrategy.getOptimalPreviewSize(supportedPreviewSizes, previewTargetSize);
  }

  public List<Size> getSupportedPhotoSizes() {
    return supportedPhotoSizes;
  }

  public Size getPhotoSize() {
    return photoSize;
  }

  public boolean setPhotoSize(Size photoSize) {
    if(!supportedPhotoSizes.contains(photoSize)) {
      return false;
    }

    this.photoSize = photoSize;
    return true;
  }

  public List<Size> getSupportedVideoSizes() {
    return supportedVideoSizes;
  }

  public Size getVideoSize() {
    return videoSize;
  }

  public CamcorderProfile getCamcorderProfile() {
    return videoQualities.getProfile(videoSize);
  }

  public boolean setVideoSize(Size videoSize) {
    if(!supportedVideoSizes.contains(videoSize)) {
      return false;
    }

    this.videoSize = videoSize;
    return true;
  }

  public List<String> getSupportedFocusModes() {
    return supportedFocusModes;
  }

  public List<String> getSupportedFlashModes() {
    return supportedFlashModes;
  }

  public boolean isZoomSupported() {
    return zoomSupported;
  }

  public int getZoom() {
    return zoom;
  }

  public int getMaxZoom() {
     return maxZoom;
  }

  public boolean isSmoothZoomSupported() {
    return smoothZoomSupported;
  }

  public boolean setZoom(int zoom) {
    synchronized (zoomLock) {
      if(zoom >= zoomRatios.size()) {
        return false;
      }
      else {
        this.zoom = zoom;
        return true;
      }
    }
  }

  public float getZoomRatio() {
    synchronized (zoomLock) {
      return zoomRatios.isEmpty() ? 0 : ((float) zoomRatios.get(zoom)) / 100;
    }
  }

  public void resetZoom(int zoom, int maxZoom, boolean zoomSupported, boolean smoothZoomSupported, List<Integer> zoomRatios) {
    synchronized (zoomLock) {
      this.zoom = zoom;
      this.maxZoom = maxZoom;
      this.zoomSupported = zoomSupported;
      this.smoothZoomSupported = smoothZoomSupported;
      this.zoomRatios = zoomRatios;
    }
  }

  public boolean isVideoSnapshotSupported() {
    return videoSnapshotSupported;
  }
}
