package com.github.yingsuopro;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BMThemeMode;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.TextureMapView;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.Polyline;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.model.LatLngBounds;
import com.baidu.mapapi.search.sug.SuggestionResult;
import com.baidu.mapapi.search.sug.SuggestionSearch;
import com.baidu.mapapi.search.sug.SuggestionSearchOption;
import com.elvishew.xlog.XLog;
import com.github.yingsuopro.database.DataBaseHistoryRoute;
import com.github.yingsuopro.route.RouteSimulator;
import com.github.yingsuopro.service.ServiceGo;
import com.github.yingsuopro.utils.DeviceLocationHelper;
import com.github.yingsuopro.utils.GoUtils;
import com.github.yingsuopro.utils.MapUtils;
import com.github.yingsuopro.utils.PlaceSearchHelper;
import com.github.yingsuopro.utils.RouteUtils;
import com.github.yingsuopro.utils.ThemeHelper;
import com.github.yingsuopro.utils.UiAnimations;
import com.github.yingsuopro.ui.RouteProgressButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RoutePageView extends FrameLayout {
    public interface OnBackListener {
        void onBack();
    }

    public interface OnMenuClickListener {
        void onMenuClick();
    }

    private OnBackListener mOnBackListener;
    private OnMenuClickListener mOnMenuClickListener;
    private static final int ROUTE_REFRESH_DELAY = 500;     /* 模拟过程中界面刷新的间隔，单位 ms */

    /* 历史路线列表使用的 KEY，与 route_item 中的控件一一对应 */
    private static final String KEY_ID = "KEY_ID";
    private static final String KEY_NAME = "KEY_NAME";
    private static final String KEY_DISTANCE = "KEY_DISTANCE";
    private static final String KEY_TIME = "KEY_TIME";

    private SharedPreferences sharedPreferences;
    /*============================== 地图相关 ==============================*/
    private TextureMapView mMapView;
    private BaiduMap mBaiduMap;
    private DeviceLocationHelper mLocationHelper;
    private LatLng mCurLatLng = null;       // 当前的真实位置
    private BitmapDescriptor mPointIndicator = null;
    private final List<LatLng> mRoutePoints = new ArrayList<>();    // 规划中的途经点
    private final List<Marker> mPointMarkers = new ArrayList<>();
    private Polyline mRoutePolyline = null;
    private Polyline mRoutePolylineCasing = null;
    private boolean isFirstLoc = true;
    /*============================== 路线模拟相关 ==============================*/
    private ServiceGo.ServiceGoBinder mServiceBinder;
    private ServiceConnection mConnection;
    private boolean isServiceBound = false;
    private boolean isRouteRunning = false;
    private boolean isRoutePaused = false;
    private double mRouteSpeed = RouteSimulator.DEFAULT_SPEED;
    private double mRouteSpeedFloat = RouteSimulator.DEFAULT_SPEED_FLOAT;
    private int mRouteInterval = RouteSimulator.DEFAULT_INTERVAL;
    private int mRouteLoop = 0;
    private double mRouteAltitude = 55.0;
    /*============================== 界面相关 ==============================*/
    private TextView mPointCountText;
    private TextView mLengthText;
    private TextView mSpeedText;
    private TextView mStatusText;
    private ImageButton mPauseButton;
    private RouteProgressButton mStartButton;
    private ImageButton mUndoButton;
    private ImageButton mClearButton;
    private ImageButton mSaveButton;
    private ImageButton mAddButton;
    private MenuItem mSearchItem;
    private SearchView mSearchView;
    private ListView mSearchList;
    private LinearLayout mSearchLayout;

    private SuggestionSearch mSuggestionSearch;
    private final Handler mRefreshHandler = new Handler(Looper.getMainLooper());
    private final Handler mSearchHandler = new Handler(Looper.getMainLooper());
    private Runnable mSearchRunnable;
    private Runnable mBaiduTimeoutRunnable;
    private String mPendingSearchKeyword = null;
    /*============================== 历史路线相关 ==============================*/
    private SQLiteDatabase mRouteHistoryDB;
    private LinearLayout mHistoryLayout;
    private RecyclerView mHistoryListView;
    private HistoryAdapter mHistoryAdapter;
    private View mHistoryNoText;
    private List<Map<String, Object>> mHistoryRecords = new ArrayList<>();
    private View mHistoryHandle;
    private LinearLayout mRoutePanel;
    private BottomSheetBehavior<LinearLayout> mRouteSheetBehavior;
    private int mHistoryHeight = 0;          /* 兼容旧逻辑的兜底高度 */
    private int mTouchSlop = 0;

    public RoutePageView(Context context) {
        super(context);
        init(context);
    }

    public void setOnBackListener(OnBackListener listener) {
        mOnBackListener = listener;
    }

    public void setOnMenuClickListener(OnMenuClickListener listener) {
        mOnMenuClickListener = listener;
    }

    /** 让路线页顶部卡片避开状态栏，同时保持地图铺到状态栏下面。 */
    public void applyTopInset(int topInset) {
        View topCard = findViewById(R.id.route_top_card);
        if (topCard == null || !(topCard.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) topCard.getLayoutParams();
        int target = Math.round(8f * getResources().getDisplayMetrics().density) + topInset;
        if (params.topMargin != target) {
            params.topMargin = target;
            topCard.setLayoutParams(params);
        }
    }

    private TextView findToolbarText(MaterialToolbar toolbar, String text) {
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof TextView) {
                CharSequence value = ((TextView) child).getText();
                if (value != null && text.contentEquals(value)) {
                    return (TextView) child;
                }
            }
        }
        return null;
    }

    private void init(Context context) {
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_background));
        LayoutInflater.from(context).inflate(R.layout.activity_route, this, true);
        // 路线页搜索框常驻展开但不应在进入页面时自动弹出输入法
        if (context instanceof Activity) {
            ((Activity) context).getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }

        MaterialToolbar toolbar = findViewById(R.id.route_toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            if (mOnBackListener != null) {
                mOnBackListener.onBack();
            }
        });
        toolbar.post(() -> {
            TextView subtitle = findToolbarText(toolbar, getResources().getString(R.string.app_name));
            if (subtitle != null) {
                subtitle.setClickable(true);
                subtitle.setFocusable(true);
                subtitle.setOnClickListener(v -> {
                    if (mOnMenuClickListener != null) {
                        mOnMenuClickListener.onMenuClick();
                    }
                });
            }
        });

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        initServiceConnection();

        initRouteDatabase();

        initRouteParam();

        initMap();

        initMapLocation();

        initMapButton();

        initSearchView();

        initRoutePanel();
    }

    /** 页面显示时由宿主调用。 */
    public void onPageResume() {
        mMapView.onResume();
        applyMapThemeMode();

        /* 重新进入界面时，同步后台路线模拟的运行状态 */
        if (!isRouteRunning && ServiceGo.isRouteRunning()) {
            isRouteRunning = true;
            isRoutePaused = false;
            mPauseButton.setVisibility(View.VISIBLE);
            mPauseButton.setImageResource(R.drawable.ic_route_pause);
            mStatusText.setText(getResources().getString(R.string.route_status_run));

            if (mStartButton != null) {
                mStartButton.setState(RouteProgressButton.STATE_RUNNING);
                mStartButton.setContentDescription(getResources().getString(R.string.route_btn_stop));
            }

            View centerMarker = findViewById(R.id.route_center_marker);
            if (centerMarker != null) {
                centerMarker.setVisibility(View.GONE);
            }

            if (!isServiceBound) {
                Intent serviceGoIntent = new Intent(getContext(), ServiceGo.class);
                getContext().bindService(serviceGoIntent, mConnection, Context.BIND_AUTO_CREATE);
                isServiceBound = true;
            }
        } else if (isRouteRunning && !ServiceGo.isRouteRunning()) {
            resetRouteUiIdle(false);
        }

        if (isRouteRunning) {
            mRefreshHandler.removeCallbacks(mRefreshTask);
            mRefreshHandler.postDelayed(mRefreshTask, ROUTE_REFRESH_DELAY);
        }
        updateActionStates();
    }

    /** 页面离开时由宿主调用。 */
    public void onPagePause() {
        mRefreshHandler.removeCallbacks(mRefreshTask);
        mMapView.onPause();
    }

    /** 处理返回键：优先收起搜索结果、搜索框或展开的历史面板。 */
    public boolean handleBackPressed() {
        if (mSearchLayout != null && mSearchLayout.getVisibility() == View.VISIBLE) {
            mSearchLayout.setVisibility(View.GONE);
            return true;
        }
        if (mSearchItem != null && mSearchItem.isActionViewExpanded()) {
            mSearchItem.collapseActionView();
            return true;
        }
        if (mRouteSheetBehavior != null
                && mRouteSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            mRouteSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            return true;
        }
        return false;
    }

    private void resetRouteUiIdle(boolean completed) {
        mRefreshHandler.removeCallbacks(mRefreshTask);
        isRouteRunning = false;
        isRoutePaused = false;
        mPauseButton.setVisibility(View.GONE);
        mStatusText.setText(getResources().getString(R.string.route_status_idle));

        if (mStartButton != null) {
            mStartButton.setState(RouteProgressButton.STATE_IDLE);
            mStartButton.setProgressImmediately(completed ? 1f : 0f);
            mStartButton.setContentDescription(getResources().getString(R.string.route_btn_start));
        }

        View centerMarker = findViewById(R.id.route_center_marker);
        if (centerMarker != null) {
            centerMarker.setVisibility(View.VISIBLE);
        }

        if (isServiceBound) {
            try {
                getContext().unbindService(mConnection);
            } catch (IllegalArgumentException ignored) {
            }
            isServiceBound = false;
        }
        mServiceBinder = null;
        updateActionStates();
    }

    /** 根据设置与系统明暗主题切换百度地图 9.0 的日间/夜间底图。 */
    private void applyMapThemeMode() {
        if (mBaiduMap == null || sharedPreferences == null) {
            return;
        }
        String mapStyle = sharedPreferences.getString("setting_map_style", "normal");
        boolean satellite = "satellite".equals(mapStyle);
        boolean night = ThemeHelper.isMapNightMode(getContext());
        BMThemeMode mode;
        if (satellite) {
            mode = night ? BMThemeMode.MapThemeModeDarkSatellite : BMThemeMode.MapThemeModeSatellite;
        } else {
            mode = night ? BMThemeMode.MapThemeModeDark : BMThemeMode.MapThemeModeDefault;
        }
        mBaiduMap.setMapThemeMode(mode);
    }

    /** 页面销毁时由宿主调用。 */
    public void release() {
        mRefreshHandler.removeCallbacksAndMessages(null);

        if (isServiceBound) {
            try {
                getContext().unbindService(mConnection);
            } catch (IllegalArgumentException ignored) {
            }
            isServiceBound = false;
        }

        if (mBaiduMap != null) {
            mBaiduMap.setMyLocationEnabled(false);
        }
        if (mMapView != null) {
            mMapView.onDestroy();
        }
        if (mSuggestionSearch != null) {
            mSuggestionSearch.destroy();
        }

        if (mLocationHelper != null) {
            mLocationHelper.stop();
        }
        if (mSearchHandler != null) {
            mSearchHandler.removeCallbacksAndMessages(null);
        }

        if (mRouteHistoryDB != null) {
            mRouteHistoryDB.close();
        }
    }

    /*============================== 路线模拟参数 ==============================*/
    private void initRouteParam() {
        try {
            mRouteSpeed = Double.parseDouble(sharedPreferences.getString("setting_route_speed",
                    getResources().getString(R.string.route_speed_default)));
        } catch (NumberFormatException e) {     // GOOD: The exception is caught.
            mRouteSpeed = RouteSimulator.DEFAULT_SPEED;
        }

        try {
            mRouteSpeedFloat = Double.parseDouble(sharedPreferences.getString("setting_route_speed_float",
                    getResources().getString(R.string.route_speed_float_default)));
        } catch (NumberFormatException e) {     // GOOD: The exception is caught.
            mRouteSpeedFloat = RouteSimulator.DEFAULT_SPEED_FLOAT;
        }

        try {
            mRouteInterval = Integer.parseInt(sharedPreferences.getString("setting_route_interval",
                    getResources().getString(R.string.route_interval_default)));
        } catch (NumberFormatException e) {     // GOOD: The exception is caught.
            mRouteInterval = RouteSimulator.DEFAULT_INTERVAL;
        }

        try {
            mRouteAltitude = Double.parseDouble(sharedPreferences.getString("setting_route_altitude",
                    sharedPreferences.getString("setting_altitude",
                            getResources().getString(R.string.setting_altitude_default))));
        } catch (NumberFormatException e) {     // GOOD: The exception is caught.
            mRouteAltitude = 55.0;
        }

        /* 循环次数只在“历史路线 -> 载入”时由数据库带出，这里使用上次保存的设置 */
        try {
            mRouteLoop = Integer.parseInt(sharedPreferences.getString("setting_route_loop",
                    getResources().getString(R.string.route_loop_default)));
        } catch (NumberFormatException e) {     // GOOD: The exception is caught.
            mRouteLoop = 0;
        }
    }

    /*============================== 地图相关 ==============================*/
    private void initMap() {
        mMapView = findViewById(R.id.route_map_view);
        mMapView.showZoomControls(false);
        mBaiduMap = mMapView.getMap();
        applyMapThemeMode();
        mBaiduMap.setMyLocationEnabled(true);

        /* 在准星模式下，位置由屏幕中心的准星决定，地图本身的点击不再添加途经点 */
        mBaiduMap.setOnMapClickListener(new BaiduMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng point) {
                mSearchView.clearFocus();
                mSearchLayout.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onMapPoiClick(MapPoi poi) {
                mSearchView.clearFocus();
                mSearchLayout.setVisibility(View.INVISIBLE);
            }
        });

        /* 拖动标记调整已经添加的途经点 */
        mBaiduMap.setOnMarkerDragListener(new BaiduMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDrag(Marker marker) {

            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                int index = mPointMarkers.indexOf(marker);
                if (index < 0) {
                    return;
                }

                LatLng oldPosition = mRoutePoints.get(index);
                LatLng newPosition = marker.getPosition();
                boolean tooCloseToPrev = index > 0
                        && RouteUtils.getDistance(mRoutePoints.get(index - 1), newPosition) < RouteUtils.MIN_POINT_DISTANCE;
                boolean tooCloseToNext = index < mRoutePoints.size() - 1
                        && RouteUtils.getDistance(mRoutePoints.get(index + 1), newPosition) < RouteUtils.MIN_POINT_DISTANCE;

                if (isRouteRunning || tooCloseToPrev || tooCloseToNext) {
                    /* 拖到相邻点上会造成 0 长度线段，直接回弹；运行中禁止拖动 */
                    marker.setPosition(oldPosition);
                    if (isRouteRunning) {
                        GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_running_locked));
                    }
                    return;
                }

                mRoutePoints.set(index, newPosition);
                updateRouteLine();
            }

            @Override
            public void onMarkerDragStart(Marker marker) {

            }
        });

        /* 地图移动结束后刷新准星提示 */
        mBaiduMap.setOnMapStatusChangeListener(new BaiduMap.OnMapStatusChangeListener() {
            @Override
            public void onMapStatusChangeStart(MapStatus mapStatus) {

            }

            @Override
            public void onMapStatusChangeStart(MapStatus mapStatus, int reason) {

            }

            @Override
            public void onMapStatusChange(MapStatus mapStatus) {

            }

            @Override
            public void onMapStatusChangeFinish(MapStatus mapStatus) {
                /* 顶部提示保持简洁，不再随地图移动显示坐标 */
            }
        });
    }

    /* 屏幕中心（准星尖端）对应的地图坐标 */
    private LatLng getCenterLatLng() {
        MapStatus mapStatus = mBaiduMap.getMapStatus();

        if (mapStatus == null || mapStatus.target == null) {
            return mCurLatLng;
        }

        return mapStatus.target;
    }

    // 开启地图的定位图层（使用系统定位，避免百度定位 AK 失效时无法定位）
    private void initMapLocation() {
        mLocationHelper = new DeviceLocationHelper(getContext());
        mLocationHelper.start(location -> {
            if (mMapView == null) {// mapview 销毁后不再处理新接收的位置
                return;
            }

            double[] bd = MapUtils.wgs2bd09(location.getLongitude(), location.getLatitude());
            if (!DeviceLocationHelper.isValidLatLng(bd[1], bd[0])) {
                return;
            }

            applyCurrentLocation(new LatLng(bd[1], bd[0]),
                    location.hasAccuracy() ? location.getAccuracy() : 0.0f);

            if (isFirstLoc) {
                isFirstLoc = false;
                MapStatus.Builder builder = new MapStatus.Builder();
                builder.target(mCurLatLng).zoom(18.0f);
                mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));

                XLog.i("First route system LatLng: " + mCurLatLng);
            }
        });
    }

    /* 把 BD09 坐标写进百度地图的定位图层，并缓存为当前位置 */
    private void applyCurrentLocation(LatLng latLng, float accuracy) {
        if (latLng == null) {
            return;
        }
        mCurLatLng = latLng;
        MyLocationData locData = new MyLocationData.Builder()
                .accuracy(accuracy)
                .direction(0)
                .latitude(latLng.latitude)
                .longitude(latLng.longitude)
                .build();
        mBaiduMap.setMyLocationData(locData);
        mBaiduMap.setMyLocationConfiguration(new MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.NORMAL, true, null));
    }

    private void initMapButton() {
        View curPosBtn = this.findViewById(R.id.route_cur_position);
        curPosBtn.setOnClickListener(v -> resetMap());
        UiAnimations.pressScale(curPosBtn);

        View zoomInBtn = this.findViewById(R.id.route_zoom_in);
        zoomInBtn.setOnClickListener(v -> mBaiduMap.animateMapStatus(MapStatusUpdateFactory.zoomIn()));
        UiAnimations.pressScale(zoomInBtn);

        View zoomOutBtn = this.findViewById(R.id.route_zoom_out);
        zoomOutBtn.setOnClickListener(v -> mBaiduMap.animateMapStatus(MapStatusUpdateFactory.zoomOut()));
        UiAnimations.pressScale(zoomOutBtn);
    }

    /* 恢复当前位置：
     * 1. 路线正在运行时优先回到模拟位置；
     * 2. 有真实定位就用真实定位；
     * 3. 没有定位时向系统请求一次定位，超时后给出明确提示，不再直接报“定位失败”。 */
    private void resetMap() {
        if (isRouteRunning && mServiceBinder != null) {
            RouteSimulator simulator = mServiceBinder.getRouteSimulator();
            if (simulator != null) {
                double[] lngLat = simulator.getCurrentPosition();
                double[] bd = MapUtils.wgs2bd09(lngLat[0], lngLat[1]);
                centerMap(bd[1], bd[0]);
                return;
            }
        }

        if (mCurLatLng == null) {
            MyLocationData locationData = mBaiduMap.getLocationData();
            if (locationData != null
                    && DeviceLocationHelper.isValidLatLng(locationData.latitude, locationData.longitude)) {
                mCurLatLng = new LatLng(locationData.latitude, locationData.longitude);
            }
        }

        if (mCurLatLng != null) {
            centerMap(mCurLatLng.latitude, mCurLatLng.longitude);
            return;
        }

        GoUtils.DisplayToast(getContext(), getResources().getString(R.string.app_location_wait));
        if (mLocationHelper != null) {
            mLocationHelper.requestOnce(new DeviceLocationHelper.OnceListener() {
                @Override
                public void onLocation(@NonNull Location location) {
                    double[] bd = MapUtils.wgs2bd09(location.getLongitude(), location.getLatitude());
                    applyCurrentLocation(new LatLng(bd[1], bd[0]),
                            location.hasAccuracy() ? location.getAccuracy() : 0.0f);
                    centerMap(bd[1], bd[0]);
                }

                @Override
                public void onTimeout() {
                    GoUtils.DisplayToast(getContext(),
                            getResources().getString(R.string.app_location_unavailable));
                }
            });
        }
    }

    private void centerMap(double latitude, double longitude) {
        MapStatus.Builder builder = new MapStatus.Builder();
        builder.target(new LatLng(latitude, longitude)).zoom(18.0f);
        mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
    }

    /*============================== 路线规划 ==============================*/
    private boolean addRoutePoint(LatLng point) {
        if (isRouteRunning) {       // 模拟过程中不允许修改路线
            return false;
        }

        /* 距离过近的点会让路线出现抖动，直接忽略并给出反馈 */
        if (!mRoutePoints.isEmpty()
                && RouteUtils.getDistance(mRoutePoints.get(mRoutePoints.size() - 1), point) < RouteUtils.MIN_POINT_DISTANCE) {
            GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_point_too_close));
            return false;
        }

        mRoutePoints.add(point);
        addPointMarker(point);
        if (mAddButton != null) {
            UiAnimations.pulse(mAddButton);
        }

        updateRouteLine();

        return true;
    }

    /* 获取途经点使用的标记图标，圆形圆点，直径比路线略粗
     * 注意：地图 SDK 的 fromResource 只能加载位图，这里的矢量图需要先绘制成位图 */
    private BitmapDescriptor getPointIndicator() {
        if (mPointIndicator == null) {
            mPointIndicator = getBitmapDescriptor(R.drawable.ic_route_point);
        }

        return mPointIndicator;
    }

    private BitmapDescriptor getBitmapDescriptor(int resId) {
        Drawable drawable = ContextCompat.getDrawable(getContext(), resId);

        if (drawable == null) {
            return null;
        }
        drawable = androidx.core.graphics.drawable.DrawableCompat.wrap(drawable.mutate());
        androidx.core.graphics.drawable.DrawableCompat.setTint(drawable, ThemeHelper.colorPrimary(getContext()));

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private void addPointMarker(LatLng point) {
        MarkerOptions ooA = new MarkerOptions()
                .position(point)
                .icon(getPointIndicator())
                .draggable(true)
                .anchor(0.5f, 0.5f);
        Marker marker = (Marker) mBaiduMap.addOverlay(ooA);
        mPointMarkers.add(marker);
    }

    /* 刷新模拟位置：使用百度地图原生的蓝色定位点，与首页保持一致 */
    private void updateSimulateLocation(double lng, double lat, float direction) {
        if (mBaiduMap == null) {
            return;
        }

        MyLocationData locData = new MyLocationData.Builder()
                .accuracy(0)
                .direction(direction)       // 此处设置开发者获取到的方向信息，顺时针0-360
                .latitude(lat)
                .longitude(lng)
                .build();
        mBaiduMap.setMyLocationData(locData);

        MyLocationConfiguration configuration = new MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.NORMAL, true, null);
        mBaiduMap.setMyLocationConfiguration(configuration);
    }

    // 根据当前的途经点刷新路线，注意这里只画平滑之后的路点，与模拟时保持一致
    private void updateRouteLine() {
        if (mRoutePolyline != null) {
            mRoutePolyline.remove();
            mRoutePolyline = null;
        }
        if (mRoutePolylineCasing != null) {
            mRoutePolylineCasing.remove();
            mRoutePolylineCasing = null;
        }

        if (mRoutePoints.size() >= 2) {
            List<LatLng> smoothPoints = RouteUtils.smoothRoute(mRoutePoints);
            /* 先画白色描边再画主色线，卫星图和深色地图上也能看清 */
            PolylineOptions casing = new PolylineOptions()
                    .width((int) (9 * getResources().getDisplayMetrics().density))
                    .color(0xCCFFFFFF)
                    .points(smoothPoints);
            mRoutePolylineCasing = (Polyline) mBaiduMap.addOverlay(casing);

            PolylineOptions ooPolyline = new PolylineOptions()
                    .width((int) (5 * getResources().getDisplayMetrics().density))
                    .color(ThemeHelper.colorPrimary(getContext()))
                    .points(smoothPoints);
            mRoutePolyline = (Polyline) mBaiduMap.addOverlay(ooPolyline);
        }

        updateRouteInfo();
    }

    /* 把准星当前指向的位置加为新的途经点 */
    private void addCenterPoint() {
        if (isRouteRunning) {       // 模拟过程中不允许修改路线
            return;
        }

        LatLng center = getCenterLatLng();

        if (center == null) {
            GoUtils.DisplayToast(getContext(), getResources().getString(R.string.history_error_location));
            return;
        }

        if (!addRoutePoint(center)) {
            return;
        }

        /* 添加之后把准星提示更新成当前坐标，方便继续规划下一个点 */
    }

    private void updateRouteInfo() {
        double distance = RouteUtils.getRouteDistance(mRoutePoints);

        mPointCountText.setText(String.format(Locale.US, getResources().getString(R.string.route_point_count), mRoutePoints.size()));
        mLengthText.setText(String.format(Locale.US, getResources().getString(R.string.route_length), formatDistance(distance)));
        mSpeedText.setText(String.format(Locale.US, getResources().getString(R.string.route_speed), formatSpeed(mRouteSpeed)));
        updateActionStates();
    }

    /** 根据路线状态同步操作按钮的可用性，避免点了没反应的“空按钮”。 */
    private void updateActionStates() {
        boolean hasPoints = !mRoutePoints.isEmpty();
        boolean canEdit = !isRouteRunning;
        setActionEnabled(mUndoButton, hasPoints && canEdit);
        setActionEnabled(mClearButton, hasPoints && canEdit);
        setActionEnabled(mSaveButton, mRoutePoints.size() >= 2 && canEdit);
        setActionEnabled(mAddButton, canEdit);
        if (mStartButton != null) {
            /* 运行中必须保持可点击（用于停止），空闲时至少两个点才能开始 */
            mStartButton.setEnabled(isRouteRunning || mRoutePoints.size() >= 2);
        }
    }

    private void setActionEnabled(ImageButton button, boolean enabled) {
        if (button == null) {
            return;
        }
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.36f);
    }

    private static String formatDistance(double distance) {
        if (distance >= 1000) {
            return String.format(Locale.US, "%.2f km", distance / 1000);
        }

        return String.format(Locale.US, "%.0f m", distance);
    }

    private static String formatSpeed(double speed) {
        return String.format(Locale.US, "%.1f", speed);
    }

    /*============================== 搜索相关 ==============================*/
    private void initSearchView() {
        mSearchLayout = findViewById(R.id.route_search_linear);
        mSearchList = findViewById(R.id.route_search_list_view);

        /* 路线搜索和位置搜索一样收进顶部卡片：点击顶栏右侧的放大镜后展开 SearchView。 */
        MaterialToolbar toolbar = findViewById(R.id.route_toolbar);
        toolbar.inflateMenu(R.menu.menu_main);
        mSearchItem = toolbar.getMenu().findItem(R.id.action_search);
        if (mSearchItem == null) {
            return;
        }
        /* 菜单 action 图标按当前主题色显式着色，深色模式下不再保持黑色 */
        Drawable searchActionIcon = ContextCompat.getDrawable(getContext(), R.drawable.ic_search);
        if (searchActionIcon != null) {
            searchActionIcon = DrawableCompat.wrap(searchActionIcon.mutate());
            DrawableCompat.setTint(searchActionIcon, ThemeHelper.colorOnSurface(getContext()));
            mSearchItem.setIcon(searchActionIcon);
        }
        mSearchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                mSearchLayout.setVisibility(View.GONE);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                mSearchLayout.setVisibility(View.GONE);
                return true;
            }
        });

        mSearchView = (SearchView) mSearchItem.getActionView();
        if (mSearchView == null) {
            return;
        }
        mSearchView.setIconified(false);
        mSearchView.onActionViewExpanded();
        mSearchView.setIconifiedByDefault(true);
        mSearchView.setSubmitButtonEnabled(false);
        mSearchView.setQueryHint(getResources().getString(R.string.route_search_hint));
        GoUtils.styleToolbarSearchView(getContext(), mSearchView);

        ImageView closeButton = mSearchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                if (TextUtils.isEmpty(mSearchView.getQuery())) {
                    mSearchItem.collapseActionView();
                    return;
                }
                mSearchView.setQuery("", false);
                mSearchLayout.setVisibility(View.GONE);
            });
        }

        mSearchView.setOnCloseListener(() -> {
            mSearchLayout.setVisibility(View.GONE);
            return false;       /* 这里必须返回 false，否则需要自行处理搜索框的折叠 */
        });

        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (query != null && !query.trim().isEmpty()) {
                    cancelScheduledSearch();
                    performSearch(query.trim());
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText != null && !newText.trim().isEmpty()) {
                    scheduleSearch(newText.trim());
                } else {
                    cancelScheduledSearch();
                    mSearchLayout.setVisibility(View.GONE);
                }

                return true;
            }
        });

        /* 搜索结果只负责把地图移到该位置，途经点仍然由准星决定 */
        mSearchList.setOnItemClickListener((parent, view, position, id) -> {
            String lng = ((TextView) view.findViewById(R.id.poi_longitude)).getText().toString();
            String lat = ((TextView) view.findViewById(R.id.poi_latitude)).getText().toString();

            LatLng latLng = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
            mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(latLng, 18.0f));

            mSearchLayout.setVisibility(View.GONE);
            mSearchView.clearFocus();
            if (mSearchItem != null) {
                mSearchItem.collapseActionView();
            }
        });

        //设置搜索建议返回值监听
        mSuggestionSearch = SuggestionSearch.newInstance();
        mSuggestionSearch.setOnGetSuggestionResultListener(suggestionResult -> {
            if (mBaiduTimeoutRunnable != null) {
                mSearchHandler.removeCallbacks(mBaiduTimeoutRunnable);
                mBaiduTimeoutRunnable = null;
            }

            if (suggestionResult != null
                    && suggestionResult.error == com.baidu.mapapi.search.core.SearchResult.ERRORNO.NO_ERROR
                    && suggestionResult.getAllSuggestions() != null) {
                List<Map<String, Object>> data = getMapList(suggestionResult);
                if (!data.isEmpty()) {
                    showSearchResults(data);
                    return;
                }
            }

            if (suggestionResult != null && suggestionResult.error != null) {
                XLog.e("Baidu suggestion error: " + suggestionResult.error);
                if (suggestionResult.error == com.baidu.mapapi.search.core.SearchResult.ERRORNO.KEY_ERROR
                        || suggestionResult.error == com.baidu.mapapi.search.core.SearchResult.ERRORNO.PERMISSION_UNFINISHED
                        || suggestionResult.error == com.baidu.mapapi.search.core.SearchResult.ERRORNO.NO_ADVANCED_PERMISSION
                        || suggestionResult.error == com.baidu.mapapi.search.core.SearchResult.ERRORNO.REQUEST_ERROR) {
                    PlaceSearchHelper.markBaiduUnavailable();
                }
            }

            if (mPendingSearchKeyword != null) {
                searchWithFallback(mPendingSearchKeyword);
            }
        });
    }

    /* 输入停止 350ms 后再发起搜索，避免每敲一个字都请求一次网络 */
    private void scheduleSearch(final String keyword) {
        cancelScheduledSearch();
        mPendingSearchKeyword = keyword;
        if (mBaiduTimeoutRunnable != null) {
            mSearchHandler.removeCallbacks(mBaiduTimeoutRunnable);
            mBaiduTimeoutRunnable = null;
        }
        mSearchRunnable = () -> performSearch(keyword);
        mSearchHandler.postDelayed(mSearchRunnable, 350);
    }

    private void cancelScheduledSearch() {
        if (mSearchRunnable != null) {
            mSearchHandler.removeCallbacks(mSearchRunnable);
            mSearchRunnable = null;
        }
    }

    /* 优先使用百度地点建议；百度 AK 不可用或超时时自动切换到开源 OSM 搜索 */
    private void performSearch(final String keyword) {
        mPendingSearchKeyword = keyword;

        if (mBaiduTimeoutRunnable != null) {
            mSearchHandler.removeCallbacks(mBaiduTimeoutRunnable);
            mBaiduTimeoutRunnable = null;
        }

        if (PlaceSearchHelper.isBaiduAvailable()) {
            try {
                String city = TextUtils.isEmpty(MainActivity.mCurrentCity) ? "全国" : MainActivity.mCurrentCity;
                mSuggestionSearch.requestSuggestion(new SuggestionSearchOption()
                        .keyword(keyword)
                        .city(city));

                mBaiduTimeoutRunnable = () -> {
                    if (keyword.equals(mPendingSearchKeyword)) {
                        PlaceSearchHelper.markBaiduUnavailable();
                        searchWithFallback(keyword);
                    }
                };
                mSearchHandler.postDelayed(mBaiduTimeoutRunnable, 1800);
                return;
            } catch (Exception e) {
                XLog.e("Baidu suggestion request failed: " + e.getClass().getSimpleName());
                PlaceSearchHelper.markBaiduUnavailable();
            }
        }

        searchWithFallback(keyword);
    }

    private void searchWithFallback(final String keyword) {
        PlaceSearchHelper.searchFallback(getContext(), keyword, items -> {
            if (!keyword.equals(mPendingSearchKeyword)) {
                return;
            }
            if (items.isEmpty()) {
                mSearchLayout.setVisibility(View.GONE);
                GoUtils.DisplayToast(getContext(), getResources().getString(R.string.app_search_null));
                return;
            }

            List<Map<String, Object>> data = new ArrayList<>();
            for (PlaceSearchHelper.Poi poi : items) {
                Map<String, Object> poiItem = new HashMap<>();
                poiItem.put(MainActivity.POI_NAME, poi.name);
                poiItem.put(MainActivity.POI_ADDRESS, poi.address);
                poiItem.put(MainActivity.POI_LONGITUDE, String.valueOf(poi.longitude));
                poiItem.put(MainActivity.POI_LATITUDE, String.valueOf(poi.latitude));
                data.add(poiItem);
            }
            showSearchResults(data);
        });
    }

    private int getSearchDropdownMaxHeight() {
        int containerHeight = getHeight();
        if (containerHeight <= 0) {
            return Math.round(320f * getResources().getDisplayMetrics().density);
        }
        int density = Math.round(getResources().getDisplayMetrics().density);
        View topCard = findViewById(R.id.route_top_card);
        int top = topCard != null && topCard.getBottom() > 0 ? topCard.getBottom() + 8 * density : 96 * density;
        int bottomLimit = mRoutePanel != null && mRoutePanel.getTop() > top
                ? mRoutePanel.getTop() - 12 * density
                : containerHeight - 132 * density;
        return Math.max(150 * density, bottomLimit - top - 12 * density);
    }

    private void showSearchResults(List<Map<String, Object>> data) {
        if (mSearchList == null || data == null || data.isEmpty()) {
            return;
        }
        SimpleAdapter simAdapt = new SimpleAdapter(
                getContext(),
                data,
                R.layout.search_poi_item,
                new String[] {MainActivity.POI_NAME, MainActivity.POI_ADDRESS,
                        MainActivity.POI_LONGITUDE, MainActivity.POI_LATITUDE}, // 与下面数组元素要一一对应
                new int[] {R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude});
        mSearchList.setAdapter(simAdapt);
        GoUtils.constrainSearchListHeight(mSearchList, getSearchDropdownMaxHeight());
        mSearchLayout.setVisibility(View.VISIBLE);
    }

    @NonNull
    private static List<Map<String, Object>> getMapList(SuggestionResult suggestionResult) {
        List<Map<String, Object>> data = new ArrayList<>();
        if (suggestionResult == null || suggestionResult.getAllSuggestions() == null) {
            return data;
        }
        int retCnt = suggestionResult.getAllSuggestions().size();

        for (int i = 0; i < retCnt; i++) {
            SuggestionResult.SuggestionInfo info = suggestionResult.getAllSuggestions().get(i);
            if (info == null || info.pt == null || TextUtils.isEmpty(info.key)) {
                continue;
            }

            String city = info.city == null ? "" : info.city;
            String district = info.district == null ? "" : info.district;

            Map<String, Object> poiItem = new HashMap<>();
            poiItem.put(MainActivity.POI_NAME, info.key);
            poiItem.put(MainActivity.POI_ADDRESS, (city + " " + district).trim());
            poiItem.put(MainActivity.POI_LONGITUDE, "" + info.pt.longitude);
            poiItem.put(MainActivity.POI_LATITUDE, "" + info.pt.latitude);
            data.add(poiItem);
        }
        return data;
    }

    /*============================== 界面相关 ==============================*/
    private void initRoutePanel() {
        mPointCountText = findViewById(R.id.route_point_count);
        mLengthText = findViewById(R.id.route_length);
        mSpeedText = findViewById(R.id.route_speed_value);
        mStatusText = findViewById(R.id.route_status);
        mPauseButton = findViewById(R.id.route_pause);
        mStatusText.setText(getResources().getString(R.string.route_status_idle));

        updateRouteInfo();

        /* 撤销最后一个途经点 */
        mUndoButton = findViewById(R.id.route_undo);
        mUndoButton.setOnClickListener(v -> {
            if (isRouteRunning) {
                GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_running_locked));
                return;
            }
            if (mRoutePoints.isEmpty()) {
                return;
            }

            mRoutePoints.remove(mRoutePoints.size() - 1);

            Marker marker = mPointMarkers.remove(mPointMarkers.size() - 1);
            marker.remove();

            updateRouteLine();
            UiAnimations.tapFeedback(v);
        });
        UiAnimations.pressScale(mUndoButton);

        /* 清空整条路线 */
        mClearButton = findViewById(R.id.route_clear);
        mClearButton.setOnClickListener(v -> {
            if (isRouteRunning) {
                GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_running_locked));
                return;
            }

            new MaterialAlertDialogBuilder(getContext())
                    .setTitle(R.string.route_clear_title)
                    .setMessage(R.string.route_clear_message)
                    .setPositiveButton(R.string.common_confirm, (dialog, which) -> clearRoute())
                    .setNegativeButton(R.string.common_cancel, null)
                    .show();
            UiAnimations.tapFeedback(v);
        });
        UiAnimations.pressScale(mClearButton);

        /* 保存路线 */
        mSaveButton = findViewById(R.id.route_save);
        mSaveButton.setOnClickListener(v -> {
            if (isRouteRunning) {
                GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_running_locked));
                return;
            }
            if (mRoutePoints.size() < 2) {
                GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_error_point));
                return;
            }
            showSaveRouteDialog();
            UiAnimations.tapFeedback(v);
        });
        UiAnimations.pressScale(mSaveButton);

        /* 历史路线直接显示在面板里，点击时钟图标展开或收起 */
        ImageButton historyBtn = findViewById(R.id.route_history);
        historyBtn.setOnClickListener(v -> {
            toggleHistorySheet();
            UiAnimations.tapFeedback(v);
        });
        UiAnimations.pressScale(historyBtn);

        /* 把准星指向的位置加为途经点 */
        mAddButton = findViewById(R.id.route_add_point);
        mAddButton.setOnClickListener(v -> {
            addCenterPoint();
            UiAnimations.tapFeedback(v);
        });
        UiAnimations.pressScale(mAddButton);

        /* 速度、频率、循环、海拔统一在这里设置 */
        ImageButton speedBtn = findViewById(R.id.route_set_speed);
        speedBtn.setOnClickListener(v -> {
            showSpeedDialog();
            UiAnimations.tapFeedback(v);
        });
        UiAnimations.pressScale(speedBtn);

        UiAnimations.pressScale(mPauseButton);

        initHistoryPanel();

        /* 开始 / 停止模拟 */
        mStartButton = findViewById(R.id.route_start);
        mStartButton.setOnClickListener(v -> {
            if (isRouteRunning) {
                stopRouteMock();
            } else {
                startRouteMock();
            }
        });

        /* 暂停 / 继续 */
        mPauseButton.setOnClickListener(v -> {
            if (!isServiceBound || !isRouteRunning) {
                return;
            }

            setRoutePaused(!isRoutePaused);
            UiAnimations.tapFeedback(v);
        });

        updateActionStates();
    }

    /* 历史路线列表的初始化，列表直接显示在面板里 */
    private void initHistoryPanel() {
        mHistoryLayout = findViewById(R.id.route_history_linear);
        mHistoryNoText = findViewById(R.id.route_history_no_textview);
        mHistoryListView = findViewById(R.id.route_history_list_view);
        mHistoryHandle = findViewById(R.id.route_panel_handle);
        mRoutePanel = findViewById(R.id.route_panel);
        mHistoryHandle.setOnClickListener(v -> toggleHistorySheet());

        mHistoryListView.setLayoutManager(new LinearLayoutManager(getContext()));
        mHistoryAdapter = new HistoryAdapter();
        mHistoryListView.setAdapter(mHistoryAdapter);

        initBottomSheet();

        refreshHistoryList();
    }

    /* 历史路线适配器：用 RecyclerView 让 BottomSheet 的嵌套滚动正常工作 */
    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.route_item, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Map<String, Object> item = mHistoryRecords.get(position);
            holder.routeId.setText(String.valueOf(item.get(KEY_ID)));
            holder.name.setText(String.valueOf(item.get(KEY_NAME)));
            holder.distance.setText(String.valueOf(item.get(KEY_DISTANCE)));
            holder.time.setText(String.valueOf(item.get(KEY_TIME)));
        }

        @Override
        public int getItemCount() {
            return mHistoryRecords.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView routeId;
            final TextView name;
            final TextView distance;
            final TextView time;

            Holder(@NonNull View itemView) {
                super(itemView);
                routeId = itemView.findViewById(R.id.RouteID);
                name = itemView.findViewById(R.id.RouteText);
                distance = itemView.findViewById(R.id.RouteDistanceText);
                time = itemView.findViewById(R.id.RouteTimeText);

                itemView.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        loadHistoryRoute(String.valueOf(mHistoryRecords.get(position).get(KEY_ID)));
                    }
                });
                itemView.setOnLongClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position == RecyclerView.NO_POSITION) {
                        return false;
                    }
                    showRouteMenu(v,
                            String.valueOf(mHistoryRecords.get(position).get(KEY_ID)),
                            String.valueOf(mHistoryRecords.get(position).get(KEY_NAME)));
                    return true;
                });
            }
        }
    }

    private void showRouteMenu(View anchor, String routeID, String name) {
        PopupMenu popupMenu = new PopupMenu(getContext(), anchor);
        popupMenu.setGravity(Gravity.END | Gravity.BOTTOM);
        popupMenu.getMenu().add(getResources().getString(R.string.route_menu_load));
        popupMenu.getMenu().add(getResources().getString(R.string.route_menu_rename));
        popupMenu.getMenu().add(getResources().getString(R.string.route_menu_delete));

        popupMenu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals(getResources().getString(R.string.route_menu_load))) {
                loadHistoryRoute(routeID);
                return true;
            } else if (title.equals(getResources().getString(R.string.route_menu_rename))) {
                renameHistoryRoute(routeID, name);
                return true;
            } else if (title.equals(getResources().getString(R.string.route_menu_delete))) {
                deleteHistoryRoute(routeID);
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    /* 使用 Material BottomSheetBehavior：拖动、吸附、嵌套滚动都交给系统组件处理 */
    private void initBottomSheet() {
        mRouteSheetBehavior = BottomSheetBehavior.from(mRoutePanel);
        mRouteSheetBehavior.setHideable(false);
        mRouteSheetBehavior.setDraggable(true);
        mRouteSheetBehavior.setFitToContents(true);
        mRouteSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        /* 折叠高度只测量操作行，不能把展开后的统计区和历史区算进去 */
        View collapsed = findViewById(R.id.route_panel_peek);
        collapsed.post(() -> {
            int peekHeight = collapsed.getHeight();
            if (peekHeight > 0) {
                mRouteSheetBehavior.setPeekHeight(peekHeight);
                mRouteSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                updateMapPadding(peekHeight);
            }
        });

        mRouteSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                updateMapPadding(Math.max(0, bottomSheet.getHeight() - bottomSheet.getTop()));
                if (mSearchLayout != null && mSearchLayout.getVisibility() == View.VISIBLE) {
                    GoUtils.constrainSearchListHeight(mSearchList, getSearchDropdownMaxHeight());
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                /* 只在稳定状态更新地图控件位置，避免拖动时频繁重排 */
            }
        });
    }

    private void updateMapPadding(int bottomPadding) {
        if (mBaiduMap != null) {
            mBaiduMap.setViewPadding(0, 0, 0, bottomPadding);
        }
    }

    private void toggleHistorySheet() {
        if (mRouteSheetBehavior == null) {
            return;
        }

        if (mRouteSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            mRouteSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            return;
        }

        mRouteSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    /* 刷新历史路线列表 */
    private void refreshHistoryList() {
        mHistoryRecords = fetchHistoryRecords();

        if (mHistoryRecords.isEmpty()) {
            mHistoryListView.setVisibility(View.GONE);
            mHistoryNoText.setVisibility(View.VISIBLE);
            return;
        }

        mHistoryNoText.setVisibility(View.GONE);
        mHistoryListView.setVisibility(View.VISIBLE);

        if (mHistoryAdapter != null) {
            mHistoryAdapter.notifyDataSetChanged();
        }
    }

    /* 读取全部历史路线 */
    private List<Map<String, Object>> fetchHistoryRecords() {
        List<Map<String, Object>> data = new ArrayList<>();

        try {
            Cursor cursor = mRouteHistoryDB.query(DataBaseHistoryRoute.TABLE_NAME, null,
                    DataBaseHistoryRoute.DB_COLUMN_ID + " > ?", new String[] {"0"},
                    null, null, DataBaseHistoryRoute.DB_COLUMN_TIMESTAMP + " DESC", null);

            while (cursor.moveToNext()) {
                Map<String, Object> item = new HashMap<>();
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(DataBaseHistoryRoute.DB_COLUMN_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(DataBaseHistoryRoute.DB_COLUMN_NAME));
                String points = cursor.getString(cursor.getColumnIndexOrThrow(DataBaseHistoryRoute.DB_COLUMN_POINTS));
                String distance = cursor.getString(cursor.getColumnIndexOrThrow(DataBaseHistoryRoute.DB_COLUMN_DISTANCE));
                long timeStamp = cursor.getLong(cursor.getColumnIndexOrThrow(DataBaseHistoryRoute.DB_COLUMN_TIMESTAMP));

                String displayName = name;
                if (displayName != null && displayName.matches("^-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?\\s*→.*")) {
                    displayName = getResources().getString(R.string.route_default_prefix)
                            + " " + GoUtils.timeStamp2Date(Long.toString(timeStamp));
                }

                item.put(KEY_ID, Integer.toString(id));
                item.put(KEY_NAME, displayName);
                item.put(KEY_DISTANCE, String.format(Locale.US, "途经点 %d  ·  %.2f km",
                        RouteUtils.stringToPoints(points).size(), parseDouble(distance, 0) / 1000));
                item.put(KEY_TIME, GoUtils.timeStamp2Date(Long.toString(timeStamp)));
                data.add(item);
            }
            cursor.close();
        } catch (Exception e) {
            data.clear();
            XLog.e("ERROR: fetchHistoryRecords");
        }

        return data;
    }

    /* 把历史路线载入到当前规划中 */
    private void loadHistoryRoute(String routeID) {
        if (TextUtils.isEmpty(routeID) || isRouteRunning) {
            return;
        }

        String routePoints = queryRoutePoints(routeID);
        List<LatLng> points = RouteUtils.stringToPoints(routePoints);

        if (points.isEmpty()) {
            return;
        }

        clearRoute();

        for (LatLng point : points) {
            if (!mRoutePoints.isEmpty()
                    && RouteUtils.getDistance(mRoutePoints.get(mRoutePoints.size() - 1), point) < RouteUtils.MIN_POINT_DISTANCE) {
                continue;
            }
            mRoutePoints.add(point);
            addPointMarker(point);
        }

        double altitude = queryRouteAltitude(routeID);
        if (altitude > 0) {
            mRouteAltitude = altitude;
        }

        updateRouteLine();
        fitRouteOnMap();

        if (mRouteSheetBehavior != null) {
            mRouteSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    /* 删除指定的历史路线 */
    private void deleteHistoryRoute(String routeID) {
        try {
            DataBaseHistoryRoute.deleteHistoryRoute(mRouteHistoryDB, routeID);
            GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_delete_ok));
            refreshHistoryList();
        } catch (Exception e) {
            XLog.e("ERROR: deleteHistoryRoute");
            GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_error_delete));
        }
    }

    /* 重命名历史路线 */
    private void renameHistoryRoute(String routeID, String name) {
        final EditText input = new EditText(getContext());
        input.setText(name);
        input.setSelection(input.length());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle(getResources().getString(R.string.route_name_title));
        builder.setView(input);
        builder.setPositiveButton(R.string.common_confirm, (dialog, whichButton) -> {
            String userInput = input.getText().toString().trim();

            if (TextUtils.isEmpty(userInput)) {
                return;
            }

            try {
                ContentValues contentValues = new ContentValues();
                contentValues.put(DataBaseHistoryRoute.DB_COLUMN_NAME, userInput);
                DataBaseHistoryRoute.updateHistoryRoute(mRouteHistoryDB, routeID, contentValues);
                refreshHistoryList();
            } catch (Exception e) {
                XLog.e("ERROR: renameHistoryRoute");
            }
        });
        builder.setNegativeButton(R.string.common_cancel, null);

        builder.show();
    }

    /* 把整条路线缩放到合适的视野 */
    private void fitRouteOnMap() {
        if (mRoutePoints.size() < 2) {
            return;
        }

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();

        for (LatLng point : mRoutePoints) {
            boundsBuilder.include(point);
        }

        try {
            mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120, 120, 120, 120));
        } catch (Exception e) {
            XLog.e("ERROR: fitRouteOnMap");
        }
    }


    /* 暂停或继续路线模拟，界面状态在这里统一刷新 */
    private void setRoutePaused(boolean isPaused) {
        if (mServiceBinder == null) {
            GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_service_connecting));
            return;
        }
        if (isPaused) {
            mServiceBinder.pauseRoute();
            isRoutePaused = true;
            mPauseButton.setImageResource(R.drawable.ic_route_play);
            mPauseButton.setContentDescription(getResources().getString(R.string.route_btn_resume));
            mStatusText.setText(getResources().getString(R.string.route_status_pause));
            if (mStartButton != null) {
                mStartButton.setState(RouteProgressButton.STATE_PAUSED);
            }
            GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_pause_ok));
        } else {
            mServiceBinder.resumeRoute();
            isRoutePaused = false;
            mPauseButton.setImageResource(R.drawable.ic_route_pause);
            mPauseButton.setContentDescription(getResources().getString(R.string.route_btn_pause));
            mStatusText.setText(getResources().getString(R.string.route_status_run));
            if (mStartButton != null) {
                mStartButton.setState(RouteProgressButton.STATE_RUNNING);
            }
            GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_resume_ok));
        }
    }

    private void clearRoute() {
        for (Marker marker : mPointMarkers) {
            marker.remove();
        }

        mPointMarkers.clear();
        mRoutePoints.clear();

        updateRouteLine();
    }

    private void showSaveRouteDialog() {
        if (mRoutePoints.size() < 2) {
            GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_error_point));
            return;
        }

        final EditText input = new EditText(getContext());
        input.setText(RouteUtils.getDefaultName(mRoutePoints));
        input.setSelection(input.length());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle(getResources().getString(R.string.route_name_title));
        builder.setView(input);
        builder.setPositiveButton(R.string.common_confirm, (dialog, whichButton) -> {
            String routeName = input.getText().toString().trim();

            if (TextUtils.isEmpty(routeName)) {
                routeName = RouteUtils.getDefaultName(mRoutePoints);
            }

            if (saveRoute(routeName)) {
                GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_save_ok));
            } else {
                GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_error_save));
            }
        });
        builder.setNegativeButton(R.string.common_cancel, null);

        builder.show();
    }

    private void showSpeedDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle(getResources().getString(R.string.route_speed_title));

        View view = LayoutInflater.from(getContext()).inflate(R.layout.route_speed_dialog, null);
        builder.setView(view);

        EditText editSpeed = view.findViewById(R.id.dialog_route_speed);
        EditText editSpeedFloat = view.findViewById(R.id.dialog_route_speed_float);
        EditText editInterval = view.findViewById(R.id.dialog_route_interval);
        EditText editLoop = view.findViewById(R.id.dialog_route_loop);
        EditText editAltitude = view.findViewById(R.id.dialog_route_altitude);

        editSpeed.setText(formatSpeed(mRouteSpeed));
        editSpeedFloat.setText(String.format(Locale.US, "%.0f", mRouteSpeedFloat));
        editInterval.setText(Integer.toString(mRouteInterval));
        editLoop.setText(Integer.toString(mRouteLoop));
        editAltitude.setText(String.format(Locale.US, "%.1f", mRouteAltitude));

        builder.setPositiveButton(R.string.common_confirm, (dialog, whichButton) -> {
            try {
                mRouteSpeed = Double.parseDouble(editSpeed.getText().toString());
                mRouteSpeedFloat = Double.parseDouble(editSpeedFloat.getText().toString());
                mRouteInterval = Integer.parseInt(editInterval.getText().toString());
                mRouteLoop = Integer.parseInt(editLoop.getText().toString());
                mRouteAltitude = Double.parseDouble(editAltitude.getText().toString());
            } catch (NumberFormatException e) {     // GOOD: The exception is caught.
                GoUtils.DisplayToast(getContext(), getResources().getString(R.string.app_error_input));
                return;
            }

            mRouteSpeed = Math.max(0.1, mRouteSpeed);
            mRouteSpeedFloat = Math.max(0, Math.min(50, mRouteSpeedFloat));
            mRouteInterval = Math.max(RouteSimulator.MIN_INTERVAL, Math.min(RouteSimulator.MAX_INTERVAL, mRouteInterval));
            mRouteLoop = Math.max(0, mRouteLoop);

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("setting_route_speed", Double.toString(mRouteSpeed));
            editor.putString("setting_route_speed_float", Double.toString(mRouteSpeedFloat));
            editor.putString("setting_route_interval", Integer.toString(mRouteInterval));
            editor.putString("setting_route_loop", Integer.toString(mRouteLoop));
            editor.putString("setting_route_altitude", Double.toString(mRouteAltitude));
            editor.apply();

            updateRouteInfo();

            /* 模拟过程中修改参数立即生效 */
            if (isServiceBound && isRouteRunning && mServiceBinder != null) {
                mServiceBinder.setRouteSpeed(mRouteSpeed);
                mServiceBinder.setRouteSpeedFloat(mRouteSpeedFloat);
                mServiceBinder.setRouteInterval(mRouteInterval);
                mServiceBinder.setRouteLoop(mRouteLoop);
                mServiceBinder.setRouteAltitude(mRouteAltitude);
            }
        });
        builder.setNegativeButton(R.string.common_cancel, null);

        builder.show();
    }

    /*============================== 路线模拟 ==============================*/
    private void initServiceConnection() {
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceBinder = (ServiceGo.ServiceGoBinder) service;
                isServiceBound = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                isServiceBound = false;
            }
        };
    }

    private void startRouteMock() {
        if (mRoutePoints.size() < 2) {
            GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_error_point));
            return;
        }

        if (!GoUtils.isNetworkAvailable(getContext())) {
            GoUtils.DisplayToast(getContext(), getResources().getString(R.string.app_error_network));
            return;
        }

        if (!GoUtils.isGpsOpened(getContext())) {
            GoUtils.showEnableGpsDialog(getContext());
            return;
        }

        if (!Settings.canDrawOverlays(getContext().getApplicationContext())) {//悬浮窗权限判断
            GoUtils.showEnableFloatWindowDialog(getContext());
            XLog.e("无悬浮窗权限!");
            return;
        }

        if (!GoUtils.isAllowMockLocation(getContext())) {
            GoUtils.showEnableMockLocationDialog(getContext());
            XLog.e("无模拟位置权限!");
            return;
        }

        /* 上一次的模拟还没有停止，先把服务和绑定清理干净 */
        if (isServiceBound) {
            getContext().unbindService(mConnection);
            isServiceBound = false;
        }

        Intent serviceGoIntent = new Intent(getContext(), ServiceGo.class);
        // 路线的起点作为模拟的初始位置，之后由服务按路线推进
        double[] latLng = MapUtils.bd2wgs(mRoutePoints.get(0).longitude, mRoutePoints.get(0).latitude);
        serviceGoIntent.putExtra(MainActivity.LNG_MSG_ID, latLng[0]);
        serviceGoIntent.putExtra(MainActivity.LAT_MSG_ID, latLng[1]);
        serviceGoIntent.putExtra(MainActivity.ALT_MSG_ID, mRouteAltitude);
        serviceGoIntent.putExtra(MainActivity.ROUTE_MSG_ID, RouteUtils.pointsToString(mRoutePoints));
        serviceGoIntent.putExtra(MainActivity.ROUTE_SPEED_MSG_ID, mRouteSpeed);
        serviceGoIntent.putExtra(MainActivity.ROUTE_SPEED_FLOAT_MSG_ID, mRouteSpeedFloat);
        serviceGoIntent.putExtra(MainActivity.ROUTE_INTERVAL_MSG_ID, mRouteInterval);
        serviceGoIntent.putExtra(MainActivity.ROUTE_LOOP_MSG_ID, mRouteLoop);
        serviceGoIntent.putExtra(MainActivity.ROUTE_ALTITUDE_MSG_ID, mRouteAltitude);

        /* 路线随机偏移：与位置随机偏移共用经度/纬度最大偏移距离 */
        boolean routeRandomOffset = sharedPreferences.getBoolean("setting_route_random_offset", true);
        double randomOffsetDefault = parseDouble(getResources().getString(R.string.setting_random_offset_default), 1.0);
        double maxLatOffset = parseDouble(sharedPreferences.getString("setting_lat_max_offset",
                Double.toString(randomOffsetDefault)), randomOffsetDefault);
        double maxLonOffset = parseDouble(sharedPreferences.getString("setting_lon_max_offset",
                Double.toString(randomOffsetDefault)), randomOffsetDefault);
        serviceGoIntent.putExtra(MainActivity.ROUTE_RANDOM_OFFSET_ENABLED_MSG_ID, routeRandomOffset);
        serviceGoIntent.putExtra(MainActivity.ROUTE_RANDOM_LAT_MAX_MSG_ID, maxLatOffset);
        serviceGoIntent.putExtra(MainActivity.ROUTE_RANDOM_LON_MAX_MSG_ID, maxLonOffset);

        getContext().bindService(serviceGoIntent, mConnection, Context.BIND_AUTO_CREATE);
        isServiceBound = true;
        getContext().startForegroundService(serviceGoIntent);
        XLog.d("startForegroundService: ServiceGo route");

        /* 开始之后恢复上一次的界面刷新任务 */
        mRefreshHandler.removeCallbacks(mRefreshTask);
        mRefreshHandler.postDelayed(mRefreshTask, ROUTE_REFRESH_DELAY);

        isRouteRunning = true;
        isRoutePaused = false;
        mPauseButton.setVisibility(View.VISIBLE);
        mPauseButton.setImageResource(R.drawable.ic_route_pause);
        mStatusText.setText(getResources().getString(R.string.route_status_run));

        if (mStartButton != null) {
            mStartButton.setState(RouteProgressButton.STATE_RUNNING);
            mStartButton.setProgressImmediately(0f);
            mStartButton.setContentDescription(getResources().getString(R.string.route_btn_stop));
            UiAnimations.pulse(mStartButton);
        }
        updateActionStates();

        /* 运行期间隐藏中心蓝色定位点，只保留百度地图原生的移动定位点 */
        View centerMarker = findViewById(R.id.route_center_marker);
        if (centerMarker != null) {
            centerMarker.setVisibility(View.GONE);
        }

        GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_start_ok));

        if (GoUtils.isWifiEnabled(getContext())) {
            GoUtils.showDisableWifiDialog(getContext());
        }
    }

    private void stopRouteMock() {
        /* 先停掉界面的刷新任务，避免停止过程中再次触发刷新 */
        mRefreshHandler.removeCallbacks(mRefreshTask);

        if (isServiceBound && mServiceBinder != null) {
            mServiceBinder.stopRoute();
        }

        Intent serviceGoIntent = new Intent(getContext(), ServiceGo.class);
        getContext().stopService(serviceGoIntent);

        resetRouteUiIdle(false);

        GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_stop_ok));
    }

    /* 模拟过程中定时刷新进度，服务结束时同步界面状态 */
    private final Runnable mRefreshTask = new Runnable() {
        @Override
        public void run() {
            if (!isRouteRunning) {
                return;
            }

            /* 服务还在连接中，等下一次再判断，避免把刚启动的模拟误判成已经结束 */
            if (!isServiceBound || mServiceBinder == null) {
                mRefreshHandler.postDelayed(this, ROUTE_REFRESH_DELAY);
                return;
            }

            if (!mServiceBinder.isRouteRunning()) {
                /* 路线已经走完，服务会自动回到摇杆模式 */
                resetRouteUiIdle(true);

                GoUtils.DisplayToast(getContext(), getResources().getString(R.string.route_finish));
                return;
            }

            RouteSimulator simulator = mServiceBinder.getRouteSimulator();

            /* 通知栏里也可以暂停/继续，这里同步一下界面状态 */
            boolean isServicePaused = mServiceBinder.isRoutePaused();

            if (isServicePaused != isRoutePaused) {
                setRoutePaused(isServicePaused);
            }

            if (simulator != null) {
                if (mStartButton != null) {
                    mStartButton.setProgress((float) simulator.getProgress());
                }
                mSpeedText.setText(String.format(Locale.US, getResources().getString(R.string.route_speed),
                        formatSpeed(simulator.getCurrentSpeed())));

                /* 把模拟位置画到地图上，箭头朝向跟随运动方向（指向下一个途经点） */
                double[] lngLat = simulator.getCurrentPosition();
                double[] bdLngLat = MapUtils.wgs2bd09(lngLat[0], lngLat[1]);
                updateSimulateLocation(bdLngLat[0], bdLngLat[1], simulator.getCurrentBearing());
            }

            mRefreshHandler.postDelayed(this, ROUTE_REFRESH_DELAY);
        }
    };

    /*============================== 历史路线 ==============================*/
    private void initRouteDatabase() {
        try {
            DataBaseHistoryRoute dbRoute = new DataBaseHistoryRoute(getContext().getApplicationContext());
            mRouteHistoryDB = dbRoute.getWritableDatabase();
        } catch (Exception e) {
            XLog.e("ERROR: sqlite init error");
        }
    }

    private boolean saveRoute(String routeName) {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(DataBaseHistoryRoute.DB_COLUMN_NAME, routeName);
            contentValues.put(DataBaseHistoryRoute.DB_COLUMN_POINTS, RouteUtils.pointsToString(mRoutePoints));
            contentValues.put(DataBaseHistoryRoute.DB_COLUMN_DISTANCE, Double.toString(RouteUtils.getRouteDistance(mRoutePoints)));
            contentValues.put(DataBaseHistoryRoute.DB_COLUMN_ALTITUDE, Double.toString(mRouteAltitude));
            contentValues.put(DataBaseHistoryRoute.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

            DataBaseHistoryRoute.saveHistoryRoute(mRouteHistoryDB, contentValues);
            return true;
        } catch (Exception e) {
            XLog.e("ERROR: saveRoute");
            return false;
        }
    }


    private String queryRoutePoints(String routeID) {
        String points = null;

        try {
            Cursor cursor = mRouteHistoryDB.query(DataBaseHistoryRoute.TABLE_NAME, null,
                    DataBaseHistoryRoute.DB_COLUMN_ID + " = ?", new String[] {routeID},
                    null, null, null);

            if (cursor.moveToNext()) {
                points = cursor.getString(cursor.getColumnIndexOrThrow(DataBaseHistoryRoute.DB_COLUMN_POINTS));
            }
            cursor.close();
        } catch (Exception e) {
            XLog.e("ERROR: queryRoutePoints");
        }

        return points;
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private double queryRouteAltitude(String routeID) {
        double altitude = -1;

        try {
            Cursor cursor = mRouteHistoryDB.query(DataBaseHistoryRoute.TABLE_NAME,
                    new String[] {DataBaseHistoryRoute.DB_COLUMN_ALTITUDE},
                    DataBaseHistoryRoute.DB_COLUMN_ID + " = ?", new String[] {routeID},
                    null, null, null);

            if (cursor.moveToNext()) {
                altitude = cursor.getDouble(0);
            }
            cursor.close();
        } catch (Exception e) {
            XLog.e("ERROR: queryRouteAltitude");
        }

        return altitude;
    }
}
