package com.github.yingsuopro.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.baidu.mapapi.model.LatLng;
import com.elvishew.xlog.XLog;
import com.github.yingsuopro.MainActivity;
import com.github.yingsuopro.R;
import com.github.yingsuopro.joystick.JoyStick;
import com.github.yingsuopro.route.RouteSimulator;
import com.github.yingsuopro.utils.RouteUtils;
import com.github.yingsuopro.utils.ThemeHelper;

import java.util.List;

public class ServiceGo extends Service {
    // 定位相关变量（默认位置：西安钟楼 WGS84）
    public static final double DEFAULT_LAT = 34.2594;
    public static final double DEFAULT_LNG = 108.9470;
    public static final double DEFAULT_ALT = 55.0D;
    public static final float DEFAULT_BEA = 0.0F;
    private double mCurLat = DEFAULT_LAT;
    private double mCurLng = DEFAULT_LNG;
    private double mCurAlt = DEFAULT_ALT;
    private float mCurBea = DEFAULT_BEA;
    private double mSpeed = 1.2;        /* 默认的速度，单位 m/s */
    private static final int HANDLER_MSG_ID = 0;
    private static final String SERVICE_GO_HANDLER_NAME = "ServiceGoLocation";
    private LocationManager mLocManager;
    private HandlerThread mLocHandlerThread;
    private Handler mLocHandler;
    private Handler mMainHandler;
    private boolean isStop = false;
    // 通知栏消息
    private static final int SERVICE_GO_NOTE_ID = 1;
    private static final String SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW = "ShowJoyStick";
    private static final String SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE = "HideJoyStick";
    private static final String SERVICE_GO_NOTE_ACTION_ROUTE_PAUSE = "PauseRoute";
    private static final String SERVICE_GO_NOTE_CHANNEL_ID = "SERVICE_GO_NOTE";
    private NoteActionReceiver mActReceiver;
    // 摇杆相关
    private JoyStick mJoyStick;
    // 路线模拟相关
    private volatile RouteSimulator mRouteSimulator = null;
    private volatile int mRouteInterval = RouteSimulator.DEFAULT_INTERVAL;
    private volatile int mRouteLoopLimit = 0;       /* 循环次数，0 表示无限循环 */
    private long mRouteLastTime = 0;
    private volatile boolean isRouteMode = false;
    private volatile boolean isRoutePaused = false;
    private static volatile boolean sServiceRunning = false;
    private static volatile boolean sRouteRunning = false;

    /* 模拟服务是否正在运行 */
    public static boolean isRunning() {
        return sServiceRunning;
    }

    /* 路线模拟是否正在运行，供界面在重新进入时同步状态 */
    public static boolean isRouteRunning() {
        return sRouteRunning;
    }

    private final ServiceGoBinder mBinder = new ServiceGoBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sServiceRunning = true;

        mMainHandler = new Handler(Looper.getMainLooper());

        mLocManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        removeTestProviderNetwork();
        addTestProviderNetwork();

        removeTestProviderGPS();
        addTestProviderGPS();

        initGoLocation();

        initNotification();

        initJoyStick();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return super.onStartCommand(intent, flags, startId);
        }

        mCurLng = intent.getDoubleExtra(MainActivity.LNG_MSG_ID, DEFAULT_LNG);
        mCurLat = intent.getDoubleExtra(MainActivity.LAT_MSG_ID, DEFAULT_LAT);
        mCurAlt = intent.getDoubleExtra(MainActivity.ALT_MSG_ID, DEFAULT_ALT);

        mJoyStick.setCurrentPosition(mCurLng, mCurLat, mCurAlt);

        /* 带有路线信息时，直接进入路线模拟；否则说明是普通的位置传送，先停掉正在跑的路线 */
        String routePoints = intent.getStringExtra(MainActivity.ROUTE_MSG_ID);
        if (!TextUtils.isEmpty(routePoints)) {
            startRouteSimulation(routePoints,
                    intent.getDoubleExtra(MainActivity.ROUTE_SPEED_MSG_ID, RouteSimulator.DEFAULT_SPEED),
                    intent.getDoubleExtra(MainActivity.ROUTE_SPEED_FLOAT_MSG_ID, 0),
                    intent.getIntExtra(MainActivity.ROUTE_INTERVAL_MSG_ID, RouteSimulator.DEFAULT_INTERVAL),
                    intent.getIntExtra(MainActivity.ROUTE_LOOP_MSG_ID, 0),
                    intent.getDoubleExtra(MainActivity.ROUTE_ALTITUDE_MSG_ID, mCurAlt),
                    intent.getBooleanExtra(MainActivity.ROUTE_RANDOM_OFFSET_ENABLED_MSG_ID, true),
                    intent.getDoubleExtra(MainActivity.ROUTE_RANDOM_LAT_MAX_MSG_ID, 1.0),
                    intent.getDoubleExtra(MainActivity.ROUTE_RANDOM_LON_MAX_MSG_ID, 1.0));
        } else {
            stopRouteSimulation();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        isStop = true;
        sServiceRunning = false;
        sRouteRunning = false;
        if (mLocHandler != null) {
            mLocHandler.removeMessages(HANDLER_MSG_ID);
        }
        if (mLocHandlerThread != null) {
            mLocHandlerThread.quit();
        }

        if (mJoyStick != null) {
            mJoyStick.destroy();
        }

        removeTestProviderNetwork();
        removeTestProviderGPS();

        if (mActReceiver != null) {
            try {
                unregisterReceiver(mActReceiver);
            } catch (IllegalArgumentException ignored) {
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE);

        super.onDestroy();
    }

    private void initNotification() {
        mActReceiver = new NoteActionReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW);
        filter.addAction(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE);
        filter.addAction(SERVICE_GO_NOTE_ACTION_ROUTE_PAUSE);
        registerReceiver(mActReceiver, filter);

        NotificationChannel mChannel = new NotificationChannel(SERVICE_GO_NOTE_CHANNEL_ID,
                getResources().getString(R.string.note_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.createNotificationChannel(mChannel);
        }

        //准备intent
        Intent clickIntent = new Intent(this, MainActivity.class);
        PendingIntent clickPI = PendingIntent.getActivity(this, 1, clickIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent showIntent = new Intent(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW);
        PendingIntent showPendingPI = PendingIntent.getBroadcast(this, 0, showIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent hideIntent = new Intent(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE);
        PendingIntent hidePendingPI = PendingIntent.getBroadcast(this, 0, hideIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, SERVICE_GO_NOTE_CHANNEL_ID)
                .setChannelId(SERVICE_GO_NOTE_CHANNEL_ID)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText(getResources().getString(R.string.app_service_tips))
                .setContentIntent(clickPI)
                .addAction(new NotificationCompat.Action(null, getResources().getString(R.string.note_show), showPendingPI))
                .addAction(new NotificationCompat.Action(null, getResources().getString(R.string.note_hide), hidePendingPI))
                .setSmallIcon(R.drawable.ic_stat_yingsuo)
                .setColor(ContextCompat.getColor(this, R.color.palette_ocean_primary))
                .build();

        startForeground(SERVICE_GO_NOTE_ID, notification);
    }

    // 路线模拟时，通知栏需要提示当前状态，并提供暂停/继续的操作
    private void updateNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            return;
        }

        Intent clickIntent = new Intent(this, MainActivity.class);
        PendingIntent clickPI = PendingIntent.getActivity(this, 1, clickIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent hideIntent = new Intent(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE);
        PendingIntent hidePendingPI = PendingIntent.getBroadcast(this, 0, hideIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, SERVICE_GO_NOTE_CHANNEL_ID)
                .setChannelId(SERVICE_GO_NOTE_CHANNEL_ID)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentIntent(clickPI)
                .setSmallIcon(R.drawable.ic_stat_yingsuo)
                .setColor(ContextCompat.getColor(this, R.color.palette_ocean_primary));

        if (isRouteMode) {
            builder.setContentText(getResources().getString(isRoutePaused
                            ? R.string.route_status_pause : R.string.route_service_running));

            Intent pauseIntent = new Intent(SERVICE_GO_NOTE_ACTION_ROUTE_PAUSE);
            PendingIntent pausePendingPI = PendingIntent.getBroadcast(this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new NotificationCompat.Action(null,
                    getResources().getString(isRoutePaused ? R.string.route_btn_resume : R.string.route_btn_pause),
                    pausePendingPI));
        } else {
            builder.setContentText(getResources().getString(R.string.app_service_tips));

            Intent showIntent = new Intent(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW);
            PendingIntent showPendingPI = PendingIntent.getBroadcast(this, 0, showIntent, PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new NotificationCompat.Action(null, getResources().getString(R.string.note_show), showPendingPI));
        }

        builder.addAction(new NotificationCompat.Action(null, getResources().getString(R.string.note_hide), hidePendingPI));

        notificationManager.notify(SERVICE_GO_NOTE_ID, builder.build());
    }

    private void initJoyStick() {
        mJoyStick = new JoyStick(ThemeHelper.wrap(this));
        mJoyStick.setListener(new JoyStick.JoyStickClickListener() {
            @Override
            public void onMoveInfo(double speed, double disLng, double disLat, double angle) {
                /* 路线模拟时位置由路线决定，摇杆不应再干预 */
                if (isRouteMode) {
                    return;
                }

                mSpeed = speed;
                // 根据当前的经纬度和距离，计算下一个经纬度
                // Latitude: 1 deg = 110.574 km // 纬度的每度的距离大约为 110.574km
                // Longitude: 1 deg = 111.320*cos(latitude) km  // 经度的每度的距离从0km到111km不等
                // 具体见：http://wp.mlab.tw/?p=2200
                mCurLng += disLng / (111.320 * Math.cos(Math.abs(mCurLat) * Math.PI / 180));
                mCurLat += disLat / 110.574;
                mCurBea = (float) angle;
            }

            @Override
            public void onPositionInfo(double lng, double lat, double alt) {
                if (isRouteMode) {
                    return;
                }

                mCurLng = lng;
                mCurLat = lat;
                mCurAlt = alt;
            }
        });
        mJoyStick.show();
    }

    private void initGoLocation() {
        // 创建 HandlerThread 实例，第一个参数是线程的名字
        mLocHandlerThread = new HandlerThread(SERVICE_GO_HANDLER_NAME, Process.THREAD_PRIORITY_FOREGROUND);
        // 启动 HandlerThread 线程
        mLocHandlerThread.start();
        // Handler 对象与 HandlerThread 的 Looper 对象的绑定
        mLocHandler = new Handler(mLocHandlerThread.getLooper()) {
            // 这里的Handler对象可以看作是绑定在HandlerThread子线程中，所以handlerMessage里的操作是在子线程中运行的
            @Override
            public void handleMessage(@NonNull Message msg) {
                try {
                    /* 路线模拟时按设定的频率刷新定位，否则保持原有的 100ms */
                    long interval = isRouteMode ? mRouteInterval : 100;
                    Thread.sleep(interval);

                    if (!isStop) {
                        if (isRouteMode) {
                            updateRouteLocation(interval);
                        }

                        setLocationNetwork();
                        setLocationGPS();

                        sendEmptyMessage(HANDLER_MSG_ID);
                    }
                } catch (InterruptedException e) {
                    XLog.e("SERVICEGO: ERROR - handleMessage");
                    Thread.currentThread().interrupt();
                }
            }
        };

        mLocHandler.sendEmptyMessage(HANDLER_MSG_ID);
    }

    // 路线模拟：按照经过的时间推进路线，并同步到要模拟的位置
    private void updateRouteLocation(long interval) {
        RouteSimulator simulator = mRouteSimulator;

        if (simulator == null) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        long elapsed = mRouteLastTime == 0 ? interval : now - mRouteLastTime;
        mRouteLastTime = now;

        /* 暂停时只记录时间，不推进路线 */
        if (isRoutePaused) {
            return;
        }

        simulator.update(elapsed);

        double[] lngLat = simulator.getCurrentPosition();
        mCurLng = lngLat[0];
        mCurLat = lngLat[1];
        mCurAlt = simulator.getAltitude();
        mCurBea = simulator.getCurrentBearing();
        mSpeed = simulator.getCurrentSpeed();

        /* 循环次数达到上限后结束模拟 */
        if (simulator.isFinished()
                || (mRouteLoopLimit > 0 && simulator.getLoopCount() >= mRouteLoopLimit)) {
            /* 注意这里要闭包捕获当前使用的模拟器，避免延时执行期间又切换了新的路线 */
            mMainHandler.post(() -> {
                if (mRouteSimulator == simulator) {
                    stopRouteSimulation();
                }
            });
        }
    }

    // 开始路线模拟
    private void startRouteSimulation(String points, double speed, double speedFloat,
                                      int interval, int loop, double altitude,
                                      boolean randomOffsetEnabled, double randomLatMax, double randomLonMax) {
        RouteSimulator simulator = new RouteSimulator();
        List<LatLng> latLngList = RouteUtils.stringToPoints(points);

        if (!simulator.setRoute(latLngList)) {
            XLog.e("SERVICEGO: ERROR - startRouteSimulation");
            return;
        }

        simulator.setSpeed(speed);
        simulator.setSpeedFloat(speedFloat);
        simulator.setRandomOffset(randomOffsetEnabled, randomLatMax, randomLonMax);

        /* 先设置海拔再开始，避免开始的一瞬间出现默认海拔 */
        simulator.setAltitude(altitude);
        simulator.setRepeat(loop != 1);
        simulator.start();

        mRouteSimulator = simulator;
        mRouteInterval = Math.max(RouteSimulator.MIN_INTERVAL, Math.min(RouteSimulator.MAX_INTERVAL, interval));
        mRouteLoopLimit = Math.max(0, loop);
        mRouteLastTime = 0;
        isRoutePaused = false;
        isRouteMode = true;
        sRouteRunning = true;

        double[] lngLat = simulator.getCurrentPosition();
        mCurLng = lngLat[0];
        mCurLat = lngLat[1];
        mCurAlt = simulator.getAltitude();
        mCurBea = simulator.getCurrentBearing();
        mSpeed = simulator.getCurrentSpeed();

        mMainHandler.post(() -> {
            if (isStop) {       /* 服务已经退出，摇杆已经销毁，不能再操作 */
                return;
            }

            mJoyStick.setCurrentPosition(mCurLng, mCurLat, mCurAlt);
            mJoyStick.hide();
            updateNotification();
        });

        XLog.i("SERVICEGO: route simulation start, point: " + latLngList.size());
    }

    // 结束路线模拟，结束后服务并不会退出，仍然可以继续使用摇杆
    private void stopRouteSimulation() {
        if (!isRouteMode) {
            return;
        }

        isRouteMode = false;
        isRoutePaused = false;
        mRouteLastTime = 0;
        mRouteSimulator = null;
        mRouteLoopLimit = 0;
        mSpeed = 1.2;       /* 恢复为步行速度 */
        sRouteRunning = false;

        mMainHandler.post(() -> {
            if (isStop) {
                return;
            }

            mJoyStick.show();
            updateNotification();
        });

        XLog.i("SERVICEGO: route simulation finish");
    }

    private void removeTestProviderGPS() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false);
                mLocManager.removeTestProvider(LocationManager.GPS_PROVIDER);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - removeTestProviderGPS");
        }
    }

    // 注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误
    @SuppressLint("wrongconstant")
    private void addTestProviderGPS() {
        try {
            // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实GPS参数，不是随便写的)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mLocManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE);
            } else {
                mLocManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
            }
            if (!mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - addTestProviderGPS");
        }
    }

    private void setLocationGPS() {
        try {
            // 尽可能模拟真实的 GPS 数据
            Location loc = new Location(LocationManager.GPS_PROVIDER);
            loc.setAccuracy(Criteria.ACCURACY_FINE);    // 设定此位置的估计水平精度，以米为单位。
            loc.setAltitude(mCurAlt);                     // 设置高度，在 WGS 84 参考坐标系中的米
            loc.setBearing(mCurBea);                       // 方向（度）
            loc.setLatitude(mCurLat);                   // 纬度（度）
            loc.setLongitude(mCurLng);                  // 经度（度）
            loc.setTime(System.currentTimeMillis());    // 本地时间
            loc.setSpeed((float) mSpeed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            Bundle bundle = new Bundle();
            bundle.putInt("satellites", 7);
            loc.setExtras(bundle);

            mLocManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc);
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - setLocationGPS");
        }
    }

    private void removeTestProviderNetwork() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false);
                mLocManager.removeTestProvider(LocationManager.NETWORK_PROVIDER);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - removeTestProviderNetwork");
        }
    }

    // 注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误
    @SuppressLint("wrongconstant")
    private void addTestProviderNetwork() {
        try {
            // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实NETWORK参数，不是随便写的)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mLocManager.addTestProvider(LocationManager.NETWORK_PROVIDER, true, false,
                        true, true, true, true,
                        true, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_COARSE);
            } else {
                mLocManager.addTestProvider(LocationManager.NETWORK_PROVIDER, true, false,
                        true, true, true, true,
                        true, Criteria.POWER_LOW, Criteria.ACCURACY_COARSE);
            }
            if (!mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
            }
        } catch (SecurityException e) {
            XLog.e("SERVICEGO: ERROR - addTestProviderNetwork");
        }
    }

    private void setLocationNetwork() {
        try {
            // 尽可能模拟真实的 NETWORK 数据
            Location loc = new Location(LocationManager.NETWORK_PROVIDER);
            loc.setAccuracy(Criteria.ACCURACY_COARSE);  // 设定此位置的估计水平精度，以米为单位。
            loc.setAltitude(mCurAlt);                     // 设置高度，在 WGS 84 参考坐标系中的米
            loc.setBearing(mCurBea);                       // 方向（度）
            loc.setLatitude(mCurLat);                   // 纬度（度）
            loc.setLongitude(mCurLng);                  // 经度（度）
            loc.setTime(System.currentTimeMillis());    // 本地时间
            loc.setSpeed((float) mSpeed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

            mLocManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, loc);
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - setLocationNetwork");
        }
    }

    public class NoteActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                if (action.equals(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW)) {
                    if (!isRouteMode) {
                        mJoyStick.show();
                    }
                }

                if (action.equals(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE)) {
                    mJoyStick.hide();
                }

                if (action.equals(SERVICE_GO_NOTE_ACTION_ROUTE_PAUSE)) {
                    if (isRouteMode) {
                        if (isRoutePaused) {
                            resumeRoute();
                        } else {
                            pauseRoute();
                        }
                    }
                }
            }
        }
    }

    private void pauseRoute() {
        isRoutePaused = true;
        mSpeed = 0;
        mRouteLastTime = 0;
        updateNotification();
    }

    private void resumeRoute() {
        isRoutePaused = false;
        mRouteLastTime = 0;
        updateNotification();
    }

    public class ServiceGoBinder extends Binder {
        public void setPosition(double lng, double lat, double alt) {
            mLocHandler.removeMessages(HANDLER_MSG_ID);
            mCurLng = lng;
            mCurLat = lat;
            mCurAlt = alt;
            mLocHandler.sendEmptyMessage(HANDLER_MSG_ID);
            mJoyStick.setCurrentPosition(mCurLng, mCurLat, mCurAlt);
        }

        public boolean isRouteRunning() {
            return isRouteMode;
        }

        public boolean isRoutePaused() {
            return isRoutePaused;
        }

        public RouteSimulator getRouteSimulator() {
            return mRouteSimulator;
        }

        public void pauseRoute() {
            if (isRouteMode) {
                ServiceGo.this.pauseRoute();
            }
        }

        public void resumeRoute() {
            if (isRouteMode) {
                ServiceGo.this.resumeRoute();
            }
        }

        public void stopRoute() {
            stopRouteSimulation();
        }

        public void setRouteSpeed(double speed) {
            if (mRouteSimulator != null) {
                mRouteSimulator.setSpeed(speed);
            }
        }

        public void setRouteSpeedFloat(double speedFloat) {
            if (mRouteSimulator != null) {
                mRouteSimulator.setSpeedFloat(speedFloat);
            }
        }

        public void setRouteAltitude(double altitude) {
            if (mRouteSimulator != null) {
                mRouteSimulator.setAltitude(altitude);
            }
        }

        public void setRouteInterval(int interval) {
            mRouteInterval = Math.max(RouteSimulator.MIN_INTERVAL, Math.min(RouteSimulator.MAX_INTERVAL, interval));
        }

        public void setRouteLoop(int loop) {
            mRouteLoopLimit = Math.max(0, loop);
            if (mRouteSimulator != null) {
                mRouteSimulator.setRepeat(loop != 1);
            }
        }
    }
}
