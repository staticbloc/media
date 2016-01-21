package com.staticbloc.media.camera;

import android.support.annotation.NonNull;
import com.staticbloc.media.utils.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public interface SizeStrategy {
  @NonNull Size getOptimalPreviewSize(@NonNull List<Size> choices, @NonNull Size targetSize);
  @NonNull Size getOptimalPhotoSize(@NonNull List<Size> choices);
  @NonNull Size getOptimalVideoSize(@NonNull List<Size> choices);

  // adapted from https://github.com/googlesamples/android-Camera2Basic/blob/master/Application/src/main/java/com/example/android/camera2basic/Camera2BasicFragment.java
  class DefaultSizeStrategy implements SizeStrategy {
    private final Size maxPreviewSize;
    private final Size targetPhotoSize;
    private final Size targetVideoSize;

    public DefaultSizeStrategy() {
      Size defaultSize = new Size(Integer.MAX_VALUE, Integer.MAX_VALUE);
      maxPreviewSize = defaultSize;
      targetPhotoSize = defaultSize;
      targetVideoSize = defaultSize;
    }

    public static class Builder {
      private Size maxPreviewSize;
      private Size targetPhotoSize;
      private Size targetVideoSize;

      public Builder maxPreviewSize(@NonNull Size maxPreviewSize) {
        this.maxPreviewSize = maxPreviewSize;
        return this;
      }

      public Builder targetPhotoSize(@NonNull Size targetPhotoSize) {
        this.targetPhotoSize = targetPhotoSize;
        return this;
      }

      public Builder targetVideoSize(@NonNull Size targetVideoSize) {
        this.targetVideoSize = targetVideoSize;
        return this;
      }

      public DefaultSizeStrategy build() {
        Size defaultSize = new Size(Integer.MAX_VALUE, Integer.MAX_VALUE);
        if(maxPreviewSize == null) maxPreviewSize = defaultSize;
        if(targetPhotoSize == null) targetPhotoSize = defaultSize;
        if(targetVideoSize == null) targetVideoSize = defaultSize;

        return new DefaultSizeStrategy(maxPreviewSize, targetPhotoSize, targetVideoSize);
      }
    }

    public DefaultSizeStrategy(Size maxPreviewSize, Size targetPhotoSize, Size targetVideoSize) {
      this.maxPreviewSize = maxPreviewSize;
      this.targetPhotoSize = targetPhotoSize;
      this.targetVideoSize = targetVideoSize;
    }

    @NonNull
    @Override
    public Size getOptimalPreviewSize(@NonNull List<Size> choices, @NonNull Size targetSize) {
      return getOptimalSize(choices, targetSize, maxPreviewSize);
    }

    @NonNull
    @Override
    public Size getOptimalPhotoSize(@NonNull List<Size> choices) {
      return getOptimalSize(choices, targetPhotoSize, targetPhotoSize);
    }

    @NonNull
    @Override
    public Size getOptimalVideoSize(@NonNull List<Size> choices) {
      return getOptimalSize(choices, targetVideoSize, targetVideoSize);
    }

    private Size getOptimalSize(List<Size> choices, Size targetSize, Size maxSize) {
      // Collect the supported resolutions that are at least as big as the target
      List<Size> bigEnough = new ArrayList<>();
      // Collect the supported resolutions that are smaller than the target
      List<Size> notBigEnough = new ArrayList<>();
      for (Size option : choices) {
        if(option.equals(targetSize)) {
          return targetSize;
        }

        if (option.width <= maxSize.width && option.height <= maxSize.height &&
            option.height == option.width * targetSize.height / targetSize.width) {
          if (option.width >= targetSize.width && option.height >= targetSize.height) {
            bigEnough.add(option);
          } else {
            notBigEnough.add(option);
          }
        }
      }

      // Pick the smallest of those big enough. If there is no one big enough, pick the
      // largest of those not big enough.
      if (bigEnough.size() > 0) {
        return Collections.min(bigEnough, new CompareSizesByArea());
      }
      else if (notBigEnough.size() > 0) {
        return Collections.max(notBigEnough, new CompareSizesByArea());
      }
      else {
        return choices.get(0);
      }
    }

    private static class CompareSizesByArea implements Comparator<Size> {
      @Override
      public int compare(Size lhs, Size rhs) {
        // We cast here to ensure the multiplications won't overflow
        return Long.signum((long) lhs.width * lhs.height -
            (long) rhs.width * rhs.height);
      }
    }
  }
}
