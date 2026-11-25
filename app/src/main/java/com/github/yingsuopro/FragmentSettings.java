package com.github.yingsuopro;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.baidu.mapapi.SDKInitializer;
import com.elvishew.xlog.XLog;
import com.github.yingsuopro.utils.GoUtils;

import java.util.Objects;

public class FragmentSettings extends PreferenceFragmentCompat {

    // Set a non-empty decimal EditTextPreference
    private void setupDecimalEditTextPreference(EditTextPreference preference) {
        if (preference != null) {
            preference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) EditTextPreference::getText);
            preference.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_CLASS_NUMBER);
                editText.setSelection(editText.length());
            });
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue.toString().trim().isEmpty()) {
                    GoUtils.DisplayToast(this.getContext(), getResources().getString(R.string.app_error_input_null));
                    return false;
                }
                return true;
            });
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        /* 卡片本身就带描边，去掉系统在行间画的分隔线 */
        setDivider(null);
        setDividerHeight(0);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences_main);

        ListPreference pfJoystick = findPreference("setting_joystick_type");
        if (pfJoystick != null) {
            // 使用自定义 SummaryProvider
            pfJoystick.setSummaryProvider((Preference.SummaryProvider<ListPreference>) preference -> Objects.requireNonNull(preference.getEntry()));
            pfJoystick.setOnPreferenceChangeListener((preference, newValue) -> !newValue.toString().trim().isEmpty());
        }

        EditTextPreference pfWalk = findPreference("setting_walk");
        setupDecimalEditTextPreference(pfWalk);

        EditTextPreference pfRun = findPreference("setting_run");
        setupDecimalEditTextPreference(pfRun);

        EditTextPreference pfBike = findPreference("setting_bike");
        setupDecimalEditTextPreference(pfBike);

        EditTextPreference pfAltitude = findPreference("setting_altitude");
        setupDecimalEditTextPreference(pfAltitude);

        EditTextPreference pfRouteSpeed = findPreference("setting_route_speed");
        setupDecimalEditTextPreference(pfRouteSpeed);

        EditTextPreference pfRouteSpeedFloat = findPreference("setting_route_speed_float");
        setupDecimalEditTextPreference(pfRouteSpeedFloat);

        EditTextPreference pfRouteInterval = findPreference("setting_route_interval");
        setupDecimalEditTextPreference(pfRouteInterval);

        EditTextPreference pfLatOffset = findPreference("setting_lat_max_offset");
        setupDecimalEditTextPreference(pfLatOffset);

        EditTextPreference pfLonOffset = findPreference("setting_lon_max_offset");
        setupDecimalEditTextPreference(pfLonOffset);

        SwitchPreferenceCompat pLog = findPreference("setting_log_off");
        if (pLog != null) {
            pLog.setOnPreferenceChangeListener((preference, newValue) -> {
                if(((SwitchPreferenceCompat) preference).isChecked() != (Boolean) newValue) {
                    XLog.d(preference.getKey() + newValue);

                    if (Boolean.parseBoolean(newValue.toString())) {
                        XLog.d("on");
                    } else {
                        XLog.d("off");
                    }
                    return true;
                } else {
                    return false;
                }
            });
        }

        EditTextPreference pfPosHisValid = findPreference("setting_history_expiration");
        setupDecimalEditTextPreference(pfPosHisValid);

        // 非开关条目统一使用右侧 chevron，开关条目保留 Switch
        applyTrailingWidgets();
    }

    /**
     * 为所有非 Switch 的 Preference 行追加右侧 chevron，
     * 让设置列表的层级与可点击性更清晰（Material 3 Expressive）。
     */
    private void applyTrailingWidgets() {
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference preference = screen.getPreference(i);
            if (preference instanceof PreferenceCategory) {
                PreferenceCategory category = (PreferenceCategory) preference;
                for (int j = 0; j < category.getPreferenceCount(); j++) {
                    bindTrailingWidget(category.getPreference(j));
                }
            } else {
                bindTrailingWidget(preference);
            }
        }
    }

    private void bindTrailingWidget(Preference preference) {
        if (preference instanceof ThemeColorPreference) {
            /* 主题色选择项自带五色圆点，不再追加 chevron */
            return;
        }
        if (preference != null && !(preference instanceof SwitchPreferenceCompat)) {
            preference.setWidgetLayoutResource(R.layout.preference_widget_chevron);
        }
    }
}