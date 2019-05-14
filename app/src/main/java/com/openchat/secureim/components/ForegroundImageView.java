package com.openchat.secureim.components;

import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;

import com.makeramen.roundedimageview.RoundedImageView;

import com.openchat.secureim.R;

public class ForegroundImageView extends RoundedImageView {

  private Drawable mForeground;

  private final Rect mSelfBounds = new Rect();
  private final Rect mOverlayBounds = new Rect();

  private int mForegroundGravity = Gravity.FILL;

  private boolean mForegroundInPadding = true;

  private boolean mForegroundBoundsChanged = false;

  public ForegroundImageView(Context context) {
    super(context);
  }

  public ForegroundImageView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ForegroundImageView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);

    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ForegroundImageView,
      defStyle, 0);

    mForegroundGravity = a.getInt(
      R.styleable.ForegroundImageView_android_foregroundGravity, mForegroundGravity);

    final Drawable d = a.getDrawable(R.styleable.ForegroundImageView_android_foreground);
    if (d != null) {
      setForeground(d);
    }

    mForegroundInPadding = a.getBoolean(
      R.styleable.ForegroundImageView_android_foregroundInsidePadding, true);

    a.recycle();
  }

  
  public int getForegroundGravity() {
    return mForegroundGravity;
  }

  
  public void setForegroundGravity(int foregroundGravity) {
    if (mForegroundGravity != foregroundGravity) {
      if ((foregroundGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) {
        foregroundGravity |= Gravity.START;
      }

      if ((foregroundGravity & Gravity.VERTICAL_GRAVITY_MASK) == 0) {
        foregroundGravity |= Gravity.TOP;
      }

      mForegroundGravity = foregroundGravity;

      if (mForegroundGravity == Gravity.FILL && mForeground != null) {
        Rect padding = new Rect();
        mForeground.getPadding(padding);
      }

      requestLayout();
    }
  }

  @TargetApi(VERSION_CODES.JELLY_BEAN)
  public ActivityOptions getThumbnailTransition() {
    return ActivityOptions.makeScaleUpAnimation(this, 0, 0, getWidth(), getHeight());
  }

  @Override
  protected boolean verifyDrawable(Drawable who) {
    return super.verifyDrawable(who) || (who == mForeground);
  }

  @Override
  @TargetApi(VERSION_CODES.HONEYCOMB)
  public void jumpDrawablesToCurrentState() {
    super.jumpDrawablesToCurrentState();
    if (mForeground != null) mForeground.jumpToCurrentState();
  }

  @Override
  protected void drawableStateChanged() {
    super.drawableStateChanged();
    if (mForeground != null && mForeground.isStateful()) {
      mForeground.setState(getDrawableState());
    }
  }

  
  public void setForeground(Drawable drawable) {
    if (mForeground != drawable) {
      if (mForeground != null) {
        mForeground.setCallback(null);
        unscheduleDrawable(mForeground);
      }

      mForeground = drawable;

      if (drawable != null) {
        setWillNotDraw(false);
        drawable.setCallback(this);
        if (drawable.isStateful()) {
          drawable.setState(getDrawableState());
        }
        if (mForegroundGravity == Gravity.FILL) {
          Rect padding = new Rect();
          drawable.getPadding(padding);
        }
      }  else {
        setWillNotDraw(true);
      }
      requestLayout();
      invalidate();
    }
  }

  
  public Drawable getForeground() {
    return mForeground;
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    mForegroundBoundsChanged = changed;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    mForegroundBoundsChanged = true;
  }

  @Override
  public void draw(@NonNull Canvas canvas) {
    super.draw(canvas);

    if (mForeground != null) {
      final Drawable foreground = mForeground;

      if (mForegroundBoundsChanged) {
        mForegroundBoundsChanged = false;
        final Rect selfBounds = mSelfBounds;
        final Rect overlayBounds = mOverlayBounds;

        final int w = getRight() - getLeft();
        final int h = getBottom() - getTop();

        if (mForegroundInPadding) {
          selfBounds.set(0, 0, w, h);
        } else {
          selfBounds.set(getPaddingLeft(), getPaddingTop(),
            w - getPaddingRight(), h - getPaddingBottom());
        }

        Gravity.apply(mForegroundGravity, foreground.getIntrinsicWidth(),
          foreground.getIntrinsicHeight(), selfBounds, overlayBounds);
        foreground.setBounds(overlayBounds);
      }

      foreground.draw(canvas);
    }
  }
}
