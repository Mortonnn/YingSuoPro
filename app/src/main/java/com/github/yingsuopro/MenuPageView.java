package com.github.yingsuopro;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.github.yingsuopro.utils.GoUtils;

/**
 * 应用菜单页：顶部只显示应用名，常用功能列表和关于合并在一页。
 */
public class MenuPageView extends FrameLayout {

    public interface OnMenuActionListener {
        void onMenuAction(int action);
    }

    public static final int ACTION_SAVED_LOCATIONS = 1;
    public static final int ACTION_SETTINGS = 2;
    public static final int ACTION_DEVELOPER = 3;
    public static final int ACTION_FEEDBACK = 4;
    public static final int ACTION_ABOUT_SELF = 5;
    public static final int ACTION_ABOUT_UPSTREAM = 6;

    private OnMenuActionListener mListener;

    public MenuPageView(Context context) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.page_menu, this, true);

        addRow(R.drawable.ic_bookmark, getResources().getString(R.string.saved_locations),
                ACTION_SAVED_LOCATIONS);
        addRow(R.drawable.ic_settings, getResources().getString(R.string.nav_menu_settings),
                ACTION_SETTINGS);
        addRow(R.drawable.ic_developer_mode, getResources().getString(R.string.nav_menu_dev),
                ACTION_DEVELOPER);
        addRow(R.drawable.ic_feedback, getResources().getString(R.string.nav_menu_feedback),
                ACTION_FEEDBACK);

        /* 关于页：第一行影梭Pro（当前仓库），第二行 GOGOGO（原项目仓库），只显示名称与版本号 */
        String selfTitle = getResources().getString(R.string.about_self_title)
                + " " + GoUtils.getVersionName(context);
        String upstreamTitle = getResources().getString(R.string.about_upstream_title)
                + " " + getResources().getString(R.string.about_upstream_version);
        addAboutRow(selfTitle, ACTION_ABOUT_SELF);
        addAboutRow(upstreamTitle, ACTION_ABOUT_UPSTREAM);
    }

    public void setOnMenuActionListener(OnMenuActionListener listener) {
        mListener = listener;
    }

    /** 让顶部卡片和页面内容避开状态栏，地图/背景仍延伸到状态栏下面。 */
    public void applyWindowInsets(int topInset, int bottomInset) {
        View topCard = findViewById(R.id.menu_top_card);
        if (topCard != null && topCard.getLayoutParams() instanceof MarginLayoutParams) {
            MarginLayoutParams params = (MarginLayoutParams) topCard.getLayoutParams();
            params.topMargin = dp(8) + topInset;
            topCard.setLayoutParams(params);
        }
        View content = findViewById(R.id.menu_content);
        if (content != null) {
            content.setPadding(dp(16), dp(92) + topInset, dp(16), dp(112) + bottomInset);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void addRow(int iconRes, String title, int action) {
        View row = LayoutInflater.from(getContext()).inflate(R.layout.menu_item,
                findViewById(R.id.menu_list), false);
        ImageView icon = row.findViewById(R.id.menu_icon);
        TextView label = row.findViewById(R.id.menu_title);
        icon.setImageDrawable(ContextCompat.getDrawable(getContext(), iconRes));
        label.setText(title);
        row.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onMenuAction(action);
            }
        });
        ((LinearLayout) findViewById(R.id.menu_list)).addView(row);
    }

    private void addAboutRow(String titleText, int action) {
        View row = LayoutInflater.from(getContext()).inflate(R.layout.menu_about_item,
                findViewById(R.id.menu_about_list), false);
        TextView title = row.findViewById(R.id.menu_about_title);
        title.setText(titleText);
        row.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onMenuAction(action);
            }
        });
        ((LinearLayout) findViewById(R.id.menu_about_list)).addView(row);
    }
}
