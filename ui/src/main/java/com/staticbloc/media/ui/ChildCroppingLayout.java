package com.staticbloc.media.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

public class ChildCroppingLayout extends ViewGroup {
  private Point displaySize;

  public ChildCroppingLayout(Context context) {
    this(context, null);
  }

  public ChildCroppingLayout(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ChildCroppingLayout(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init();
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public ChildCroppingLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    init();
  }

  private void init() {
    final Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    displaySize = new Point();
    display.getSize(displaySize);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    // Find out how big everyone wants to be
    measureChildren(widthMeasureSpec, heightMeasureSpec);

    // Check against minimum height and width
    final int maxHeight = Math.max(0, getSuggestedMinimumHeight());
    final int maxWidth = Math.max(0, getSuggestedMinimumWidth());

    setMeasuredDimension(resolveSize(maxWidth, widthMeasureSpec), resolveSize(maxHeight, heightMeasureSpec));
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    final int count = getChildCount();
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();

    if(count != 1) {
      throw new IllegalStateException("A ChildCroppingLayout must have one child");
    }

    final View child = getChildAt(0);
    if(child == null) {
      return;
    }

    LayoutParams lp = child.getLayoutParams();
    if(lp.width != LayoutParams.MATCH_PARENT && lp.height != LayoutParams.MATCH_PARENT) {
      throw new IllegalStateException("A ChildCroppingLayout's child's layout_width and layout_height should be set to match_parent");
    }

    if (child.getVisibility() != GONE) {
      final int widthCroppingFactor = Math.round(displaySize.x / ((float) getMeasuredWidth()));
      final int heightCroppingFactor = Math.round(displaySize.y / ((float) getMeasuredHeight()));

      if(widthCroppingFactor == 1 && heightCroppingFactor == 1) {
        child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
      }
      else {
        final int childLeft = -((child.getMeasuredWidth() / widthCroppingFactor) - (viewWidth / widthCroppingFactor));
        final int childTop = -((child.getMeasuredHeight() / heightCroppingFactor) - (viewHeight / heightCroppingFactor));
        child.layout(childLeft, childTop,
            childLeft + child.getMeasuredWidth(),
            childTop + child.getMeasuredHeight());
      }
    }
  }

  @Override
  protected LayoutParams generateDefaultLayoutParams() {
    return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
  }
}
