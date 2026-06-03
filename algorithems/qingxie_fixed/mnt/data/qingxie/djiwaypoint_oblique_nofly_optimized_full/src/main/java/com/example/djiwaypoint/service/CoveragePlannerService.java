package com.example.djiwaypoint.service;

import com.example.djiwaypoint.dto.CoverageRequest;
import com.example.djiwaypoint.dto.GeoPoint;
import com.example.djiwaypoint.model.DroneInfo;
import com.example.djiwaypoint.model.FlightPlan;
import com.example.djiwaypoint.model.LatLng;
import com.example.djiwaypoint.model.PayloadInfo;
import com.example.djiwaypoint.model.Waypoint;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class CoveragePlannerService {

    private final AreaCoveragePlanner areaCoveragePlanner;

    public CoveragePlannerService(AreaCoveragePlanner areaCoveragePlanner) {
        this.areaCoveragePlanner = areaCoveragePlanner;
    }

    /**
     * 把 dto.CoverageRequest 转成 model.FlightPlan，并调用 AreaCoveragePlanner 生成航点
     */
    public FlightPlan buildPlan(CoverageRequest req) {
        if (req == null) throw new IllegalArgumentException("CoverageRequest 不能为空");

        List<GeoPoint> boundaryGp = read(req, "missionBoundary", List.class);
        if (boundaryGp == null || boundaryGp.size() < 3) {
            throw new IllegalArgumentException("missionBoundary 至少 3 个点");
        }

        Double spacingMeters = read(req, "spacingMeters", Double.class);
        Double flightHeight  = read(req, "flightHeight", Double.class);
        Double flightSpeed   = read(req, "flightSpeed", Double.class);

        DroneInfo device     = read(req, "device", DroneInfo.class);
        PayloadInfo payload  = read(req, "payload", PayloadInfo.class);
        String templateType  = read(req, "templateType", String.class);

        // noFlyAreas: List<List<GeoPoint>>
        List<List<GeoPoint>> noFlyGp = read(req, "noFlyAreas", List.class);

        double spacing = spacingMeters != null ? spacingMeters : 20.0;
        double height  = flightHeight != null ? flightHeight : 80.0;
        double safetyMargin = 20.0;

        // 1) boundary -> LatLng
        List<LatLng> missionBoundary = toLatLngListGeo(boundaryGp);

        // 2) noFly -> List<List<LatLng>>
        List<List<LatLng>> noFlyZones = new ArrayList<>();
        if (noFlyGp != null) {
            for (List<GeoPoint> poly : noFlyGp) {
                noFlyZones.add(toLatLngListGeo(poly));
            }
        }

        // 3) 生成航点
        List<Waypoint> waypoints = areaCoveragePlanner.generateCoverage(
                missionBoundary,
                noFlyZones,
                spacing,
                height,
                safetyMargin
        );
        if (waypoints == null) waypoints = new ArrayList<>();

        // 4) 组装 FlightPlan（把 boundary/noFly 也塞进去方便调试/导出）
        FlightPlan plan = new FlightPlan();
        plan.setDevice(device);
        plan.setPayload(payload);
        plan.setTemplateType(templateType != null ? templateType : "mapping2d");

        plan.setAutoFlightSpeed(flightSpeed != null ? flightSpeed : 10.0);
        plan.setGlobalShootHeight(height);
        plan.setWaypoints(waypoints);

        // flightArea / noFlyAreas（FlightPlan 里用的是 Waypoint）
        plan.setFlightArea(toWaypointListGeo(boundaryGp));
        plan.setNoFlyAreas(toWaypointPolyListGeo(noFlyGp));

        // 如果没传 payload，按机型补默认
        if (plan.getPayload() == null) {
            plan.setDefaultPayloadForDrone();
        }

        // 兼容可能存在的可选全局参数
        Double globalRTHHeight = read(req, "globalRTHHeight", Double.class);
        if (globalRTHHeight != null) plan.setGlobalRTHHeight(globalRTHHeight);

        String globalWaypointTurnMode = read(req, "globalWaypointTurnMode", String.class);
        if (globalWaypointTurnMode != null) plan.setGlobalWaypointTurnMode(globalWaypointTurnMode);

        Boolean globalUseStraightLine = read(req, "globalUseStraightLine", Boolean.class);
        if (globalUseStraightLine != null) plan.setGlobalUseStraightLine(globalUseStraightLine);

        String globalWaypointHeadingMode = read(req, "globalWaypointHeadingMode", String.class);
        if (globalWaypointHeadingMode != null) plan.setGlobalWaypointHeadingMode(globalWaypointHeadingMode);

        Double globalWaypointHeadingAngle = read(req, "globalWaypointHeadingAngle", Double.class);
        if (globalWaypointHeadingAngle != null) plan.setGlobalWaypointHeadingAngle(globalWaypointHeadingAngle);

        String finishAction = read(req, "finishAction", String.class);
        if (finishAction != null) plan.setFinishAction(finishAction);

        // takeoffPoint（如果你 dto 里有）
        Object takeoffPoint = read(req, "takeoffPoint", Object.class);
        if (takeoffPoint != null) {
            Double lon = read(takeoffPoint, "longitude", Double.class);
            Double lat = read(takeoffPoint, "latitude", Double.class);
            Double alt = read(takeoffPoint, "height", Double.class);
            if (lon != null && lat != null) {
                FlightPlan.TakeoffPoint tp = new FlightPlan.TakeoffPoint();
                tp.setLongitude(lon);
                tp.setLatitude(lat);
                tp.setHeight(alt != null ? alt : 0.0);
                plan.setTakeoffPoint(tp);
            }
        }

        return plan;
    }

    // =========================
    // GeoPoint -> LatLng / Waypoint
    // =========================
    private List<LatLng> toLatLngListGeo(List<GeoPoint> pts) {
        if (pts == null || pts.isEmpty()) return Collections.emptyList();
        List<LatLng> list = new ArrayList<>();
        for (GeoPoint p : pts) {
            Double lon = read(p, "lng", Double.class);
            if (lon == null) lon = read(p, "lon", Double.class);
            if (lon == null) lon = read(p, "longitude", Double.class);

            Double lat = read(p, "lat", Double.class);
            if (lat == null) lat = read(p, "latitude", Double.class);

            if (lon != null && lat != null) list.add(new LatLng(lon, lat));
        }
        return list;
    }

    private List<Waypoint> toWaypointListGeo(List<GeoPoint> pts) {
        if (pts == null || pts.isEmpty()) return new ArrayList<>();
        List<Waypoint> list = new ArrayList<>();
        for (GeoPoint p : pts) {
            Double lon = read(p, "lng", Double.class);
            if (lon == null) lon = read(p, "lon", Double.class);
            if (lon == null) lon = read(p, "longitude", Double.class);

            Double lat = read(p, "lat", Double.class);
            if (lat == null) lat = read(p, "latitude", Double.class);

            if (lon == null || lat == null) continue;

            Waypoint w = new Waypoint();
            w.setLongitude(lon);
            w.setLatitude(lat);
            list.add(w);
        }
        return list;
    }

    private List<List<Waypoint>> toWaypointPolyListGeo(List<List<GeoPoint>> polys) {
        if (polys == null || polys.isEmpty()) return new ArrayList<>();
        List<List<Waypoint>> out = new ArrayList<>();
        for (List<GeoPoint> poly : polys) {
            out.add(toWaypointListGeo(poly));
        }
        return out;
    }

    // =========================
    // 反射读取：getter 优先，失败再读字段
    // =========================
    @SuppressWarnings("unchecked")
    private <T> T read(Object obj, String name, Class<T> type) {
        if (obj == null) return null;

        // 1) getter
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            Method m = obj.getClass().getMethod(getter);
            Object v = m.invoke(obj);
            if (v == null) return null;
            return (T) v;
        } catch (Exception ignore) {}

        // 2) boolean getter: isXxx
        String isGetter = "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            Method m = obj.getClass().getMethod(isGetter);
            Object v = m.invoke(obj);
            if (v == null) return null;
            return (T) v;
        } catch (Exception ignore) {}

        // 3) field
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(obj);
            if (v == null) return null;
            return (T) v;
        } catch (Exception ignore) {}

        return null;
    }
}
