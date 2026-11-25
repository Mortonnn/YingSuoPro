package com.github.yingsuopro.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.baidu.mapapi.model.LatLng;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 路线平滑回归测试：锁定“短段夹在长段之间时绕出去再折返”的旧 bug。
 */
public class RouteUtilsSmoothingTest {

    private static final double BASE_LAT = 34.12;
    private static final double BASE_LNG = 108.83;

    private static LatLng ll(double northMeters, double eastMeters) {
        double lat = BASE_LAT + northMeters / 110574.0;
        double lng = BASE_LNG + eastMeters / (111320.0 * Math.cos(Math.toRadians(BASE_LAT)));
        return new LatLng(lat, lng);
    }

    private static List<LatLng> segment(List<LatLng> smooth, LatLng start, LatLng end) {
        int s = -1;
        for (int i = 0; i < smooth.size(); i++) {
            if (s < 0 && RouteUtils.getDistance(smooth.get(i), start) < 0.01) {
                s = i;
            } else if (s >= 0 && RouteUtils.getDistance(smooth.get(i), end) < 0.01) {
                return smooth.subList(s, i + 1);
            }
        }
        return new ArrayList<>();
    }

    private static double along(LatLng q, LatLng a, LatLng b) {
        double k = 111320.0 * Math.cos(Math.toRadians((a.latitude + b.latitude) / 2));
        double bx = (b.longitude - a.longitude) * k;
        double by = (b.latitude - a.latitude) * 110574.0;
        double qx = (q.longitude - a.longitude) * k;
        double qy = (q.latitude - a.latitude) * 110574.0;
        return (qx * bx + qy * by) / Math.hypot(bx, by);
    }

    @Test
    public void shortMiddleSegmentMustNotBacktrackOrOvershoot() {
        List<LatLng> pts = Arrays.asList(ll(0, 0), ll(100, 0), ll(102, 0), ll(202, 0));
        List<LatLng> seg = segment(RouteUtils.smoothRoute(pts), pts.get(1), pts.get(2));
        assertTrue("curve must contain the segment", seg.size() > 2);

        double length = RouteUtils.getDistance(pts.get(1), pts.get(2));
        double previous = -1e9;
        for (LatLng q : seg) {
            double s = along(q, pts.get(1), pts.get(2));
            assertTrue("backtrack: " + s, s >= -0.05);
            assertTrue("overshoot: " + s + " > " + length, s <= length + 0.05);
            assertTrue("non-monotone: " + s, s >= previous - 1e-6);
            previous = s;
        }
    }

    @Test
    public void duplicatePointsMustNotInflateRouteLength() {
        List<LatLng> pts = Arrays.asList(ll(0, 0), ll(100, 0), ll(100, 0), ll(200, 0));
        List<LatLng> smooth = RouteUtils.smoothRoute(pts);
        double length = RouteUtils.getRouteDistance(smooth);
        assertTrue("length inflated to " + length, length <= 202.0);
    }

    @Test
    public void originalWaypointsMustBePreserved() {
        List<LatLng> pts = Arrays.asList(ll(0, 0), ll(80, 20), ll(110, 90), ll(40, 130));
        List<LatLng> smooth = RouteUtils.smoothRoute(pts);
        for (LatLng point : pts) {
            boolean found = false;
            for (LatLng sample : smooth) {
                if (RouteUtils.getDistance(point, sample) < 0.02) {
                    found = true;
                    break;
                }
            }
            assertTrue("missing waypoint " + point, found);
        }
    }

    @Test
    public void longSegmentsUseAdaptiveSampling() {
        List<LatLng> pts = Arrays.asList(ll(0, 0), ll(0, 1000), ll(1000, 1000));
        List<LatLng> smooth = RouteUtils.smoothRoute(pts);
        for (int i = 1; i < smooth.size(); i++) {
            double gap = RouteUtils.getDistance(smooth.get(i - 1), smooth.get(i));
            assertTrue("sample gap too large: " + gap, gap <= 3.0);
        }
    }

    @Test
    public void totalDistanceIsCloseToOriginal() {
        List<LatLng> pts = Arrays.asList(ll(0, 0), ll(100, 0), ll(102, 0), ll(202, 0));
        List<LatLng> smooth = RouteUtils.smoothRoute(pts);
        assertEquals(202.0, RouteUtils.getRouteDistance(smooth), 2.0);
    }
}
