// src/main/java/com/example/djiwaypoint/service/LocalProjector.java
package com.example.djiwaypoint.service;

import org.locationtech.jts.geom.Coordinate;

/**
 * 简单局部投影：以任务区域中心为原点，
 * 近似把经纬度转换成 x/y（单位: 米）
 */
public class LocalProjector {

    private static final double R = 6378137.0; // 地球半径 (m)

    private final double lat0Rad;
    private final double lon0Rad;

    public LocalProjector(double lat0Deg, double lon0Deg) {
        this.lat0Rad = Math.toRadians(lat0Deg);
        this.lon0Rad = Math.toRadians(lon0Deg);
    }

    public static LocalProjector fromLatLngArray(java.util.List<com.example.djiwaypoint.model.LatLng> pts) {
        double sumLat = 0, sumLon = 0;
        for (var p : pts) {
            sumLat += p.getLatitude();
            sumLon += p.getLongitude();
        }
        double lat0 = sumLat / pts.size();
        double lon0 = sumLon / pts.size();
        return new LocalProjector(lat0, lon0);
    }

    /** 经纬度转局部平面坐标 (x: 东, y: 北), 单位: m */
    public Coordinate toLocal(double lonDeg, double latDeg) {
        double latRad = Math.toRadians(latDeg);
        double lonRad = Math.toRadians(lonDeg);

        double dLat = latRad - lat0Rad;
        double dLon = lonRad - lon0Rad;

        double x = R * dLon * Math.cos(lat0Rad);
        double y = R * dLat;
        return new Coordinate(x, y);
    }

    /** 局部平面坐标 -> 经纬度 */
    public Coordinate toLonLat(Coordinate local) {
        double dLat = local.y / R;
        double dLon = local.x / (R * Math.cos(lat0Rad));

        double latRad = lat0Rad + dLat;
        double lonRad = lon0Rad + dLon;

        return new Coordinate(Math.toDegrees(lonRad), Math.toDegrees(latRad));
    }
}
