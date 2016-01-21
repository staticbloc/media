package com.staticbloc.media.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.AttributeSet;
import android.view.TextureView;
import android.view.ViewGroup;
import com.staticbloc.media.utils.Size;

public class PreviewTextureView extends TextureView {
  private int previewWidth = 0;
  private int previewHeight = 0;

  public PreviewTextureView(Context context) {
    this(context, null);
  }

  public PreviewTextureView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public PreviewTextureView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public PreviewTextureView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  public void setAspectRatio(Size size) {
    if (size.width < 0 || size.height < 0) {
      throw new IllegalArgumentException("Size cannot be negative.");
    }

    final int orientation = getResources().getConfiguration().orientation;
    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
      previewWidth = size.width;
      previewHeight = size.height;
    }
    else {
      //noinspection SuspiciousNameCombination
      previewWidth = size.height;
      //noinspection SuspiciousNameCombination
      previewHeight = size.width;
    }

    requestLayout();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (getParent() instanceof ViewGroup) {
      ViewGroup parent = ((ViewGroup) getParent());
      int parentWidth = parent.getWidth();
      if (parentWidth != 0) {
        if (previewWidth == 0 || previewHeight == 0) {
          // The surface needs a non-zero size for the callbacks to trigger
          setMeasuredDimension(1, 1);
          return;
        }

        double ratio = (double) parentWidth / (double) previewWidth;

        double newPreviewWidth = (double) previewWidth * ratio;
        double newPreviewHeight = (double) previewHeight * ratio;

        setMeasuredDimension((int) newPreviewWidth, (int) newPreviewHeight);
      }
      else {
        // The surface needs a non-zero size for the callbacks to trigger
        setMeasuredDimension(1, 1);
      }
    }
    else {
      // The surface needs a non-zero size for the callbacks to trigger
      setMeasuredDimension(1, 1);
    }
  }
}
