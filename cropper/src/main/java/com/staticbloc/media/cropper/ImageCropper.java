package com.staticbloc.media.cropper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import com.staticbloc.media.utils.BitmapUtils;
import com.staticbloc.media.utils.PhotoWriter;

import java.io.File;
import java.io.FileDescriptor;
import java.io.InputStream;
import java.util.concurrent.Executor;

public final class ImageCropper {
  private final Executor executor;
  private final Handler handler;

  private Rect rect;
  private int outWidth;
  private int outHeight;

  public interface InputFactory<T> {
    @NonNull BitmapRegionDecoder createBitmapRegionDecoder(@NonNull T input) throws Throwable;
  }

  public interface OutputTransformer<T> {
    T transformCroppedBitmap(Bitmap croppedBitmap);
  }

  public interface OnCropListener<I, O> {
    void onCropSuccess(I input, O output);
    void onCropFailure(Throwable e);
  }

  public ImageCropper(Executor executor, Handler handler) {
    this.executor = executor;
    this.handler = handler;
  }

  public final void setCroppingRect(Rect rect) {
    this.rect = rect;
    this.outWidth = rect.width();
    this.outHeight = rect.height();
  }

  public final <I, O>  void cropAsync(final @NonNull I input, @NonNull final InputFactory<I> inputFactory,
                                      @NonNull final OutputTransformer<O> outputTransformer, final @NonNull OnCropListener<I, O> onCropListener) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        O output = null;
        Throwable t = null;
        try {
          output = crop(input, inputFactory, outputTransformer);
        }
        catch (Throwable e) {
          t = e;
        }

        final O callbackOutput = output;
        final Throwable callbackT = t;
        handler.post(new Runnable() {
          @Override
          public void run() {
            if(callbackT != null) {
              onCropListener.onCropFailure(callbackT);
            }
            else {
              onCropListener.onCropSuccess(input, callbackOutput);
            }
          }
        });
      }
    });
  }

  @WorkerThread
  @Nullable
  public final <I, O> O crop(final @NonNull I input, @NonNull InputFactory<I> inputFactory, @NonNull final OutputTransformer<O> outputTransformer) throws Throwable {
    if(rect == null) {
      throw new IllegalStateException("Cannot crop without a cropping rect");
    }

    BitmapRegionDecoder decoder = inputFactory.createBitmapRegionDecoder(input);

    // We currently ensure our rotation is correct at capture time
    // if that ever changes we need to do something with this
//        if (exifRotation != 0) {
//            final int width = decoder.getWidth();
//            final int height = decoder.getHeight();
//          // Adjust crop area to account for image rotation
//          Matrix matrix = new Matrix();
//          matrix.setRotate(-exifRotation);
//
//          RectF adjusted = new RectF();
//          matrix.mapRect(adjusted, new RectF(rect));
//
//          // Adjust to account for origin at 0,0
//          adjusted.offset(adjusted.left < 0 ? width : 0, adjusted.top < 0 ? height : 0);
//          rect = new Rect((int) adjusted.left, (int) adjusted.top, (int) adjusted.right, (int) adjusted.bottom);
//        }

    Bitmap croppedImage = decoder.decodeRegion(rect, new BitmapFactory.Options());

//      if (rect.width() > outWidth || rect.height() > outHeight) {
//        Matrix matrix = new Matrix();
//        matrix.postScale((float) outWidth / rect.width(), (float) outHeight / rect.height());
//        croppedImage = Bitmap.createBitmap(croppedImage, 0, 0, croppedImage.getWidth(), croppedImage.getHeight(), matrix, true);
//      }

    return outputTransformer.transformCroppedBitmap(croppedImage);
  }

  public static class BitmapInput implements InputFactory<Bitmap> {
    @NonNull @Override
    public BitmapRegionDecoder createBitmapRegionDecoder(@NonNull Bitmap input) throws Throwable {
      byte[] data = BitmapUtils.bitmapToByteArray(input);
      return BitmapRegionDecoder.newInstance(data, 0, data.length, false);
    }
  }

  public static class ByteArrayInput implements InputFactory<byte[]> {
    @NonNull @Override
    public BitmapRegionDecoder createBitmapRegionDecoder(@NonNull byte[] input) throws Throwable {
      return BitmapRegionDecoder.newInstance(input, 0, input.length, false);
    }
  }

  public static class FileInput implements InputFactory<File> {
    @NonNull @Override
    public BitmapRegionDecoder createBitmapRegionDecoder(@NonNull File input) throws Throwable {
      return BitmapRegionDecoder.newInstance(input.getAbsolutePath(), false);
    }
  }

  public static class FileDescriptorInput implements InputFactory<FileDescriptor> {
    @NonNull @Override
    public BitmapRegionDecoder createBitmapRegionDecoder(@NonNull FileDescriptor input) throws Throwable {
      return BitmapRegionDecoder.newInstance(input, false);
    }
  }

  public static class UriInput implements InputFactory<Uri> {
    private final Context context;

    public UriInput(Context context) {
      this.context = context.getApplicationContext();
    }

    @NonNull @Override
    public BitmapRegionDecoder createBitmapRegionDecoder(@NonNull Uri input) throws Throwable {
      InputStream is = null;
      try {
        is = context.getContentResolver().openInputStream(input);
        return BitmapRegionDecoder.newInstance(is, false);
      }
      finally {
        try { if(is != null) is.close(); } catch(Exception ignore) {}
      }
    }
  }

  public static class BitmapOutput implements OutputTransformer<Bitmap> {
    @Override
    public Bitmap transformCroppedBitmap(Bitmap croppedBitmap) {
      return croppedBitmap;
    }
  }

  public static class ByteArrayOutput implements OutputTransformer<byte[]> {
    @Override
    public byte[] transformCroppedBitmap(Bitmap croppedBitmap) {
      return BitmapUtils.bitmapToByteArray(croppedBitmap);
    }
  }

  public static class FileOutput implements OutputTransformer<File> {
    @IntRange(from=1, to=100) private int compressQuality;
    private Bitmap.CompressFormat compressFormat;
    private File file;

    public FileOutput(int compressQuality, Bitmap.CompressFormat compressFormat, @NonNull String file) {
      this(compressQuality, compressFormat, new File(file));
    }

    public FileOutput(int compressQuality, Bitmap.CompressFormat compressFormat, @NonNull File file) {
      this.compressQuality = compressQuality;
      this.compressFormat = compressFormat;
      this.file = file;
    }

    @Override
    public File transformCroppedBitmap(Bitmap croppedBitmap) {
      return PhotoWriter.writePhotoToFile(croppedBitmap, compressQuality, compressFormat, file);
    }
  }
}