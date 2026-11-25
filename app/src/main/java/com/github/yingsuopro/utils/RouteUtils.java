package com.github.yingsuopro.utils;

import com.baidu.mapapi.model.LatLng;
import com.elvishew.xlog.XLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RouteUtils {
    /* 向心 Catmull-Rom 的 alpha；0.5 为 centripetal，可避免尖点和自交 */
    private static final double CENTRIPETAL_ALPHA = 0.5;
    /* 途经点之间的最小间距（米），过近的点会导致路线抖动，规划时直接过滤 */
    public static final double MIN_POINT_DISTANCE = 1.0;

    /**
     * 计算两个坐标点之间的距离（米）
     * @param latLng1 第一个点
     * @param latLng2 第二个点
     * @return 两点之间的距离，单位米
     */
    public static double getDistance(LatLng latLng1, LatLng latLng2) {
        return getDistance(latLng1.longitude, latLng1.latitude, latLng2.longitude, latLng2.latitude);
    }

    /**
     * 计算两个经纬度坐标点之间的距离（米）
     */
    public static double getDistance(double lng1, double lat1, double lng2, double lat2) {
        double dLng = (lng2 - lng1) * 111320 * Math.cos(Math.toRadians(lat1));
        double dLat = (lat2 - lat1) * 110574;

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
     * 使用 <b>向心 Catmull-Rom（centripetal Catmull-Rom，alpha = 0.5）</b>。
     * 旧实现使用均匀参数化（uniform Catmull-Rom），当相邻途经点间距相差很大、
     * 或连续转向较急时，样条切线会被拉向远处控制点，导致曲线冲过目标点再折返，
     * 看起来就像“莫名其妙多途经了一个点又绕回来”。向心参数化把节点间距纳入
     * 切线计算，可以证明不会产生尖点（cusp）和自交，曲线始终贴着相邻两点之间
     * 的走廊走，同时仍然精确经过每一个原始途经点。
     * <p>
     * 首尾使用镜像控制点，保证起点与终点方向自然；采样数按实际段长自适应。
     *
     * @param points 原始的途经点（BD09）
     * @return 平滑之后的路点（包含原始的途经点）
     */
    public static List<LatLng> smoothRoute(List<LatLng> points) {
        List<LatLng> smoothPoints = new ArrayList<>();
        List<LatLng> cleanPoints = sanitizePoints(points);

        if (cleanPoints.isEmpty()) {
            return smoothPoints;
        }

        if (cleanPoints.size() < 3) {        // 少于三个点无法构成曲线
            smoothPoints.addAll(cleanPoints);
            return smoothPoints;
        }

        for (int i = 0; i < cleanPoints.size() - 1; i++) {
            LatLng p0 = getControlPoint(cleanPoints, i - 1);
            LatLng p1 = cleanPoints.get(i);
            LatLng p2 = cleanPoints.get(i + 1);
            LatLng p3 = getControlPoint(cleanPoints, i + 2);

            /* 向心参数化的节点参数：t(i+1) = t(i) + |P(i+1)-P(i)|^0.5 */
            double t0 = 0.0;
            double t1 = t0 + Math.pow(Math.max(getDistance(p0, p1), 0.01), CENTRIPETAL_ALPHA);
            double t2 = t1 + Math.pow(Math.max(getDistance(p1, p2), 0.01), CENTRIPETAL_ALPHA);
            double t3 = t2 + Math.pow(Math.max(getDistance(p2, p3), 0.01), CENTRIPETAL_ALPHA);

            int sampleCount = getSampleCount(p1, p2);
            for (int j = 0; j < sampleCount; j++) {
                double t = t1 + (t2 - t1) * ((double) j / sampleCount);
                smoothPoints.add(centripetalCatmullRom(p0, p1, p2, p3, t0, t1, t2, t3, t));
            }
        }

        smoothPoints.add(cleanPoints.get(cleanPoints.size() - 1));

        return smoothPoints;
    }

    /**
     * 过滤非法坐标和重复点。重复点会让切线长度为 0，是曲线出现跳变/折返的另一个诱因。
     */
    private static List<LatLng> sanitizePoints(List<LatLng> points) {
        List<LatLng> result = new ArrayList<>();

        if (points == null) {
            return result;
        }

        for (LatLng point : points) {
            if (point == null
                    || Double.isNaN(point.latitude) || Double.isNaN(point.longitude)
                    || Double.isInfinite(point.latitude) || Double.isInfinite(point.longitude)
                    || Math.abs(point.latitude) > 90.0 || Math.abs(point.longitude) > 180.0) {
                continue;
            }

            if (result.isEmpty() || getDistance(result.get(result.size() - 1), point) > 0.01) {
                result.add(point);
            }
        }

        return result;
    }

    /**
     * 根据两点间实际距离决定插值密度：短段少插值，长段多插值，避免长直线上出现折角。
     */
    private static int getSampleCount(LatLng from, LatLng to) {
        double meters = getDistance(from, to);
        int samples = (int) Math.ceil(meters / 2.5);
        return Math.max(8, Math.min(512, samples));
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
     * 向心 Catmull-Rom 的 Barry-Goldman 形式。
     * t 在 [t1, t2] 之间取值，结果一定经过 p1 与 p2，且不会产生自交环。
     */
    private static LatLng centripetalCatmullRom(LatLng p0, LatLng p1, LatLng p2, LatLng p3,
                                                 double t0, double t1, double t2, double t3,
                                                 double t) {
        double a1x = lerp(p0.longitude, p1.longitude, t0, t1, t);
        double a1y = lerp(p0.latitude, p1.latitude, t0, t1, t);
        double a2x = lerp(p1.longitude, p2.longitude, t1, t2, t);
        double a2y = lerp(p1.latitude, p2.latitude, t1, t2, t);
        double a3x = lerp(p2.longitude, p3.longitude, t2, t3, t);
        double a3y = lerp(p2.latitude, p3.latitude, t2, t3, t);

        double b1x = lerp(a1x, a2x, t0, t2, t);
        double b1y = lerp(a1y, a2y, t0, t2, t);
        double b2x = lerp(a2x, a3x, t1, t3, t);
        double b2y = lerp(a2y, a3y, t1, t3, t);

        double cx = lerp(b1x, b2x, t1, t2, t);
        double cy = lerp(b1y, b2y, t1, t2, t);

        return new LatLng(cy, cx);
    }

    /** 一维线性插值：在参数 t 处求 a(t0) 到 b(t1) 的值。 */
    private static double lerp(double a, double b, double t0, double t1, double t) {
        if (Math.abs(t1 - t0) < 1e-9) {
            return a;
        }
        double weight = (t - t0) / (t1 - t0);
        return a + (b - a) * weight;
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

                double latitude = Double.parseDouble(lngLat[1]);
                double longitude = Double.parseDouble(lngLat[0]);
                if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                        || Math.abs(latitude) > 90.0 || Math.abs(longitude) > 180.0) {
                    continue;
                }
                pointList.add(new LatLng(latitude, longitude));
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
