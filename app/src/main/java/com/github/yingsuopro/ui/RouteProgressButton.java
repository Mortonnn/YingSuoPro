package com.github.yingsuopro.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.github.yingsuopro.R;
import com.github.yingsuopro.utils.ThemeHelper;

/**
 * 路线模拟的圆形进度按钮：进度环围绕播放/停止按钮绘制，替代原先的横向进度条。
 * 运行时带有呼吸光晕，进度推进用短动画平滑过渡，暂停时环变淡。
 */
public class RouteProgressButton extends View {

    public static final int STATE_IDLE = 0;
    public static final int STATE_RUNNING = 1;
    public static final int STATE_PAUSED = 2;

    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHeadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRingRect = new RectF();
    private final RectF mButtonRect = new RectF();

    private Drawable mPlayIcon;
    private Drawable mStopIcon;
    private Drawable mCurrentIcon;

    private float mProgress = 0f;
    private float mDisplayedProgress = 0f;
    private float mHaloPhase = 0f;
    private float mCornerMorph = 0f;
    private int mState = STATE_IDLE;
    private ValueAnimator mProgressAnimator;
    private ValueAnimator mHaloAnimator;
    private ValueAnimator mCornerAnimator;
    private OnClickListener mClickListener;

    private int mColorPrimary;
    private int mColorOnPrimary;
    private int mColorTrack;

    public RouteProgressButton(Context context) {
        this(context, null);
    }

    public RouteProgressButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setClickable(true);
        setFocusable(true);
        setWillNotDraw(false);

        mColorPrimary = ThemeHelper.colorPrimary(context);
        mColorOnPrimary = ThemeHelper.colorOnPrimary(context);
        mColorTrack = ContextCompat.getColor(context, R.color.surface_container_highest);

        mTrackPaint.setStyle(Paint.Style.STROKE);
        mTrackPaint.setStrokeWidth(dp(4));
        mTrackPaint.setStrokeCap(Paint.Cap.ROUND);
        mTrackPaint.setColor(ThemeHelper.colorWithAlpha(mColorTrack, 0.75f));

        mProgressPaint.setStyle(Paint.Style.STROKE);
        mProgressPaint.setStrokeWidth(dp(4.5f));
        mProgressPaint.setStrokeCap(Paint.Cap.ROUND);
        mProgressPaint.setColor(mColorPrimary);

        mButtonPaint.setStyle(Paint.Style.FILL);
        mButtonPaint.setColor(mColorPrimary);

        mHeadPaint.setStyle(Paint.Style.FILL);
        mHeadPaint.setColor(mColorOnPrimary);

        mPlayIcon = tintedDrawable(context, R.drawable.ic_route_play, mColorOnPrimary);
        mStopIcon = tintedDrawable(context, R.drawable.ic_route_stop, mColorOnPrimary);
        mCurrentIcon = mPlayIcon;
    }

    private Drawable tintedDrawable(Context context, int resId, int color) {
        Drawable drawable = ContextCompat.getDrawable(context, resId);
        if (drawable == null) {
            return null;
        }
        drawable = DrawableCompat.wrap(drawable.mutate());
        DrawableCompat.setTint(drawable, color);
        return drawable;
    }

    public void setState(int state) {
        mState = state;
        if (state == STATE_RUNNING) {
            mCurrentIcon = mStopIcon;
            startHalo();
            setAlpha(1f);
        } else if (state == STATE_PAUSED) {
            mCurrentIcon = mStopIcon;
            stopHalo();
            setAlpha(0.86f);
        } else {
            mCurrentIcon = mPlayIcon;
            stopHalo();
            setAlpha(1f);
        }
        animateCornerMorph(state == STATE_IDLE ? 0f : 1f);
        invalidate();
    }

    /** Android 16/17 风格的状态形变：空闲时是圆角方形，运行时变成完整圆形。 */
    private void animateCornerMorph(float target) {
        if (mCornerAnimator != null) {
            mCornerAnimator.cancel();
        }
        mCornerAnimator = ValueAnimator.ofFloat(mCornerMorph, target);
        mCornerAnimator.setDuration(320);
        mCornerAnimator.setInterpolator(new android.view.animation.OvershootInterpolator(1.2f));
        mCornerAnimator.addUpdateListener(animation -> {
            mCornerMorph = (float) animation.getAnimatedValue();
            invalidate();
        });
        mCornerAnimator.start();
    }

    public int getState() {
        return mState;
    }

    /** 设置 0~1 的路线进度。 */
    public void setProgress(float progress) {
        float target = Math.max(0f, Math.min(1f, progress));
        if (Math.abs(target - mProgress) < 0.0005f) {
            return;
        }
        mProgress = target;
        if (mProgressAnimator != null) {
            mProgressAnimator.cancel();
        }
        mProgressAnimator = ValueAnimator.ofFloat(mDisplayedProgress, target);
        mProgressAnimator.setDuration(420);
        mProgressAnimator.setInterpolator(new DecelerateInterpolator());
        mProgressAnimator.addUpdateListener(animation -> {
            mDisplayedProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        mProgressAnimator.start();
    }

    /** 不播放动画地重置/设置进度。 */
    public void setProgressImmediately(float progress) {
        if (mProgressAnimator != null) {
            mProgressAnimator.cancel();
            mProgressAnimator = null;
        }
        mProgress = Math.max(0f, Math.min(1f, progress));
        mDisplayedProgress = mProgress;
        invalidate();
    }

    private void startHalo() {
        if (mHaloAnimator != null) {
            return;
        }
        mHaloAnimator = ValueAnimator.ofFloat(0f, 1f);
        mHaloAnimator.setDuration(1900);
        mHaloAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mHaloAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mHaloAnimator.addUpdateListener(animation -> {
            mHaloPhase = (float) animation.getAnimatedValue();
            invalidate();
        });
        mHaloAnimator.start();
    }

    private void stopHalo() {
        if (mHaloAnimator != null) {
            mHaloAnimator.cancel();
            mHaloAnimator = null;
        }
        mHaloPhase = 0f;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = dp(76);
        int width = resolveSize(desired, widthMeasureSpec);
        int height = resolveSize(desired, heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float ringRadius = Math.min(getWidth(), getHeight()) / 2f - dp(4.5f);
        float buttonRadius = ringRadius - dp(10.5f);

        // 运行中的呼吸光晕
        if (mState == STATE_RUNNING) {
            float glowRadius = buttonRadius + dp(6) + dp(8) * mHaloPhase;
            mGlowPaint.setShader(new RadialGradient(cx, cy, glowRadius,
                    new int[] {ThemeHelper.colorWithAlpha(mColorPrimary, 0.20f),
                            ThemeHelper.colorWithAlpha(mColorPrimary, 0.06f),
                            Color.TRANSPARENT},
                    new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, glowRadius, mGlowPaint);
            mGlowPaint.setShader(null);
        }

        mRingRect.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius);

        // 轨道
        canvas.drawArc(mRingRect, -90f, 360f, false, mTrackPaint);

        // 进度弧
        if (mDisplayedProgress > 0.001f) {
            mProgressPaint.setAlpha(mState == STATE_PAUSED ? 150 : 255);
            canvas.drawArc(mRingRect, -90f, 360f * mDisplayedProgress, false, mProgressPaint);

            // 进度头的小光点
            double angle = Math.toRadians(-90f + 360f * mDisplayedProgress);
            float headX = cx + ringRadius * (float) Math.cos(angle);
            float headY = cy + ringRadius * (float) Math.sin(angle);
            mHeadPaint.setAlpha(mState == STATE_PAUSED ? 160 : 235);
            canvas.drawCircle(headX, headY, dp(2.2f), mHeadPaint);
        }

        // 中央按钮：空闲为圆角方形，运行时形变为圆形
        mButtonPaint.setColor(mState == STATE_PAUSED
                ? ThemeHelper.colorWithAlpha(mColorPrimary, 0.78f) : mColorPrimary);
        float minRadius = dp(17);
        float corner = minRadius + (buttonRadius - minRadius) * Math.max(0f, Math.min(1f, mCornerMorph));
        mButtonRect.set(cx - buttonRadius, cy - buttonRadius, cx + buttonRadius, cy + buttonRadius);
        canvas.drawRoundRect(mButtonRect, corner, corner, mButtonPaint);

        // 图标
        if (mCurrentIcon != null) {
            int iconSize = dp(24);
            int offset = mCurrentIcon == mPlayIcon ? dp(1.5f) : 0;
            int left = (int) (cx - iconSize / 2f + offset);
            int top = (int) (cy - iconSize / 2f);
            mCurrentIcon.setBounds(left, top, left + iconSize, top + iconSize);
            mCurrentIcon.draw(canvas);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            stopHalo();
            setAlpha(0.38f);
        } else {
            setAlpha(mState == STATE_PAUSED ? 0.86f : 1f);
            if (mState == STATE_RUNNING) {
                startHalo();
            }
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).start();
                return true;
            case MotionEvent.ACTION_UP:
                animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.4f))
                        .start();
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                animate().scaleX(1f).scaleY(1f).setDuration(200).start();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        if (mClickListener != null) {
            mClickListener.onClick(this);
        }
        return true;
    }

    @Override
    public void setOnClickListener(OnClickListener listener) {
        mClickListener = listener;
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return android.widget.Button.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT, 0f, 100f, mDisplayedProgress * 100f));
        String state = mState == STATE_RUNNING ? "运行中"
                : (mState == STATE_PAUSED ? "已暂停" : "未开始");
        info.setStateDescription(state + "，进度 " + Math.round(mDisplayedProgress * 100) + "%");
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mProgressAnimator != null) {
            mProgressAnimator.cancel();
            mProgressAnimator = null;
        }
        if (mCornerAnimator != null) {
            mCornerAnimator.cancel();
            mCornerAnimator = null;
        }
        stopHalo();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mState == STATE_RUNNING && isEnabled()) {
            startHalo();
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
