package com.github.yingsuopro.route;

import com.baidu.mapapi.model.LatLng;
import com.github.yingsuopro.utils.MapUtils;
import com.github.yingsuopro.utils.RouteUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 路线模拟的核心逻辑
 * <p>
 * 负责把规划好的路线转换成平滑的路点，并按照设定的速度依次走完这些路点。
 * 这里只做纯粹的坐标计算，不涉及任何界面和服务，方便在 ServiceGo 中按固定间隔驱动。
 * 注意：路线途经点使用 BD09 坐标（与地图一致），内部统一转换成 WGS84 坐标（与定位一致）。
 */
public class RouteSimulator {
    /* 默认的运动速度，单位 m/s */
    public static final double DEFAULT_SPEED = 3.5;
    /* 默认的定位刷新间隔，单位 ms */
    public static final int DEFAULT_INTERVAL = 200;
    /* 默认的速度浮动，单位 % */
    public static final double DEFAULT_SPEED_FLOAT = 10;
    /* 刷新间隔的上下限，单位 ms，间隔过小会频繁刷新定位，过大则移动不够连贯 */
    public static final int MIN_INTERVAL = 100;
    public static final int MAX_INTERVAL = 2000;
    /* 速度浮动的一个完整周期，单位 s */
    private static final double SPEED_FLOAT_PERIOD = 20.0;

    /* 平滑之后的路点（WGS84），每个元素均为 {经度, 纬度} */
    private final List<double[]> mPath = new ArrayList<>();
    /* 未叠加随机偏移的原始路点，循环时基于它重新平移，避免误差累积 */
    private final List<double[]> mBasePath = new ArrayList<>();
    /* 每个路点到起点的距离，单位米，用于快速定位当前所处的路段 */
    private final List<Double> mPathDistance = new ArrayList<>();

    /* 路线随机偏移：仅在循环模拟时生效，每次重新开始循环随机平移整条路线 */
    private boolean mRandomOffsetEnabled = false;
    private double mRandomLatMaxMeters = 1.0;
    private double mRandomLonMaxMeters = 1.0;
    private double mOffsetLatMeters = 0;
    private double mOffsetLonMeters = 0;

    private volatile double mTotalDistance = 0;
    private volatile double mSpeed = DEFAULT_SPEED;
    private volatile double mSpeedFloat = DEFAULT_SPEED_FLOAT;   /* 速度浮动，单位 % */
    private volatile double mAltitude = 55.0;
    private volatile boolean isRepeat = false;
    private volatile boolean isPaused = false;
    private volatile boolean isFinished = false;
    private double mTravelled = 0;
    private int mCurIndex = 0;
    private volatile int mLoopCount = 0;
    private long mStartTime = 0;
    private double mCurLng = 0;
    private double mCurLat = 0;
    private float mCurBearing = 0;

    /**
     * 设置路线的途经点，设置时会自动做平滑处理
     * @param points 途经点（BD09 坐标）
     * @return 途经点是否有效，少于两个点时返回 false
     */
    public synchronized boolean setRoute(List<LatLng> points) {
        mPath.clear();
        mBasePath.clear();
        mPathDistance.clear();
        mTotalDistance = 0;

        if (points == null || points.size() < 2) {
            return false;
        }

        List<LatLng> smoothPoints = RouteUtils.smoothRoute(points);
        double distance = 0;
        double prevLng = 0;
        double prevLat = 0;

        for (int i = 0; i < smoothPoints.size(); i++) {
            LatLng point = smoothPoints.get(i);
            // 地图上使用 BD09 坐标，定位使用 WGS84 坐标，这里统一转换并在同一坐标系下累计距离
            double[] lngLat = MapUtils.bd2wgs(point.longitude, point.latitude);

            if (i > 0) {
                distance += RouteUtils.getDistance(prevLng, prevLat, lngLat[0], lngLat[1]);
            }
            prevLng = lngLat[0];
            prevLat = lngLat[1];

            mBasePath.add(new double[] {lngLat[0], lngLat[1]});
            mPath.add(new double[] {lngLat[0], lngLat[1]});
            mPathDistance.add(distance);
        }

        mTotalDistance = distance;

        return mTotalDistance > 0;
    }

    /**
     * 设置路线随机偏移。与位置随机偏移共用经度/纬度最大偏移距离。
     * 该偏移只会在循环模拟（loop != 1）时生效。
     */
    public void setRandomOffset(boolean enabled, double latMaxMeters, double lonMaxMeters) {
        mRandomOffsetEnabled = enabled;
        mRandomLatMaxMeters = Math.max(0, latMaxMeters);
        mRandomLonMaxMeters = Math.max(0, lonMaxMeters);
    }

    /** 每次循环开始时重新随机平移整条路线，让多圈轨迹不会完全重合。 */
    private void applyNewRandomOffset() {
        mOffsetLatMeters = (Math.random() * 2 - 1) * mRandomLatMaxMeters;
        mOffsetLonMeters = (Math.random() * 2 - 1) * mRandomLonMaxMeters;
        applyPathOffset();
    }

    private void applyPathOffset() {
        for (int i = 0; i < mBasePath.size(); i++) {
            double[] base = mBasePath.get(i);
            double lat = base[1] + mOffsetLatMeters / 110574.0;
            double lon = base[0] + mOffsetLonMeters / (111320.0 * Math.cos(Math.toRadians(base[1])));
            mPath.set(i, new double[] {lon, lat});
        }
    }

    private void clearRandomOffset() {
        mOffsetLatMeters = 0;
        mOffsetLonMeters = 0;
        applyPathOffset();
    }

    public void setSpeed(double speed) {
        if (speed > 0) {
            mSpeed = speed;
        }
    }

    public void setSpeedFloat(double speedFloat) {
        /* 速度浮动最大 50%，再大就不像是正常的移动了 */
        mSpeedFloat = Math.max(0, Math.min(50, speedFloat));
    }

    public void setAltitude(double altitude) {
        mAltitude = altitude;
    }

    public void setRepeat(boolean repeat) {
        isRepeat = repeat;
    }

    public boolean isFinished() {
        return isFinished;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public double getTotalDistance() {
        return mTotalDistance;
    }

    public double getAltitude() {
        return mAltitude;
    }

    public int getLoopCount() {
        return mLoopCount;
    }

    /**
     * 路线的完成进度
     * @return 0.0 ~ 1.0
     */
    public synchronized double getProgress() {
        if (mTotalDistance <= 0) {
            return 0;
        }

        return Math.min(1.0, mTravelled / mTotalDistance);
    }

    /**
     * 当前实际的移动速度，包含了速度浮动的影响
     */
    public double getCurrentSpeed() {
        double speed = mSpeed;
        double speedFloat = mSpeedFloat;
        if (speedFloat <= 0) {
            return speed;
        }

        double phase = (System.nanoTime() / 1_000_000L - mStartTime) / 1000.0;
        double factor = 1 + speedFloat / 100.0 * Math.sin(2 * Math.PI * phase / SPEED_FLOAT_PERIOD);

        return Math.max(0.1, speed * factor);
    }

    /**
     * 当前模拟位置
     * @return {经度, 纬度}，WGS84 坐标
     */
    public synchronized double[] getCurrentPosition() {
        return new double[] {mCurLng, mCurLat};
    }

    public synchronized float getCurrentBearing() {
        return mCurBearing;
    }

    /**
     * 开始模拟，总是从路线的起点开始
     */
    public synchronized void start() {
        mStartTime = System.nanoTime() / 1_000_000L;
        mTravelled = 0;
        mCurIndex = 0;
        mLoopCount = 0;
        isPaused = false;

        /* 路线随机偏移只在循环模式下生效 */
        if (isRepeat && mRandomOffsetEnabled) {
            applyNewRandomOffset();
        } else {
            clearRandomOffset();
        }

        /* 只有一个点时无法移动，直接认为已经走完 */
        isFinished = mPath.size() < 2;
        updateCurrentPosition();
    }

    public void pause() {
        isPaused = true;
    }

    public void resume() {
        isPaused = false;
    }

    /**
     * 按经过的时间推进模拟
     * @param elapsedMs 距离上一次推进的时间，单位 ms
     * @return 是否仍在模拟中
     */
    public synchronized boolean update(long elapsedMs) {
        if (isFinished || isPaused || mPath.size() < 2) {
            return false;
        }

        mTravelled += getCurrentSpeed() * elapsedMs / 1000.0;

        if (mTravelled >= mTotalDistance) {
            if (isRepeat) {
                /* 循环模拟，走完之后回到起点重新开始；每一圈重新随机平移一次路线 */
                mTravelled = mTravelled % mTotalDistance;
                mLoopCount++;
                if (mRandomOffsetEnabled) {
                    applyNewRandomOffset();
                }
            } else {
                mTravelled = mTotalDistance;
                isFinished = true;
            }
        }

        updateCurrentPosition();

        return !isFinished;
    }

    /* 根据已走过的距离计算当前所在的坐标与方向 */
    private void updateCurrentPosition() {
        if (mPath.isEmpty()) {
            return;
        }

        if (mPath.size() < 2) {
            mCurLng = mPath.get(0)[0];
            mCurLat = mPath.get(0)[1];
            return;
        }

        /* 循环模拟回到起点时，索引需要往回退 */
        while (mCurIndex > 0 && mPathDistance.get(mCurIndex) > mTravelled) {
            mCurIndex--;
        }

        while (mCurIndex < mPathDistance.size() - 2 && mPathDistance.get(mCurIndex + 1) < mTravelled) {
            mCurIndex++;
        }

        double[] start = mPath.get(mCurIndex);
        double[] end = mPath.get(mCurIndex + 1);
        double segmentDistance = mPathDistance.get(mCurIndex + 1) - mPathDistance.get(mCurIndex);
        double scale = segmentDistance > 0 ? (mTravelled - mPathDistance.get(mCurIndex)) / segmentDistance : 0;

        mCurLng = start[0] + (end[0] - start[0]) * scale;
        mCurLat = start[1] + (end[1] - start[1]) * scale;

        if (segmentDistance > 0) {
            mCurBearing = (float) Math.toDegrees(Math.atan2(end[0] - start[0], end[1] - start[1]));
            if (mCurBearing < 0) {      // 由 -180 ~ +180 转为 0 ~ 360
                mCurBearing += 360;
            }
        }
    }
}
