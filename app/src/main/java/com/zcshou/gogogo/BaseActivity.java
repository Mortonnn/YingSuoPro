package com.zcshou.gogogo;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 所有页面的基类。
 *
 * Material 3 Expressive 重构后，页面统一使用自定义的浮动顶栏（MaterialToolbar），
 * 不再依赖系统 ActionBar；状态栏与导航栏的浅色/深色图标由主题中的
 * windowLightStatusBar / windowLightNavigationBar 控制（含深色模式）。
 */
public class BaseActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
