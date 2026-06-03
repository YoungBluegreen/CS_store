package com.example.djiwaypoint.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class OrthoMission {
    private FlightPlan plan;
    private List<Waypoint> waypoints;

    /** 正射拍摄相关 actionGroups（开拍/停拍/云台） */
    private List<WpmlActionGroup> actionGroups = new ArrayList<>();

    /** 供 writer 侧使用：哪些 index 需要挂 actionGroup */
    public List<WpmlActionGroup> groupsStartingAt(int index) {
        List<WpmlActionGroup> out = new ArrayList<>();
        if (actionGroups == null) return out;
        for (WpmlActionGroup g : actionGroups) {
            if (g != null && g.getStartIndex() == index) {
                out.add(g);
            }
        }
        return out;
    }

    public List<WpmlActionGroup> groupsEndingAt(int index) {
        List<WpmlActionGroup> out = new ArrayList<>();
        for (WpmlActionGroup g : actionGroups) if (g.getEndIndex() == index) out.add(g);
        return out;
    }
}
