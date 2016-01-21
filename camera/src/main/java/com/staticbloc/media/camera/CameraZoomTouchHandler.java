package com.staticbloc.media.camera;

import android.support.annotation.NonNull;
import android.view.MotionEvent;
import android.view.View;

public class CameraZoomTouchHandler implements View.OnTouchListener {
  private final int scrollTrackPixelCount;
  private OnNewZoomValueListener onNewZoomValueListener;

  private float startingPoint;
  private float startingTrackPoint;

  private boolean enabled;
  private boolean reset;

  private int maxZoom;
  private int currentZoom;

  private boolean trackingScrolling;

  public interface OnNewZoomValueListener {
    void onZoomingStarted();
    void onNewZoomValue(int newZoom);
    void onZoomingEnded();
  }

  public CameraZoomTouchHandler(@NonNull SimpleCamera camera, int trackSize, @NonNull OnNewZoomValueListener onNewZoomValueListener) {
    this.scrollTrackPixelCount = trackSize;
    this.onNewZoomValueListener = onNewZoomValueListener;

    this.maxZoom = camera.getMaxZoom();
    this.currentZoom = camera.getZoom();
    this.enabled = camera.isZoomSupported();
  }

  public void onZoomParametersChanged(@NonNull SimpleCamera camera) {
    this.maxZoom = camera.getMaxZoom();
    this.currentZoom = camera.getZoom();
    this.enabled = camera.isZoomSupported();

    // only reset if we are in middle of tracking
    this.reset = trackingScrolling;
  }

  private void reset() {
    startingPoint = 0;
    startingTrackPoint = 0;
    reset = false;
  }

  @Override
  public boolean onTouch(View v, MotionEvent event) {
    if(trackingScrolling && event.getAction() == MotionEvent.ACTION_UP && event.getPointerCount() <= 1) {
      onNewZoomValueListener.onZoomingEnded();
      trackingScrolling = false;
    }

    if(reset || !enabled) {
      reset();
      return false;
    }

    if(event.getAction() == MotionEvent.ACTION_DOWN) {
      startingPoint = event.getRawY();
      float pixelPerZoomUnit = (maxZoom >= scrollTrackPixelCount ? 1 : scrollTrackPixelCount / maxZoom);
      startingTrackPoint = (currentZoom == 0 ? 1 : currentZoom * pixelPerZoomUnit);
      trackingScrolling = true;
      onNewZoomValueListener.onZoomingStarted();
    }
    else if(event.getAction() == MotionEvent.ACTION_MOVE) {
      float pixelPerZoomUnit = (maxZoom >= scrollTrackPixelCount ? 1 : scrollTrackPixelCount / maxZoom);

      final float distance = startingPoint - event.getRawY();
      final boolean movingUp = distance >= 0;
      final float absoluteDistance = Math.abs(distance);

      final float newTrackPoint;
      if(movingUp) {
        newTrackPoint = startingTrackPoint + absoluteDistance;
      }
      else {
        newTrackPoint = startingTrackPoint - absoluteDistance;
      }

      int newZoom = Math.round(newTrackPoint / pixelPerZoomUnit);
      if(newZoom < 0) {
        newZoom = 0;
      }
      else if(newZoom > maxZoom) {
        newZoom = maxZoom;
      }

      this.currentZoom = newZoom;
      onNewZoomValueListener.onNewZoomValue(currentZoom);
    }

    return true;
  }
}
