package com.example.djiwaypoint.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PathRouter
 *
 * 用于在已有覆盖条带(COVERAGE segments)之间插入路径级绕飞连接段(CONNECTOR segments)，
 * 确保所有连接段在几何上不穿过任何圆形禁飞区(NoFlyCircle)。
 *
 * 使用方法：
 * 1. 把蛇形航线生成的每一条“覆盖条带”封装为一个 PathSegment，type = COVERAGE，nodes 为该条带的 PathNode 列表。
 * 2. 准备好禁飞区列表 List<NoFlyCircle>（单位：米）。
 * 3. 调用 buildRoutedPath(coverageSegments, noFlyCircles)，得到 RoutedPath。
 * 4. 后续 KMZGenerator 只需按 RoutedPath.segments 顺序生成多条 wayline。
 */
public class PathRouter {

    // ===================== 对外暴露的数据结构 =====================

    public enum SegmentType {
        COVERAGE,   // 覆盖条带
        CONNECTOR   // 段与段之间的连接绕飞
    }

    /**
     * 简单的空间点：经度/纬度/高度
     */
    public static class PathNode {
        public double lon;   // degrees
        public double lat;   // degrees
        public double alt;   // meters

        public PathNode(double lon, double lat, double alt) {
            this.lon = lon;
            this.lat = lat;
            this.alt = alt;
        }
    }

    /**
     * 航迹段：包含一串点，以及段类型（覆盖或连接）
     */
    public static class PathSegment {
        public SegmentType type;
        public List<PathNode> nodes;

        public PathSegment(SegmentType type, List<PathNode> nodes) {
            this.type = type;
            this.nodes = nodes != null ? nodes : new ArrayList<>();
        }
    }

    /**
     * 圆形禁飞区
     */
    public static class NoFlyCircle {
        public double lon;            // center lon (degrees)
        public double lat;            // center lat (degrees)
        public double radiusMeters;   // 半径（米）
        public double safetyMargin;   // 额外安全距离（米）

        public NoFlyCircle(double lon, double lat, double radiusMeters, double safetyMargin) {
            this.lon = lon;
            this.lat = lat;
            this.radiusMeters = radiusMeters;
            this.safetyMargin = safetyMargin;
        }
    }

    /**
     * 路由后的完整路径：按飞行顺序排好的 segments
     */
    public static class RoutedPath {
        public List<PathSegment> segments = new ArrayList<>();
    }

    // ===================== 对外主入口 =====================

    /**
     * 输入一组“覆盖条带” segments，自动在相邻条带之间插入路径级绕飞连接段。
     *
     * @param coverageSegments  只包含 type = COVERAGE 的 PathSegment，顺序即飞行顺序
     * @param noFlyCircles      圆形禁飞区列表（可为空）
     * @return                  带有 CONNECTOR 段的完整 RoutedPath
     */
    public static RoutedPath buildRoutedPath(List<PathSegment> coverageSegments,
                                             List<NoFlyCircle> noFlyCircles) {

        if (coverageSegments == null) {
            coverageSegments = Collections.emptyList();
        }
        if (noFlyCircles == null) {
            noFlyCircles = Collections.emptyList();
        }

        RoutedPath result = new RoutedPath();

        if (coverageSegments.isEmpty()) {
            return result;
        }

        // 构建局部平面坐标系（米），用于判定线段与圆的相交关系
        LocalFrame frame = LocalFrame.fromSegmentsAndCircles(coverageSegments, noFlyCircles);

        // 先加入第一条覆盖段
        result.segments.add(coverageSegments.get(0));

        // 相邻覆盖条带之间插入 CONNECTOR 段（必要时）
        for (int i = 0; i < coverageSegments.size() - 1; i++) {
            PathSegment cur = coverageSegments.get(i);
            PathSegment next = coverageSegments.get(i + 1);

            if (cur.nodes.isEmpty() || next.nodes.isEmpty()) {
                // 有问题的条带，直接跳过连接
                result.segments.add(next);
                continue;
            }

            PathNode p = cur.nodes.get(cur.nodes.size() - 1);   // 当前条带末端
            PathNode q = next.nodes.get(0);                     // 下一条带起点

            List<PathNode> detourRoute = buildDetourPath(frame, p, q, noFlyCircles);

            // detourRoute 含 p,q；只在存在中间点时才插入 CONNECTOR 段，避免重复航点
            if (detourRoute.size() > 2) {
                List<PathNode> inner = detourRoute.subList(1, detourRoute.size() - 1);
                if (!inner.isEmpty()) {
                    PathSegment connector = new PathSegment(SegmentType.CONNECTOR, new ArrayList<>(inner));
                    result.segments.add(connector);
                }
            }

            // 再加下一条覆盖段
            result.segments.add(next);
        }

        return result;
    }

    // ===================== 核心几何逻辑 =====================

    /**
     * 计算从 p 到 q 的路径，如果直线不穿任何禁飞区，返回 [p, q]；
     * 否则构造 P-M-H-Q 类型的折线路径绕飞（必要时迭代增大偏移距离）。
     */
    private static List<PathNode> buildDetourPath(LocalFrame frame,
                                                  PathNode p,
                                                  PathNode q,
                                                  List<NoFlyCircle> noFlyCircles) {

        if (noFlyCircles == null || noFlyCircles.isEmpty()) {
            // 没有禁飞区
            List<PathNode> direct = new ArrayList<>();
            direct.add(p);
            direct.add(q);
            return direct;
        }

        Vec2 P = frame.toLocal(p.lon, p.lat);
        Vec2 Q = frame.toLocal(q.lon, q.lat);

        // 先检查直接连线是否穿任何圈
        if (!intersectsAnyCircle(frame, P, Q, noFlyCircles)) {
            List<PathNode> direct = new ArrayList<>();
            direct.add(p);
            direct.add(q);
            return direct;
        }

        // 构造绕飞折线：以 PQ 中点为基准，沿垂直方向偏移
        Vec2 v = Q.sub(P);
        double len = v.length();
        if (len < 1e-3) {
            // p、q 几乎重合，实在没法绕，直接返回
            List<PathNode> same = new ArrayList<>();
            same.add(p);
            same.add(q);
            return same;
        }

        Vec2 u = v.scale(1.0 / len);   // 单位方向向量
        Vec2 n = new Vec2(-u.y, u.x);  // 垂直方向之一

        Vec2 M = P.add(Q).scale(0.5);  // 中点

        // 估一个“典型半径”，以选择初始偏移距离
        double typicalRadius = estimateTypicalRadius(noFlyCircles);
        if (typicalRadius <= 0) {
            typicalRadius = 20.0; // fallback：20m
        }

        double d = 1.5 * typicalRadius;   // 初始偏移
        int maxIter = 6;

        Vec2 H = null;
        for (int i = 0; i < maxIter; i++) {
            Vec2 candidateH = M.add(n.scale(d));

            // 检查 P-H 和 H-Q 是否都不穿任一禁飞圈
            if (!intersectsAnyCircle(frame, P, candidateH, noFlyCircles)
                    && !intersectsAnyCircle(frame, candidateH, Q, noFlyCircles)) {
                H = candidateH;
                break;
            }

            // 尝试反方向
            Vec2 candidateH2 = M.add(n.scale(-d));
            if (!intersectsAnyCircle(frame, P, candidateH2, noFlyCircles)
                    && !intersectsAnyCircle(frame, candidateH2, Q, noFlyCircles)) {
                H = candidateH2;
                break;
            }

            // 再绕远一点
            d *= 1.5;
        }

        List<PathNode> result = new ArrayList<>();
        result.add(p);

        if (H != null) {
            double[] lonLat = frame.toLonLat(H);
            double altMid = (p.alt + q.alt) * 0.5;
            PathNode midNode = new PathNode(lonLat[0], lonLat[1], altMid);
            result.add(midNode);
        }

        result.add(q);
        return result;
    }

    /**
     * 判断线段 AB 是否与任一禁飞圈相交
     */
    private static boolean intersectsAnyCircle(LocalFrame frame,
                                               Vec2 A,
                                               Vec2 B,
                                               List<NoFlyCircle> noFlyCircles) {
        for (NoFlyCircle c : noFlyCircles) {
            Vec2 C = frame.toLocal(c.lon, c.lat);
            double r = (c.radiusMeters > 0 ? c.radiusMeters : 0.0)
                    + (c.safetyMargin > 0 ? c.safetyMargin : 0.0);

            if (segmentIntersectsCircle(A, B, C, r)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 线段 AB 与圆 (C, r) 是否相交（包括穿越和进入）
     */
    private static boolean segmentIntersectsCircle(Vec2 A, Vec2 B, Vec2 C, double r) {
        Vec2 AB = B.sub(A);
        double ab2 = AB.dot(AB);
        if (ab2 < 1e-6) {
            // A、B 几乎重合，退化为点到圆
            double dist2 = C.sub(A).dot(C.sub(A));
            return dist2 < r * r;
        }

        double t = C.sub(A).dot(AB) / ab2;
        t = Math.max(0.0, Math.min(1.0, t));   // clamp 到 [0,1]

        Vec2 closest = A.add(AB.scale(t));
        Vec2 diff = C.sub(closest);
        double dist2 = diff.dot(diff);

        return dist2 < r * r;
    }

    /**
     * 粗略估计一个“典型半径”，用于选择初始偏移距离 d
     */
    private static double estimateTypicalRadius(List<NoFlyCircle> noFlyCircles) {
        if (noFlyCircles == null || noFlyCircles.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        int count = 0;
        for (NoFlyCircle c : noFlyCircles) {
            if (c.radiusMeters > 0) {
                sum += c.radiusMeters;
                count++;
            }
        }
        if (count == 0) {
            return 0.0;
        }
        return sum / count;
    }

    // ===================== 局部坐标系 & 向量工具 =====================

    /**
     * 简单 2D 向量（单位：米）
     */
    private static class Vec2 {
        final double x;
        final double y;

        Vec2(double x, double y) {
            this.x = x;
            this.y = y;
        }

        Vec2 add(Vec2 other) {
            return new Vec2(this.x + other.x, this.y + other.y);
        }

        Vec2 sub(Vec2 other) {
            return new Vec2(this.x - other.x, this.y - other.y);
        }

        Vec2 scale(double s) {
            return new Vec2(this.x * s, this.y * s);
        }

        double dot(Vec2 other) {
            return this.x * other.x + this.y * other.y;
        }

        double length() {
            return Math.sqrt(this.x * this.x + this.y * this.y);
        }
    }

    /**
     * 将经纬度(度)映射到局部平面坐标(米)的简单近似
     */
    private static class LocalFrame {
        private static final double EARTH_RADIUS = 6378137.0; // meters

        private final double lon0Rad;
        private final double lat0Rad;

        private LocalFrame(double lon0Deg, double lat0Deg) {
            this.lon0Rad = Math.toRadians(lon0Deg);
            this.lat0Rad = Math.toRadians(lat0Deg);
        }

        /**
         * 根据所有段和禁飞区的平均位置构建局部坐标系
         */
        static LocalFrame fromSegmentsAndCircles(List<PathSegment> segments,
                                                 List<NoFlyCircle> circles) {
            double sumLon = 0.0;
            double sumLat = 0.0;
            int count = 0;

            if (segments != null) {
                for (PathSegment seg : segments) {
                    if (seg.nodes == null) continue;
                    for (PathNode n : seg.nodes) {
                        sumLon += n.lon;
                        sumLat += n.lat;
                        count++;
                    }
                }
            }

            if (circles != null) {
                for (NoFlyCircle c : circles) {
                    sumLon += c.lon;
                    sumLat += c.lat;
                    count++;
                }
            }

            if (count == 0) {
                // fallback：北京附近（随便给一个，不会真的用到）
                return new LocalFrame(116.0, 40.0);
            }

            double lon0 = sumLon / count;
            double lat0 = sumLat / count;
            return new LocalFrame(lon0, lat0);
        }

        /**
         * 经纬度 → 本地二维坐标（米）
         */
        Vec2 toLocal(double lonDeg, double latDeg) {
            double lonRad = Math.toRadians(lonDeg);
            double latRad = Math.toRadians(latDeg);

            double dLon = lonRad - lon0Rad;
            double dLat = latRad - lat0Rad;

            double x = EARTH_RADIUS * dLon * Math.cos(lat0Rad);
            double y = EARTH_RADIUS * dLat;
            return new Vec2(x, y);
        }

        /**
         * 本地二维坐标（米） → 经纬度（度）
         */
        double[] toLonLat(Vec2 p) {
            double dLat = p.y / EARTH_RADIUS;
            double dLon = p.x / (EARTH_RADIUS * Math.cos(lat0Rad));

            double latRad = lat0Rad + dLat;
            double lonRad = lon0Rad + dLon;

            double latDeg = Math.toDegrees(latRad);
            double lonDeg = Math.toDegrees(lonRad);
            return new double[]{lonDeg, latDeg};
        }
    }

    // ===================== （可选）简单自测 main =====================
    // 你可以在本地跑一下 main，看打印的点是否绕开禁飞圈。
    /*
    public static void main(String[] args) {
        PathNode p = new PathNode(116.0, 40.0, 100);
        PathNode q = new PathNode(116.001, 40.0, 100);

        List<PathSegment> segments = new ArrayList<>();
        List<PathNode> nodes1 = new ArrayList<>();
        nodes1.add(p);
        segments.add(new PathSegment(SegmentType.COVERAGE, nodes1));

        List<PathNode> nodes2 = new ArrayList<>();
        nodes2.add(q);
        segments.add(new PathSegment(SegmentType.COVERAGE, nodes2));

        List<NoFlyCircle> circles = new ArrayList<>();
        // 圆心大约在中间，半径约 50m
        circles.add(new NoFlyCircle(116.0005, 40.0, 50, 20));

        RoutedPath path = buildRoutedPath(segments, circles);

        int idx = 0;
        for (PathSegment seg : path.segments) {
            System.out.println("Segment " + (idx++) + " type=" + seg.type);
            for (PathNode n : seg.nodes) {
                System.out.println("  " + n.lon + ", " + n.lat + ", alt=" + n.alt);
            }
        }
    }
    */
}
