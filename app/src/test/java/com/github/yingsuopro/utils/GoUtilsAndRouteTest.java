package com.github.yingsuopro.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.baidu.mapapi.model.LatLng;
import com.github.yingsuopro.route.RouteSimulator;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 坐标解析、历史搜索与路线模拟核心逻辑单元测试。
 */
public class GoUtilsAndRouteTest {

    @Test
    public void parseLatLngTextSupportsCommaAndLegacyBracketFormat() {
        assertArrayEquals(new String[]{"108.953543", "34.265678"},
                GoUtils.parseLatLngText("108.953543, 34.265678"));
        assertArrayEquals(new String[]{"108.953543", "34.265678"},
                GoUtils.parseLatLngText("[经度:108.953543 纬度:34.265678]"));
        assertNull(GoUtils.parseLatLngText(""));
        assertNull(GoUtils.parseLatLngText("invalid"));
    }

    @Test
    public void matchesHistoryQueryChecksVisibleFieldsOnly() {
        Map<String, Object> record = new HashMap<>();
        record.put("KEY_ID", "999");
        record.put("KEY_LOCATION", "西安钟楼");
        record.put("KEY_TIME", "2026-11-25 16:00:00");

        assertTrue(GoUtils.matchesHistoryQuery(record, "钟楼", "KEY_LOCATION", "KEY_TIME"));
        assertTrue(GoUtils.matchesHistoryQuery(record, "2026-11-25", "KEY_LOCATION", "KEY_TIME"));
        assertFalse(GoUtils.matchesHistoryQuery(record, "KEY_LOCATION", "KEY_LOCATION", "KEY_TIME"));
        assertFalse(GoUtils.matchesHistoryQuery(record, "999", "KEY_LOCATION", "KEY_TIME"));
    }

    @Test
    public void routePointsRoundTripPreservesCoordinates() {
        List<LatLng> points = Arrays.asList(
                new LatLng(34.265678, 108.953543),
                new LatLng(34.266900, 108.955100)
        );
        String serialized = RouteUtils.pointsToString(points);
        List<LatLng> parsed = RouteUtils.stringToPoints(serialized);
        assertEquals(2, parsed.size());
        assertEquals(34.265678, parsed.get(0).latitude, 1e-6);
        assertEquals(108.953543, parsed.get(0).longitude, 1e-6);
        assertEquals(34.266900, parsed.get(1).latitude, 1e-6);
        assertEquals(108.955100, parsed.get(1).longitude, 1e-6);
    }

    @Test
    public void routeSimulatorAdvancesAndFinishesWhenNotRepeating() {
        RouteSimulator simulator = new RouteSimulator();
        List<LatLng> points = Arrays.asList(
                new LatLng(34.265678, 108.953543),
                new LatLng(34.266578, 108.953543)
        );
        assertTrue(simulator.setRoute(points));
        assertTrue(simulator.getTotalDistance() > 50.0);

        simulator.setSpeed(10.0);
        simulator.setSpeedFloat(0.0);
        simulator.setRepeat(false);
        simulator.start();

        assertFalse(simulator.isFinished());
        assertEquals(0.0, simulator.getProgress(), 1e-6);

        simulator.update(2000);
        double midProgress = simulator.getProgress();
        assertTrue("expected progress > 0, got " + midProgress, midProgress > 0.0 && midProgress < 1.0);
        assertNotNull(simulator.getCurrentPosition());

        simulator.pause();
        simulator.update(5000);
        assertEquals(midProgress, simulator.getProgress(), 1e-6);

        simulator.resume();
        simulator.update(30000);
        assertTrue(simulator.isFinished());
        assertEquals(1.0, simulator.getProgress(), 1e-6);
    }
}
