package com.github.yingsuopro.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.github.yingsuopro.R;
import com.github.yingsuopro.utils.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Liquid Glass 分段控件，可配置为 2 项（坐标类型切换）或 3 项（首页底部 Dock）。
 *
 * <p>用自绘方式实现局部 Liquid Glass：半透明玻璃胶囊、横向滑动的玻璃滑块、
 * 高光与边缘折射、按压拉伸和带轻微回弹的弹簧动画。滑块会跟随手指方向产生流动感，
 * 同时保留可聚焦、可点击的子项以保证可达性。</p>
 */
public class LiquidGlassSegmentedControl extends ViewGroup {

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int index);
    }

    private static final long ANIM_DURATION = 430L;

    private final List<TextView> mSegments = new ArrayList<>();
    private final List<Drawable> mIcons = new ArrayList<>();
    private final List<String> mLabels = new ArrayList<>();
    private boolean mShowLabels = true;
    private int mSegmentHeightDp = 40;
    private int mIconSizeDp = 18;
    private final Paint mThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTopLightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mThumbRect = new RectF();
    private final RectF mInnerRect = new RectF();
    private final ArgbEvaluator mArgbEvaluator = new ArgbEvaluator();

    private int mSelectedIndex = 0;
    private float mCurrentPosition = 0f;
    private float mThumbLeft = 0f;
    private float mThumbRight = 0f;
    private float mStretch = 0f;
    private float mSegmentWidth = 0f;
    private float mTouchDownRawX = 0f;
    private float mDragStartPosition = 0f;
    private boolean mDragging = false;
    private ValueAnimator mAnimator;
    private OnSelectionChangedListener mListener;

    private int mColorSelectedText;
    private int mColorNormalText;
    private int mColorPrimaryContainer;
    private int mColorGlassBorder;
    private int mColorGlassHighlight;
    private int mColorGlassSurface;
    private int mColorGlassSurfaceStrong;

    public LiquidGlassSegmentedControl(Context context) {
        this(context, null);
    }

    public LiquidGlassSegmentedControl(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setWillNotDraw(false);
        setClipToPadding(false);
        setPadding(dp(5), dp(5), dp(5), dp(5));
        /* 深色模式下不投影，避免在深色背景上出现一圈灰白色光影 */
        setElevation(isNightMode(context) ? 0 : dp(3));

        mColorSelectedText = ThemeHelper.colorOnPrimaryContainer(context);
        mColorNormalText = ThemeHelper.colorOnSurfaceVariant(context);
        mColorPrimaryContainer = ThemeHelper.colorPrimaryContainer(context);
        mColorGlassBorder = ContextCompat.getColor(context, R.color.glass_border);
        mColorGlassHighlight = ContextCompat.getColor(context, R.color.glass_highlight);
        mColorGlassSurface = ContextCompat.getColor(context, R.color.glass_surface);
        mColorGlassSurfaceStrong = ContextCompat.getColor(context, R.color.glass_surface_strong);

        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {mColorGlassSurfaceStrong, mColorGlassSurface});
        background.setCornerRadius(dp(26));
        background.setStroke(dp(1), mColorGlassBorder);
        setBackground(background);

        configure(new String[]{
                context.getString(R.string.tab_position_mock),
                context.getString(R.string.tab_route_mock)
        }, new int[]{R.drawable.ic_my_location, R.drawable.ic_route}, 0);
    }

    /**
     * 重建分段项。Dock 使用 3 项，坐标类型切换使用 2 项。
     */
    public void configure(String[] labels, int[] icons, int initialIndex) {
        removeAllViews();
        mSegments.clear();
        mIcons.clear();
        mLabels.clear();

        Context context = getContext();
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView segment = new TextView(context);
            segment.setGravity(Gravity.CENTER);
            segment.setSingleLine(true);
            segment.setText(mShowLabels ? labels[i] : "");
            segment.setTextSize(14f);
            segment.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            segment.setIncludeFontPadding(false);
            segment.setCompoundDrawablePadding(dp(6));
            segment.setPadding(dp(8), 0, dp(8), 0);
            segment.setContentDescription(labels[i]);
            segment.setFocusable(true);
            segment.setSelected(i == initialIndex);
            segment.setOnClickListener(v -> select(index, true));
            segment.setOnTouchListener((v, event) -> handleSegmentTouch(event, index));

            Drawable icon = icons != null && i < icons.length
                    ? ContextCompat.getDrawable(context, icons[i]) : null;
            if (icon != null) {
                icon = DrawableCompat.wrap(icon.mutate());
                icon.setBounds(0, 0, dp(mIconSizeDp), dp(mIconSizeDp));
            }
            mIcons.add(icon);
            mLabels.add(labels[i]);
            mSegments.add(segment);
            addView(segment);
        }

        setSelectedIndex(initialIndex, false);
    }

    public void setSegmentTextSize(float sizeSp) {
        for (TextView segment : mSegments) {
            segment.setTextSize(sizeSp);
        }
        requestLayout();
    }

    /** Dock 可以只显示图标，文字保留在无障碍描述中。 */
    public void setShowLabels(boolean show) {
        mShowLabels = show;
        for (int i = 0; i < mSegments.size(); i++) {
            mSegments.get(i).setText(show ? mLabels.get(i) : "");
        }
        requestLayout();
    }

    public void setSegmentHeightDp(int heightDp) {
        mSegmentHeightDp = heightDp;
        requestLayout();
    }

    public void setIconSizeDp(int sizeDp) {
        mIconSizeDp = sizeDp;
        for (Drawable icon : mIcons) {
            if (icon != null) {
                icon.setBounds(0, 0, dp(sizeDp), dp(sizeDp));
            }
        }
        requestLayout();
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        mListener = listener;
    }

    public int getSelectedIndex() {
        return mSelectedIndex;
    }

    /**
     * 选中某一项。
     *
     * @param index   0 = 位置模拟，1 = 路线模拟
     * @param animate 是否播放玻璃滑块动画
     */
    public void setSelectedIndex(int index, boolean animate) {
        if (mSegments.isEmpty()) {
            return;
        }
        int safeIndex = Math.max(0, Math.min(mSegments.size() - 1, index));
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        for (int i = 0; i < mSegments.size(); i++) {
            mSegments.get(i).setSelected(i == safeIndex);
        }

        if (!animate) {
            mSelectedIndex = safeIndex;
            mCurrentPosition = safeIndex;
            requestLayout();
            invalidate();
            applyTextColors(safeIndex, safeIndex, 1f);
            return;
        }

        final float from = mCurrentPosition;
        final float to = safeIndex;
        mSelectedIndex = safeIndex;

        mAnimator = ValueAnimator.ofFloat(from, to);
        mAnimator.setDuration(ANIM_DURATION);
        mAnimator.setInterpolator(new OvershootInterpolator(0.75f));
        mAnimator.addUpdateListener(animation -> {
            mCurrentPosition = (float) animation.getAnimatedValue();
            updateThumbBounds();
            applyTextColors(from, to, animation.getAnimatedFraction());
            invalidate();
        });
        mAnimator.start();
    }

    private void select(int index, boolean animate) {
        if (index == mSelectedIndex) {
            /* 拖动未越过中线时回弹到原位置 */
            setSelectedIndex(index, animate);
            return;
        }
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        setSelectedIndex(index, animate);
        if (mListener != null) {
            mListener.onSelectionChanged(index);
        }
    }

    /** 支持横向拖动：滑块跟手移动，松手后按最近的一项回弹吸附。 */
    private boolean handleSegmentTouch(android.view.MotionEvent event, int touchedIndex) {
        switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                if (mAnimator != null) {
                    mAnimator.cancel();
                    mAnimator = null;
                }
                mDragging = true;
                mTouchDownRawX = event.getRawX();
                mDragStartPosition = mCurrentPosition;
                requestDisallowInterceptTouchEvent(true);
                return true;
            case android.view.MotionEvent.ACTION_MOVE:
                if (mDragging && mSegmentWidth > 0f) {
                    float delta = (event.getRawX() - mTouchDownRawX) / mSegmentWidth;
                    mCurrentPosition = Math.max(0f, Math.min(mSegments.size() - 1, mDragStartPosition + delta));
                    updateThumbBounds();
                    applyTextColors(0f, 0f, 1f);
                    invalidate();
                }
                return true;
            case android.view.MotionEvent.ACTION_UP:
                if (mDragging) {
                    float travel = Math.abs(event.getRawX() - mTouchDownRawX);
                    int target = travel < dp(6)
                            ? touchedIndex
                            : Math.round(mCurrentPosition);
                    mDragging = false;
                    select(target, true);
                }
                return true;
            case android.view.MotionEvent.ACTION_CANCEL:
                mDragging = false;
                select(mSelectedIndex, true);
                return true;
            default:
                return true;
        }
    }

    private void updateThumbBounds() {
        int contentLeft = getPaddingLeft();
        int contentRight = getWidth() - getPaddingRight();
        mSegmentWidth = (contentRight - contentLeft) / (float) Math.max(1, mSegments.size());
        float left = contentLeft + mSegmentWidth * mCurrentPosition;
        float overshoot = Math.abs(mCurrentPosition - Math.round(mCurrentPosition));
        mStretch = dp(9) * Math.min(1f, overshoot * 2.6f);
        mThumbLeft = Math.max(contentLeft, left - mStretch);
        mThumbRight = Math.min(contentRight, left + mSegmentWidth + mStretch);
    }

    private void applyTextColors(float from, float to, float fraction) {
        for (int i = 0; i < mSegments.size(); i++) {
            float distance = Math.abs(mCurrentPosition - i);
            float selectedWeight = Math.max(0f, Math.min(1f, 1f - distance));
            int textColor = (int) mArgbEvaluator.evaluate(selectedWeight, mColorNormalText, mColorSelectedText);
            mSegments.get(i).setTextColor(textColor);
            Drawable icon = i < mIcons.size() ? mIcons.get(i) : null;
            if (icon != null) {
                DrawableCompat.setTint(icon, textColor);
                mSegments.get(i).setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int childHeight = dp(mSegmentHeightDp);
        int width = 0;
        for (TextView segment : mSegments) {
            segment.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY));
            width += segment.getMeasuredWidth();
        }
        width += getPaddingLeft() + getPaddingRight();
        int height = childHeight + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int contentLeft = getPaddingLeft();
        int contentTop = getPaddingTop();
        int contentRight = r - l - getPaddingRight();
        int contentBottom = b - t - getPaddingBottom();
        int segmentWidth = (contentRight - contentLeft) / Math.max(1, mSegments.size());

        for (int i = 0; i < mSegments.size(); i++) {
            int left = contentLeft + i * segmentWidth;
            int right = i == mSegments.size() - 1 ? contentRight : left + segmentWidth;
            TextView segment = mSegments.get(i);
            if (!mShowLabels) {
                // 图标模式下，compound drawable 默认贴在左侧；用等宽内边距把它压到格子中央。
                int horizontalPadding = Math.max(0, (segmentWidth - dp(mIconSizeDp)) / 2);
                if (segment.getPaddingLeft() != horizontalPadding
                        || segment.getPaddingRight() != horizontalPadding) {
                    segment.setPadding(horizontalPadding, 0, horizontalPadding, 0);
                }
            }
            segment.layout(left, contentTop, right, contentBottom);
        }
        updateThumbBounds();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = dp(24);
        boolean night = isNightMode(getContext());

        if (!night) {
            // 顶部玻璃高光：仅浅色模式保留，深色模式会显得发白
            mTopLightPaint.setShader(new LinearGradient(0, getPaddingTop(),
                    0, getHeight() * 0.62f,
                    mColorGlassHighlight, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(0, getPaddingTop(), getWidth(), getHeight() - getPaddingBottom(),
                    radius, radius, mTopLightPaint);
        }

        mThumbRect.set(mThumbLeft, getPaddingTop() + dp(1),
                mThumbRight, getHeight() - getPaddingBottom() - dp(1));

        if (!night) {
            // 柔和投影，营造玻璃悬浮感（深色模式下不绘制）
            mShadowPaint.setColor(ContextCompat.getColor(getContext(), R.color.glass_shadow));
            RectF shadowRect = new RectF(mThumbRect);
            shadowRect.offset(0, dp(2));
            canvas.drawRoundRect(shadowRect, radius, radius, mShadowPaint);
        }

        // 玻璃滑块主体：浅色模式保留白色高光；深色模式使用纯主色容器，避免灰白光晕
        mThumbPaint.setColor(ThemeHelper.colorWithAlpha(mColorPrimaryContainer, night ? 0.96f : 0.62f));
        canvas.drawRoundRect(mThumbRect, radius, radius, mThumbPaint);

        if (!night) {
            mThumbPaint.setShader(new LinearGradient(mThumbRect.left, mThumbRect.top,
                    mThumbRect.right, mThumbRect.bottom,
                    new int[] {ThemeHelper.colorWithAlpha(Color.WHITE, 0.70f),
                            ThemeHelper.colorWithAlpha(Color.WHITE, 0.10f)},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(mThumbRect, radius, radius, mThumbPaint);
            mThumbPaint.setShader(null);
        }

        // 边缘折射高光
        mThumbBorderPaint.setStyle(Paint.Style.STROKE);
        mThumbBorderPaint.setStrokeWidth(dp(1));
        mThumbBorderPaint.setColor(mColorGlassBorder);
        canvas.drawRoundRect(mThumbRect, radius, radius, mThumbBorderPaint);

        if (!night) {
            // 内部液体高光（深色模式不绘制）
            mInnerRect.set(mThumbRect.left + dp(1), mThumbRect.top + dp(1),
                    mThumbRect.right - dp(1), mThumbRect.bottom - dp(1));
            float cx = mInnerRect.centerX() - mInnerRect.width() * 0.18f;
            float cy = mInnerRect.top + mInnerRect.height() * 0.28f;
            mGlowPaint.setShader(new RadialGradient(cx, cy, Math.max(dp(12), mInnerRect.width() * 0.55f),
                    new int[] {ThemeHelper.colorWithAlpha(Color.WHITE, 0.55f), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(mInnerRect, radius - dp(1), radius - dp(1), mGlowPaint);
            mGlowPaint.setShader(null);
        }
    }

    private boolean isNightMode(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
