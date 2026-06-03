package com.example.djiwaypoint.service;

import com.example.djiwaypoint.model.Waypoint;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTWriter;

import java.util.ArrayList;
import java.util.List;

public class WktUtils {

    private static final GeometryFactory GF = new GeometryFactory();

    /**
     * 把 coverageArea + noFlyAreas 组合成：
     *   可飞区域 = 外轮廓 Polygon － 所有禁飞区 Polygon
     * 的 WKT（结果可能是 Polygon 或 MultiPolygon）
     */
    public static String buildCoverageWkt(List<Waypoint> coverageArea,
                                          List<List<Waypoint>> noFlyAreas) {

        if (coverageArea == null || coverageArea.size() < 3) {
            throw new IllegalArgumentException("coverageArea 点数不足，无法构造 Polygon");
        }

        // 1. 外轮廓：先转成闭合的线环
        LinearRing outerRing = toClosedRing(coverageArea);
        Polygon shell = GF.createPolygon(outerRing);

        Geometry result = shell;

        // 2. 依次把每个禁飞区“扣掉”：result = result - hole
        if (noFlyAreas != null && !noFlyAreas.isEmpty()) {
            List<Polygon> holePolygons = new ArrayList<>();
            for (List<Waypoint> area : noFlyAreas) {
                if (area == null || area.size() < 3) {
                    continue;
                }
                LinearRing holeRing = toClosedRing(area);
                Polygon holePoly = GF.createPolygon(holeRing);
                holePolygons.add(holePoly);
            }

            for (Polygon holePoly : holePolygons) {
                // 只在相交时才做 difference，避免不必要的计算
                if (result.intersects(holePoly)) {
                    result = result.difference(holePoly);
                }
            }
        }

        return new WKTWriter().write(result);
    }

    /**
     * 把一组 Waypoint 转成“首尾闭合”的 LinearRing
     * - 如果首尾已经相同，就不再重复
     * - 顺带去掉连续重复点，防止奇怪的折返
     */
    private static LinearRing toClosedRing(List<Waypoint> pts) {
        if (pts == null || pts.size() < 3) {
            throw new IllegalArgumentException("多边形顶点数量不足");
        }

        List<Coordinate> coordsList = new ArrayList<>();

        // 先去掉连续重复点
        Waypoint prev = null;
        for (Waypoint wp : pts) {
            if (wp == null) continue;
            if (prev != null &&
                    Double.compare(prev.getLongitude(), wp.getLongitude()) == 0 &&
                    Double.compare(prev.getLatitude(),  wp.getLatitude())  == 0) {
                // 连续重复点，跳过
                continue;
            }
            coordsList.add(new Coordinate(wp.getLongitude(), wp.getLatitude()));
            prev = wp;
        }

        if (coordsList.size() < 3) {
            throw new IllegalArgumentException("有效多角形顶点数量不足");
        }

        // 首尾闭合
        Coordinate first = coordsList.get(0);
        Coordinate last  = coordsList.get(coordsList.size() - 1);
        if (Double.compare(first.x, last.x) != 0 ||
                Double.compare(first.y, last.y) != 0) {
            coordsList.add(new Coordinate(first.x, first.y));
        }

        Coordinate[] coords = coordsList.toArray(new Coordinate[0]);
        return GF.createLinearRing(coords);
    }
}
