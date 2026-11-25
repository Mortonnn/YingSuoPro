package com.github.yingsuopro;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.github.yingsuopro.utils.ThemeHelper;

/**
 * 主题色选择项：设置页直接展示五个色点，点击后弹出大色板。
 * 选择后立即持久化并重建当前 Activity，使整套 Material 主题属性生效。
 */
public class ThemeColorPreference extends Preference {

    private static final int[] SWATCH_IDS = {
            R.id.theme_swatch_1, R.id.theme_swatch_2, R.id.theme_swatch_3,
            R.id.theme_swatch_4, R.id.theme_swatch_5
    };

    public ThemeColorPreference(Context context) {
        super(context);
        init();
    }

    public ThemeColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeColorPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.preference_row);
        setWidgetLayoutResource(R.layout.preference_widget_theme_swatches);
        setSummary(ThemeHelper.getThemeName(getContext(), ThemeHelper.getThemeKey(getContext())));
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        String key = ThemeHelper.getThemeKey(getContext());
        int selectedIndex = ThemeHelper.getThemeIndex(key);

        for (int i = 0; i < SWATCH_IDS.length; i++) {
            View swatch = holder.findViewById(SWATCH_IDS[i]);
            if (swatch == null) {
                continue;
            }
            swatch.setBackground(buildSwatchDrawable(getContext(), i, i == selectedIndex));
        }
    }

    private GradientDrawable buildSwatchDrawable(Context context, int index, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(ThemeHelper.getThemeSwatchColor(context, index));
        int strokeColor = selected
                ? ThemeHelper.colorOnSurface(context)
                : ThemeHelper.colorWithAlpha(ThemeHelper.colorOnSurface(context), 0.18f);
        drawable.setStroke(dp(context, selected ? 2 : 1), strokeColor);
        return drawable;
    }

    @Override
    protected void onClick() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        int padding = dp(context, 12);
        row.setPadding(padding, padding, padding, padding);

        String currentKey = ThemeHelper.getThemeKey(context);
        AlertDialog[] dialogRef = new AlertDialog[1];

        for (int i = 0; i < ThemeHelper.THEME_KEYS.length; i++) {
            final int index = i;
            String key = ThemeHelper.THEME_KEYS[i];

            FrameLayout item = new FrameLayout(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(context, 44), dp(context, 44));
            params.setMargins(dp(context, 4), 0, dp(context, 4), 0);
            item.setLayoutParams(params);
            item.setContentDescription(ThemeHelper.getThemeName(context, key));

            View circle = new View(context);
            FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            circle.setLayoutParams(circleParams);
            circle.setBackground(buildSwatchDrawable(context, index, key.equals(currentKey)));
            item.addView(circle);

            if (key.equals(currentKey)) {
                ImageView check = new ImageView(context);
                FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(
                        dp(context, 24), dp(context, 24), Gravity.CENTER);
                check.setLayoutParams(checkParams);
                check.setImageResource(R.drawable.ic_check_circle);
                check.setColorFilter(0xFFFFFFFF);
                item.addView(check);
            }

            item.setOnClickListener(v -> {
                ThemeHelper.setThemeKey(context, key);
                persistString(key);
                notifyChanged();
                if (dialogRef[0] != null) {
                    dialogRef[0].dismiss();
                }
                if (context instanceof Activity) {
                    ((Activity) context).recreate();
                }
            });

            row.addView(item);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.theme_color_dialog_title)
                .setView(row)
                .setNegativeButton(R.string.common_cancel, null);
        dialogRef[0] = builder.show();
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
