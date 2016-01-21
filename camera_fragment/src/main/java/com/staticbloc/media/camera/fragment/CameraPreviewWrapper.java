package com.staticbloc.media.camera.fragment;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.support.annotation.NonNull;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import com.staticbloc.media.camera.CameraPreview;
import com.staticbloc.media.ui.PreviewSurfaceView;
import com.staticbloc.media.ui.PreviewTextureView;
import com.staticbloc.media.utils.Size;

/*package*/ class CameraPreviewWrapper {
  /*package*/ static final int SURFACE_VIEW_PREVIEW = 0;
  /*package*/ static final int TEXTURE_VIEW_PREVIEW = 1;

  private PreviewSurfaceView surfaceView;
  private PreviewTextureView textureView;

  private CameraPreviewListener cameraPreviewListener;
  private Size targetSize;

  public interface CameraPreviewListener {
    void onPreviewReady(CameraPreview cameraPreview);
    void onPreviewViewSizeChanged(int width, int height);
  }

  private SurfaceHolder.Callback surfaceHolderCallback = new SurfaceHolder.Callback() {
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
      cameraPreviewListener.onPreviewReady(new CameraPreview(holder, targetSize));
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      cameraPreviewListener.onPreviewViewSizeChanged(width, height);
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {}
  };

  private CameraPreviewWrapper(PreviewSurfaceView surfaceView) {
    this.surfaceView = surfaceView;
  }

  private CameraPreviewWrapper(PreviewTextureView textureView) {
    this.textureView = textureView;
  }

  public View getPreviewView() {
    if(surfaceView != null) {
      return surfaceView;
    }
    else {
      return textureView;
    }
  }

  public void setPreviewViewAspectRatio(Size aspectRatioSize) {
    if(surfaceView != null) {
      surfaceView.setPreviewSize(aspectRatioSize);
    }
    else if(textureView != null) {
      textureView.setAspectRatio(aspectRatioSize);
    }
  }

  public void registerCameraPreviewListener(@NonNull final CameraPreviewListener cameraPreviewListener, @NonNull final Size targetSize) {
    this.cameraPreviewListener = cameraPreviewListener;
    this.targetSize = targetSize;

    if(surfaceView != null) {
      SurfaceHolder surfaceHolder = surfaceView.getHolder();
      Surface surface = surfaceHolder.getSurface();
      if(surface != null && surface.isValid()) {
        cameraPreviewListener.onPreviewReady(new CameraPreview(surfaceHolder, targetSize));
      }
      else {
        surfaceView.getHolder().addCallback(surfaceHolderCallback);
      }
    }
    else if(textureView != null) {
      if(textureView.isAvailable()) {
        cameraPreviewListener.onPreviewReady(new CameraPreview(textureView.getSurfaceTexture(), targetSize));
      }

      textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
          cameraPreviewListener.onPreviewReady(new CameraPreview(surface, targetSize));
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
          cameraPreviewListener.onPreviewViewSizeChanged(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
          return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
      });
    }
  }

  public void unregisterCameraPreviewListener() {
    if(surfaceView != null) {
      surfaceView.getHolder().removeCallback(surfaceHolderCallback);
    }
    else if(textureView != null) {
      textureView.setSurfaceTextureListener(null);
    }
    cameraPreviewListener = null;
  }

  public void destroy() {
    if(surfaceView != null) {
      surfaceView = null;
    }
    else if(textureView != null) {
      textureView = null;
    }
  }

  public static CameraPreviewWrapper newInstance(@NonNull Context context, int previewType) {
    CameraPreviewWrapper cameraPreviewWrapper;
    if(previewType == SURFACE_VIEW_PREVIEW) {
      cameraPreviewWrapper = new CameraPreviewWrapper(new PreviewSurfaceView(context));
    }
    else {
      cameraPreviewWrapper = new CameraPreviewWrapper(new PreviewTextureView(context));
    }

    return cameraPreviewWrapper;
  }
}
