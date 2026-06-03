package com.example.djiwaypoint.service;

import com.example.djiwaypoint.model.FlightPlan;
import com.example.djiwaypoint.model.OrthoMission;
import com.example.djiwaypoint.model.Waypoint;
import com.example.djiwaypoint.model.WpmlAction;
import com.example.djiwaypoint.model.WpmlActionGroup;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
public class OrthoMissionBuilder {

    /** photoIntervalMeter 没填时默认 40m */
    private static final double DEFAULT_INTERVAL_M = 40.0;
    private static final double DEFAULT_OBLIQUE_PITCH = -45.0;
    private static final double MIN_SEGMENT_METERS = 0.30;

    public OrthoMission build(FlightPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan 不能为空");
        }

        String missionType = plan.getMissionType() == null
                ? "ortho"
                : plan.getMissionType().trim().toLowerCase(Locale.ROOT);

        if ("oblique".equals(missionType)) {
            return buildOblique(plan);
        }
        return buildOrtho(plan);
    }

    private OrthoMission buildOrtho(FlightPlan plan) {
        List<Waypoint> nadir = sanitizeSection(withGimbalPitch(cloneWaypoints(plan.getWaypoints()), -90.0));
        ensureWaypoints(nadir);

        reindex(nadir);

        int lastIdx = nadir.size() - 1;
        double interval = resolveInterval(plan);

        OrthoMission mission = new OrthoMission();
        mission.setPlan(plan);
        mission.setWaypoints(nadir);

        List<WpmlActionGroup> groups = new ArrayList<>();
        int groupId = 0;
        groups.add(buildGimbalRotateAtStart(groupId++, 0, -90.0));
        groups.add(buildDistanceShooting(groupId++, 0, lastIdx, interval));
        groups.add(buildStopShootingAtEnd(groupId++, lastIdx));
        mission.setActionGroups(groups);

        return mission;
    }

    private OrthoMission buildOblique(FlightPlan plan) {
        ensureWaypoints(plan.getWaypoints());
        ensureWaypoints(plan.getObliqueCrossWaypoints());

        double interval = resolveInterval(plan);
        double obliquePitch = resolveObliquePitch(plan);

        List<Waypoint> merged = new ArrayList<>();
        List<WpmlActionGroup> groups = new ArrayList<>();
        int groupId = 0;

        List<Waypoint> nadirForward = sanitizeSection(withGimbalPitch(cloneWaypoints(plan.getWaypoints()), -90.0));
        List<Waypoint> nadirReverse = sanitizeSection(withGimbalPitch(reverseClone(plan.getWaypoints()), -90.0));

        List<Waypoint> eastForward = sanitizeSection(
                withGimbalPitch(withFixedHeading(cloneWaypoints(plan.getWaypoints()), 90.0), obliquePitch)
        );
        List<Waypoint> eastReverse = sanitizeSection(
                withGimbalPitch(withFixedHeading(reverseClone(plan.getWaypoints()), 90.0), obliquePitch)
        );

        List<Waypoint> westForward = sanitizeSection(
                withGimbalPitch(withFixedHeading(cloneWaypoints(plan.getWaypoints()), -90.0), obliquePitch)
        );
        List<Waypoint> westReverse = sanitizeSection(
                withGimbalPitch(withFixedHeading(reverseClone(plan.getWaypoints()), -90.0), obliquePitch)
        );

        List<Waypoint> transferRaw = sanitizeSection(
                withGimbalPitch(cloneWaypoints(plan.getObliqueTransferWaypoints()), obliquePitch)
        );

        List<Waypoint> crossForward = sanitizeSection(
                withGimbalPitch(withFixedHeading(cloneWaypoints(plan.getObliqueCrossWaypoints()), 0.0), obliquePitch)
        );
        List<Waypoint> crossReverse = sanitizeSection(
                withGimbalPitch(withFixedHeading(reverseClone(plan.getObliqueCrossWaypoints()), 180.0), obliquePitch)
        );

        // 1) 主正射段
        List<Waypoint> nadirSection = chooseForCurrentEndpoint(merged, nadirForward, nadirReverse);
        int nadirStart = merged.size();
        appendSection(merged, nadirSection);
        int nadirEnd = merged.size() - 1;
        addSectionActions(groups, groupId, nadirStart, nadirEnd, -90.0, interval);
        groupId += 3;

        // 2) 东侧倾斜段
        List<Waypoint> eastSection = chooseForCurrentEndpoint(merged, eastForward, eastReverse);
        int eastStart = merged.size();
        appendSection(merged, eastSection);
        int eastEnd = merged.size() - 1;
        addSectionActions(groups, groupId, eastStart, eastEnd, obliquePitch, interval);
        groupId += 3;

        // 3) 西侧倾斜段
        List<Waypoint> westSection = chooseForCurrentEndpoint(merged, westForward, westReverse);
        int westStart = merged.size();
        appendSection(merged, westSection);
        int westEnd = merged.size() - 1;
        addSectionActions(groups, groupId, westStart, westEnd, obliquePitch, interval);
        groupId += 3;

        // 4) 转场段，不拍照
        if (!transferRaw.isEmpty()) {
            List<Waypoint> transfer = chooseForCurrentEndpoint(merged, transferRaw, reverseClone(transferRaw));
            appendSection(merged, transfer);
        }

        // 5) 第一条横向倾斜段
        List<Waypoint> firstCross = chooseForCurrentEndpoint(merged, crossForward, crossReverse);
        int firstCrossStart = merged.size();
        appendSection(merged, firstCross);
        int firstCrossEnd = merged.size() - 1;
        addSectionActions(groups, groupId, firstCrossStart, firstCrossEnd, obliquePitch, interval);
        groupId += 3;

        // 6) 第二条横向倾斜段，取未使用的反向
        List<Waypoint> secondCross = chooseUnusedOpposite(firstCross, crossForward, crossReverse);
        secondCross = chooseForCurrentEndpoint(merged, secondCross, reverseClone(secondCross));
        int secondCrossStart = merged.size();
        appendSection(merged, secondCross);
        int secondCrossEnd = merged.size() - 1;
        addSectionActions(groups, groupId, secondCrossStart, secondCrossEnd, obliquePitch, interval);

        reindex(merged);

        OrthoMission mission = new OrthoMission();
        mission.setPlan(plan);
        mission.setWaypoints(merged);
        mission.setActionGroups(groups);
        return mission;
    }

    private void addSectionActions(List<WpmlActionGroup> groups,
                                   int baseGroupId,
                                   int startIdx,
                                   int endIdx,
                                   double pitchAngle,
                                   double intervalMeters) {
        if (endIdx < startIdx || startIdx < 0) {
            return;
        }
        groups.add(buildGimbalRotateAtStart(baseGroupId, startIdx, pitchAngle));
        groups.add(buildDistanceShooting(baseGroupId + 1, startIdx, endIdx, intervalMeters));
        groups.add(buildStopShootingAtEnd(baseGroupId + 2, endIdx));
    }

    private double resolveInterval(FlightPlan plan) {
        return plan.getPhotoIntervalMeter() > 0 ? plan.getPhotoIntervalMeter() : DEFAULT_INTERVAL_M;
    }

    private double resolveObliquePitch(FlightPlan plan) {
        return plan.getObliqueGimbalPitch() == 0 ? DEFAULT_OBLIQUE_PITCH : plan.getObliqueGimbalPitch();
    }

    private List<Waypoint> chooseUnusedOpposite(List<Waypoint> chosen, List<Waypoint> optionA, List<Waypoint> optionB) {
        if (isSameDirection(chosen, optionA)) {
            return optionB;
        }
        return optionA;
    }

    private boolean isSameDirection(List<Waypoint> a, List<Waypoint> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return sameCoordinate(a.get(0), b.get(0))
                && sameCoordinate(a.get(a.size() - 1), b.get(b.size() - 1));
    }

    private List<Waypoint> chooseForCurrentEndpoint(List<Waypoint> merged,
                                                    List<Waypoint> optionA,
                                                    List<Waypoint> optionB) {
        List<Waypoint> a = sanitizeSection(optionA);
        List<Waypoint> b = sanitizeSection(optionB);

        if (merged == null || merged.isEmpty()) {
            return !a.isEmpty() ? a : b;
        }
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }

        Waypoint end = merged.get(merged.size() - 1);
        double da = planarDistance(end, a.get(0));
        double db = planarDistance(end, b.get(0));
        return da <= db ? a : b;
    }

    private void ensureWaypoints(List<Waypoint> waypoints) {
        if (waypoints == null || waypoints.size() < 2) {
            throw new IllegalArgumentException("waypoints 至少需要 2 个点");
        }
    }

    private void appendSection(List<Waypoint> merged, List<Waypoint> section) {
        if (section == null || section.isEmpty()) {
            return;
        }

        if (merged.isEmpty()) {
            for (Waypoint wp : section) {
                merged.add(copyOf(wp));
            }
            return;
        }

        int startIdx = 0;
        Waypoint prev = merged.get(merged.size() - 1);

        if (sameCoordinate(prev, section.get(0))) {
            startIdx = 1;
        }

        for (int i = startIdx; i < section.size(); i++) {
            Waypoint wp = section.get(i);
            if (sameCoordinate(merged.get(merged.size() - 1), wp)) {
                continue;
            }
            if (planarDistance(merged.get(merged.size() - 1), wp) < MIN_SEGMENT_METERS) {
                continue;
            }
            merged.add(copyOf(wp));
        }
    }

    private List<Waypoint> sanitizeSection(List<Waypoint> section) {
        if (section == null || section.isEmpty()) {
            return new ArrayList<>();
        }

        List<Waypoint> out = new ArrayList<>();
        for (Waypoint wp : section) {
            if (wp == null) {
                continue;
            }

            if (out.isEmpty()) {
                out.add(copyOf(wp));
                continue;
            }

            Waypoint prev = out.get(out.size() - 1);
            if (sameCoordinate(prev, wp)) {
                continue;
            }
            if (planarDistance(prev, wp) < MIN_SEGMENT_METERS) {
                continue;
            }

            out.add(copyOf(wp));
        }
        return out;
    }

    private boolean sameCoordinate(Waypoint a, Waypoint b) {
        return a != null
                && b != null
                && Math.abs(a.getLongitude() - b.getLongitude()) < 1e-9
                && Math.abs(a.getLatitude() - b.getLatitude()) < 1e-9;
    }

    private void reindex(List<Waypoint> waypoints) {
        for (int i = 0; i < waypoints.size(); i++) {
            waypoints.get(i).setIndex(i);
        }
    }

    private List<Waypoint> withFixedHeading(List<Waypoint> waypoints, double headingAngle) {
        if (waypoints == null) {
            return new ArrayList<>();
        }
        for (Waypoint wp : waypoints) {
            wp.setUseGlobalHeading(false);
            wp.setHeadingMode("fixedHeading");
            wp.setHeadingAngle(normalizeHeading(headingAngle));
        }
        return waypoints;
    }

    private List<Waypoint> withGimbalPitch(List<Waypoint> waypoints, double pitchAngle) {
        if (waypoints == null) {
            return new ArrayList<>();
        }
        for (Waypoint wp : waypoints) {
            wp.setGimbalPitchAngle(pitchAngle);
        }
        return waypoints;
    }

    private double normalizeHeading(double heading) {
        double h = heading;
        while (h > 180.0) {
            h -= 360.0;
        }
        while (h <= -180.0) {
            h += 360.0;
        }
        return h;
    }

    private double planarDistance(Waypoint a, Waypoint b) {
        double radLat = Math.toRadians((a.getLatitude() + b.getLatitude()) * 0.5);
        double dx = (b.getLongitude() - a.getLongitude()) * 111320.0 * Math.cos(radLat);
        double dy = (b.getLatitude() - a.getLatitude()) * 111132.0;
        return Math.hypot(dx, dy);
    }

    private List<Waypoint> cloneWaypoints(List<Waypoint> src) {
        if (src == null || src.isEmpty()) {
            return new ArrayList<>();
        }

        List<Waypoint> out = new ArrayList<>(src.size());
        for (Waypoint wp : src) {
            if (wp != null) {
                out.add(copyOf(wp));
            }
        }
        return out;
    }

    private List<Waypoint> reverseClone(List<Waypoint> src) {
        List<Waypoint> out = cloneWaypoints(src);
        Collections.reverse(out);
        return out;
    }

    private Waypoint copyOf(Waypoint src) {
        Waypoint w = new Waypoint();
        w.setIndex(src.getIndex());
        w.setLongitude(src.getLongitude());
        w.setLatitude(src.getLatitude());
        w.setHeight(src.getHeight());
        w.setEllipsoidHeight(src.getEllipsoidHeight());
        w.setSpeed(src.getSpeed());
        w.setUseGlobalHeight(src.isUseGlobalHeight());
        w.setUseGlobalSpeed(src.isUseGlobalSpeed());
        w.setUseGlobalHeading(src.isUseGlobalHeading());
        w.setUseGlobalTurnParam(src.isUseGlobalTurnParam());
        w.setHeadingMode(src.getHeadingMode());
        w.setHeadingAngle(src.getHeadingAngle());
        w.setTurnMode(src.getTurnMode());
        w.setGimbalPitchAngle(src.getGimbalPitchAngle());
        return w;
    }

    private WpmlActionGroup buildGimbalRotateAtStart(int groupId, int startIdx, double pitchAngle) {
        WpmlActionGroup g = new WpmlActionGroup();
        g.setActionGroupId(groupId);
        g.setStartIndex(startIdx);
        g.setEndIndex(startIdx);
        g.setActionGroupMode("sequence");
        g.setTriggerType("reachPoint");

        List<WpmlAction> actions = new ArrayList<>();
        WpmlAction a0 = new WpmlAction();
        a0.setActionId(0);
        a0.setActionActuatorFunc("gimbalRotate");
        a0.setActuatorFuncParamInnerXml(
                "<wpml:gimbalHeadingYawBase>aircraft</wpml:gimbalHeadingYawBase>\n" +
                        "<wpml:gimbalRotateMode>absoluteAngle</wpml:gimbalRotateMode>\n" +
                        "<wpml:gimbalPitchRotateEnable>1</wpml:gimbalPitchRotateEnable>\n" +
                        "<wpml:gimbalPitchRotateAngle>" + fmt(pitchAngle) + "</wpml:gimbalPitchRotateAngle>\n" +
                        "<wpml:gimbalRollRotateEnable>0</wpml:gimbalRollRotateEnable>\n" +
                        "<wpml:gimbalRollRotateAngle>0</wpml:gimbalRollRotateAngle>\n" +
                        "<wpml:gimbalYawRotateEnable>0</wpml:gimbalYawRotateEnable>\n" +
                        "<wpml:gimbalYawRotateAngle>0</wpml:gimbalYawRotateAngle>\n" +
                        "<wpml:gimbalRotateTimeEnable>0</wpml:gimbalRotateTimeEnable>\n" +
                        "<wpml:gimbalRotateTime>0</wpml:gimbalRotateTime>\n" +
                        "<wpml:payloadPositionIndex>0</wpml:payloadPositionIndex>\n"
        );
        actions.add(a0);
        g.setActions(actions);
        return g;
    }

    private WpmlActionGroup buildDistanceShooting(int groupId, int startIdx, int endIdx, double intervalMeters) {
        WpmlActionGroup g = new WpmlActionGroup();
        g.setActionGroupId(groupId);
        g.setStartIndex(startIdx);
        g.setEndIndex(endIdx);
        g.setActionGroupMode("sequence");
        g.setTriggerType("multipleDistance");
        g.setTriggerParam(intervalMeters);

        List<WpmlAction> actions = new ArrayList<>();
        WpmlAction a0 = new WpmlAction();
        a0.setActionId(0);
        a0.setActionActuatorFunc("startContinuousShooting");
        a0.setActuatorFuncParamInnerXml(
                "<wpml:shootingMode>distance</wpml:shootingMode>\n" +
                        "<wpml:shootingDistance>" + fmt(intervalMeters) + "</wpml:shootingDistance>\n" +
                        "<wpml:payloadPositionIndex>0</wpml:payloadPositionIndex>\n" +
                        "<wpml:useGlobalPayloadLensIndex>0</wpml:useGlobalPayloadLensIndex>\n" +
                        "<wpml:payloadLensIndex>visible</wpml:payloadLensIndex>\n"
        );
        actions.add(a0);
        g.setActions(actions);
        return g;
    }

    private WpmlActionGroup buildStopShootingAtEnd(int groupId, int endIdx) {
        WpmlActionGroup g = new WpmlActionGroup();
        g.setActionGroupId(groupId);
        g.setStartIndex(endIdx);
        g.setEndIndex(endIdx);
        g.setActionGroupMode("sequence");
        g.setTriggerType("reachPoint");

        List<WpmlAction> actions = new ArrayList<>();
        WpmlAction a0 = new WpmlAction();
        a0.setActionId(0);
        a0.setActionActuatorFunc("stopContinuousShooting");
        a0.setActuatorFuncParamInnerXml(
                "<wpml:payloadPositionIndex>0</wpml:payloadPositionIndex>\n" +
                        "<wpml:useGlobalPayloadLensIndex>0</wpml:useGlobalPayloadLensIndex>\n" +
                        "<wpml:payloadLensIndex>visible</wpml:payloadLensIndex>\n"
        );
        actions.add(a0);
        g.setActions(actions);
        return g;
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.2f", v);
    }
}