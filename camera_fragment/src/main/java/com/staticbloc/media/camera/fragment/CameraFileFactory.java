package com.staticbloc.media.camera.fragment;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public abstract class CameraFileFactory implements Parcelable {
  private final File parentDirectory;

  public CameraFileFactory(@NonNull File parentDirectory) {
    this.parentDirectory = parentDirectory;
  }

  public CameraFileFactory(Parcel p) {
    String path = p.readString();
    parentDirectory = new File(path);
  }

  @NonNull protected File getParentDirectory() {
    return parentDirectory;
  }

  @NonNull public abstract File getFileForPhoto();
  @NonNull public abstract File getFileForVideo();

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel p, int flags) {
    p.writeString(parentDirectory.getAbsolutePath());
  }

  public static final class Default extends CameraFileFactory {
    private final SimpleDateFormat format;

    public Default(@NonNull File parentDirectory) {
      super(parentDirectory);

      format = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
    }

    public Default(Parcel p) {
      super(p);

      format = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
    }

    @NonNull @Override
    public File getFileForPhoto() {
      String fileName = format.format(new Date()) + ".jpg";
      return new File(getParentDirectory(), fileName);
    }

    @NonNull @Override
    public File getFileForVideo() {
      String fileName = format.format(new Date()) + ".mp4";
      return new File(getParentDirectory(), fileName);
    }

    public static final Creator<Default> CREATOR = new Creator<Default>() {
      @Override
      public Default createFromParcel(Parcel source) {
        return new Default(source);
      }

      @Override
      public Default[] newArray(int size) {
        return new Default[0];
      }
    };
  }
}
