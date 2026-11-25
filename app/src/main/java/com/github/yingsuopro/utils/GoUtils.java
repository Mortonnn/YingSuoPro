package com.github.yingsuopro.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GoUtils {
    public static boolean isDeveloperOptionsEnabled(Context context) {
        return Settings.Global.getInt(
                context.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
        ) == 1;
    }

    // WIFI是否可用
    public static boolean isWifiConnected(Context context) {
        // 从 API 29 开始，NetworkInfo 被标记为过时，这里更换新方法
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network nw = connectivityManager.getActiveNetwork();
        if (nw == null) {
            return false;
        }
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
        return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
    }

    public static boolean isWifiEnabled(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return wifiManager.isWifiEnabled();
    }

    //MOBILE网络是否可用
    public static boolean isMobileConnected(Context context) {
        // 从 API 29 开始，NetworkInfo 被标记为过时，这里更换新方法
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network nw = connectivityManager.getActiveNetwork();
        if (nw == null) {
            return false;
        }
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
        return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    // 断是否有网络连接，但是如果该连接的网络无法上网，也会返回true
    public static boolean isNetworkConnected(Context context) {
        // 从 API 29 开始，NetworkInfo 被标记为过时，这里更换新方法
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network nw = connectivityManager.getActiveNetwork();
        if (nw == null) return false;
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
        return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH));
    }

    //网络是否可用
    public static boolean isNetworkAvailable(Context context) {
        return ((isWifiConnected(context) || isMobileConnected(context)) && isNetworkConnected(context));
    }

    //判断GPS是否打开
    public static  boolean isGpsOpened(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    // 判断是否已在开发者选项中开启模拟位置权限（注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误）
    @SuppressLint("wrongconstant")
    public static boolean isAllowMockLocation(Context context) {
        boolean canMockPosition = false;
        int index;

        try {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);//获得LocationManager引用

            List<String> list = locationManager.getAllProviders();
            for (index = 0; index < list.size(); index++) {
                if (list.get(index).equals(LocationManager.GPS_PROVIDER)) {
                    break;
                }
            }

            if (index < list.size()) {
                // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实GPS参数，不是随便写的)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    locationManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false,
                            false, true, true, true, ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE);
                } else {
                    locationManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false,
                            false, true, true, true, Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
                }
                canMockPosition = true;
            }

            // 模拟位置可用
            if (canMockPosition) {
                // remove test provider
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false);
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return canMockPosition;
    }

    /**
     * [获取应用程序版本名称]
     * @param context context
     * @return 当前应用的版本名称
     */
    public static synchronized String getVersionName(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();

            PackageInfo packageInfo = packageManager.getPackageInfo(
                    context.getPackageName(), 0);

            return packageInfo.versionName;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 获取App的名称
     * @param context 上下文
     * @return 名称
     */
    public static String getAppName(Context context) {
        PackageManager pm = context.getPackageManager();
        //获取包信息
        try {
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            int labelRes = applicationInfo.labelRes;
            return context.getResources().getString(labelRes);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static  String timeStamp2Date(String seconds) {
        if (seconds == null || seconds.isEmpty() || seconds.equals("null")) {
            return "";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        return sdf.format(new Date(Long.parseLong(seconds + "000")));
    }

    //提醒开启位置模拟的弹框
    public static  void showEnableMockLocationDialog(Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("启用位置模拟")//这里是表头的内容
                .setMessage("请在\"开发者选项→选择模拟位置信息应用\"中进行设置")//这里是中间显示的具体信息
                .setPositiveButton("设置",(dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .setNegativeButton("取消",(dialog, which) -> {
                })
                .show();
    }

    //提醒开启悬浮窗的弹框
    public static  void showEnableFloatWindowDialog(Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("启用悬浮窗")//这里是表头的内容
                .setMessage("为了模拟定位的稳定性，建议开启\"显示悬浮窗\"选项")//这里是中间显示的具体信息
                .setPositiveButton("设置",(dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.getPackageName()));
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> {

                })
                .show();
    }

    //显示开启GPS的提示
    public static  void showEnableGpsDialog(Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("启用定位服务")
                .setMessage("是否开启 GPS 定位服务？")
                .setPositiveButton("确定",(dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        context.startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .setNegativeButton("取消",(dialog, which) -> {

                })
                .show();
    }

    public static final String PREF_WIFI_WARNING_IGNORED = "setting_wifi_warning_ignored";

    // 提醒开启位置模拟的弹框
    public static void showDisableWifiDialog(Context context) {
        android.content.SharedPreferences prefs =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(PREF_WIFI_WARNING_IGNORED, false)) {
            return;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle("关闭 WIFI 提示")
                .setMessage("开启 WIFI 后（即使没有连接热点）将导致定位闪回真实位置。建议关闭 WIFI，使用移动流量进行游戏！")
                .setPositiveButton("去关闭",(dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                        context.startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .setNeutralButton("永久忽略", (dialog, which) ->
                        prefs.edit().putBoolean(PREF_WIFI_WARNING_IGNORED, true).apply())
                .setNegativeButton("忽略",(dialog, which) -> {
                })
                .show();
    }

    public static  void DisplayToast(Context context, String str) {
        Toast toast = Toast.makeText(context, str, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.TOP, 0, 100);
        toast.show();
    }

    public static double parseDoubleSafe(String value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 解析 "lng,lat" 或旧版 "[经度:lng 纬度:lat]" 格式的坐标文本。 */
    public static String[] parseLatLngText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int bracketStart = trimmed.indexOf('[');
        int bracketEnd = trimmed.indexOf(']');
        if (bracketStart >= 0 && bracketEnd > bracketStart) {
            String inner = trimmed.substring(bracketStart + 1, bracketEnd);
            String[] parts = inner.trim().split("\\s+");
            if (parts.length == 2 && parts[0].contains(":") && parts[1].contains(":")) {
                return new String[] {
                        parts[0].substring(parts[0].indexOf(':') + 1).trim(),
                        parts[1].substring(parts[1].indexOf(':') + 1).trim()
                };
            }
        }
        String[] commaParts = trimmed.split(",");
        if (commaParts.length == 2) {
            return new String[] {commaParts[0].trim(), commaParts[1].trim()};
        }
        return null;
    }

    /** 统一过滤历史记录 Map 中的可见字段。 */
    public static boolean matchesHistoryQuery(Map<String, Object> record, String query, String... keys) {
        if (record == null || query == null) {
            return false;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return true;
        }
        for (String key : keys) {
            Object val = record.get(key);
            if (val != null && val.toString().toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** 统一设置顶部 SearchView 的文字与图标语义色。 */
    public static void styleToolbarSearchView(Context context, SearchView searchView) {
        if (context == null || searchView == null) {
            return;
        }
        android.view.View searchPlate = searchView.findViewById(androidx.appcompat.R.id.search_plate);
        if (searchPlate != null) {
            searchPlate.setBackground(null);
        }
        int textColor = ThemeHelper.colorOnSurface(context);
        int hintColor = ThemeHelper.colorOnSurfaceVariant(context);
        ImageView searchButton = searchView.findViewById(androidx.appcompat.R.id.search_button);
        if (searchButton != null) {
            searchButton.setColorFilter(textColor);
        }
        ImageView searchMagIcon = searchView.findViewById(androidx.appcompat.R.id.search_mag_icon);
        if (searchMagIcon != null) {
            searchMagIcon.setColorFilter(textColor);
        }
        ImageView searchClose = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        if (searchClose != null) {
            searchClose.setColorFilter(textColor);
        }
        android.widget.EditText searchText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        if (searchText != null) {
            searchText.setTextColor(textColor);
            searchText.setHintTextColor(hintColor);
        }
    }

    /** 限制地图搜索下拉列表的最大高度，确保不会被底部操作按钮或面板遮挡。 */
    public static void constrainSearchListHeight(ListView listView, int maxHeightPx) {
        if (listView == null || maxHeightPx <= 0) {
            return;
        }
        ListAdapter adapter = listView.getAdapter();
        if (adapter == null) {
            return;
        }
        int width = listView.getWidth();
        if (width <= 0) {
            listView.post(() -> constrainSearchListHeight(listView, maxHeightPx));
            return;
        }
        int widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.AT_MOST);
        int totalHeight = 0;
        int count = adapter.getCount();
        for (int i = 0; i < count; i++) {
            android.view.View item = adapter.getView(i, null, listView);
            item.measure(widthSpec, android.view.View.MeasureSpec.UNSPECIFIED);
            totalHeight += item.getMeasuredHeight();
            if (totalHeight >= maxHeightPx) {
                break;
            }
        }
        if (count > 1) {
            totalHeight += listView.getDividerHeight() * (count - 1);
        }
        int targetHeight = totalHeight > maxHeightPx
                ? maxHeightPx
                : android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        android.view.ViewGroup.LayoutParams lp = listView.getLayoutParams();
        if (lp.height != targetHeight) {
            lp.height = targetHeight;
            listView.setLayoutParams(lp);
        }
    }

    /* 计数器类 */
    public static class TimeCount extends CountDownTimer {
        private TimeCountListener mListener;

        public TimeCount(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);//参数依次为总时长,和计时的时间间隔
        }

        @Override
        public void onFinish() {//计时完毕时触发
            mListener.onFinish();
        }

        @Override
        public void onTick(long millisUntilFinished) { //计时过程显示
            mListener.onTick(millisUntilFinished);
        }

        public void setListener(TimeCountListener mListener) {
            this.mListener = mListener;
        }

        public interface TimeCountListener {
            void onTick(long millisUntilFinished);
            void onFinish();
        }
    }
}