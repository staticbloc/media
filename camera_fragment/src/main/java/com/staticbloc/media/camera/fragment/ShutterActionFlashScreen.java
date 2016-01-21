package com.staticbloc.media.camera.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.FrameLayout;
import com.staticbloc.media.camera.PhotoCaptureRequest;

import java.lang.ref.WeakReference;

/*package*/ class ShutterActionFlashScreen implements PhotoCaptureRequest.OnShutterActionListener {
  private WeakReference<FrameLayout> containerRef;
  private int flashDuration;

  public ShutterActionFlashScreen(@NonNull FrameLayout container) {
    this(container, 100);
  }

  public ShutterActionFlashScreen(@NonNull FrameLayout container, int flashDuration) {
    this.containerRef = new WeakReference<>(container);
    this.flashDuration = flashDuration;
  }

  @Override
  public void onShutterAction() {
    final FrameLayout container = containerRef.get();
    if(container == null) {
      return;
    }
    final View flashView = new View(container.getContext());
    flashView.setAlpha(0);
    flashView.setBackgroundColor(Color.WHITE);
    ObjectAnimator showAnimator = ObjectAnimator.ofFloat(flashView, "alpha", 0, 1);
    showAnimator.setDuration(100);
    ObjectAnimator hideAnimator = ObjectAnimator.ofFloat(flashView, "alpha", 1, 0);
    hideAnimator.setStartDelay(flashDuration);
    hideAnimator.setDuration(100);

    container.addView(flashView, container.getChildCount());

    final AnimatorSet animatorSet = new AnimatorSet();
    animatorSet.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        container.removeView(flashView);
        animatorSet.removeListener(this);
      }
    });
    animatorSet.playSequentially(showAnimator, hideAnimator);
    animatorSet.start();
  }
}
