package com.github.yingsuopro;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.yingsuopro.utils.ThemeHelper;
import com.github.yingsuopro.utils.UiAnimations;

/**
 * 所有页面的基类。
 *
 * <p>在 super.onCreate 之前套用用户选择的主题调色板，确保布局 inflate 时
 * 拿到的就是正确的主题属性；同时统一处理页面入场动效。</p>
 */
public class BaseActivity extends AppCompatActivity {

    private String mAppliedThemeKey;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        mAppliedThemeKey = ThemeHelper.getThemeKey(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        /* 从设置页返回时如果主题色变了，重建当前页面，让所有 ?attr 语义色重新解析 */
        if (mAppliedThemeKey != null
                && !mAppliedThemeKey.equals(ThemeHelper.getThemeKey(this))) {
            recreate();
            return;
        }
        super.onResume();
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);
        animateContentIn();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        animateContentIn();
    }

    private void animateContentIn() {
        View content = findViewById(android.R.id.content);
        if (content instanceof android.view.ViewGroup && ((android.view.ViewGroup) content).getChildCount() > 0) {
            UiAnimations.fadeInUp(((android.view.ViewGroup) content).getChildAt(0));
        }
    }
}
