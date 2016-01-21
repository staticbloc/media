package com.staticbloc.media.utils;

import android.hardware.Camera;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Size implements Parcelable {
  public final int width;
  public final int height;

  public Size(Parcel p) {
    this(p.readInt(), p.readInt());
  }

  public Size(int width, int height) {
    this.width = width;
    this.height = height;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Size size = (Size) o;

    return width == size.width && height == size.height;

  }

  @Override
  public int hashCode() {
    int result = width;
    result = 31 * result + height;
    return result;
  }

  @Override
  public String toString() {
    return "Size{" +
        "width=" + width +
        ", height=" + height +
        '}';
  }

  //////////////////////////////
  // Parcelable apis
  //////////////////////////////
  public static final Creator<Size> CREATOR = new Creator<Size>() {
    public Size createFromParcel(Parcel p) {
      return new Size(p);
    }

    public Size[] newArray(int size) {
      return new Size[size];
    }
  };

  public int describeContents() {
    return 0;
  }

  public void writeToParcel(Parcel p, int flags) {
    p.writeInt(width);
    p.writeInt(height);
  }
  //////////////////////////////
  // end Parcelable apis
  //////////////////////////////

  @NonNull
  public static List<Size> convertSizes(@Nullable List<Camera.Size> sizesToConvert) {
    if(sizesToConvert == null) {
      return new ArrayList<>();
    }

    List<Size> sizes = new ArrayList<>(sizesToConvert.size());
    for(Camera.Size sizeToConvert : sizesToConvert) {
      sizes.add(new Size(sizeToConvert.width, sizeToConvert.height));
    }
    return sizes;
  }
}