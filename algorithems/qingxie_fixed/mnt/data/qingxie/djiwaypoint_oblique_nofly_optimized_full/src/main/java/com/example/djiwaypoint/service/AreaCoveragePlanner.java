package com.example.djiwaypoint.service;

import com.example.djiwaypoint.model.LatLng;
import com.example.djiwaypoint.model.Waypoint;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;
import org.locationtech.jts.precision.EnhancedPrecisionOp;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 面状航线生成器（禁飞区强制生效版）
 * 解决：difference 失败被吞 -> 禁飞区被忽略
 */
@Service
public class AreaCoveragePlanner {

    private static final Logger log = LoggerFactory.getLogger(AreaCoveragePlanner.class);
    private final GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 0);

    private static final double EPS_AREA = 1e-6;
    private static final double PATH_TOLERANCE_M = 0.20;

    public List<Waypoint> generateCoverage(
            List<LatLng> missionBoundary,
            List<List<LatLng>> noFlyAreas,
            double spacingMeters,
            double flightHeight,
            double safetyMargin
    ) {
        return generateCoverage(missionBoundary, noFlyAreas, spacingMeters, flightHeight, safetyMargin, 0.0);
    }

    public List<Waypoint> generateCoverage(
            List<LatLng> missionBoundary,
            List<List<LatLng>> noFlyAreas,
            double spacingMeters,
            double flightHeight,
            double safetyMargin,
            double sweepAngleDeg
    ) {
        // ✅ 用于确认 Planner 真的被调用、禁飞区真的传进来
        log.info("[AP] invoked: missionPts={}, noFlyAreas={}",
                missionBoundary == null ? -1 : missionBoundary.size(),
                noFlyAreas == null ? 0 : noFlyAreas.size());

        if (missionBoundary == null || missionBoundary.size() < 3) {
            throw new IllegalArgumentException("任务区域顶点数量不足");
        }

        // 1) 初始化投影
        LocalProjector projector = new LocalProjector(missionBoundary.get(0));

        // 2) 构建任务区（先修复几何）
        Geometry missionGeom = buildPolygon(missionBoundary, projector).buffer(0);
        if (missionGeom.isEmpty() || !missionGeom.isValid()) {
            throw new IllegalStateException("任务区多边形无效/为空（可能自交/重复点/点序错误）");
        }

        // 3) 构建禁飞区（先修复几何；并拆 MultiPolygon）
        int rawNfCount = (noFlyAreas == null) ? 0 : noFlyAreas.size();
        List<Polygon> nfPolys = new ArrayList<>();

        if (noFlyAreas != null) {
            for (int i = 0; i < noFlyAreas.size(); i++) {
                List<LatLng> nf = noFlyAreas.get(i);
                if (nf == null || nf.size() < 3) {
                    log.warn("NFZ[{}] 顶点不足，忽略", i);
                    continue;
                }

                Geometry nfGeom = buildPolygon(nf, projector).buffer(0);
                if (nfGeom.isEmpty() || !nfGeom.isValid()) {
                    log.warn("NFZ[{}] 几何无效/为空（自交/重复点/点序），忽略", i);
                    continue;
                }

                double interArea = nfGeom.intersection(missionGeom).getArea();
                if (interArea < EPS_AREA) {
                    log.warn("NFZ[{}] 与任务区几乎不相交（interArea≈0）。优先检查：坐标系（高德GCJ-02未转WGS84）或禁飞区不在任务区内", i);
                }

                if (nfGeom instanceof Polygon) {
                    nfPolys.add((Polygon) nfGeom);
                } else if (nfGeom instanceof MultiPolygon) {
                    MultiPolygon mp = (MultiPolygon) nfGeom;
                    for (int k = 0; k < mp.getNumGeometries(); k++) {
                        Geometry g = mp.getGeometryN(k);
                        if (g instanceof Polygon) nfPolys.add((Polygon) g);
                    }
                } else {
                    log.warn("NFZ[{}] 类型不是 Polygon/MultiPolygon：{}，忽略", i, nfGeom.getGeometryType());
                }
            }
        }

        log.info("[AP] nf rawCount={}, validPolys={}", rawNfCount, nfPolys.size());

        // 前端传了禁飞区，但这里一个都没留下：直接报错（否则一定会生成穿禁飞区航线）
        if (rawNfCount > 0 && nfPolys.isEmpty()) {
            throw new IllegalStateException("前端传入了禁飞区，但全部无效/被过滤（顶点不足、自交、重复点、坐标系错误等）");
        }

        // 4) 计算可飞区域（严格差集）
        double actualMargin = Math.max(safetyMargin, 0.5);
        Geometry allowed = buildAllowedAreaStrict(missionGeom, nfPolys, actualMargin);

        if (allowed.isEmpty()) {
            throw new IllegalStateException("可飞区域为空（禁飞区/安全距离过大 或 几何异常/坐标系异常）");
        }

        // ✅ 硬校验：传了禁飞区但面积几乎不变 -> 直接报错
        if (!nfPolys.isEmpty()) {
            double delta = Math.abs(missionGeom.getArea() - allowed.getArea());
            log.info("[AP] missionArea={}, allowedArea={}, delta={}", missionGeom.getArea(), allowed.getArea(), delta);
            if (delta < EPS_AREA) {
                throw new IllegalStateException("禁飞区差集未生效：面积几乎不变。优先检查：GCJ-02→WGS84、禁飞区点序/重复点/自交");
            }
        }

        // 5) 生成蛇形路径（支持旋转扫描方向）
        double sweepRad = Math.toRadians(sweepAngleDeg);
        Geometry sweepFrameAllowed = Math.abs(sweepAngleDeg) < 1e-9 ? allowed : rotateGeometry(allowed, -sweepRad);
        Geometry sweepFrameAllowedTol = sweepFrameAllowed.buffer(PATH_TOLERANCE_M).buffer(0);
        List<Coordinate> pathSweepFrame = buildSnakePath(sweepFrameAllowed, sweepFrameAllowedTol, spacingMeters);
        List<Coordinate> pathLocal = Math.abs(sweepAngleDeg) < 1e-9
                ? pathSweepFrame
                : rotateCoordinates(pathSweepFrame, sweepRad);

// ===== 航段合法性校验：任意线段必须完全在 allowed 内 =====
        // ===== 航段合法性校验：允许一定容差，避免几何边界误判 =====
// 你禁飞区安全外扩是 5m，这里给 0.20m 容差不会影响安全性，但能显著减少 JTS 边界毛刺误判
        Geometry allowedTol = allowed.buffer(0.20).buffer(0);

        for (int i = 0; i < pathLocal.size() - 1; i++) {
            Coordinate a = pathLocal.get(i);
            Coordinate b = pathLocal.get(i + 1);
            LineString seg = gf.createLineString(new Coordinate[]{a, b});

            if (!allowedTol.covers(seg)) {
                // 帮你定位：把这一段的端点打印出来
                log.error("[AP] segment violation i={} A=({},{}) B=({},{})",
                        i, a.x, a.y, b.x, b.y);
                throw new IllegalStateException("检测到航段穿越禁飞区/不可飞区域：segmentIndex=" + i);
            }
        }


        // 6) 转回经纬度
        List<Waypoint> waypoints = new ArrayList<>();
        int idx = 0;
        for (Coordinate cLocal : pathLocal) {
            Coordinate lonLat = projector.toLonLat(cLocal);
            Waypoint wp = new Waypoint();
            wp.setIndex(idx++);
            wp.setLongitude(lonLat.x);
            wp.setLatitude(lonLat.y);
            wp.setHeight(flightHeight);
            wp.setEllipsoidHeight(flightHeight);
            waypoints.add(wp);
        }

        log.info("[AP] generated waypoints={}", waypoints.size());
        return waypoints;
    }

    public List<Waypoint> generateSafeTransfer(
            List<LatLng> missionBoundary,
            List<List<LatLng>> noFlyAreas,
            Waypoint from,
            Waypoint to,
            double flightHeight,
            double safetyMargin
    ) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("转场起终点不能为空");
        }
        if (missionBoundary == null || missionBoundary.size() < 3) {
            throw new IllegalArgumentException("任务区域顶点数量不足，无法生成转场航线");
        }

        LocalProjector projector = new LocalProjector(missionBoundary.get(0));
        Geometry missionGeom = buildPolygon(missionBoundary, projector).buffer(0);
        if (missionGeom.isEmpty() || !missionGeom.isValid()) {
            throw new IllegalStateException("任务区多边形无效/为空，无法生成转场航线");
        }

        List<Polygon> nfPolys = new ArrayList<>();
        if (noFlyAreas != null) {
            for (List<LatLng> nf : noFlyAreas) {
                if (nf == null || nf.size() < 3) continue;
                Geometry nfGeom = buildPolygon(nf, projector).buffer(0);
                if (nfGeom.isEmpty() || !nfGeom.isValid()) continue;
                if (nfGeom instanceof Polygon) {
                    nfPolys.add((Polygon) nfGeom);
                } else if (nfGeom instanceof MultiPolygon) {
                    MultiPolygon mp = (MultiPolygon) nfGeom;
                    for (int k = 0; k < mp.getNumGeometries(); k++) {
                        Geometry g = mp.getGeometryN(k);
                        if (g instanceof Polygon) nfPolys.add((Polygon) g);
                    }
                }
            }
        }

        Geometry allowed = buildAllowedAreaStrict(missionGeom, nfPolys, Math.max(safetyMargin, 0.5));
        Geometry allowedTol = allowed.buffer(PATH_TOLERANCE_M).buffer(0);

        Coordinate start = projector.toLocal(from.getLongitude(), from.getLatitude());
        Coordinate end = projector.toLocal(to.getLongitude(), to.getLatitude());
        List<Coordinate> pathLocal = getSafePath(start, end, allowed, allowedTol);
        if (pathLocal.isEmpty() || !isPathCovered(pathLocal, allowedTol)) {
            throw new IllegalStateException("无法生成安全转场航线");
        }

        List<Waypoint> out = new ArrayList<>();
        for (int i = 0; i < pathLocal.size(); i++) {
            Coordinate lonLat = projector.toLonLat(pathLocal.get(i));
            Waypoint wp = new Waypoint();
            wp.setIndex(i);
            wp.setLongitude(lonLat.x);
            wp.setLatitude(lonLat.y);
            wp.setHeight(flightHeight);
            wp.setEllipsoidHeight(flightHeight);
            out.add(wp);
        }
        return out;
    }

    // ================= 你的蛇形/绕行逻辑（保留原风格） =================

    private List<Coordinate> buildSnakePath(Geometry allowed, Geometry allowedTol, double spacingMeters) {
        List<Stripe> stripes = new ArrayList<>();
        Envelope env = allowed.getEnvelopeInternal();

        double extra = 5000.0;
        double minSegLen = Math.min(1.0, spacingMeters * 0.1);

        double y = env.getMinY() + spacingMeters / 2;
        while (y <= env.getMaxY()) {
            Coordinate p1 = new Coordinate(env.getMinX() - extra, y);
            Coordinate p2 = new Coordinate(env.getMaxX() + extra, y);
            LineString sweep = gf.createLineString(new Coordinate[]{p1, p2});

            Geometry cross = allowed.intersection(sweep);
            if (!cross.isEmpty()) {
                Stripe stripe = new Stripe(y);
                extractLineStrings(cross, stripe.segments);
                stripe.segments.removeIf(s -> s.getLength() < minSegLen);

                if (!stripe.segments.isEmpty()) {
                    stripe.segments.sort((a, b) -> Double.compare(a.getCoordinateN(0).x, b.getCoordinateN(0).x));
                    stripes.add(stripe);
                }
            }
            y += spacingMeters;
        }

        List<Coordinate> path = new ArrayList<>();
        boolean leftToRight = true;

        for (Stripe stripe : stripes) {
            List<LineString> segs = new ArrayList<>(stripe.segments);
            if (!leftToRight) Collections.reverse(segs);

            for (LineString seg : segs) {
                Coordinate start = seg.getCoordinateN(0);
                Coordinate end = seg.getCoordinateN(seg.getNumPoints() - 1);

                boolean segIsWestToEast = start.x < end.x;
                Coordinate west = segIsWestToEast ? start : end;
                Coordinate east = segIsWestToEast ? end : start;

                Coordinate from = leftToRight ? west : east;
                Coordinate to   = leftToRight ? east : west;

                if (path.isEmpty()) {
                    path.add(from);
                } else {
                    Coordinate last = path.get(path.size() - 1);
                    if (!isSamePoint(last, from)) {
                        // 强制绕行（保留你 V5 风格）
                        List<Coordinate> detour = getSafePath(last, from, allowed, allowedTol);

                        if (!detour.isEmpty()) {
                            if (isSamePoint(detour.get(0), last)) detour.remove(0);
                            path.addAll(detour);
                        } else {
                            log.error("无法生成安全绕行路径: {} -> {}", last, from);
                            throw new IllegalStateException("无法生成安全绕行路径，区域可能被禁飞区切断");
                        }
                    }
                }
                path.add(to);
            }
            leftToRight = !leftToRight;
        }
        return path;
    }

    private List<Coordinate> getSafePath(Coordinate p1, Coordinate p2, Geometry allowed, Geometry allowedTol) {
        if (isSegmentCovered(p1, p2, allowedTol)) {
            List<Coordinate> direct = new ArrayList<>();
            direct.add(p1);
            direct.add(p2);
            return direct;
        }

        List<LineString> rings = getAllRings(allowedTol);
        if (rings.isEmpty()) return Collections.emptyList();

        List<Coordinate> detour = shortestBoundaryDetour(rings, p1, p2, allowedTol);
        if (!detour.isEmpty()) return detour;

        List<LineString> strictRings = getAllRings(allowed);
        if (!strictRings.isEmpty()) {
            detour = shortestBoundaryDetour(strictRings, p1, p2, allowedTol);
            if (!detour.isEmpty()) return detour;
        }

        return Collections.emptyList();
    }

    private List<Coordinate> shortestBoundaryDetour(List<LineString> rings, Coordinate p1, Coordinate p2, Geometry allowedTol) {
        double bestTotalCost = Double.MAX_VALUE;
        List<Coordinate> bestPath = Collections.emptyList();

        for (LineString ring : rings) {
            if (!ring.isClosed()) continue;

            LengthIndexedLine lil = new LengthIndexedLine(ring);
            double i1 = lil.project(p1);
            double i2 = lil.project(p2);
            Coordinate q1 = lil.extractPoint(i1);
            Coordinate q2 = lil.extractPoint(i2);

            List<Coordinate> pathFwd = extractRingPath(ring, i1, i2, false, lil);
            List<Coordinate> pathRev = extractRingPath(ring, i1, i2, true, lil);

            double lenFwd = getPathLength(pathFwd);
            double lenRev = getPathLength(pathRev);

            List<Coordinate> ringPath = (lenFwd < lenRev) ? pathFwd : pathRev;
            double ringLen = Math.min(lenFwd, lenRev);

            double totalCost = p1.distance(q1) + ringLen + q2.distance(p2);

            List<Coordinate> candidate = buildCandidatePath(p1, q1, ringPath, q2, p2);
            if (candidate.isEmpty() || !isPathCovered(candidate, allowedTol)) {
                continue;
            }

            if (totalCost < bestTotalCost) {
                bestTotalCost = totalCost;

                bestPath = candidate;
            }
        }
        return bestPath;
    }

    private List<Coordinate> buildCandidatePath(Coordinate p1, Coordinate q1, List<Coordinate> ringPath, Coordinate q2, Coordinate p2) {
        List<Coordinate> candidate = new ArrayList<>();
        candidate.add(copyCoordinate(p1));
        appendCoordinate(candidate, q1);
        if (ringPath != null) {
            for (Coordinate c : ringPath) {
                appendCoordinate(candidate, c);
            }
        }
        appendCoordinate(candidate, q2);
        appendCoordinate(candidate, p2);
        return candidate;
    }

    private void appendCoordinate(List<Coordinate> path, Coordinate c) {
        if (c == null) return;
        if (path.isEmpty() || !isSamePoint(path.get(path.size() - 1), c)) {
            path.add(copyCoordinate(c));
        }
    }

    private Coordinate copyCoordinate(Coordinate c) {
        return new Coordinate(c.x, c.y, c.getZ());
    }

    private boolean isPathCovered(List<Coordinate> path, Geometry allowedTol) {
        if (path == null || path.size() < 2) return false;
        for (int i = 0; i < path.size() - 1; i++) {
            if (!isSegmentCovered(path.get(i), path.get(i + 1), allowedTol)) {
                return false;
            }
        }
        return true;
    }

    private boolean isSegmentCovered(Coordinate a, Coordinate b, Geometry allowedTol) {
        LineString seg = gf.createLineString(new Coordinate[]{a, b});
        return allowedTol.covers(seg);
    }

    private boolean isSamePoint(Coordinate c1, Coordinate c2) {
        return c1.distance(c2) < 0.01; // 1cm（米制）
    }

    private List<LineString> getAllRings(Geometry g) {
        List<LineString> rings = new ArrayList<>();
        if (g instanceof Polygon) {
            Polygon p = (Polygon) g;
            rings.add(p.getExteriorRing());
            for (int i = 0; i < p.getNumInteriorRing(); i++) rings.add(p.getInteriorRingN(i));
        } else if (g instanceof GeometryCollection) {
            for (int i = 0; i < g.getNumGeometries(); i++) rings.addAll(getAllRings(g.getGeometryN(i)));
        }
        return rings;
    }

    private List<Coordinate> extractRingPath(LineString ring, double i1, double i2, boolean reverse, LengthIndexedLine lil) {
        double L = ring.getLength();
        List<Coordinate> coords = new ArrayList<>();

        if (!reverse) {
            if (i2 >= i1) {
                LineString ls = (LineString) lil.extractLine(i1, i2);
                Collections.addAll(coords, ls.getCoordinates());
            } else {
                LineString p1 = (LineString) lil.extractLine(i1, L);
                LineString p2 = (LineString) lil.extractLine(0, i2);
                Collections.addAll(coords, p1.getCoordinates());
                Coordinate[] c2 = p2.getCoordinates();
                for (int k = 1; k < c2.length; k++) coords.add(c2[k]);
            }
        } else {
            List<Coordinate> fwd = extractRingPath(ring, i2, i1, false, lil);
            Collections.reverse(fwd);
            coords = fwd;
        }
        return coords;
    }

    private void extractLineStrings(Geometry g, List<LineString> result) {
        for (int i = 0; i < g.getNumGeometries(); i++) {
            Geometry sub = g.getGeometryN(i);
            if (sub instanceof LineString) result.add((LineString) sub);
            else if (sub instanceof GeometryCollection) extractLineStrings(sub, result);
        }
    }

    private double getPathLength(List<Coordinate> path) {
        double l = 0;
        for (int i = 0; i < path.size() - 1; i++) l += path.get(i).distance(path.get(i + 1));
        return l;
    }

    private Geometry rotateGeometry(Geometry g, double angleRad) {
        AffineTransformation tx = AffineTransformation.rotationInstance(angleRad);
        return tx.transform(g);
    }

    private List<Coordinate> rotateCoordinates(List<Coordinate> coords, double angleRad) {
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        List<Coordinate> out = new ArrayList<>(coords.size());
        for (Coordinate c : coords) {
            double x = c.x * cos - c.y * sin;
            double y = c.x * sin + c.y * cos;
            out.add(new Coordinate(x, y));
        }
        return out;
    }

    private Polygon buildPolygon(List<LatLng> pts, LocalProjector projector) {
        int n = pts.size();
        Coordinate[] coords = new Coordinate[n + 1];
        for (int i = 0; i < n; i++) {
            LatLng p = pts.get(i);
            coords[i] = projector.toLocal(p.getLongitude(), p.getLatitude());
        }
        coords[n] = coords[0];
        LinearRing shell = gf.createLinearRing(coords);
        return gf.createPolygon(shell);
    }

    /**
     * 严格差集：失败不吞，必须保证禁飞区真的被扣除
     */
    private Geometry buildAllowedAreaStrict(Geometry missionGeom, List<Polygon> noFlyPolys, double safetyMargin) {
        Geometry missionClean = missionGeom.buffer(0);
        if (noFlyPolys == null || noFlyPolys.isEmpty()) return missionClean;

        double bufferDist = Math.max(0.1, safetyMargin);
        List<Polygon> bufferedNfz = new ArrayList<>();

        for (Polygon nfz : noFlyPolys) {
            Geometry nfzClean = nfz.buffer(0);
            if (nfzClean.isEmpty()) continue;

            Geometry buffered = nfzClean.buffer(bufferDist, 8).buffer(0);
            for (int i = 0; i < buffered.getNumGeometries(); i++) {
                Geometry g = buffered.getGeometryN(i);
                if (g instanceof Polygon) bufferedNfz.add((Polygon) g);
            }
        }

        if (bufferedNfz.isEmpty()) {
            throw new IllegalStateException("禁飞区 buffer 后全部为空/无效，请检查禁飞区顶点/坐标系");
        }

        Geometry nfUnion = CascadedPolygonUnion.union(bufferedNfz).buffer(0);

        // 1) 普通差集
        try {
            return missionClean.difference(nfUnion).buffer(0);
        } catch (Exception e1) {
            log.warn("difference 失败，尝试 EnhancedPrecisionOp：{}", e1.getMessage());
        }

        // 2) EnhancedPrecisionOp（兼容老 JTS）
        try {
            return EnhancedPrecisionOp.difference(missionClean, nfUnion).buffer(0);
        } catch (Exception e2) {
            log.warn("EnhancedPrecisionOp 失败，尝试 OverlayNG（若存在）：{}", e2.getMessage());
        }

        // 3) OverlayNG（反射调用，避免你工程没该类导致无法编译）
        try {
            Class<?> overlayNg = Class.forName("org.locationtech.jts.operation.overlayng.OverlayNG");
            Method overlay = overlayNg.getMethod("overlay", Geometry.class, Geometry.class, int.class);
            int DIFFERENCE = overlayNg.getField("DIFFERENCE").getInt(null);
            Geometry diff3 = (Geometry) overlay.invoke(null, missionClean, nfUnion, DIFFERENCE);
            return diff3.buffer(0);
        } catch (Exception e3) {
            throw new IllegalStateException("禁飞区差集彻底失败（几何无效/自交/坐标系异常）。优先检查：GCJ-02→WGS84、点序/重复点", e3);
        }
    }

    private static class Stripe {
        double y;
        List<LineString> segments = new ArrayList<>();
        Stripe(double y) { this.y = y; }
    }

    private static class LocalProjector {
        private final double originLat, originLon, metersPerLat, metersPerLon;

        public LocalProjector(LatLng origin) {
            this.originLat = origin.getLatitude();
            this.originLon = origin.getLongitude();
            double radLat = Math.toRadians(originLat);

            // lat：约 111132m/deg
            this.metersPerLat = 111132.954 - 559.822 * Math.cos(2 * radLat);

            // ✅ lon：约 111320m/deg * cos(lat)（比你原先 111132.954*cos 更准）
            this.metersPerLon = 111320.0 * Math.cos(radLat);
        }

        public Coordinate toLocal(double lon, double lat) {
            return new Coordinate((lon - originLon) * metersPerLon, (lat - originLat) * metersPerLat);
        }

        public Coordinate toLonLat(Coordinate c) {
            return new Coordinate(originLon + c.x / metersPerLon, originLat + c.y / metersPerLat);
        }
    }
}
