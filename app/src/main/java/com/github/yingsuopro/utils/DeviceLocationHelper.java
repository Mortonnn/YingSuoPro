package com.github.yingsuopro.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.elvishew.xlog.XLog;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 系统定位兜底工具。
 *
 * 百度定位 SDK 的 AK 失效时（错误码 505），百度 LocationClient 只会反复回调无效坐标，
 * 无法再提供当前位置。这里直接使用 Android 系统的 LocationManager 获取 GPS/网络定位，
 * 再通过 MapUtils.wgs2bd09() 转成百度地图使用的 BD09 坐标，保证：
 *   1. 定位不依赖百度 AK；
 *   2. 点击“返回当前位置”有明确的成功 / 超时结果，而不是永远提示定位失败。
 */
public class DeviceLocationHelper {
    public interface Listener {
        void onLocation(@NonNull Location location);
    }

    public interface OnceListener {
        void onLocation(@NonNull Location location);

        void onTimeout();
    }

    private static final long REQUEST_TIMEOUT_MS = 10000L;
    private static final long UPDATE_INTERVAL_MS = 2000L;

    private final Context mContext;
    private final LocationManager mLocationManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private Listener mContinuousListener;
    private boolean mContinuousStarted = false;
    private LocationListener mContinuousLocationListener;

    private OnceListener mOnceListener;
    private LocationListener mOnceLocationListener;
    private Runnable mOnceTimeoutRunnable;

    public DeviceLocationHelper(Context context) {
        mContext = context.getApplicationContext();
        mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
    }

    /** 判断经纬度是否为有效坐标（排除百度/系统定位失败时返回的 0,0 和非法值）。 */
    public static boolean isValidLatLng(double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)
                || Double.isInfinite(latitude) || Double.isInfinite(longitude)) {
            return false;
        }
        if (Math.abs(latitude) < 0.000001 && Math.abs(longitude) < 0.000001) {
            return false;
        }
        return latitude >= -90.0 && latitude <= 90.0 && longitude >= -180.0 && longitude <= 180.0;
    }

    public static boolean isValidLocation(Location location) {
        return location != null
                && isValidLatLng(location.getLatitude(), location.getLongitude());
    }

    /**
     * 返回最近一次可用的系统定位。优先选择时间更新的 GPS / 网络 / 融合定位结果。
     */
    @SuppressLint("MissingPermission")
    public Location getLastKnownLocation() {
        if (mLocationManager == null) {
            return null;
        }

        Location best = null;
        long bestTime = Long.MIN_VALUE;
        String[] providers = {
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                "fused",
                LocationManager.PASSIVE_PROVIDER
        };

        for (String provider : providers) {
            try {
                Location location = mLocationManager.getLastKnownLocation(provider);
                if (!isValidLocation(location)) {
                    continue;
                }
                long time = location.getTime();
                if (best == null || time > bestTime) {
                    best = location;
                    bestTime = time;
                }
            } catch (SecurityException e) {
                XLog.e("DeviceLocationHelper: no location permission for " + provider);
            } catch (Exception e) {
                XLog.e("DeviceLocationHelper: getLastKnownLocation failed for " + provider);
            }
        }

        return best;
    }

    /**
     * 开始持续定位。SDK 会立即回调最近一次可用定位，之后按系统定位更新回调。
     */
    @SuppressLint("MissingPermission")
    public void start(@NonNull Listener listener) {
        if (mLocationManager == null) {
            return;
        }

        stop();

        mContinuousListener = listener;
        mContinuousStarted = true;

        Location lastKnown = getLastKnownLocation();
        if (isValidLocation(lastKnown)) {
            mHandler.post(() -> {
                if (mContinuousStarted && mContinuousListener != null) {
                    mContinuousListener.onLocation(lastKnown);
                }
            });
        }

        mContinuousLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                if (isValidLocation(location) && mContinuousListener != null) {
                    mContinuousListener.onLocation(location);
                }
            }

            @Override
            public void onProviderEnabled(@NonNull String provider) {
            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }
        };

        try {
            if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        UPDATE_INTERVAL_MS, 0f, mContinuousLocationListener, Looper.getMainLooper());
            }
            if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        UPDATE_INTERVAL_MS, 0f, mContinuousLocationListener, Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            XLog.e("DeviceLocationHelper: requestLocationUpdates permission denied");
        } catch (Exception e) {
            XLog.e("DeviceLocationHelper: requestLocationUpdates failed");
        }
    }

    /**
     * 请求一次定位。已有可用定位时立即回调；否则最多等待 10 秒，超时回调 onTimeout()。
     */
    @SuppressLint("MissingPermission")
    public void requestOnce(@NonNull OnceListener listener) {
        if (mLocationManager == null) {
            listener.onTimeout();
            return;
        }

        cancelOnceRequest();
        mOnceListener = listener;

        Location lastKnown = getLastKnownLocation();
        if (isValidLocation(lastKnown)) {
            mHandler.post(() -> {
                if (mOnceListener != null) {
                    OnceListener callback = mOnceListener;
                    clearOnceRequest();
                    callback.onLocation(lastKnown);
                }
            });
            return;
        }

        mOnceLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                if (!isValidLocation(location) || mOnceListener == null) {
                    return;
                }
                OnceListener callback = mOnceListener;
                clearOnceRequest();
                callback.onLocation(location);
            }

            @Override
            public void onProviderEnabled(@NonNull String provider) {
            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }
        };

        try {
            if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        1000L, 0f, mOnceLocationListener, Looper.getMainLooper());
            }
            if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        1000L, 0f, mOnceLocationListener, Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            XLog.e("DeviceLocationHelper: requestOnce permission denied");
            clearOnceRequest();
            listener.onTimeout();
            return;
        } catch (Exception e) {
            XLog.e("DeviceLocationHelper: requestOnce failed");
            clearOnceRequest();
            listener.onTimeout();
            return;
        }

        mOnceTimeoutRunnable = () -> {
            OnceListener callback = mOnceListener;
            clearOnceRequest();
            if (callback != null) {
                callback.onTimeout();
            }
        };
        mHandler.postDelayed(mOnceTimeoutRunnable, REQUEST_TIMEOUT_MS);
    }

    public void stop() {
        mContinuousStarted = false;
        mContinuousListener = null;
        if (mLocationManager != null && mContinuousLocationListener != null) {
            try {
                mLocationManager.removeUpdates(mContinuousLocationListener);
            } catch (Exception e) {
                XLog.e("DeviceLocationHelper: remove continuous updates failed");
            }
        }
        mContinuousLocationListener = null;
        cancelOnceRequest();
    }

    private void cancelOnceRequest() {
        if (mLocationManager != null && mOnceLocationListener != null) {
            try {
                mLocationManager.removeUpdates(mOnceLocationListener);
            } catch (Exception e) {
                XLog.e("DeviceLocationHelper: remove once updates failed");
            }
        }
        mOnceLocationListener = null;
        mOnceListener = null;
        if (mOnceTimeoutRunnable != null) {
            mHandler.removeCallbacks(mOnceTimeoutRunnable);
            mOnceTimeoutRunnable = null;
        }
    }

    private void clearOnceRequest() {
        if (mLocationManager != null && mOnceLocationListener != null) {
            try {
                mLocationManager.removeUpdates(mOnceLocationListener);
            } catch (Exception e) {
                XLog.e("DeviceLocationHelper: clear once updates failed");
            }
        }
        mOnceLocationListener = null;
        mOnceListener = null;
        if (mOnceTimeoutRunnable != null) {
            mHandler.removeCallbacks(mOnceTimeoutRunnable);
            mOnceTimeoutRunnable = null;
        }
    }

    /**
     * 使用系统 Geocoder 反查城市名，供百度地点建议做城市过滤；失败时静默忽略。
     */
    public static void resolveCity(Context context, double latitude, double longitude,
                                   Consumer<String> callback) {
        if (context == null || callback == null || !Geocoder.isPresent()) {
            return;
        }

        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(context, Locale.CHINA);
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                if (addresses == null || addresses.isEmpty()) {
                    return;
                }
                Address address = addresses.get(0);
                String city = address.getLocality();
                if (TextUtils.isEmpty(city)) {
                    city = address.getAdminArea();
                }
                if (!TextUtils.isEmpty(city)) {
                    callback.accept(city);
                }
            } catch (Exception e) {
                XLog.e("DeviceLocationHelper: resolveCity failed");
            }
        }, "resolve-city").start();
    }
}
