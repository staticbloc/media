package com.staticbloc.media.camera;

import android.graphics.SurfaceTexture;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Surface;
import android.view.SurfaceHolder;
import com.staticbloc.media.utils.Size;

import java.lang.ref.WeakReference;

public class CameraPreview {
  private final WeakReference<SurfaceHolder> surfaceHolder;
  private final WeakReference<SurfaceTexture> surfaceTexture;

  private final Size previewSize;

  public CameraPreview(@NonNull SurfaceHolder surfaceHolder, @NonNull Size previewSize) {
    this.surfaceHolder = new WeakReference<>(surfaceHolder);
    this.previewSize = previewSize;
    this.surfaceTexture = null;
  }

  public CameraPreview(@NonNull SurfaceTexture surfaceTexture, @NonNull Size previewSize) {
    this.surfaceTexture = new WeakReference<>(surfaceTexture);
    this.previewSize = previewSize;
    this.surfaceHolder = null;
  }

  /*package*/ boolean hasSurfaceHolder() {
    return surfaceHolder != null && surfaceHolder.get() != null;
  }

  /*package*/ boolean hasSurfaceTexture() {
    return surfaceTexture != null && surfaceTexture.get() != null;
  }

  @Nullable
  /*pacakge*/ SurfaceHolder getSurfaceHolder() {
    return surfaceHolder == null ? null : surfaceHolder.get();
  }

  @Nullable
  /*package*/ SurfaceTexture getSurfaceTexture() {
    return surfaceTexture == null ? null : surfaceTexture.get();
  }

  @Nullable
  /*package*/ Surface getSurface() {
    if(surfaceTexture != null) {
      SurfaceTexture texture = surfaceTexture.get();
      if(texture != null) {
        return new Surface(texture);
      }
    }
    else if(surfaceHolder != null) {
      SurfaceHolder holder = surfaceHolder.get();
      if(holder != null) {
        return holder.getSurface();
      }
    }

    return null;
  }

  @NonNull
  /*package*/ Size getPreviewSize() {
    return previewSize;
  }

  /*package*/ void release() {
    if(surfaceHolder != null) {
      surfaceHolder.clear();
    }

    if(surfaceTexture != null) {
      surfaceTexture.clear();
    }
  }
}
