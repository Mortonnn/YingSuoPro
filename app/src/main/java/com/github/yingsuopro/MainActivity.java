package com.github.yingsuopro;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceManager;

import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BMThemeMode;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.InfoWindow;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.baidu.mapapi.search.sug.SuggestionResult;
import com.baidu.mapapi.search.sug.SuggestionSearch;
import com.baidu.mapapi.search.sug.SuggestionSearchOption;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.yingsuopro.service.ServiceGo;
import com.github.yingsuopro.database.DataBaseHistoryLocation;
import com.github.yingsuopro.database.DataBaseHistorySearch;
import com.github.yingsuopro.utils.DeviceLocationHelper;
import com.github.yingsuopro.utils.PlaceSearchHelper;
import com.github.yingsuopro.utils.ShareUtils;
import com.github.yingsuopro.utils.GoUtils;
import com.github.yingsuopro.utils.MapUtils;
import com.github.yingsuopro.utils.ThemeHelper;
import com.github.yingsuopro.utils.UiAnimations;

import com.elvishew.xlog.XLog;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends BaseActivity implements SensorEventListener {
    /* 对外 */
    public static final String LAT_MSG_ID = "LAT_VALUE";
    public static final String LNG_MSG_ID = "LNG_VALUE";
    public static final String ALT_MSG_ID = "ALT_VALUE";

    public static final String POI_NAME = "POI_NAME";
    public static final String POI_ADDRESS = "POI_ADDRESS";
    public static final String POI_LONGITUDE = "POI_LONGITUDE";
    public static final String POI_LATITUDE = "POI_LATITUDE";

    /* 路线模拟相关的参数，由 RoutePageView 传入 ServiceGo */
    public static final String ROUTE_MSG_ID = "ROUTE_POINTS";
    public static final String ROUTE_SPEED_MSG_ID = "ROUTE_SPEED";
    public static final String ROUTE_SPEED_FLOAT_MSG_ID = "ROUTE_SPEED_FLOAT";
    public static final String ROUTE_INTERVAL_MSG_ID = "ROUTE_INTERVAL";
    public static final String ROUTE_LOOP_MSG_ID = "ROUTE_LOOP";
    public static final String ROUTE_ALTITUDE_MSG_ID = "ROUTE_ALTITUDE";
    public static final String ROUTE_RANDOM_OFFSET_ENABLED_MSG_ID = "ROUTE_RANDOM_OFFSET_ENABLED";
    public static final String ROUTE_RANDOM_LAT_MAX_MSG_ID = "ROUTE_RANDOM_LAT_MAX";
    public static final String ROUTE_RANDOM_LON_MAX_MSG_ID = "ROUTE_RANDOM_LON_MAX";

    private OkHttpClient mOkHttpClient;
    private SharedPreferences sharedPreferences;

    /*============================== 主界面地图 相关 ==============================*/
    /************** 地图 *****************/
    public final static BitmapDescriptor mMapIndicator = BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding_blue);
    public static String mCurrentCity = null;
    private MapView mMapView;
    private static BaiduMap mBaiduMap = null;
    private static LatLng mMarkLatLngMap = new LatLng(34.265678, 108.953543); // 当前标记的地图点（默认西安钟楼 BD09）
    private static String mMarkName = null;
    private GeoCoder mGeoCoder;
    private SensorManager mSensorManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorMagnetic;
    private float[] mAccValues = new float[3];//加速度传感器数据
    private float[] mMagValues = new float[3];//地磁传感器数据
    private final float[] mR = new float[9];//旋转矩阵，用来保存磁场和加速度的数据
    private final float[] mDirectionValues = new float[3];//模拟方向传感器的数据（原始数据为弧度）
    /************** 定位 *****************/
    private DeviceLocationHelper mLocationHelper = null;
    private double mCurrentLat = 0.0;       // 当前位置的百度纬度
    private double mCurrentLon = 0.0;       // 当前位置的百度经度
    private float mCurrentDirection = 0.0f;
    private boolean isFirstLoc = true; // 是否首次定位
    private boolean isMockServStart = false;
    private boolean isServiceBound = false;
    private ServiceGo.ServiceGoBinder mServiceBinder;
    private ServiceConnection mConnection;
    private ExtendedFloatingActionButton mButtonStart;
    private com.github.yingsuopro.ui.LiquidGlassSegmentedControl mBottomDock;
    private View mHomePageContainer;
    private FrameLayout mRoutePageContainer;
    private FrameLayout mMenuPageContainer;
    private RoutePageView mRoutePageView;
    private MenuPageView mMenuPageView;
    private int mCurrentPage = 0;
    private int mLastDockPage = 0;
    private int mStatusBarInset = 0;
    private int mNavigationBarInset = 0;
    /*============================== 历史记录 相关 ==============================*/
    private SQLiteDatabase mLocationHistoryDB;
    private SQLiteDatabase mSearchHistoryDB;
    /*============================== SearchView 相关 ==============================*/
    private SearchView searchView;
    private ListView mSearchList;
    private LinearLayout mSearchLayout;
    private ListView mSearchHistoryList;
    private LinearLayout mHistoryLayout;
    private MenuItem searchItem;
    private SuggestionSearch mSuggestionSearch;
    private final Handler mSearchHandler = new Handler(Looper.getMainLooper());
    private Runnable mSearchRunnable;
    private Runnable mBaiduTimeoutRunnable;
    private String mPendingSearchKeyword = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupEdgeToEdge();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.mode_position);
            getSupportActionBar().setSubtitle(R.string.app_name);
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        toolbar.post(() -> bindMenuTrigger(toolbar));

        XLog.i("MainActivity: onCreate");

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mOkHttpClient = new OkHttpClient();

        initPageContainers();

        initMap();

        initMapLocation();

        initMapButton();

        initModeTabs();
        showPage(0);

        initGoBtn();

        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceBinder = (ServiceGo.ServiceGoBinder) service;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                isServiceBound = false;
                mServiceBinder = null;
            }
        };

        if (ServiceGo.isRunning()) {
            bindService(new Intent(MainActivity.this, ServiceGo.class), mConnection, BIND_AUTO_CREATE);
            isServiceBound = true;
            isMockServStart = !ServiceGo.isRouteRunning();
        }

        initStoreHistory();

        initSearchView();
    }

    @Override
    protected void onPause() {
        XLog.i("MainActivity: onPause");
        mMapView.onPause();
        if (mRoutePageView != null) {
            mRoutePageView.onPagePause();
        }
        mSensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        XLog.i("MainActivity: onResume");
        mMapView.onResume();
        mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);
        applyMapStyle();
        if (mBottomDock != null) {
            mBottomDock.setSelectedIndex(mCurrentPage, false);
        }
        applyPageVisibility();
        if (mRoutePageView != null && mCurrentPage == 1) {
            mRoutePageView.onPageResume();
        }
        isMockServStart = ServiceGo.isRunning() && !ServiceGo.isRouteRunning();
        if (ServiceGo.isRunning() && !isServiceBound) {
            bindService(new Intent(MainActivity.this, ServiceGo.class), mConnection, BIND_AUTO_CREATE);
            isServiceBound = true;
        }
        setToolbarModeTitle(mCurrentPage == 0 ? R.string.mode_position
                : mCurrentPage == 1 ? R.string.mode_route : R.string.dock_menu);
        refreshStartFab();
        super.onResume();
    }

    @Override
    protected void onStop() {
        XLog.i("MainActivity: onStop");
        //取消注册传感器监听
        mSensorManager.unregisterListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        XLog.i("MainActivity: onDestroy");

        if (isServiceBound) {
            try {
                unbindService(mConnection); // 解绑服务，避免内存泄漏
            } catch (IllegalArgumentException ignored) {
            }
            isServiceBound = false;
        }
        if (isFinishing() && isMockServStart) {
            Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
            stopService(serviceGoIntent);
        }

        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }

        // 退出时停止系统定位
        if (mLocationHelper != null) {
            mLocationHelper.stop();
        }
        if (mSearchHandler != null) {
            mSearchHandler.removeCallbacksAndMessages(null);
        }
        // 关闭定位图层
        if (mBaiduMap != null) {
            mBaiduMap.setMyLocationEnabled(false);
        }
        if (mMapView != null) {
            mMapView.onDestroy();
        }

        //poi search destroy
        if (mSuggestionSearch != null) {
            mSuggestionSearch.destroy();
        }

        if (mRoutePageView != null) {
            mRoutePageView.release();
        }

        //close db
        if (mLocationHistoryDB != null) {
            mLocationHistoryDB.close();
        }
        if (mSearchHistoryDB != null) {
            mSearchHistoryDB.close();
        }

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mCurrentPage == 2) {
            showPage(mLastDockPage);
            return;
        }
        if (mCurrentPage == 1) {
            if (mRoutePageView != null && mRoutePageView.handleBackPressed()) {
                return;
            }
            selectDockPage(0);
            return;
        }
        if (mCurrentPage != 0) {
            selectDockPage(0);
            return;
        }
        if (mSearchLayout != null && mSearchLayout.getVisibility() == View.VISIBLE) {
            mSearchLayout.setVisibility(View.GONE);
            return;
        }
        if (mHistoryLayout != null && mHistoryLayout.getVisibility() == View.VISIBLE) {
            mHistoryLayout.setVisibility(View.GONE);
            return;
        }
        if (searchItem != null && searchItem.isActionViewExpanded()) {
            searchItem.collapseActionView();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        //找到searchView
        searchItem = menu.findItem(R.id.action_search);
        /* 菜单 action 图标不会被 menu XML 的 iconTint 稳定着色，这里按当前主题色显式设置 */
        Drawable searchActionIcon = ContextCompat.getDrawable(this, R.drawable.ic_search);
        if (searchActionIcon != null) {
            searchActionIcon = DrawableCompat.wrap(searchActionIcon.mutate());
            DrawableCompat.setTint(searchActionIcon, ThemeHelper.colorOnSurface(this));
            searchItem.setIcon(searchActionIcon);
        }
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                mSearchLayout.setVisibility(View.GONE);
                mHistoryLayout.setVisibility(View.GONE);
                return true;  // Return true to collapse action view
            }
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                mSearchLayout.setVisibility(View.GONE);
                showSearchHistoryIfAvailable();
                return true;  // Return true to expand action view
            }
        });

        searchView = (SearchView) searchItem.getActionView();
        searchView.setIconified(false);// 设置searchView处于展开状态
        searchView.onActionViewExpanded();// 当展开无输入内容的时候，没有关闭的图标
        searchView.setIconifiedByDefault(true);//默认为true在框内，设置false则在框外
        searchView.setSubmitButtonEnabled(false);//显示提交按钮
        GoUtils.styleToolbarSearchView(this, searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (query != null && !query.trim().isEmpty()) {
                    String keyword = query.trim();
                    cancelScheduledSearch();
                    performSearch(keyword);
                    saveSearchKeyword(keyword);
                    mBaiduMap.clear();
                    mSearchLayout.setVisibility(View.GONE);
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                //当输入框内容改变的时候回调
                //搜索历史置为不可见
                mHistoryLayout.setVisibility(View.GONE);

                if (newText != null && !newText.trim().isEmpty()) {
                    scheduleSearch(newText.trim());
                } else {
                    cancelScheduledSearch();
                    mSearchLayout.setVisibility(View.GONE);
                }

                return true;
            }
        });

        // 搜索框的清除按钮(该按钮属于安卓系统图标)
        ImageView closeButton = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                if (TextUtils.isEmpty(searchView.getQuery())) {
                    searchItem.collapseActionView();
                    return;
                }
                searchView.setQuery("", false);
                mSearchLayout.setVisibility(View.GONE);
                showSearchHistoryIfAvailable();
            });
        }

        return true;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            mAccValues = sensorEvent.values;
        }
        else if(sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
            mMagValues = sensorEvent.values;
        }

        SensorManager.getRotationMatrix(mR, null, mAccValues, mMagValues);
        SensorManager.getOrientation(mR, mDirectionValues);
        mCurrentDirection = (float) Math.toDegrees(mDirectionValues[0]);    // 弧度转角度
        if (mCurrentDirection < 0) {    // 由 -180 ~ + 180 转为 0 ~ 360
            mCurrentDirection += 360;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    /*============================== 全面屏 ==============================*/
    private void setupEdgeToEdge() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);

        View root = findViewById(R.id.page_container);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            mStatusBarInset = bars.top;
            mNavigationBarInset = bars.bottom;
            applyPageInsets();
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void applyPageInsets() {
        applyTopMargin(findViewById(R.id.app_bar_card), 8f, mStatusBarInset);
        applyTopMargin(findViewById(R.id.search_linear), 84f, mStatusBarInset);
        applyTopMargin(findViewById(R.id.search_history_linear), 84f, mStatusBarInset);
        applyTopMargin(findViewById(R.id.map_control_stack), 84f, mStatusBarInset);
        applyBottomMargin(findViewById(R.id.faBtnStart), 92f, mNavigationBarInset);
        applyBottomMargin(findViewById(R.id.app_statement), 86f, mNavigationBarInset);
        applyBottomMargin(findViewById(R.id.bottomDock), 18f, mNavigationBarInset);

        View routeContainer = findViewById(R.id.route_page_container);
        if (routeContainer != null) {
            routeContainer.setPadding(0, 0, 0, dp(86f) + mNavigationBarInset);
        }
        if (mRoutePageView != null) {
            mRoutePageView.applyTopInset(mStatusBarInset);
        }
        if (mMenuPageView != null) {
            mMenuPageView.applyWindowInsets(mStatusBarInset, mNavigationBarInset);
        }
    }

    private void applyTopMargin(View view, float baseDp, int extraPx) {
        if (view == null || !(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        int target = dp(baseDp) + extraPx;
        if (params.topMargin != target) {
            params.topMargin = target;
            view.setLayoutParams(params);
        }
    }

    private void applyBottomMargin(View view, float baseDp, int extraPx) {
        if (view == null || !(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        int target = dp(baseDp) + extraPx;
        if (params.bottomMargin != target) {
            params.bottomMargin = target;
            view.setLayoutParams(params);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /*============================== Dock 页面 ==============================*/
    private void initPageContainers() {
        mHomePageContainer = findViewById(R.id.home_page_container);
        mRoutePageContainer = findViewById(R.id.route_page_container);
        mMenuPageContainer = findViewById(R.id.menu_page_container);
    }

    private void ensureRoutePage() {
        if (mRoutePageView == null) {
            mRoutePageView = new RoutePageView(this);
            mRoutePageView.setOnBackListener(() -> selectDockPage(0));
            mRoutePageView.setOnMenuClickListener(this::showMenuPage);
            mRoutePageContainer.addView(mRoutePageView,
                    new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT));
            mRoutePageView.applyTopInset(mStatusBarInset);
        }
    }

    private void ensureMenuPage() {
        if (mMenuPageView == null) {
            mMenuPageView = new MenuPageView(this);
            mMenuPageView.setOnMenuActionListener(this::handleMenuAction);
            mMenuPageContainer.addView(mMenuPageView,
                    new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT));
            mMenuPageView.applyWindowInsets(mStatusBarInset, mNavigationBarInset);
        }
    }

    private void handleMenuAction(int action) {
        if (action == MenuPageView.ACTION_SAVED_LOCATIONS) {
            startActivity(new Intent(MainActivity.this, HistoryActivity.class));
        } else if (action == MenuPageView.ACTION_SETTINGS) {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        } else if (action == MenuPageView.ACTION_DEVELOPER) {
            if (!GoUtils.isDeveloperOptionsEnabled(this)) {
                GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_dev));
            } else {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                } catch (Exception e) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_dev));
                }
            }
        } else if (action == MenuPageView.ACTION_FEEDBACK) {
            File file = new File(getExternalFilesDir("Logs"), GoApplication.LOG_FILE_NAME);
            ShareUtils.shareFile(this, file, getString(R.string.nav_menu_feedback));
        } else if (action == MenuPageView.ACTION_ABOUT_SELF) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.about_self_url))));
        } else if (action == MenuPageView.ACTION_ABOUT_UPSTREAM) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.about_upstream_url))));
        }
    }

    /*============================== 主界面地图 相关 ==============================*/
    private void initMap() {
        // 地图初始化
        mMapView = findViewById(R.id.bdMapView);
        mMapView.showZoomControls(false);
        mBaiduMap = mMapView.getMap();
        applyMapStyle();
        mBaiduMap.setMyLocationEnabled(true);
        mBaiduMap.setOnMapTouchListener(event -> {

        });
        mBaiduMap.setOnMapClickListener(new BaiduMap.OnMapClickListener() {
            /**
             * 单击地图
             */
            @Override
            public void onMapClick(LatLng point) {
                hideSearchOverlays();
                mMarkLatLngMap = point;
                markMap();
            }
            /**
             * 单击地图中的POI点
             */
            @Override
            public void onMapPoiClick(MapPoi poi) {
                hideSearchOverlays();
                mMarkLatLngMap = poi.getPosition();
                markMap();
            }
        });
        mBaiduMap.setOnMapLongClickListener(new BaiduMap.OnMapLongClickListener() {
            /**
             * 长按地图
             */
            @Override
            public void onMapLongClick(LatLng point) {
                mMarkLatLngMap = point;
                markMap();
                mGeoCoder.reverseGeoCode(new ReverseGeoCodeOption().location(point));
            }
        });
        mBaiduMap.setOnMapDoubleClickListener(new BaiduMap.OnMapDoubleClickListener() {
            /**
             * 双击地图
             */
            @Override
            public void onMapDoubleClick(LatLng point) {
                mBaiduMap.animateMapStatus(MapStatusUpdateFactory.zoomIn());
            }
        });

        View poiView = View.inflate(MainActivity.this, R.layout.location_poi_info, null);
        TextView poiAddress = poiView.findViewById(R.id.poi_address);
        TextView poiLongitude = poiView.findViewById(R.id.poi_longitude);
        TextView poiLatitude = poiView.findViewById(R.id.poi_latitude);
        ImageButton ibSave = poiView.findViewById(R.id.poi_save);
        ibSave.setOnClickListener(v -> {
            recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_location_save));
        });
        ImageButton ibCopy = poiView.findViewById(R.id.poi_copy);
        ibCopy.setOnClickListener(v -> {
            //获取剪贴板管理器：
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            // 创建普通字符型ClipData
            ClipData mClipData = ClipData.newPlainText("Label", mMarkLatLngMap.toString());
            // 将 ClipData内容放到系统剪贴板里。
            cm.setPrimaryClip(mClipData);

            GoUtils.DisplayToast(this,  getResources().getString(R.string.app_location_copy));
        });
        ImageButton ibShare = poiView.findViewById(R.id.poi_share);
        ibShare.setOnClickListener(v -> ShareUtils.shareText(MainActivity.this, "分享位置", poiLongitude.getText()+","+poiLatitude.getText()));
        ImageButton ibFly = poiView.findViewById(R.id.poi_fly);
        ibFly.setOnClickListener(this::doGoLocation);
        mGeoCoder = GeoCoder.newInstance();
        mGeoCoder.setOnGetGeoCodeResultListener(new OnGetGeoCoderResultListener() {
            @Override
            public void onGetGeoCodeResult(GeoCodeResult geoCodeResult) {
                XLog.i(geoCodeResult.getLocation());
            }

            @Override
            public void onGetReverseGeoCodeResult(ReverseGeoCodeResult reverseGeoCodeResult) {
                if (reverseGeoCodeResult == null || reverseGeoCodeResult.error != SearchResult.ERRORNO.NO_ERROR) {
                    XLog.i("逆地理位置失败!");
                } else {
                    mMarkName = String.valueOf(reverseGeoCodeResult.getAddress());
                    poiLatitude.setText(String.valueOf(reverseGeoCodeResult.getLocation().latitude));
                    poiLongitude.setText(String.valueOf(reverseGeoCodeResult.getLocation().longitude));
                    poiAddress.setText(reverseGeoCodeResult.getAddress());
                    final InfoWindow mInfoWindow = new InfoWindow(poiView, reverseGeoCodeResult.getLocation(), -100);
                    mBaiduMap.showInfoWindow(mInfoWindow);
                }
            }
        });

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);// 获取传感器管理服务
        if (mSensorManager != null) {
            mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (mSensorAccelerometer != null) {
                mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
            }
            mSensorMagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if (mSensorMagnetic != null) {
                mSensorManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    //开启地图的定位图层（使用系统定位，避免百度定位 AK 失效时无法获取当前位置）
    private void initMapLocation() {
        mLocationHelper = new DeviceLocationHelper(this);
        mLocationHelper.start(location -> {
            if (mMapView == null || mBaiduMap == null) {
                return;
            }

            double[] bd = MapUtils.wgs2bd09(location.getLongitude(), location.getLatitude());
            applyCurrentLocation(bd[0], bd[1], location.hasAccuracy() ? location.getAccuracy() : 0.0f);

            if (TextUtils.isEmpty(mCurrentCity)) {
                DeviceLocationHelper.resolveCity(MainActivity.this,
                        location.getLatitude(), location.getLongitude(), city -> mCurrentCity = city);
            }

            if (isFirstLoc) {
                isFirstLoc = false;
                mMarkLatLngMap = new LatLng(bd[1], bd[0]);
                MapStatus.Builder builder = new MapStatus.Builder();
                builder.target(mMarkLatLngMap).zoom(18.0f);
                mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
                XLog.i("First system LatLng: " + mMarkLatLngMap);
            }
        });
    }

    /* 把 BD09 坐标写进百度地图的定位图层，并缓存为当前位置 */
    private void applyCurrentLocation(double longitude, double latitude, float accuracy) {
        mCurrentLon = longitude;
        mCurrentLat = latitude;

        MyLocationData locData = new MyLocationData.Builder()
                .accuracy(accuracy)
                .direction(mCurrentDirection)       // 此处设置开发者获取到的方向信息，顺时针0-360
                .latitude(latitude)
                .longitude(longitude)
                .build();
        mBaiduMap.setMyLocationData(locData);

        MyLocationConfiguration configuration = new MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.NORMAL, true, null);
        mBaiduMap.setMyLocationConfiguration(configuration);
    }

    /** 底部 Dock：位置模拟 / 路线模拟 / 关于（菜单页）。 */
    private void initModeTabs() {
        mBottomDock = findViewById(R.id.bottomDock);
        if (mBottomDock == null) {
            return;
        }
        mBottomDock.setIconSizeDp(24);
        mBottomDock.setSegmentHeightDp(56);
        mBottomDock.configure(new String[]{
                        getString(R.string.tab_position_mock),
                        getString(R.string.tab_route_mock),
                        getString(R.string.setting_group_about)},
                new int[]{R.drawable.ic_home_position, R.drawable.ic_route, R.drawable.ic_info}, 0);
        mBottomDock.setShowLabels(false);
        mBottomDock.setOnSelectionChangedListener(this::showPage);
    }

    /** 点击 Dock 项时切换页面；Dock 本身始终固定在底部。 */
    private void selectDockPage(int index) {
        if (mBottomDock == null) {
            return;
        }
        if (mBottomDock.getSelectedIndex() != index) {
            mBottomDock.setSelectedIndex(index, true);
        }
        showPage(index);
    }

    private void showPage(int index) {
        if (index < 0 || index > 2) {
            return;
        }
        if (mRoutePageView != null && mCurrentPage == 1 && index != 1) {
            mRoutePageView.onPagePause();
        }

        if (index == 1) {
            ensureRoutePage();
        } else if (index == 2) {
            ensureMenuPage();
        }

        if (index <= 1) {
            mLastDockPage = index;
        }
        mCurrentPage = index;
        if (mBottomDock != null && mBottomDock.getSelectedIndex() != index) {
            mBottomDock.setSelectedIndex(index, false);
        }
        applyPageVisibility();

        if (index == 1 && mRoutePageView != null) {
            mRoutePageView.onPageResume();
        } else if (index == 0) {
            isMockServStart = ServiceGo.isRunning() && !ServiceGo.isRouteRunning();
            refreshStartFab();
        }

        /* 顶栏卡片和页面内容一起切换，不做整页上浮动画，避免切换时顶部框体跟着晃动 */
        setToolbarModeTitle(index == 0 ? R.string.mode_position
                : index == 1 ? R.string.mode_route : R.string.app_name);
    }

    private void applyPageVisibility() {
        if (mRoutePageContainer == null || mMenuPageContainer == null) {
            return;
        }
        mRoutePageContainer.setVisibility(mCurrentPage == 1 ? View.VISIBLE : View.GONE);
        mMenuPageContainer.setVisibility(mCurrentPage == 2 ? View.VISIBLE : View.GONE);
    }

    private void showMenuPage() {
        mLastDockPage = mCurrentPage == 1 ? 1 : 0;
        showPage(2);
    }

    private void bindMenuTrigger(Toolbar toolbar) {
        if (toolbar == null) {
            return;
        }
        TextView subtitle = findToolbarText(toolbar, getString(R.string.app_name));
        if (subtitle != null) {
            subtitle.setClickable(true);
            subtitle.setFocusable(true);
            subtitle.setOnClickListener(v -> showMenuPage());
        }
    }

    private TextView findToolbarText(Toolbar toolbar, String text) {
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

    private void setToolbarModeTitle(int titleRes) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(titleRes);
            getSupportActionBar().setSubtitle(R.string.app_name);
        }
    }

    /** 地图样式统一由设置页维护，首页与路线页共用同一个偏好项。 */
    private void applyMapStyle() {
        if (mBaiduMap == null || sharedPreferences == null) {
            return;
        }
        String style = sharedPreferences.getString("setting_map_style", "normal");
        boolean satellite = "satellite".equals(style);
        boolean night = ThemeHelper.isMapNightMode(this);
        BMThemeMode mode;
        if (satellite) {
            mode = night ? BMThemeMode.MapThemeModeDarkSatellite : BMThemeMode.MapThemeModeSatellite;
        } else {
            mode = night ? BMThemeMode.MapThemeModeDark : BMThemeMode.MapThemeModeDefault;
        }
        mBaiduMap.setMapThemeMode(mode);
    }

    //地图上各按键的监听
    private void initMapButton() {
        View savedBtn = this.findViewById(R.id.saved_positions);
        savedBtn.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, HistoryActivity.class)));
        UiAnimations.pressScale(savedBtn);

        View saveCurrentBtn = this.findViewById(R.id.save_current);
        saveCurrentBtn.setOnClickListener(v -> {
            if (mMarkLatLngMap == null) {
                GoUtils.DisplayToast(this, getString(R.string.app_error_location));
                return;
            }
            recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
            GoUtils.DisplayToast(this, getString(R.string.app_location_save));
        });
        UiAnimations.pressScale(saveCurrentBtn);

        View curPosBtn = this.findViewById(R.id.cur_position);
        curPosBtn.setOnClickListener(v -> resetMap());
        UiAnimations.pressScale(curPosBtn);

        View zoomInBtn = this.findViewById(R.id.zoom_in);
        zoomInBtn.setOnClickListener(v -> mBaiduMap.animateMapStatus(MapStatusUpdateFactory.zoomIn()));
        UiAnimations.pressScale(zoomInBtn);

        View zoomOutBtn = this.findViewById(R.id.zoom_out);
        zoomOutBtn.setOnClickListener(v -> mBaiduMap.animateMapStatus(MapStatusUpdateFactory.zoomOut()));
        UiAnimations.pressScale(zoomOutBtn);

        View inputPosBtn = this.findViewById(R.id.input_pos);
        UiAnimations.pressScale(inputPosBtn);
        inputPosBtn.setOnClickListener(v -> {
            AlertDialog dialog;
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(MainActivity.this);
            builder.setTitle(R.string.input_position_title);
            View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.location_input, null);
            builder.setView(view);
            dialog = builder.show();

            EditText dialog_lng = view.findViewById(R.id.joystick_longitude);
            EditText dialog_lat = view.findViewById(R.id.joystick_latitude);
            com.github.yingsuopro.ui.LiquidGlassSegmentedControl posTypeSwitch = view.findViewById(R.id.pos_type_switch);
            posTypeSwitch.configure(new String[]{
                            getString(R.string.input_position_baidu),
                            getString(R.string.input_position_gps)},
                    null, 0);
            posTypeSwitch.setSegmentTextSize(13f);

            Button btnGo = view.findViewById(R.id.input_position_ok);
            btnGo.setOnClickListener(v2 -> {
                String dialog_lng_str = dialog_lng.getText().toString().trim();
                String dialog_lat_str = dialog_lat.getText().toString().trim();

                if (TextUtils.isEmpty(dialog_lng_str) || TextUtils.isEmpty(dialog_lat_str)) {
                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_input));
                    return;
                }
                double dialog_lng_double;
                double dialog_lat_double;
                try {
                    dialog_lng_double = Double.parseDouble(dialog_lng_str);
                    dialog_lat_double = Double.parseDouble(dialog_lat_str);
                } catch (NumberFormatException e) {
                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_input));
                    return;
                }

                if (dialog_lng_double > 180.0 || dialog_lng_double < -180.0) {
                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_longitude));
                } else if (dialog_lat_double > 90.0 || dialog_lat_double < -90.0) {
                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_latitude));
                } else {
                    if (posTypeSwitch.getSelectedIndex() == 0) {
                        mMarkLatLngMap = new LatLng(dialog_lat_double, dialog_lng_double);
                    } else {
                        double[] bdLonLat = MapUtils.wgs2bd09(dialog_lng_double, dialog_lat_double);
                        mMarkLatLngMap = new LatLng(bdLonLat[1], bdLonLat[0]);
                    }
                    mMarkName = getString(R.string.input_position_default_name);

                    markMap();

                    MapStatusUpdate mapstatusupdate = MapStatusUpdateFactory.newLatLng(mMarkLatLngMap);
                    mBaiduMap.setMapStatus(mapstatusupdate);

                    dialog.dismiss();
                }
            });

            Button btnCancel = view.findViewById(R.id.input_position_cancel);
            btnCancel.setOnClickListener(v1 -> dialog.dismiss());
        });
    }

    //标定选择的位置
    private void markMap() {
        if (mMarkLatLngMap != null) {
            MarkerOptions ooA = new MarkerOptions().position(mMarkLatLngMap).icon(mMapIndicator).anchor(0.5f, 0.5f);
            mBaiduMap.clear();
            mBaiduMap.addOverlay(ooA);
            /* 运行中选中新位置时，主按钮立即从“停止模拟”切换为“跳到这里” */
            refreshStartFab();
        }
    }

    private void resetMap() {
        double latitude = mCurrentLat;
        double longitude = mCurrentLon;

        /* 定位尚未回调时 mCurrentLat/mCurrentLon 仍为 0，直接跳转会到几内亚湾。
         * 优先取地图图层已拿到的定位，仍为空则请求一次系统定位并等待回调。 */
        if (isZeroCoordinate(latitude, longitude)) {
            MyLocationData locationData = mBaiduMap.getLocationData();
            if (locationData != null) {
                latitude = locationData.latitude;
                longitude = locationData.longitude;
            }
        }

        if (isZeroCoordinate(latitude, longitude)) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_location_wait));
            if (mLocationHelper != null) {
                mLocationHelper.requestOnce(new DeviceLocationHelper.OnceListener() {
                    @Override
                    public void onLocation(@NonNull Location location) {
                        double[] bd = MapUtils.wgs2bd09(location.getLongitude(), location.getLatitude());
                        applyCurrentLocation(bd[0], bd[1],
                                location.hasAccuracy() ? location.getAccuracy() : 0.0f);
                        centerOnCurrentLocation(bd[1], bd[0]);
                    }

                    @Override
                    public void onTimeout() {
                        GoUtils.DisplayToast(MainActivity.this,
                                getResources().getString(R.string.app_location_unavailable));
                    }
                });
            }
            return;
        }

        mBaiduMap.clear();
        mMarkLatLngMap = null;
        applyCurrentLocation(longitude, latitude, 0.0f);
        centerOnCurrentLocation(latitude, longitude);
        refreshStartFab();
    }

    private void centerOnCurrentLocation(double latitude, double longitude) {
        MapStatus.Builder builder = new MapStatus.Builder();
        builder.target(new LatLng(latitude, longitude)).zoom(18.0f);
        mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
    }

    private boolean isZeroCoordinate(double latitude, double longitude) {
        return Math.abs(latitude) < 0.000001 && Math.abs(longitude) < 0.000001;
    }

    // 在地图上显示位置
    public static boolean showLocation(String name, String bd09Longitude, String bd09Latitude) {
        if (mBaiduMap == null) {
            return false;
        }
        boolean ret = true;

        try {
            if (!bd09Longitude.isEmpty() && !bd09Latitude.isEmpty()) {
                mMarkName = name;
                mMarkLatLngMap = new LatLng(Double.parseDouble(bd09Latitude), Double.parseDouble(bd09Longitude));
                MarkerOptions ooA = new MarkerOptions().position(mMarkLatLngMap).icon(mMapIndicator).anchor(0.5f, 0.5f);
                mBaiduMap.clear();
                mBaiduMap.addOverlay(ooA);
                MapStatusUpdate mapstatusupdate = MapStatusUpdateFactory.newLatLng(mMarkLatLngMap);
                mBaiduMap.setMapStatus(mapstatusupdate);
            }
        } catch (Exception e) {
            ret = false;
            XLog.e("ERROR: showHistoryLocation");
        }

        return ret;
    }

    private void initGoBtn() {
        mButtonStart = findViewById(R.id.faBtnStart);
        mButtonStart.setOnClickListener(this::doGoLocation);
        mButtonStart.setCornerRadius(Math.round(getResources().getDimension(R.dimen.radius_m)));
        updateStartFab(false);
    }

    /**
     * 同步首页主 FAB 的文案、图标与配色。
     *
     * <p>四个状态：未运行未选点（提示色）、未运行已选点（主色开始）、
     * 运行中已选新点（“跳到这里”）、运行中未选点（“停止模拟”）。
     * 这样运行中点击地图后按钮会立即变成“跳到这里”，而不是继续显示“停止模拟”。</p>
     */
    private void updateStartFab(boolean ignored) {
        refreshStartFab();
    }

    private void refreshStartFab() {
        if (mButtonStart == null) {
            return;
        }

        boolean routeRunning = ServiceGo.isRouteRunning();
        boolean hasPoint = mMarkLatLngMap != null;
        int textRes;
        int iconRes;
        int background;
        int foreground;

        if (routeRunning) {
            textRes = R.string.fab_route_running;
            iconRes = R.drawable.ic_route;
            background = ThemeHelper.colorSecondaryContainer(this);
            foreground = ThemeHelper.colorOnSecondaryContainer(this);
        } else if (isMockServStart && hasPoint) {
            textRes = R.string.fab_jump_here;
            iconRes = R.drawable.ic_navigation;
            background = ThemeHelper.colorTertiaryContainer(this);
            foreground = ThemeHelper.colorOnTertiaryContainer(this);
        } else if (isMockServStart) {
            textRes = R.string.fab_stop_mock;
            iconRes = R.drawable.ic_stop;
            background = ThemeHelper.colorErrorContainer(this);
            foreground = ThemeHelper.colorOnErrorContainer(this);
        } else if (hasPoint) {
            textRes = R.string.fab_start_mock;
            iconRes = R.drawable.ic_navigation;
            background = ThemeHelper.colorPrimary(this);
            foreground = ThemeHelper.colorOnPrimary(this);
        } else {
            textRes = R.string.fab_start_mock;
            iconRes = R.drawable.ic_add_location;
            background = ThemeHelper.colorPrimaryContainer(this);
            foreground = ThemeHelper.colorOnPrimaryContainer(this);
        }

        mButtonStart.setText(textRes);
        mButtonStart.setIconResource(iconRes);
        mButtonStart.setBackgroundTintList(ColorStateList.valueOf(background));
        mButtonStart.setTextColor(foreground);
        mButtonStart.setIconTint(ColorStateList.valueOf(foreground));
        mButtonStart.setContentDescription(getString(textRes));
        /* 所有状态统一使用默认 16dp 圆角，只用背景/文字颜色区分状态。 */
        mButtonStart.setCornerRadius(Math.round(getResources().getDimension(R.dimen.radius_m)));
    }

    private void startGoLocation() {
        Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
        if (!isServiceBound) {
            bindService(serviceGoIntent, mConnection, BIND_AUTO_CREATE);    // 绑定服务和活动，之后活动就可以去调服务的方法了
            isServiceBound = true;
        }
        double[] latLng = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
        serviceGoIntent.putExtra(LNG_MSG_ID, latLng[0]);
        serviceGoIntent.putExtra(LAT_MSG_ID, latLng[1]);
        double alt = GoUtils.parseDoubleSafe(sharedPreferences.getString("setting_altitude", "55.0"), 55.0);
        serviceGoIntent.putExtra(ALT_MSG_ID, alt);

        startForegroundService(serviceGoIntent);
        XLog.d("startForegroundService: ServiceGo");

        isMockServStart = true;
    }

    private void stopGoLocation() {
        if (isServiceBound) {
            try {
                unbindService(mConnection); // 解绑服务，服务要记得解绑，不要造成内存泄漏
            } catch (IllegalArgumentException ignored) {
            }
            isServiceBound = false;
        }
        mServiceBinder = null;
        Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
        stopService(serviceGoIntent);
        isMockServStart = false;
        refreshStartFab();
    }

    private void doGoLocation(View v) {
        if (!GoUtils.isNetworkAvailable(this)) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_network));
            return;
        }

        if (!GoUtils.isGpsOpened(this)) {
            GoUtils.showEnableGpsDialog(this);
            return;
        }

        if (!Settings.canDrawOverlays(getApplicationContext())) {//悬浮窗权限判断
            GoUtils.showEnableFloatWindowDialog(this);
            XLog.e("无悬浮窗权限!");
            return;
        }

        /* 路线模拟和位置传送使用的是同一个服务，两者不能同时进行 */
        if (ServiceGo.isRouteRunning()) {
            GoUtils.DisplayToast(this, getString(R.string.route_running_conflict));
            return;
        }

        if (isMockServStart) {
            if (mMarkLatLngMap == null) {
                stopGoLocation();
                GoUtils.DisplayToast(this, "模拟位置已终止");
                refreshStartFab();
            } else {
                if (mServiceBinder == null) {
                    GoUtils.DisplayToast(this, getString(R.string.route_service_connecting));
                    return;
                }
                double[] latLng = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
                double alt = GoUtils.parseDoubleSafe(sharedPreferences.getString("setting_altitude", "55.0"), 55.0);
                mServiceBinder.setPosition(latLng[0], latLng[1], alt);
                GoUtils.DisplayToast(this, "已传送到新位置");

                recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

                mBaiduMap.clear();
                mMarkLatLngMap = null;
                /* 已传送完成：按钮从“跳到这里”回到“停止模拟” */
                refreshStartFab();

                if (GoUtils.isWifiEnabled(MainActivity.this)) {
                    GoUtils.showDisableWifiDialog(MainActivity.this);
                }
            }
        } else {
            if (!GoUtils.isAllowMockLocation(this)) {
                GoUtils.showEnableMockLocationDialog(this);
                XLog.e("无模拟位置权限!");
            } else {
                if (mMarkLatLngMap == null) {
                    GoUtils.DisplayToast(this, "请先点击地图位置或者搜索位置");
                } else {
                    startGoLocation();
                    refreshStartFab();
                    GoUtils.DisplayToast(this, "模拟位置已启动");

                    recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
                    mBaiduMap.clear();
                    mMarkLatLngMap = null;
                    refreshStartFab();

                    if (GoUtils.isWifiEnabled(MainActivity.this)) {
                        GoUtils.showDisableWifiDialog(MainActivity.this);
                    }
                }
            }
        }
    }

    /*============================== 历史记录 相关 ==============================*/
    private void initStoreHistory() {
        try {
            // 定位历史
            DataBaseHistoryLocation dbLocation = new DataBaseHistoryLocation(getApplicationContext());
            mLocationHistoryDB = dbLocation.getWritableDatabase();
            // 搜索历史
            DataBaseHistorySearch dbHistory = new DataBaseHistorySearch(getApplicationContext());
            mSearchHistoryDB = dbHistory.getWritableDatabase();
        } catch (Exception e) {
            XLog.e("ERROR: sqlite init error");
        }
    }

    //获取查询历史
    private List<Map<String, Object>> getSearchHistory() {
        List<Map<String, Object>> data = new ArrayList<>();

        try {
            Cursor cursor = mSearchHistoryDB.query(DataBaseHistorySearch.TABLE_NAME, null,
                    DataBaseHistorySearch.DB_COLUMN_ID + " > ?", new String[] {"0"},
                    null, null, DataBaseHistorySearch.DB_COLUMN_TIMESTAMP + " DESC", null);

            while (cursor.moveToNext()) {
                Map<String, Object> searchHistoryItem = new HashMap<>();
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_KEY, cursor.getString(1));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, cursor.getString(2));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, "" + cursor.getInt(3));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, "" + cursor.getInt(4));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, cursor.getString(7));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, cursor.getString(8));
                data.add(searchHistoryItem);
            }
            cursor.close();
        } catch (Exception e) {
            XLog.e("ERROR: getSearchHistory");
        }

        return data;
    }

    // 记录请求的位置信息
    private void recordCurrentLocation(double lng, double lat) {
        //参数坐标系：bd09
        final String safeCode = BuildConfig.MAPS_SAFE_CODE;
        final String ak = sharedPreferences.getString("setting_map_key", BuildConfig.MAPS_API_KEY);
        final double[] latLng = MapUtils.bd2wgs(lng, lat);
        final String fallbackName = mMarkName == null
                ? getResources().getString(R.string.history_location_default_name) : mMarkName;
        //bd09坐标的位置信息
        String mapApiUrl = "https://api.map.baidu.com/reverse_geocoding/v3/?ak=" + ak + "&output=json&coordtype=bd09ll" + "&location=" + lat + "," + lng + "&mcode=" + safeCode;

        okhttp3.Request request = new okhttp3.Request.Builder().url(mapApiUrl).get().build();
        final Call call = mOkHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                XLog.e("HTTP: HTTP GET FAILED");
                saveLocationHistoryRecord(fallbackName, latLng[0], latLng[1], lng, lat);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String locationName = fallbackName;
                try (ResponseBody responseBody = response.body()) {
                    if (response.isSuccessful() && responseBody != null) {
                        JSONObject getRetJson = new JSONObject(responseBody.string());
                        if (Integer.parseInt(getRetJson.getString("status")) == 0) {
                            JSONObject posInfoJson = getRetJson.getJSONObject("result");
                            String formattedAddress = posInfoJson.optString("formatted_address");
                            if (!TextUtils.isEmpty(formattedAddress)) {
                                locationName = formattedAddress;
                            }
                        }
                    }
                } catch (JSONException | NumberFormatException e) {
                    XLog.e("JSON: resolve json error");
                }
                saveLocationHistoryRecord(locationName, latLng[0], latLng[1], lng, lat);
            }
        });
    }

    private void saveLocationHistoryRecord(String name, double wgsLng, double wgsLat, double bdLng, double bdLat) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, name);
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(wgsLng));
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, String.valueOf(wgsLat));
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, Double.toString(bdLng));
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, Double.toString(bdLat));
        DataBaseHistoryLocation.saveHistoryLocation(mLocationHistoryDB, contentValues);
    }

    /*============================== SearchView 相关 ==============================*/
    private void hideSearchOverlays() {
        if (mSearchLayout != null) {
            mSearchLayout.setVisibility(View.GONE);
        }
        if (mHistoryLayout != null) {
            mHistoryLayout.setVisibility(View.GONE);
        }
        if (searchView != null) {
            searchView.clearFocus();
        }
    }

    private int getSearchDropdownMaxHeight() {
        int containerHeight = mHomePageContainer != null ? mHomePageContainer.getHeight() : 0;
        if (containerHeight <= 0) {
            return dp(360);
        }
        int top = dp(84) + mStatusBarInset;
        int bottomLimit = (mButtonStart != null && mButtonStart.getTop() > top)
                ? mButtonStart.getTop() - dp(12)
                : containerHeight - dp(160) - mNavigationBarInset;
        return Math.max(dp(160), bottomLimit - top - dp(12));
    }

    private void showSearchHistoryIfAvailable() {
        List<Map<String, Object>> data = getSearchHistory();
        if (data.isEmpty()) {
            mHistoryLayout.setVisibility(View.GONE);
            return;
        }
        SimpleAdapter simAdapt = new SimpleAdapter(
                MainActivity.this,
                data,
                R.layout.search_item,
                new String[] {DataBaseHistorySearch.DB_COLUMN_KEY,
                        DataBaseHistorySearch.DB_COLUMN_DESCRIPTION,
                        DataBaseHistorySearch.DB_COLUMN_TIMESTAMP,
                        DataBaseHistorySearch.DB_COLUMN_IS_LOCATION,
                        DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM,
                        DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM},
                new int[] {R.id.search_key,
                        R.id.search_description,
                        R.id.search_timestamp,
                        R.id.search_isLoc,
                        R.id.search_longitude,
                        R.id.search_latitude});
        mSearchHistoryList.setAdapter(simAdapt);
        GoUtils.constrainSearchListHeight(mSearchHistoryList, getSearchDropdownMaxHeight());
        mHistoryLayout.setVisibility(View.VISIBLE);
    }
    private void initSearchView() {
        mSearchLayout = findViewById(R.id.search_linear);
        mHistoryLayout = findViewById(R.id.search_history_linear);

        mSearchList = findViewById(R.id.search_list_view);
        mSearchList.setOnItemClickListener((parent, view, position, id) -> {
            String lng = ((TextView) view.findViewById(R.id.poi_longitude)).getText().toString();
            String lat = ((TextView) view.findViewById(R.id.poi_latitude)).getText().toString();
            mMarkName = ((TextView) view.findViewById(R.id.poi_name)).getText().toString();
            mMarkLatLngMap = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
            MapStatusUpdate mapstatusupdate = MapStatusUpdateFactory.newLatLng(mMarkLatLngMap);
            mBaiduMap.setMapStatus(mapstatusupdate);

            markMap();

            double[] latLng = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

            // mSearchList.setVisibility(View.GONE);
            //搜索历史 插表参数
            ContentValues contentValues = new ContentValues();
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, mMarkName);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, ((TextView) view.findViewById(R.id.poi_address)).getText().toString());
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_RESULT);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, lng);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, lat);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

            DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
            mSearchLayout.setVisibility(View.GONE);
            searchItem.collapseActionView();
        });
        //搜索历史列表的点击监听
        mSearchHistoryList = findViewById(R.id.search_history_list_view);
        mSearchHistoryList.setOnItemClickListener((parent, view, position, id) -> {
            String searchDescription = ((TextView) view.findViewById(R.id.search_description)).getText().toString();
            String searchKey = ((TextView) view.findViewById(R.id.search_key)).getText().toString();
            String searchIsLoc = ((TextView) view.findViewById(R.id.search_isLoc)).getText().toString();

            //如果是定位搜索
            if (searchIsLoc.equals("1")) {
                String lng = ((TextView) view.findViewById(R.id.search_longitude)).getText().toString();
                String lat = ((TextView) view.findViewById(R.id.search_latitude)).getText().toString();
                mMarkName = searchKey;
                mMarkLatLngMap = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
                MapStatusUpdate mapstatusupdate = MapStatusUpdateFactory.newLatLng(mMarkLatLngMap);
                mBaiduMap.setMapStatus(mapstatusupdate);

                markMap();

                double[] latLng = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

                //设置列表不可见
                mHistoryLayout.setVisibility(View.GONE);
                searchItem.collapseActionView();
                //更新表
                ContentValues contentValues = new ContentValues();
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, searchKey);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, searchDescription);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_RESULT);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, lng);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, lat);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

                DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
            } else if (searchIsLoc.equals("0")) { //如果仅仅是搜索
                try {
                    searchView.setQuery(searchKey, true);
                } catch (Exception e) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_search));
                    XLog.e(getResources().getString(R.string.app_error_search));
                }
            } else {
                XLog.e(getResources().getString(R.string.app_error_param));
            }
        });
        mSearchHistoryList.setOnItemLongClickListener((parent, view, position, id) -> {
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle(R.string.search_history_delete_title)
                    .setMessage(R.string.search_history_delete_message)
                    .setPositiveButton(R.string.common_confirm, (dialog, which) -> {
                        String searchKey = ((TextView) view.findViewById(R.id.search_key)).getText().toString();

                        try {
                            mSearchHistoryDB.delete(DataBaseHistorySearch.TABLE_NAME, DataBaseHistorySearch.DB_COLUMN_KEY + " = ?", new String[] {searchKey});
                            showSearchHistoryIfAvailable();
                        } catch (Exception e) {
                            XLog.e("ERROR: delete database error");
                            GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.history_delete_error));
                        }
                    })
                    .setNegativeButton(R.string.common_cancel, null)
                    .show();
            return true;
        });
        //设置搜索建议返回值监听
        mSuggestionSearch = SuggestionSearch.newInstance();
        mSuggestionSearch.setOnGetSuggestionResultListener(suggestionResult -> {
            if (mBaiduTimeoutRunnable != null) {
                mSearchHandler.removeCallbacks(mBaiduTimeoutRunnable);
                mBaiduTimeoutRunnable = null;
            }

            if (suggestionResult != null
                    && suggestionResult.error == SearchResult.ERRORNO.NO_ERROR
                    && suggestionResult.getAllSuggestions() != null) {
                List<Map<String, Object>> data = getMapList(suggestionResult);
                if (!data.isEmpty()) {
                    showSearchResults(data);
                    return;
                }
            }

            if (suggestionResult != null && suggestionResult.error != null) {
                XLog.e("Baidu suggestion error: " + suggestionResult.error);
                if (suggestionResult.error == SearchResult.ERRORNO.KEY_ERROR
                        || suggestionResult.error == SearchResult.ERRORNO.PERMISSION_UNFINISHED
                        || suggestionResult.error == SearchResult.ERRORNO.NO_ADVANCED_PERMISSION
                        || suggestionResult.error == SearchResult.ERRORNO.REQUEST_ERROR) {
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
                String city = TextUtils.isEmpty(mCurrentCity) ? "全国" : mCurrentCity;
                mSuggestionSearch.requestSuggestion(new SuggestionSearchOption()
                        .keyword(keyword)
                        .city(city));

                /* 百度接口异常时可能迟迟不回调，超时后直接走兜底，避免界面一直空着 */
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
        PlaceSearchHelper.searchFallback(this, keyword, items -> {
            if (!keyword.equals(mPendingSearchKeyword)) {
                return;
            }
            if (items.isEmpty()) {
                mSearchLayout.setVisibility(View.GONE);
                GoUtils.DisplayToast(this, getResources().getString(R.string.app_search_null));
                return;
            }

            List<Map<String, Object>> data = new ArrayList<>();
            for (PlaceSearchHelper.Poi poi : items) {
                Map<String, Object> poiItem = new HashMap<>();
                poiItem.put(POI_NAME, poi.name);
                poiItem.put(POI_ADDRESS, poi.address);
                poiItem.put(POI_LONGITUDE, String.valueOf(poi.longitude));
                poiItem.put(POI_LATITUDE, String.valueOf(poi.latitude));
                data.add(poiItem);
            }
            showSearchResults(data);
        });
    }

    private void showSearchResults(List<Map<String, Object>> data) {
        if (mSearchList == null || data == null || data.isEmpty()) {
            return;
        }
        SimpleAdapter simAdapt = new SimpleAdapter(
                MainActivity.this,
                data,
                R.layout.search_poi_item,
                new String[] {POI_NAME, POI_ADDRESS, POI_LONGITUDE, POI_LATITUDE}, // 与下面数组元素要一一对应
                new int[] {R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude});
        mSearchList.setAdapter(simAdapt);
        GoUtils.constrainSearchListHeight(mSearchList, getSearchDropdownMaxHeight());
        mSearchLayout.setVisibility(View.VISIBLE);
    }

    private void saveSearchKeyword(String keyword) {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, keyword);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, "搜索关键字");
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_KEY);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
            DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
        } catch (Exception e) {
            XLog.e("save search keyword failed");
        }
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
            poiItem.put(POI_NAME, info.key);
            poiItem.put(POI_ADDRESS, (city + " " + district).trim());
            poiItem.put(POI_LONGITUDE, "" + info.pt.longitude);
            poiItem.put(POI_LATITUDE, "" + info.pt.latitude);
            data.add(poiItem);
        }
        return data;
    }
}
