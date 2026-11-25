package com.github.yingsuopro.utils;

import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.animation.ValueAnimator;

import com.google.android.material.button.MaterialButton;

import java.util.WeakHashMap;

/**
 * 轻量动效工具：统一按压反馈、页面入场和状态脉冲。
 * 不依赖第三方动画库，保证在 API 27+ 上稳定运行。
 */
public final class UiAnimations {

    private static final WeakHashMap<View, ValueAnimator> MORPH_ANIMATORS = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> BASE_CORNERS = new WeakHashMap<>();

    private UiAnimations() {
    }

    /**
     * 给按钮加入按下缩小、抬起回弹的触感，不拦截原有点击事件。
     * MaterialButton 还会同步做 M3 Expressive 的圆角形变：按下时从圆角矩形
     * 变得得更圆（或变成胶囊），松开后回弹，类似 Pixel 控制中心的磁贴反馈。
     */
    public static void pressScale(View view) {
        if (view == null) {
            return;
        }
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(0.94f)
                            .scaleY(0.94f)
                            .setDuration(90)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    morphCorner(v, true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(260)
                            .setInterpolator(new OvershootInterpolator(2.4f))
                            .start();
                    morphCorner(v, false);
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** 把按钮圆角平滑地过渡到目标值（单位 dp），用于状态切换时的 M3 形状形变。 */
    public static void animateCornerTo(MaterialButton button, float targetDp) {
        if (button == null) {
            return;
        }
        float density = button.getResources().getDisplayMetrics().density;
        float target = targetDp * density;
        ValueAnimator old = MORPH_ANIMATORS.get(button);
        if (old != null) {
            old.cancel();
        }
        ValueAnimator animator = ValueAnimator.ofFloat(button.getCornerRadius(), target);
        animator.setDuration(360);
        animator.setInterpolator(new OvershootInterpolator(1.4f));
        animator.addUpdateListener(animation ->
                button.setCornerRadius(Math.round((float) animation.getAnimatedValue())));
        BASE_CORNERS.put(button, target);
        MORPH_ANIMATORS.put(button, animator);
        animator.start();
    }

    /** M3 Expressive 形变：只在 MaterialButton 上生效，避免破坏自定义背景。 */
    private static void morphCorner(View view, boolean pressed) {
        if (!(view instanceof MaterialButton)) {
            return;
        }
        MaterialButton button = (MaterialButton) view;
        Float stored = BASE_CORNERS.get(view);
        int base = stored != null ? Math.round(stored) : button.getCornerRadius();
        if (stored == null) {
            BASE_CORNERS.put(view, (float) base);
        }
        float delta = view.getResources().getDisplayMetrics().density * 8f;
        float target = pressed ? base + delta : base;
        ValueAnimator old = MORPH_ANIMATORS.get(view);
        if (old != null) {
            old.cancel();
        }
        ValueAnimator animator = ValueAnimator.ofFloat(button.getCornerRadius(), target);
        animator.setDuration(pressed ? 140 : 260);
        animator.setInterpolator(pressed ? new DecelerateInterpolator() : new OvershootInterpolator(1.8f));
        animator.addUpdateListener(animation ->
                button.setCornerRadius(Math.round((float) animation.getAnimatedValue())));
        MORPH_ANIMATORS.put(view, animator);
        animator.start();
    }

    /** 页面进入时的柔和上浮 + 淡入，避免生硬切换。 */
    public static void fadeInUp(View root) {
        if (root == null) {
            return;
        }
        float offset = root.getResources().getDisplayMetrics().density * 10f;
        root.setAlpha(0f);
        root.setTranslationY(offset);
        root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(320)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /** 状态变化时的一次轻微脉冲，用于开始/停止模拟等关键动作。 */
    public static void pulse(View view) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        view.animate()
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(120)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(260)
                        .setInterpolator(new OvershootInterpolator(2.8f))
                        .start())
                .start();
    }

    public static void tapFeedback(View view) {
        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }
}
