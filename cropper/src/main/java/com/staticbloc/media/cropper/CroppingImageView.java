package com.staticbloc.media.cropper;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.OverScroller;

// Adapted from https://github.com/MikeOrtiz/TouchImageView
public class CroppingImageView extends ImageView {

  private static final String DEBUG = "DEBUG";

  // SuperMin and SuperMax multipliers. Determine how much the image can be
  // zoomed below or above the zoom boundaries, before animating back to the
  // min/max zoom boundary.
  private static final float SUPER_MIN_MULTIPLIER = .75f;
  private static final float SUPER_MAX_MULTIPLIER = 1.25f;

  // Scale of image ranges from minScale to maxScale, where minScale == 1
  // when the image is stretched to fit view.
  private float normalizedScale;

  // Matrix applied to image. MSCALE_X and MSCALE_Y should always be equal.
  // MTRANS_X and MTRANS_Y are the other values used. prevMatrix is the matrix
  // saved prior to the screen rotating.
  private Matrix matrix, prevMatrix;

  private enum State { NONE, DRAG, ZOOM, FLING, ANIMATE_ZOOM }

  private State state;

  private float minScale;
  private float maxScale;
  private float superMinScale;
  private float superMaxScale;
  private float[] m;

  private Context context;
  private Fling fling;

  private ScaleType mScaleType;

  private boolean imageRenderedAtLeastOnce;
  private boolean onDrawReady;

  private ZoomVariables delayedZoomVariables;
  private boolean delayedSetOptimalZoom;

  // Size of view and previous view size (ie before rotation)
  private int viewWidth, viewHeight, prevViewWidth, prevViewHeight;

  // Size of image when it is stretched to fit view. Before and After rotation.
  private float matchViewWidth, matchViewHeight, prevMatchViewWidth, prevMatchViewHeight;

  private ScaleGestureDetector mScaleDetector;
  private GestureDetector mGestureDetector;
  private GestureDetector.OnDoubleTapListener doubleTapListener = null;
  private OnTouchListener userTouchListener = null;
  private OnTouchImageViewListener touchImageViewListener = null;

  public CroppingImageView(Context context) {
    this(context, null);
  }

  public CroppingImageView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public CroppingImageView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context);
  }

  private void init(Context context) {
    this.context = context;

    super.setClickable(true);

    mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    mGestureDetector = new GestureDetector(context, new GestureListener());

    matrix = new Matrix();
    prevMatrix = new Matrix();

    m = new float[9];
    normalizedScale = 1;
    if (mScaleType == null) {
      mScaleType = ScaleType.FIT_CENTER;
    }

    minScale = 1;
    maxScale = 3;

    superMinScale = SUPER_MIN_MULTIPLIER * minScale;
    superMaxScale = SUPER_MAX_MULTIPLIER * maxScale;

    setImageMatrix(matrix);
    setScaleType(ScaleType.MATRIX);
    setState(State.NONE);

    onDrawReady = false;

    super.setOnTouchListener(new PrivateOnTouchListener());
  }

  @Override
  public void setOnTouchListener(View.OnTouchListener l) {
    userTouchListener = l;
  }

  public void setOnTouchImageViewListener(OnTouchImageViewListener l) {
    touchImageViewListener = l;
  }

  public void setOnDoubleTapListener(GestureDetector.OnDoubleTapListener l) {
    doubleTapListener = l;
  }

  @Override
  public void setImageResource(int resId) {
    imageRenderedAtLeastOnce = false;
    super.setImageResource(resId);
    savePreviousImageValues();
    setOptimalZoomForNewImage();
  }

  @Override
  public void setImageBitmap(Bitmap bm) {
    imageRenderedAtLeastOnce = false;
    super.setImageBitmap(bm);
    savePreviousImageValues();
    setOptimalZoomForNewImage();
  }

  @Override
  public void setImageDrawable(Drawable drawable) {
    imageRenderedAtLeastOnce = false;
    super.setImageDrawable(drawable);
    savePreviousImageValues();
    setOptimalZoomForNewImage();
  }

  @Override
  public void setImageURI(Uri uri) {
    imageRenderedAtLeastOnce = false;
    super.setImageURI(uri);
    savePreviousImageValues();
    setOptimalZoomForNewImage();
  }

  private void setOptimalZoomForNewImage() {
    if (!onDrawReady) {
      delayedSetOptimalZoom = true;
      return;
    }

    Point size = getDrawableHeightAndWidth();

    if (!size.equals(0, 0)) {
      float widthRatio = (Math.max(viewWidth, size.x)) / (Math.min(viewWidth, size.x));
      float heightRatio = (Math.max(viewHeight, size.y)) / (Math.min(viewHeight, size.y));
      float minZoom = ((Math.min(widthRatio, heightRatio)) / (Math.max(widthRatio, heightRatio)));
      setMinZoom(minZoom);
      setZoom(minZoom);
    }
    else {
      fitImageToView();
    }
  }

  @Override
  public void setScaleType(ScaleType type) {
    if (type == ScaleType.MATRIX) {
      super.setScaleType(ScaleType.MATRIX);

    }
    else {
      mScaleType = type;
      if (onDrawReady) {
        // If the image is already rendered, scaleType has been called programmatically
        // and the TouchImageView should be updated with the new scaleType.
        setZoom(this);
      }
    }
  }

  @Override
  public ScaleType getScaleType() {
    return mScaleType;
  }

  /**
   * Returns false if image is in initial, unzoomed state. False, otherwise.
   *
   * @return true if image is zoomed
   */
  public boolean isZoomed() {
    return normalizedScale != 1;
  }

  /**
   * Return a Rect representing the zoomed image.
   *
   * @return rect representing zoomed image
   */
  public RectF getZoomedRect() {
    if (mScaleType == ScaleType.FIT_XY) {
      throw new UnsupportedOperationException("getZoomedRect() not supported with FIT_XY");
    }
    PointF topLeft = transformCoordTouchToBitmap(0, 0, true);
    PointF bottomRight = transformCoordTouchToBitmap(viewWidth, viewHeight, true);

    Point size = getDrawableHeightAndWidth();
    if (size.x == 0 || size.y == 0) {
      return new RectF();
    }

    return new RectF(topLeft.x / size.x, topLeft.y / size.y, bottomRight.x / size.x, bottomRight.y / size.y);
  }

  /**
   * Save the current matrix and view dimensions
   * in the prevMatrix and prevView variables.
   */
  private void savePreviousImageValues() {
    if (matrix != null && viewHeight != 0 && viewWidth != 0) {
      matrix.getValues(m);
      prevMatrix.setValues(m);
      prevMatchViewHeight = matchViewHeight;
      prevMatchViewWidth = matchViewWidth;
      prevViewHeight = viewHeight;
      prevViewWidth = viewWidth;
    }
  }

  @Override
  public Parcelable onSaveInstanceState() {
    Bundle bundle = new Bundle();
    bundle.putParcelable("instanceState", super.onSaveInstanceState());
    bundle.putFloat("saveScale", normalizedScale);
    bundle.putFloat("matchViewHeight", matchViewHeight);
    bundle.putFloat("matchViewWidth", matchViewWidth);
    bundle.putInt("viewWidth", viewWidth);
    bundle.putInt("viewHeight", viewHeight);
    matrix.getValues(m);
    bundle.putFloatArray("matrix", m);
    bundle.putBoolean("imageRendered", imageRenderedAtLeastOnce);
    return bundle;
  }

  @Override
  public void onRestoreInstanceState(Parcelable state) {
    if (state instanceof Bundle) {
      Bundle bundle = (Bundle) state;
      normalizedScale = bundle.getFloat("saveScale");
      m = bundle.getFloatArray("matrix");
      prevMatrix.setValues(m);
      prevMatchViewHeight = bundle.getFloat("matchViewHeight");
      prevMatchViewWidth = bundle.getFloat("matchViewWidth");
      prevViewHeight = bundle.getInt("viewHeight");
      prevViewWidth = bundle.getInt("viewWidth");
      imageRenderedAtLeastOnce = bundle.getBoolean("imageRendered");
      super.onRestoreInstanceState(bundle.getParcelable("instanceState"));
      return;
    }

    super.onRestoreInstanceState(state);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    onDrawReady = true;
    imageRenderedAtLeastOnce = true;

    if (delayedSetOptimalZoom) {
      delayedZoomVariables = null;
      setOptimalZoomForNewImage();
      delayedSetOptimalZoom = false;
    }
    else {
      if (delayedZoomVariables != null) {
        setZoom(delayedZoomVariables.scale, delayedZoomVariables.focusX, delayedZoomVariables.focusY, delayedZoomVariables.scaleType);
        delayedZoomVariables = null;
      }
    }

    super.onDraw(canvas);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    savePreviousImageValues();
  }

  /**
   * Get the max zoom multiplier.
   *
   * @return max zoom multiplier.
   */
  public float getMaxZoom() {
    return maxScale;
  }

  /**
   * Set the max zoom multiplier. Default value: 3.
   *
   * @param max max zoom multiplier.
   */
  public void setMaxZoom(float max) {
    maxScale = max;
    superMaxScale = SUPER_MAX_MULTIPLIER * maxScale;
  }

  /**
   * Get the min zoom multiplier.
   *
   * @return min zoom multiplier.
   */
  public float getMinZoom() {
    return minScale;
  }

  /**
   * Get the current zoom. This is the zoom relative to the initial
   * scale, not the original resource.
   *
   * @return current zoom multiplier.
   */
  public float getCurrentZoom() {
    return normalizedScale;
  }

  /**
   * Set the min zoom multiplier. Default value: 1.
   *
   * @param min min zoom multiplier.
   */
  public void setMinZoom(float min) {
    minScale = min;
    superMinScale = SUPER_MIN_MULTIPLIER * minScale;
  }

  public void reset() {
    normalizedScale = 1;

    matrix.getValues(m);
    m[Matrix.MTRANS_X] = 0;
    m[Matrix.MTRANS_Y] = 0;
    matrix.setValues(m);

    prevMatrix.getValues(m);
    m[Matrix.MTRANS_X] = 0;
    m[Matrix.MTRANS_Y] = 0;
    prevMatrix.setValues(m);

    imageRenderedAtLeastOnce = false;

    fitImageToView();
  }

  /**
   * Reset zoom and translation to initial state.
   */
  public void resetZoom() {
    normalizedScale = 1;
    fitImageToView();
  }

  /**
   * Set zoom to the specified scale. Image will be centered by default.
   *
   * @param scale
   */
  public void setZoom(float scale) {
    setZoom(scale, 0.5f, 0.5f);
  }

  /**
   * Set zoom to the specified scale. Image will be centered around the point
   * (focusX, focusY). These floats range from 0 to 1 and denote the focus point
   * as a fraction from the left and top of the view. For example, the top left
   * corner of the image would be (0, 0). And the bottom right corner would be (1, 1).
   *
   * @param scale
   * @param focusX
   * @param focusY
   */
  public void setZoom(float scale, float focusX, float focusY) {
    setZoom(scale, focusX, focusY, mScaleType);
  }

  /**
   * Set zoom to the specified scale. Image will be centered around the point
   * (focusX, focusY). These floats range from 0 to 1 and denote the focus point
   * as a fraction from the left and top of the view. For example, the top left
   * corner of the image would be (0, 0). And the bottom right corner would be (1, 1).
   *
   * @param scale
   * @param focusX
   * @param focusY
   * @param scaleType
   */
  public void setZoom(float scale, float focusX, float focusY, ScaleType scaleType) {
    // setZoom can be called before the image is on the screen, but at this point,
    // image and view sizes have not yet been calculated in onMeasure. Thus, we should
    // delay calling setZoom until the view has been measured.
    if (!onDrawReady) {
      delayedZoomVariables = new ZoomVariables(scale, focusX, focusY, scaleType);
      return;
    }

    if (scaleType != mScaleType) {
      setScaleType(scaleType);
    }
    resetZoom();
    scaleImage(scale, viewWidth / 2, viewHeight / 2, true);
    matrix.getValues(m);
    m[Matrix.MTRANS_X] = -((focusX * getImageWidth()) - (viewWidth * 0.5f));
    m[Matrix.MTRANS_Y] = -((focusY * getImageHeight()) - (viewHeight * 0.5f));
    matrix.setValues(m);
    fixTrans();
    setImageMatrix(matrix);
  }

  /**
   * Set zoom parameters equal to another TouchImageView. Including scale, position,
   * and ScaleType.
   */
  public void setZoom(CroppingImageView img) {
    PointF center = img.getScrollPosition();
    setZoom(img.getCurrentZoom(), center.x, center.y, img.getScaleType());
  }

  /**
   * Return the point at the center of the zoomed image. The PointF coordinates range
   * in value between 0 and 1 and the focus point is denoted as a fraction from the left
   * and top of the view. For example, the top left corner of the image would be (0, 0).
   * And the bottom right corner would be (1, 1).
   *
   * @return PointF representing the scroll position of the zoomed image.
   */
  public PointF getScrollPosition() {
    Drawable drawable = getDrawable();
    if (drawable == null) {
      return null;
    }
    int drawableWidth = drawable.getIntrinsicWidth();
    int drawableHeight = drawable.getIntrinsicHeight();

    PointF point = transformCoordTouchToBitmap(viewWidth / 2, viewHeight / 2, true);
    point.x /= drawableWidth;
    point.y /= drawableHeight;
    return point;
  }

  /**
   * Set the focus point of the zoomed image. The focus points are denoted as a fraction from the
   * left and top of the view. The focus points can range in value between 0 and 1.
   *
   * @param focusX
   * @param focusY
   */
  public void setScrollPosition(float focusX, float focusY) {
    setZoom(normalizedScale, focusX, focusY);
  }

  /**
   * Performs boundary checking and fixes the image matrix if it
   * is out of bounds.
   */
  private void fixTrans() {
    matrix.getValues(m);
    float transX = m[Matrix.MTRANS_X];
    float transY = m[Matrix.MTRANS_Y];

    float fixTransX = getFixTrans(transX, viewWidth, getImageWidth());
    float fixTransY = getFixTrans(transY, viewHeight, getImageHeight());

    if (fixTransX != 0 || fixTransY != 0) {
      matrix.postTranslate(fixTransX, fixTransY);
    }
  }

  /**
   * When transitioning from zooming from focus to zoom from center (or vice versa)
   * the image can become unaligned within the view. This is apparent when zooming
   * quickly. When the content size is less than the view size, the content will often
   * be centered incorrectly within the view. fixScaleTrans first calls fixTrans() and
   * then makes sure the image is centered correctly within the view.
   */
  private void fixScaleTrans() {
    fixTrans();
    matrix.getValues(m);
    if (getImageWidth() < viewWidth) {
      m[Matrix.MTRANS_X] = (viewWidth - getImageWidth()) / 2;
    }

    if (getImageHeight() < viewHeight) {
      m[Matrix.MTRANS_Y] = (viewHeight - getImageHeight()) / 2;
    }
    matrix.setValues(m);
  }

  private float getFixTrans(float trans, float viewSize, float contentSize) {
    float minTrans, maxTrans;

    if (contentSize <= viewSize) {
      minTrans = 0;
      maxTrans = viewSize - contentSize;

    }
    else {
      minTrans = viewSize - contentSize;
      maxTrans = 0;
    }

    if (trans < minTrans)
      return -trans + minTrans;
    if (trans > maxTrans)
      return -trans + maxTrans;
    return 0;
  }

  private float getFixDragTrans(float delta, float viewSize, float contentSize) {
    if (contentSize <= viewSize) {
      return 0;
    }
    return delta;
  }

  private float getImageWidth() {
    return matchViewWidth * normalizedScale;
  }

  private float getImageHeight() {
    return matchViewHeight * normalizedScale;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    Drawable drawable = getDrawable();
    if (drawable == null || drawable.getIntrinsicWidth() == 0 || drawable.getIntrinsicHeight() == 0) {
      setMeasuredDimension(0, 0);
      return;
    }

    Point size = getDrawableHeightAndWidth();
    int drawableWidth = size.x;
    int drawableHeight = size.y;
    int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    int heightSize = MeasureSpec.getSize(heightMeasureSpec);
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    int totalViewWidth = setViewSize(widthMode, widthSize, drawableWidth);
    int totalViewHeight = setViewSize(heightMode, heightSize, drawableHeight);

    // Image view width, height must consider padding
    viewWidth = totalViewWidth - getPaddingLeft() - getPaddingRight();
    viewHeight = totalViewHeight - getPaddingTop() - getPaddingBottom();

    // Set view dimensions
    setMeasuredDimension(viewWidth, viewHeight);

    // Fit content within view
    fitImageToView();
  }

  /**
   * If the normalizedScale is equal to 1, then the image is made to fit the screen. Otherwise,
   * it is made to fit the screen according to the dimensions of the previous image matrix. This
   * allows the image to maintain its zoom after rotation.
   */
  private void fitImageToView() {
    Drawable drawable = getDrawable();
    if (drawable == null || drawable.getIntrinsicWidth() == 0 || drawable.getIntrinsicHeight() == 0) {
      return;
    }
    if (matrix == null || prevMatrix == null) {
      return;
    }

    int drawableWidth = drawable.getIntrinsicWidth();
    int drawableHeight = drawable.getIntrinsicHeight();

    //
    // Scale image for view
    //
    float scaleX = (float) viewWidth / drawableWidth;
    float scaleY = (float) viewHeight / drawableHeight;

    switch (mScaleType) {
      case CENTER:
        scaleX = scaleY = 1;
        break;

      case CENTER_CROP:
        scaleX = scaleY = Math.max(scaleX, scaleY);
        break;

      case CENTER_INSIDE:
        scaleX = scaleY = Math.min(1, Math.min(scaleX, scaleY));

      case FIT_CENTER:
      case FIT_START:
      case FIT_END:
        scaleX = scaleY = Math.min(scaleX, scaleY);
        break;

      case FIT_XY:
        break;

    }

    //
    // Center the image
    //
    float redundantXSpace = viewWidth - (scaleX * drawableWidth);
    float redundantYSpace = viewHeight - (scaleY * drawableHeight);
    matchViewWidth = viewWidth - redundantXSpace;
    matchViewHeight = viewHeight - redundantYSpace;
    if (!isZoomed() && !imageRenderedAtLeastOnce) {
      //
      // Stretch and center image to fit view
      //
      matrix.setScale(scaleX, scaleY);
      switch (mScaleType) {
        case FIT_START:
          matrix.postTranslate(0, 0);
          break;
        case FIT_END:
          matrix.postTranslate(redundantXSpace, redundantYSpace);
          break;
        default:
          matrix.postTranslate(redundantXSpace / 2, redundantYSpace / 2);
      }
      normalizedScale = 1;

    }
    else {
      //
      // These values should never be 0 or we will set viewWidth and viewHeight
      // to NaN in translateMatrixAfterRotate. To avoid this, call savePreviousImageValues
      // to set them equal to the current values.
      //
      if (prevMatchViewWidth == 0 || prevMatchViewHeight == 0) {
        savePreviousImageValues();
      }

      prevMatrix.getValues(m);

      //
      // Rescale Matrix after rotation
      //
      m[Matrix.MSCALE_X] = matchViewWidth / drawableWidth * normalizedScale;
      m[Matrix.MSCALE_Y] = matchViewHeight / drawableHeight * normalizedScale;

      //
      // TransX and TransY from previous matrix
      //
      float transX = m[Matrix.MTRANS_X];
      float transY = m[Matrix.MTRANS_Y];

      //
      // Width
      //
      float prevActualWidth = prevMatchViewWidth * normalizedScale;
      float actualWidth = getImageWidth();
      translateMatrixAfterRotate(Matrix.MTRANS_X, transX, prevActualWidth, actualWidth, prevViewWidth, viewWidth, drawableWidth);

      //
      // Height
      //
      float prevActualHeight = prevMatchViewHeight * normalizedScale;
      float actualHeight = getImageHeight();
      translateMatrixAfterRotate(Matrix.MTRANS_Y, transY, prevActualHeight, actualHeight, prevViewHeight, viewHeight, drawableHeight);

      //
      // Set the matrix to the adjusted scale and translate values.
      //
      matrix.setValues(m);
    }
    fixTrans();
    setImageMatrix(matrix);
  }

  /**
   * Set view dimensions based on layout params
   *
   * @param mode
   * @param size
   * @param dimension
   * @return
   */
  private int setViewSize(int mode, int size, int dimension) {
    int viewSize;
    switch (mode) {
      case MeasureSpec.EXACTLY:
        viewSize = size;
        break;

      case MeasureSpec.AT_MOST:
        viewSize = Math.min(dimension, size);
        break;

      case MeasureSpec.UNSPECIFIED:
        viewSize = dimension;
        break;

      default:
        viewSize = size;
        break;
    }
    return viewSize;
  }

  /**
   * After rotating, the matrix needs to be translated. This function finds the area of image
   * which was previously centered and adjusts translations so that is again the center, post-rotation.
   *
   * @param axis          Matrix.MTRANS_X or Matrix.MTRANS_Y
   * @param trans         the value of trans in that axis before the rotation
   * @param prevImageSize the width/height of the image before the rotation
   * @param imageSize     width/height of the image after rotation
   * @param prevViewSize  width/height of view before rotation
   * @param viewSize      width/height of view after rotation
   * @param drawableSize  width/height of drawable
   */
  private void translateMatrixAfterRotate(int axis, float trans, float prevImageSize, float imageSize, int prevViewSize, int viewSize, int drawableSize) {
    if (imageSize < viewSize) {
      //
      // The width/height of image is less than the view's width/height. Center it.
      //
      m[axis] = (viewSize - (drawableSize * m[Matrix.MSCALE_X])) * 0.5f;

    }
    else if (trans > 0) {
      //
      // The image is larger than the view, but was not before rotation. Center it.
      //
      m[axis] = -((imageSize - viewSize) * 0.5f);

    }
    else {
      //
      // Find the area of the image which was previously centered in the view. Determine its distance
      // from the left/top side of the view as a fraction of the entire image's width/height. Use that percentage
      // to calculate the trans in the new view width/height.
      //
      float percentage = (Math.abs(trans) + (0.5f * prevViewSize)) / prevImageSize;
      m[axis] = -((percentage * imageSize) - (viewSize * 0.5f));
    }
  }

  private void setState(State state) {
    this.state = state;
  }

  public boolean canScrollHorizontallyFroyo(int direction) {
    return canScrollHorizontally(direction);
  }

  @Override
  public boolean canScrollHorizontally(int direction) {
    matrix.getValues(m);
    float x = m[Matrix.MTRANS_X];

    if (getImageWidth() < viewWidth) {
      return false;

    }
    else if (x >= -1 && direction < 0) {
      return false;

    }
    else if (Math.abs(x) + viewWidth + 1 >= getImageWidth() && direction > 0) {
      return false;
    }

    return true;
  }

  @Override
  public boolean canScrollVertically(int direction) {
    matrix.getValues(m);
    float y = m[Matrix.MTRANS_Y];

    if (getImageHeight() < viewHeight) {
      return false;

    }
    else if (y >= -1 && direction < 0) {
      return false;

    }
    else if (Math.abs(y) + viewHeight + 1 >= getImageHeight() && direction > 0) {
      return false;
    }

    return true;
  }

  /**
   * Gesture Listener detects a single click or long click and passes that on
   * to the view's listener.
   *
   * @author Ortiz
   */
  private class GestureListener extends GestureDetector.SimpleOnGestureListener {

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
      if (doubleTapListener != null) {
        return doubleTapListener.onSingleTapConfirmed(e);
      }
      return performClick();
    }

    @Override
    public void onLongPress(MotionEvent e) {
      performLongClick();
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
      if (fling != null) {
        // If a previous fling is still active, it should be cancelled so that two flings
        // are not run simultaenously.
        fling.cancelFling();
      }
      fling = new Fling((int) velocityX, (int) velocityY);
      compatPostOnAnimation(fling);
      return super.onFling(e1, e2, velocityX, velocityY);
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
      boolean consumed = false;
      if (doubleTapListener != null) {
        consumed = doubleTapListener.onDoubleTap(e);
      }
      if (state == State.NONE) {
        float targetZoom = (normalizedScale == minScale) ? maxScale : minScale;
        DoubleTapZoom doubleTap = new DoubleTapZoom(targetZoom, e.getX(), e.getY(), false);
        compatPostOnAnimation(doubleTap);
        consumed = true;
      }
      return consumed;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
      if (doubleTapListener != null) {
        return doubleTapListener.onDoubleTapEvent(e);
      }
      return false;
    }
  }

  public interface OnTouchImageViewListener {
    void onMove();
  }

  /**
   * Responsible for all touch events. Handles the heavy lifting of drag and also sends
   * touch events to Scale Detector and Gesture Detector.
   *
   * @author Ortiz
   */
  private class PrivateOnTouchListener implements OnTouchListener {
    // Remember last point position for dragging
    private PointF last = new PointF();

    @Override
    public boolean onTouch(View v, MotionEvent event) {
      mScaleDetector.onTouchEvent(event);
      mGestureDetector.onTouchEvent(event);
      PointF curr = new PointF(event.getX(), event.getY());

      if (state == State.NONE || state == State.DRAG || state == State.FLING) {
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
            last.set(curr);
            if (fling != null)
              fling.cancelFling();
            setState(State.DRAG);
            break;

          case MotionEvent.ACTION_MOVE:
            if (state == State.DRAG) {
              float deltaX = curr.x - last.x;
              float deltaY = curr.y - last.y;
              float fixTransX = getFixDragTrans(deltaX, viewWidth, getImageWidth());
              float fixTransY = getFixDragTrans(deltaY, viewHeight, getImageHeight());
              matrix.postTranslate(fixTransX, fixTransY);
              fixTrans();
              last.set(curr.x, curr.y);
            }
            break;

          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_POINTER_UP:
            setState(State.NONE);
            break;
        }
      }

      setImageMatrix(matrix);

      // User-defined OnTouchListener
      if (userTouchListener != null) {
        userTouchListener.onTouch(v, event);
      }

      // OnTouchImageViewListener is set: TouchImageView dragged by user
      if (touchImageViewListener != null) {
        touchImageViewListener.onMove();
      }

      //
      // indicate event was handled
      //
      return true;
    }
  }

  /**
   * ScaleListener detects user two finger scaling and scales image.
   *
   * @author Ortiz
   */
  private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
      setState(State.ZOOM);
      return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
      scaleImage(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY(), true);

      // OnTouchImageViewListener is set: TouchImageView pinch zoomed by user
      if (touchImageViewListener != null) {
        touchImageViewListener.onMove();
      }
      return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
      super.onScaleEnd(detector);
      setState(State.NONE);
      boolean animateToZoomBoundary = false;
      float targetZoom = normalizedScale;
      if (normalizedScale > maxScale) {
        targetZoom = maxScale;
        animateToZoomBoundary = true;

      }
      else if (normalizedScale < minScale) {
        targetZoom = minScale;
        animateToZoomBoundary = true;
      }

      if (animateToZoomBoundary) {
        DoubleTapZoom doubleTap = new DoubleTapZoom(targetZoom, viewWidth / 2, viewHeight / 2, true);
        compatPostOnAnimation(doubleTap);
      }
    }
  }

  private void scaleImage(double deltaScale, float focusX, float focusY, boolean stretchImageToSuper) {
    float lowerScale, upperScale;
    if (stretchImageToSuper) {
      lowerScale = superMinScale;
      upperScale = superMaxScale;

    }
    else {
      lowerScale = minScale;
      upperScale = maxScale;
    }

    float origScale = normalizedScale;
    normalizedScale *= deltaScale;
    if (normalizedScale > upperScale) {
      normalizedScale = upperScale;
      deltaScale = upperScale / origScale;
    }
    else if (normalizedScale < lowerScale) {
      normalizedScale = lowerScale;
      deltaScale = lowerScale / origScale;
    }

    matrix.postScale((float) deltaScale, (float) deltaScale, focusX, focusY);
    fixScaleTrans();
  }

  /**
   * DoubleTapZoom calls a series of runnables which apply
   * an animated zoom in/out graphic to the image.
   *
   * @author Ortiz
   */
  private class DoubleTapZoom implements Runnable {

    private long startTime;
    private static final float ZOOM_TIME = 500;
    private float startZoom, targetZoom;
    private float bitmapX, bitmapY;
    private boolean stretchImageToSuper;
    private AccelerateDecelerateInterpolator interpolator = new AccelerateDecelerateInterpolator();
    private PointF startTouch;
    private PointF endTouch;

    DoubleTapZoom(float targetZoom, float focusX, float focusY, boolean stretchImageToSuper) {
      setState(State.ANIMATE_ZOOM);
      startTime = System.currentTimeMillis();
      this.startZoom = normalizedScale;
      this.targetZoom = targetZoom;
      this.stretchImageToSuper = stretchImageToSuper;
      PointF bitmapPoint = transformCoordTouchToBitmap(focusX, focusY, false);
      this.bitmapX = bitmapPoint.x;
      this.bitmapY = bitmapPoint.y;

      // Used for translating image during scaling
      startTouch = transformCoordBitmapToTouch(bitmapX, bitmapY);
      endTouch = new PointF(viewWidth / 2, viewHeight / 2);
    }

    @Override
    public void run() {
      float t = interpolate();
      double deltaScale = calculateDeltaScale(t);
      scaleImage(deltaScale, bitmapX, bitmapY, stretchImageToSuper);
      translateImageToCenterTouchPosition(t);
      fixScaleTrans();
      setImageMatrix(matrix);

      // OnTouchImageViewListener is set: double tap runnable updates listener
      // with every frame
      if (touchImageViewListener != null) {
        touchImageViewListener.onMove();
      }

      if (t < 1f) {
        // We haven't finished zooming
        compatPostOnAnimation(this);

      }
      else {
        // Finished zooming
        setState(State.NONE);
      }
    }

    /**
     * Interpolate between where the image should start and end in order to translate
     * the image so that the point that is touched is what ends up centered at the end
     * of the zoom.
     *
     * @param t
     */
    private void translateImageToCenterTouchPosition(float t) {
      float targetX = startTouch.x + t * (endTouch.x - startTouch.x);
      float targetY = startTouch.y + t * (endTouch.y - startTouch.y);
      PointF curr = transformCoordBitmapToTouch(bitmapX, bitmapY);
      matrix.postTranslate(targetX - curr.x, targetY - curr.y);
    }

    /**
     * Use interpolator to get t
     *
     * @return
     */
    private float interpolate() {
      long currTime = System.currentTimeMillis();
      float elapsed = (currTime - startTime) / ZOOM_TIME;
      elapsed = Math.min(1f, elapsed);
      return interpolator.getInterpolation(elapsed);
    }

    /**
     * Interpolate the current targeted zoom and get the delta
     * from the current zoom.
     *
     * @param t
     * @return
     */
    private double calculateDeltaScale(float t) {
      double zoom = startZoom + t * (targetZoom - startZoom);
      return zoom / normalizedScale;
    }
  }

  /**
   * This function will transform the coordinates in the touch event to the coordinate
   * system of the drawable that the imageview contain
   *
   * @param x            x-coordinate of touch event
   * @param y            y-coordinate of touch event
   * @param clipToBitmap Touch event may occur within view, but outside image content. True, to clip return value
   *                     to the bounds of the bitmap size.
   * @return Coordinates of the point touched, in the coordinate system of the original drawable.
   */
  private PointF transformCoordTouchToBitmap(float x, float y, boolean clipToBitmap) {
    matrix.getValues(m);
    Point size = getDrawableHeightAndWidth();
    float origW = size.x;
    float origH = size.y;
    float transX = m[Matrix.MTRANS_X];
    float transY = m[Matrix.MTRANS_Y];
    float finalX = ((x - transX) * origW) / getImageWidth();
    float finalY = ((y - transY) * origH) / getImageHeight();

    if (clipToBitmap) {
      finalX = Math.min(Math.max(finalX, 0), origW);
      finalY = Math.min(Math.max(finalY, 0), origH);
    }

    return new PointF(finalX, finalY);
  }

  /**
   * Inverse of transformCoordTouchToBitmap. This function will transform the coordinates in the
   * drawable's coordinate system to the view's coordinate system.
   *
   * @param bx x-coordinate in original bitmap coordinate system
   * @param by y-coordinate in original bitmap coordinate system
   * @return Coordinates of the point in the view's coordinate system.
   */
  private PointF transformCoordBitmapToTouch(float bx, float by) {
    matrix.getValues(m);
    Point size = getDrawableHeightAndWidth();
    float origW = size.x;
    float origH = size.y;
    float px = bx / origW;
    float py = by / origH;
    float finalX = m[Matrix.MTRANS_X] + getImageWidth() * px;
    float finalY = m[Matrix.MTRANS_Y] + getImageHeight() * py;
    return new PointF(finalX, finalY);
  }

  /**
   * Fling launches sequential runnables which apply
   * the fling graphic to the image. The values for the translation
   * are interpolated by the Scroller.
   *
   * @author Ortiz
   */
  private class Fling implements Runnable {

    CompatScroller scroller;
    int currX, currY;

    Fling(int velocityX, int velocityY) {
      setState(State.FLING);
      scroller = new CompatScroller(context);
      matrix.getValues(m);

      int startX = (int) m[Matrix.MTRANS_X];
      int startY = (int) m[Matrix.MTRANS_Y];
      int minX, maxX, minY, maxY;

      if (getImageWidth() > viewWidth) {
        minX = viewWidth - (int) getImageWidth();
        maxX = 0;

      }
      else {
        minX = maxX = startX;
      }

      if (getImageHeight() > viewHeight) {
        minY = viewHeight - (int) getImageHeight();
        maxY = 0;

      }
      else {
        minY = maxY = startY;
      }

      scroller.fling(startX, startY, (int) velocityX, (int) velocityY, minX,
          maxX, minY, maxY);
      currX = startX;
      currY = startY;
    }

    public void cancelFling() {
      if (scroller != null) {
        setState(State.NONE);
        scroller.forceFinished(true);
      }
    }

    @Override
    public void run() {
      // OnTouchImageViewListener is set: TouchImageView listener has been flung by user.
      // Listener runnable updated with each frame of fling animation.
      if (touchImageViewListener != null) {
        touchImageViewListener.onMove();
      }

      if (scroller.isFinished()) {
        scroller = null;
        return;
      }

      if (scroller.computeScrollOffset()) {
        int newX = scroller.getCurrX();
        int newY = scroller.getCurrY();
        int transX = newX - currX;
        int transY = newY - currY;
        currX = newX;
        currY = newY;
        matrix.postTranslate(transX, transY);
        fixTrans();
        setImageMatrix(matrix);
        compatPostOnAnimation(this);
      }
    }
  }

  @TargetApi(Build.VERSION_CODES.GINGERBREAD)
  private class CompatScroller {
    OverScroller overScroller;

    public CompatScroller(Context context) {
      overScroller = new OverScroller(context);
    }

    public void fling(int startX, int startY, int velocityX, int velocityY, int minX, int maxX, int minY, int maxY) {
      overScroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY);
    }

    public void forceFinished(boolean finished) {
      overScroller.forceFinished(finished);
    }

    public boolean isFinished() {
      return overScroller.isFinished();
    }

    public boolean computeScrollOffset() {
      overScroller.computeScrollOffset();
      return overScroller.computeScrollOffset();
    }

    public int getCurrX() {
      return overScroller.getCurrX();
    }

    public int getCurrY() {
      return overScroller.getCurrY();
    }
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  private void compatPostOnAnimation(Runnable runnable) {
    postOnAnimation(runnable);
  }

  private class ZoomVariables {
    public float scale;
    public float focusX;
    public float focusY;
    public ScaleType scaleType;

    public ZoomVariables(float scale, float focusX, float focusY, ScaleType scaleType) {
      this.scale = scale;
      this.focusX = focusX;
      this.focusY = focusY;
      this.scaleType = scaleType;
    }
  }

  /*
  We need this because of https://code.google.com/p/android/issues/detail?id=183304
   */
  private Point getDrawableHeightAndWidth() {
    Drawable d = getDrawable();
    if (d instanceof BitmapDrawable) {
      Bitmap b = ((BitmapDrawable) d).getBitmap();
      if (b == null) {
        return new Point(0, 0);
      }
      else {
        return new Point(b.getWidth(), b.getHeight());
      }
    }
    else if (d != null) {
      return new Point(d.getIntrinsicWidth(), d.getIntrinsicHeight());
    }
    else {
      return new Point(0, 0);
    }
  }

  private void printMatrixInfo() {
    float[] n = new float[9];
    matrix.getValues(n);
    Log.d(DEBUG, "Scale: " + n[Matrix.MSCALE_X] + " TransX: " + n[Matrix.MTRANS_X] + " TransY: " + n[Matrix.MTRANS_Y]);
  }

  public Rect getCroppingRect() {
    int left, top, bottom, right;

    int minDimen = Math.min(viewWidth, viewHeight);
    int maxDimen = Math.max(viewWidth, viewHeight);
    int difference = maxDimen - minDimen;

    // we're a square - heuristic
    if (difference <= 10) {
      left = 0;
      top = 0;
      right = minDimen;
      bottom = minDimen;
    }
    else if (viewHeight == maxDimen) {
      left = 0;
      top = viewHeight / 6;
      right = viewWidth;
      bottom = top + viewWidth;
    }
    else {
      left = viewWidth / 6;
      top = 0;
      right = left + viewHeight;
      bottom = viewHeight;
    }

    PointF topLeft = transformCoordTouchToBitmap(left, top, true);
    PointF bottomRight = transformCoordTouchToBitmap(right, bottom, true);
    return new Rect(Math.round(topLeft.x), Math.round(topLeft.y), Math.round(bottomRight.x), Math.round(bottomRight.y));
  }
}