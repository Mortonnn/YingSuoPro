package com.github.yingsuopro.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.github.yingsuopro.R;

/**
 * 运行时主题色管理。
 *
 * <p>Material 3 的颜色角色由主题属性（colorPrimary 等）承载。为了让用户可以在
 * 设置里切换主题色，这里通过 ThemeOverlay 在 Activity 创建前覆写这些属性；
 * 布局与图标全部改用 {@code ?attr/colorPrimary} 一类语义属性，因此切色后
 * 按钮、图标、路线、环形进度都会同步换色，而不是停留在绿色。</p>
 */
public final class ThemeHelper {

    public static final String PREF_KEY = "setting_theme_color";
    public static final String DEFAULT_THEME = "ocean";

    /** 与设置页顺序保持一致 */
    public static final String[] THEME_KEYS = {
            "ocean", "lime", "violet", "sunset", "rose"
    };

    private static final int[] THEME_LABELS = {
            R.string.theme_color_ocean,
            R.string.theme_color_lime,
            R.string.theme_color_violet,
            R.string.theme_color_sunset,
            R.string.theme_color_rose
    };

    private static final int[] THEME_OVERLAYS = {
            R.style.ThemeOverlay_App_Palette_Ocean,
            R.style.ThemeOverlay_App_Palette_Lime,
            R.style.ThemeOverlay_App_Palette_Violet,
            R.style.ThemeOverlay_App_Palette_Sunset,
            R.style.ThemeOverlay_App_Palette_Rose
    };

    private static final int[] THEME_SWATCHES = {
            R.color.palette_ocean_primary,
            R.color.palette_lime_primary,
            R.color.palette_violet_primary,
            R.color.palette_sunset_primary,
            R.color.palette_rose_primary
    };

    private ThemeHelper() {
    }

    public static String getThemeKey(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String value = preferences.getString(PREF_KEY, DEFAULT_THEME);
        return isValidKey(value) ? value : DEFAULT_THEME;
    }

    /** 地图日夜模式：跟随系统 / 强制日间 / 强制夜间。 */
    public static boolean isMapNightMode(Context context) {
        String mode = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("setting_map_theme", "auto");
        if ("dark".equals(mode)) {
            return true;
        }
        if ("light".equals(mode)) {
            return false;
        }
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void setThemeKey(Context context, String key) {
        if (!isValidKey(key)) {
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_KEY, key)
                .commit();
    }

    public static boolean isValidKey(String key) {
        if (key == null) {
            return false;
        }
        for (String themeKey : THEME_KEYS) {
            if (themeKey.equals(key)) {
                return true;
            }
        }
        return false;
    }

    public static int getThemeIndex(String key) {
        for (int i = 0; i < THEME_KEYS.length; i++) {
            if (THEME_KEYS[i].equals(key)) {
                return i;
            }
        }
        return 0;
    }

    public static String getThemeName(Context context, String key) {
        return context.getString(THEME_LABELS[getThemeIndex(key)]);
    }

    public static int getThemeSwatchColor(Context context, String key) {
        return ContextCompat.getColor(context, THEME_SWATCHES[getThemeIndex(key)]);
    }

    public static int getThemeSwatchColor(Context context, int index) {
        int safeIndex = Math.max(0, Math.min(THEME_SWATCHES.length - 1, index));
        return ContextCompat.getColor(context, THEME_SWATCHES[safeIndex]);
    }

    /** 在 Activity#onCreate 的 super 之前调用，把用户选中的调色板叠到当前主题上。 */
    public static void applyTheme(Activity activity) {
        if (activity == null) {
            return;
        }
        int overlay = getOverlayRes(getThemeKey(activity));
        if (overlay != 0) {
            activity.getTheme().applyStyle(overlay, true);
        }
    }

    /** 为悬浮窗等独立 Context 套用同一套主题与调色板。 */
    public static Context wrap(Context base) {
        ContextThemeWrapper wrapper = new ContextThemeWrapper(base, R.style.AppTheme);
        int overlay = getOverlayRes(getThemeKey(base));
        if (overlay != 0) {
            wrapper.getTheme().applyStyle(overlay, true);
        }
        return wrapper;
    }

    @StyleRes
    private static int getOverlayRes(String key) {
        return THEME_OVERLAYS[getThemeIndex(key)];
    }

    /** 解析主题属性对应的颜色，替代写死的 ContextCompat.getColor(R.color.primary)。 */
    public static int resolveColor(Context context, int attrRes) {
        if (context == null) {
            return 0;
        }
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attrRes, value, true)) {
            return 0;
        }
        if (value.resourceId != 0) {
            return ContextCompat.getColor(context, value.resourceId);
        }
        return value.data;
    }

    public static int colorPrimary(Context context) {
        return resolveColor(context, com.google.android.material.R.attr.colorPrimary);
    }

    public static int colorOnPrimary(Context context) {
        return resolveColor(context, com.google.android.material.R.attr.colorOnPrimary);
    }

    public static int colorPrimaryContainer(Context context) {
        return resolveColor(context, com.google.android.material.R.attr.colorPrimaryContainer);
    }

    public static int colorOnPrimaryContainer(Context context) {
        return resolveColor(context, com.google.android.material.R.attr.colorOnPrimaryContainer);
    }

    public static int colorSecondaryContainer(Context context) {
        return resolveColor(context, com.google.android.material.R.attr.colorSecondaryContainer);
    }

    public static int colorOnSecondaryContainer(Context context) {
        return resolveColor(context, com.google.android.material.R.attr.colorOnSecondaryContainer);
    }

    public static int colorOnSurface(Context context) {
        return resolveColor(context, com.google.android.material.R.attr.colorOnSurface);
    }

    public static int colorOnSurfaceVariant(Context context) {
        return resolveColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant);
    }

    public static int colorTertiaryContainer(Context context) {
        return resolveColor(context, com.google.android.material.R.attr.colorTertiaryContainer);
    }

    public static int colorOnTertiaryContainer(Context context) {
        return resolveColor(context, com.google.android.material.R.attr.colorOnTertiaryContainer);
    }

    public static int colorError(Context context) {
        return resolveColor(context, com.google.android.material.R.attr.colorError);
    }

    public static int colorErrorContainer(Context context) {
        return resolveColor(context, com.google.android.material.R.attr.colorErrorContainer);
    }

    public static int colorOnErrorContainer(Context context) {
        return resolveColor(context, com.google.android.material.R.attr.colorOnErrorContainer);
    }

    /** 调色板主色的浅色/深色版本，用于自绘控件里的渐变。 */
    public static int colorPrimarySoft(Context context) {
        int color = colorPrimary(context);
        return (color & 0x00FFFFFF) | 0x33000000;
    }

    public static int colorWithAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** 供自定义 Preference 读取颜色数组使用。 */
    public static int[] readThemeColors(Context context) {
        int[] colors = new int[THEME_KEYS.length];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = getThemeSwatchColor(context, i);
        }
        return colors;
    }

    /** 兼容旧 API：读取数值型主题属性。 */
    public static int resolveDimension(Context context, int attrRes, int fallback) {
        TypedArray array = context.obtainStyledAttributes(new int[] {attrRes});
        try {
            return array.getDimensionPixelSize(0, fallback);
        } finally {
            array.recycle();
        }
    }
}
