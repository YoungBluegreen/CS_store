// CoverageRequest.java
package com.example.djiwaypoint.dto;

import java.util.List;

public class CoverageRequest {

    private List<GeoPoint> waypoints;
    private Double coverageSpacing;
    private Double globalShootHeight;
    private List<List<GeoPoint>> noFlyAreas;

    public CoverageRequest() {}

    public List<GeoPoint> getWaypoints() { return waypoints; }
    public void setWaypoints(List<GeoPoint> waypoints) { this.waypoints = waypoints; }

    public Double getCoverageSpacing() { return coverageSpacing; }
    public void setCoverageSpacing(Double coverageSpacing) { this.coverageSpacing = coverageSpacing; }

    public Double getGlobalShootHeight() { return globalShootHeight; }
    public void setGlobalShootHeight(Double globalShootHeight) { this.globalShootHeight = globalShootHeight; }

    public List<List<GeoPoint>> getNoFlyAreas() { return noFlyAreas; }
    public void setNoFlyAreas(List<List<GeoPoint>> noFlyAreas) { this.noFlyAreas = noFlyAreas; }
}
