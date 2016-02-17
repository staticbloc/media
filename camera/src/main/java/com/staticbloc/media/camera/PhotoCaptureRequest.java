package com.staticbloc.media.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.staticbloc.media.utils.BitmapUtils;
import com.staticbloc.media.utils.PhotoWriter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Collection;

public abstract class PhotoCaptureRequest<T> {
  private final PhotoCaptureSession<T> photoCaptureSession;

  private PhotoCaptureRequest() {
    photoCaptureSession = new PhotoCaptureSession<>();
  }

  @NonNull
  public static FileCaptureRequest asFile(@NonNull String file) {
    return new FileCaptureRequest(file);
  }

  @NonNull
  public static FileCaptureRequest asFile(@NonNull File file) {
    return new FileCaptureRequest(file);
  }

  @NonNull
  public static ByteCaptureRequest asByteArray() {
    return new ByteCaptureRequest();
  }

  @NonNull
  public static BitmapCaptureRequest asBitmap() {
    return new BitmapCaptureRequest();
  }

  private PhotoCapturedListener<T> photoCapturedListener;
  private OnShutterActionListener shutterActionListener;
  private Transformation[] transformations;
  private boolean restartPreview = false;
  private boolean mirrorFrontCameraImage = false;

  @NonNull
  public PhotoCaptureRequest<T> photoReadyListener(@NonNull PhotoCapturedListener<T> photoCapturedListener) {
    this.photoCapturedListener = photoCapturedListener;
    return this;
  }

  @NonNull
  public PhotoCaptureRequest<T> shutterActionListener(@NonNull OnShutterActionListener shutterActionListener) {
    this.shutterActionListener = shutterActionListener;
    return this;
  }

  @NonNull
  public PhotoCaptureRequest<T> transformations(@NonNull Transformation... transformations) {
    this.transformations = transformations;
    return this;
  }

  @NonNull
  public PhotoCaptureRequest<T> restartPreview(boolean restartPreview) {
    this.restartPreview = restartPreview;
    return this;
  }

  @NonNull
  public PhotoCaptureRequest<T> mirrorFrontCameraImage(boolean mirrorFrontCameraImage) {
    this.mirrorFrontCameraImage = mirrorFrontCameraImage;
    return this;
  }

  @NonNull
  /*package*/ PhotoCaptureSession<T> getPhotoCaptureSession() {
    return photoCaptureSession;
  }

  /*package*/ void onCapture(@NonNull byte[] data, @NonNull Handler callbackHandler, @SimpleCamera.CameraType int cameraType) {
    if(photoCaptureSession.isCancelled()) return;

    if(cameraType != SimpleCamera.CAMERA_TYPE_FRONT) {
      mirrorFrontCameraImage = false;
    }

    Bitmap photo = ByteArrayToBitmapTransformation.transform(data, mirrorFrontCameraImage);

    if(transformations != null) {
      for(Transformation transformation : transformations) {
        if(photoCaptureSession.isCancelled()) return;
        photo = transformation.transform(photo);
      }
    }

    if(photoCaptureSession.isCancelled()) return;

    Throwable t = null;
    T value = null;
    try {
      value = onTransformed(photo);

      if(!photoCaptureSession.set(value)) {
        if(value != null) {
          onCancelled(value);
        }
        return;
      }
    }
    catch (Throwable e) {
      t = e;
      photoCaptureSession.cancel(t);
      if(value != null) {
        onCancelled(value);
      }

      // null this out in case it is an expensive object (e.g. Bitmap, byte[], etc...)
      // also, we don't want to pass a good value together with the Throwable
      value = null;
    }

    final Throwable callbackT = t;
    final T callbackValue = value;
    callbackHandler.post(new Runnable() {
      @Override
      public void run() {
        if(photoCapturedListener != null) photoCapturedListener.onPhotoCaptured(callbackT, callbackValue);
      }
    });
  }

  @NonNull
  protected abstract T onTransformed(@NonNull Bitmap photo) throws Throwable;
  protected abstract void onCancelled(@NonNull T value);

  @NonNull
  public PhotoCapturedListener<T> getPhotoCapturedListener() {
    return photoCapturedListener;
  }

  @Nullable
  public OnShutterActionListener getShutterActionListener() {
    return shutterActionListener;
  }

  public boolean shouldRestartPreview() {
    return restartPreview;
  }

  public interface OnShutterActionListener {
    void onShutterAction();
  }

  public interface PhotoCapturedListener<T> {
    void onPhotoCaptured(@Nullable Throwable t, @Nullable T photo);
    void onCancelled();
  }

  public interface Transformation {
    @NonNull
    Bitmap transform(Bitmap data);
  }

  public static final class ByteCaptureRequest extends PhotoCaptureRequest<byte[]> {
    @NonNull @Override
    protected byte[] onTransformed(@NonNull Bitmap photo) {
      return BitmapUtils.bitmapToByteArray(photo);
    }

    @Override
    protected void onCancelled(@NonNull byte[] value) {}
  }

  public static final class FileCaptureRequest extends PhotoCaptureRequest<File> {
    private File file;

    private FileCaptureRequest(@NonNull String file) {
      this(new File(file));
    }

    private FileCaptureRequest(@NonNull File file) {
      this.file = file;
    }

    @NonNull @Override
    protected File onTransformed(@NonNull Bitmap photo) throws Throwable {
      return PhotoWriter.writePhotoToFile(photo, file);
    }

    @Override
    protected void onCancelled(@NonNull File value) {
      value.delete();
    }
  }

  public static final class BitmapCaptureRequest extends PhotoCaptureRequest<Bitmap> {
    @NonNull @Override
    protected Bitmap onTransformed(@NonNull Bitmap photo) throws Throwable {
      return photo;
    }

    @Override
    protected void onCancelled(@NonNull Bitmap value) {
      if(!value.isRecycled()) value.recycle();
    }
  }

  private static class ByteArrayToBitmapTransformation {
    private ByteArrayToBitmapTransformation() {}

    @NonNull
    public static Bitmap transform(byte[] data, boolean mirrorImage) {
      final int orientation = getOrientation(data);

      Matrix matrix = new Matrix();
      if(mirrorImage) {
        matrix.postScale(-1, 1);
      }

      boolean shouldTransform = applyOrientation(orientation, matrix, mirrorImage);
      if(!shouldTransform && !mirrorImage) {
        return BitmapFactory.decodeByteArray(data, 0, data.length);
      }

      return transformData(data, matrix);
    }

    private static int getOrientation(byte[] data) {
      InputStream is = null;
      try {
        is = new ByteArrayInputStream(data);
        Metadata metadata = ImageMetadataReader.readMetadata(is);
        Collection<ExifIFD0Directory> directories = metadata.getDirectoriesOfType(ExifIFD0Directory.class);
        for(ExifIFD0Directory directory : directories) {
          if(directory.containsTag(ExifDirectoryBase.TAG_ORIENTATION)) {
            return directory.getInt(ExifDirectoryBase.TAG_ORIENTATION);
          }
        }

        return ExifInterface.ORIENTATION_NORMAL;
      }
      catch (Exception e) {
        return ExifInterface.ORIENTATION_NORMAL;
      }
      finally {
        if(is != null) try { is.close(); } catch(Exception ignore) {}
      }
    }

    private static boolean applyOrientation(int orientation, @NonNull Matrix matrix, boolean mirrorImage) {
      switch (orientation) {
        case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
          if(!mirrorImage) matrix.postScale(-1, 1);
          return true;
        case ExifInterface.ORIENTATION_ROTATE_180:
          if(!mirrorImage) {
            matrix.postRotate(180);
          }
          return true;
        case ExifInterface.ORIENTATION_FLIP_VERTICAL:
          if(!mirrorImage) matrix.postScale(-1, 1);
          return true;
        case ExifInterface.ORIENTATION_TRANSPOSE:
          if(!mirrorImage) matrix.postScale(-1, 1);
          return true;
        case ExifInterface.ORIENTATION_ROTATE_90:
          if(mirrorImage) {
            matrix.postRotate(270);
          }
          else {
            matrix.postRotate(90);
          }
          return true;
        case ExifInterface.ORIENTATION_TRANSVERSE:
          if(!mirrorImage) matrix.postScale(-1, 1);
          return true;
        case ExifInterface.ORIENTATION_ROTATE_270:
          if(mirrorImage) {
            matrix.postRotate(90);
          }
          else {
            matrix.postRotate(270);
          }
          return true;
        default:
          return false;
      }
    }

    @NonNull
    private static Bitmap transformData(@NonNull byte[] data, @NonNull Matrix matrix) {
      Bitmap picture = null;
      Bitmap tranformedPicture;

      try {
        picture = BitmapFactory.decodeByteArray(data, 0, data.length);

        tranformedPicture = Bitmap.createBitmap(picture, 0, 0, picture.getWidth(), picture.getHeight(), matrix, true);
        if(tranformedPicture != picture) {
          picture.recycle();
        }
        picture = null;

        return tranformedPicture;
      }
      finally {
        if(picture != null) picture.recycle();
      }
    }
  }
}
