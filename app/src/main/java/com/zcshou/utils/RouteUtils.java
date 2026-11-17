package com.zcshou.utils;

import com.baidu.mapapi.model.LatLng;
import com.elvishew.xlog.XLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RouteUtils {
    /* 每两个途经点之间的插值点数，数值越大曲线越平滑 */
    private static final int SMOOTH_SAMPLE_COUNT = 16;
    /* 途经点之间的最小间距（米），过近的点会导致路线抖动，规划时直接过滤 */
    public static final double MIN_POINT_DISTANCE = 1.0;

    /**
     * 计算两个坐标点之间的距离（米）
     * @param latLng1 第一个点
     * @param latLng2 第二个点
     * @return 两点之间的距离，单位米
     */
    public static double getDistance(LatLng latLng1, LatLng latLng2) {
        double dLng = (latLng2.longitude - latLng1.longitude) * 111320 * Math.cos(Math.toRadians(latLng1.latitude));
        double dLat = (latLng2.latitude - latLng1.latitude) * 110574;

        return Math.sqrt(dLng * dLng + dLat * dLat);
    }

    /**
     * 计算整条路线的长度（米）
     * @param points 路线的途经点
     * @return 路线总长度，单位米
     */
    public static double getRouteDistance(List<LatLng> points) {
        double distance = 0;

        if (points == null || points.size() < 2) {
            return distance;
        }

        for (int i = 1; i < points.size(); i++) {
            distance += getDistance(points.get(i - 1), points.get(i));
        }

        return distance;
    }

    /**
     * 计算从 point1 指向 point2 的方位角（正北为 0，顺时针 0 ~ 360）
     * @param point1 起点
     * @param point2 终点
     * @return 方位角，单位度
     */
    public static double getBearing(LatLng point1, LatLng point2) {
        // 注意安卓中的三角函数使用的是弧度
        double lat1 = Math.toRadians(point1.latitude);
        double lat2 = Math.toRadians(point2.latitude);
        double dLng = Math.toRadians(point2.longitude - point1.longitude);
        double y = Math.sin(dLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
        double bearing = Math.toDegrees(Math.atan2(y, x));

        if (bearing < 0) {      // 由 -180 ~ +180 转为 0 ~ 360
            bearing += 360;
        }

        return bearing;
    }

    /**
     * 对规划的路线做平滑处理，避免出现生硬的折角
     * <p>
     * 采用 Catmull-Rom 样条曲线，曲线一定会经过每一个途经点，同时在途经点之间插入若干
     * 过渡点，使得转向是渐变的。首尾两段采用镜像点作为控制点，保证起点和终点方向自然。
     * @param points 原始的途经点
     * @return 平滑之后的路点（包含原始的途经点）
     */
    public static List<LatLng> smoothRoute(List<LatLng> points) {
        List<LatLng> smoothPoints = new ArrayList<>();

        if (points == null || points.isEmpty()) {
            return smoothPoints;
        }

        if (points.size() < 3) {        // 少于三个点无法构成曲线
            smoothPoints.addAll(points);
            return smoothPoints;
        }

        for (int i = 0; i < points.size() - 1; i++) {
            LatLng p0 = getControlPoint(points, i - 1);
            LatLng p1 = points.get(i);
            LatLng p2 = points.get(i + 1);
            LatLng p3 = getControlPoint(points, i + 2);

            for (int j = 0; j < SMOOTH_SAMPLE_COUNT; j++) {
                smoothPoints.add(catmullRom(p0, p1, p2, p3, (double) j / SMOOTH_SAMPLE_COUNT));
            }
        }

        smoothPoints.add(points.get(points.size() - 1));

        return smoothPoints;
    }

    /**
     * 获取样条曲线的控制点，越界时使用边界点的镜像，保证首尾段也是平滑的
     */
    private static LatLng getControlPoint(List<LatLng> points, int index) {
        if (index < 0) {
            /* 起点之前没有点，用第一个点到第二个点的反向延长点作为控制点 */
            LatLng first = points.get(0);
            LatLng second = points.get(1);
            return new LatLng(first.latitude * 2 - second.latitude, first.longitude * 2 - second.longitude);
        }

        if (index > points.size() - 1) {
            /* 终点之后没有点，用最后一个点到前一个点的反向延长点作为控制点 */
            LatLng last = points.get(points.size() - 1);
            LatLng prev = points.get(points.size() - 2);
            return new LatLng(last.latitude * 2 - prev.latitude, last.longitude * 2 - prev.longitude);
        }

        return points.get(index);
    }

    /**
     * Catmull-Rom 样条插值，t 取值 [0, 1) 表示 p1 到 p2 之间的位置
     */
    private static LatLng catmullRom(LatLng p0, LatLng p1, LatLng p2, LatLng p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;

        double latitude = 0.5 * ((2 * p1.latitude) +
                (-p0.latitude + p2.latitude) * t +
                (2 * p0.latitude - 5 * p1.latitude + 4 * p2.latitude - p3.latitude) * t2 +
                (-p0.latitude + 3 * p1.latitude - 3 * p2.latitude + p3.latitude) * t3);

        double longitude = 0.5 * ((2 * p1.longitude) +
                (-p0.longitude + p2.longitude) * t +
                (2 * p0.longitude - 5 * p1.longitude + 4 * p2.longitude - p3.longitude) * t2 +
                (-p0.longitude + 3 * p1.longitude - 3 * p2.longitude + p3.longitude) * t3);

        return new LatLng(latitude, longitude);
    }

    /**
     * 途径点转为数据库存储的字符串，格式为 “经度,纬度;经度,纬度;...”
     */
    public static String pointsToString(List<LatLng> points) {
        StringBuilder builder = new StringBuilder();

        if (points != null) {
            for (int i = 0; i < points.size(); i++) {
                LatLng point = points.get(i);

                if (i > 0) {
                    builder.append(";");
                }

                builder.append(String.format(Locale.US, "%.7f", point.longitude));
                builder.append(",");
                builder.append(String.format(Locale.US, "%.7f", point.latitude));
            }
        }

        return builder.toString();
    }

    /**
     * 数据库存储的字符串还原为途径点，与 pointsToString 对应
     */
    public static List<LatLng> stringToPoints(String points) {
        List<LatLng> pointList = new ArrayList<>();

        if (points == null || points.isEmpty()) {
            return pointList;
        }

        try {
            String[] pointArray = points.split(";");

            for (String point : pointArray) {
                String[] lngLat = point.split(",");

                if (lngLat.length != 2) {
                    continue;
                }

                pointList.add(new LatLng(Double.parseDouble(lngLat[1]), Double.parseDouble(lngLat[0])));
            }
        } catch (Exception e) {
            XLog.e("ROUTE: ERROR - stringToPoints");
            pointList.clear();
        }

        return pointList;
    }

    /**
     * 路线名称为空时，用起点和终点的坐标生成一个默认名称
     */
    public static String getDefaultName(List<LatLng> points) {
        if (points == null || points.isEmpty()) {
            return "";
        }

        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        return "路线 " + format.format(new Date());
    }

    /**
     * 准星当前所指位置的提示文字
     */
    public static String getAddressTips(LatLng point) {
        return String.format(Locale.US, "准星位置: %.6f, %.6f", point.longitude, point.latitude);
    }
}
