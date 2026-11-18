package com.zcshou.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.CoordinateConverter;
import com.elvishew.xlog.XLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 地点搜索兜底服务。
 *
 * 百度 SuggestionSearch 依赖有效 AK；当前项目内置的 AK 在部分设备上会返回 505 / 201，
 * 此时搜索会没有任何结果。这里补一个开源、无需密钥的 OpenStreetMap Nominatim 搜索，
 * 在百度建议不可用时自动兜底，保证首页和路线页的搜索框始终可用。
 *
 * 数据来源：OpenStreetMap Nominatim（ODbL 许可），返回 WGS84 坐标，
 * 本类会统一转换成百度地图使用的 BD09 坐标后再交给界面显示。
 */
public final class PlaceSearchHelper {
    public static class Poi {
        public final String name;
        public final String address;
        public final double longitude;
        public final double latitude;

        public Poi(String name, String address, double longitude, double latitude) {
            this.name = name;
            this.address = address;
            this.longitude = longitude;
            this.latitude = latitude;
        }
    }

    public interface ResultCallback {
        void onResult(@NonNull List<Poi> items);
    }

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String BAIDU_WEB_URL = "https://map.baidu.com/?qt=s&ie=utf-8&wd=";
    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong(0);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static volatile boolean sBaiduAvailable = true;

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build();

    private PlaceSearchHelper() {
    }

    /**
     * 搜索兜底总入口：先走百度网页版地点搜索（无需 AK，国内网络可用），
     * 百度网页不可用时再走 OSM Nominatim。
     */
    public static void searchFallback(Context context, String keyword, ResultCallback callback) {
        searchBaiduWeb(context, keyword, items -> {
            if (items != null && !items.isEmpty()) {
                callback.onResult(items);
            } else {
                searchOsm(context, keyword, callback);
            }
        });
    }

    /**
     * 百度地图网页版的 qt=s 搜索结果不需要 App AK，返回 BD09MC 坐标，
     * 在客户端转成 BD09LL 后交给地图标记。
     */
    public static void searchBaiduWeb(Context context, String keyword, ResultCallback callback) {
        if (keyword == null || keyword.trim().isEmpty()) {
            callback.onResult(new ArrayList<>());
            return;
        }

        final long requestId = REQUEST_SEQUENCE.incrementAndGet();
        XLog.e("PlaceSearchHelper: Baidu web start " + keyword);
        String url;
        try {
            url = BAIDU_WEB_URL + URLEncoder.encode(keyword.trim(), "UTF-8");
        } catch (Exception e) {
            callback.onResult(new ArrayList<>());
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 "
                        + "Chrome/120.0 Mobile Safari/537.36")
                .header("Accept", "application/json,text/plain,*/*")
                .get()
                .build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                XLog.e("PlaceSearchHelper: Baidu web search failed");
                dispatch(requestId, new ArrayList<>(), callback);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                List<Poi> result = new ArrayList<>();
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject root = new JSONObject(response.body().string());
                        Object content = root.opt("content");
                        XLog.e("PlaceSearchHelper: Baidu web content " + (content == null ? "null" : content.getClass().getSimpleName()));
                        if (content instanceof JSONArray) {
                            JSONArray array = (JSONArray) content;
                            Set<String> seen = new HashSet<>();
                            for (int i = 0; i < array.length() && result.size() < 12; i++) {
                                JSONObject item = array.optJSONObject(i);
                                if (item == null) {
                                    continue;
                                }
                                String name = item.optString("name");
                                if (name == null || name.trim().isEmpty()) {
                                    continue;
                                }
                                name = name.replaceAll("<[^>]+>", "").trim();
                                String address = item.optString("addr");
                                if (address == null || address.trim().isEmpty()) {
                                    address = item.optString("address");
                                }
                                double x = item.optDouble("x", 0);
                                double y = item.optDouble("y", 0);
                                if (x == 0 || y == 0 || !seen.add(name + "|" + address)) {
                                    continue;
                                }
                                LatLng bd09ll = new CoordinateConverter()
                                        .from(CoordinateConverter.CoordType.BD09MC)
                                        .coord(new LatLng(y / 100.0, x / 100.0))
                                        .convert();
                                if (bd09ll == null) {
                                    continue;
                                }
                                result.add(new Poi(name, address, bd09ll.longitude, bd09ll.latitude));
                            }
                        } else if (content instanceof JSONObject) {
                            // 搜索城市名时百度返回的是单个 content 对象，而不是数组。
                            JSONObject item = (JSONObject) content;
                            String name = item.optString("name");
                            if (name == null || name.trim().isEmpty()) {
                                name = item.optString("cname");
                            }
                            String address = item.optString("poi_address");
                            if (address == null || address.trim().isEmpty()) {
                                address = item.optString("addr");
                            }
                            double x = item.optDouble("x", 0);
                            double y = item.optDouble("y", 0);
                            JSONObject point = item.optJSONObject("point");
                            if ((x == 0 || y == 0) && point != null) {
                                x = point.optDouble("x", 0);
                                y = point.optDouble("y", 0);
                            }
                            if (name != null && !name.trim().isEmpty() && x != 0 && y != 0) {
                                name = name.replaceAll("<[^>]+>", "").trim();
                                LatLng bd09ll = new CoordinateConverter()
                                        .from(CoordinateConverter.CoordType.BD09MC)
                                        .coord(new LatLng(y / 100.0, x / 100.0))
                                        .convert();
                                if (bd09ll != null) {
                                    result.add(new Poi(name, address, bd09ll.longitude, bd09ll.latitude));
                                }
                            }
                        }
                    } else {
                        XLog.e("PlaceSearchHelper: Baidu web HTTP " + response.code());
                    }
                } catch (Exception e) {
                    XLog.e("PlaceSearchHelper: parse Baidu web result failed");
                } finally {
                    response.close();
                }
                XLog.e("PlaceSearchHelper: Baidu web result " + result.size());
                dispatch(requestId, result, callback);
            }
        });
    }

    public static boolean isBaiduAvailable() {
        return sBaiduAvailable;
    }

    public static void markBaiduUnavailable() {
        sBaiduAvailable = false;
    }

    /**
     * 用 OpenStreetMap Nominatim 搜索地点。回调一定在主线程执行，结果为空时返回空列表。
     */
    public static void searchOsm(Context context, String keyword, ResultCallback callback) {
        if (keyword == null || keyword.trim().isEmpty()) {
            callback.onResult(new ArrayList<>());
            return;
        }

        final long requestId = REQUEST_SEQUENCE.incrementAndGet();
        final String version = GoUtils.getVersionName(context);
        String url;
        try {
            url = NOMINATIM_URL
                    + "?format=jsonv2"
                    + "&limit=12"
                    + "&accept-language=zh-CN"
                    + "&countrycodes=cn"
                    + "&q=" + URLEncoder.encode(keyword.trim(), "UTF-8");
        } catch (Exception e) {
            callback.onResult(new ArrayList<>());
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "YingSuoPro/" + version
                        + " (Android; https://github.com/Mortonnn/YingSuoPro)")
                .header("Accept", "application/json")
                .get()
                .build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                XLog.e("PlaceSearchHelper: Nominatim request failed");
                dispatch(requestId, new ArrayList<>(), callback);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                List<Poi> result = new ArrayList<>();
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String body = response.body().string();
                        JSONArray array = new JSONArray(body);
                        Set<String> seen = new HashSet<>();
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject item = array.optJSONObject(i);
                            if (item == null) {
                                continue;
                            }
                            String name = item.optString("name");
                            String displayName = item.optString("display_name");
                            if (name == null || name.isEmpty()) {
                                name = displayName;
                            }
                            if (name == null || name.isEmpty() || displayName.isEmpty()) {
                                continue;
                            }
                            String key = name + "|" + item.optString("lat") + "|" + item.optString("lon");
                            if (!seen.add(key)) {
                                continue;
                            }
                            double lat = Double.parseDouble(item.optString("lat", "0"));
                            double lon = Double.parseDouble(item.optString("lon", "0"));
                            double[] bd = MapUtils.wgs2bd09(lon, lat);
                            result.add(new Poi(name, displayName, bd[0], bd[1]));
                        }
                    } else {
                        XLog.e("PlaceSearchHelper: Nominatim HTTP " + response.code());
                    }
                } catch (Exception e) {
                    XLog.e("PlaceSearchHelper: parse Nominatim result failed");
                } finally {
                    response.close();
                }
                dispatch(requestId, result, callback);
            }
        });
    }

    private static void dispatch(long requestId, List<Poi> result, ResultCallback callback) {
        MAIN_HANDLER.post(() -> {
            // 只把最后一次输入的结果回给界面，避免旧请求覆盖新结果
            if (requestId == REQUEST_SEQUENCE.get()) {
                callback.onResult(result);
            }
        });
    }
}

