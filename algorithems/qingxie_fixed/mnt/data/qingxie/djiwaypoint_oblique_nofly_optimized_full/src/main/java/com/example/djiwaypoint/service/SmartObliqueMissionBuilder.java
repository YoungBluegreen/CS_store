package com.example.djiwaypoint.service;

import com.example.djiwaypoint.model.FlightPlan;
import com.example.djiwaypoint.model.OrthoMission;
import com.example.djiwaypoint.model.Waypoint;
import com.example.djiwaypoint.model.WpmlAction;
import com.example.djiwaypoint.model.WpmlActionGroup;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class SmartObliqueMissionBuilder {

    private static final double DEFAULT_SMART_OBLIQUE_PITCH = -45.0;
    private static final double DEFAULT_ORTHO_PITCH = -90.0;

    /**
     * 双拍版：
     * 每次触发仅执行：
     * 1) 正射
     * 2) 单侧倾斜
     *
     * 相邻测线左右交替。
     */
    private static final double FORWARD_LEFT_YAW = 45.0;
    private static final double FORWARD_RIGHT_YAW = -45.0;

    private static final double DEDUPE_EPS_METERS = 0.2;

    /**
     * Mavic 3M RGB 相机保守工程估算参数
     */
    private static final double RGB_FOCAL_MM = 12.29;
    private static final double RGB_SENSOR_WIDTH_MM = 17.3;
    private static final double RGB_SENSOR_HEIGHT_MM = 13.0;
    private static final double DEFAULT_FORWARD_OVERLAP = 0.80;

    /**
     * 拍照间距上下限
     */
    private static final double MIN_PHOTO_SPACING_METERS = 12.0;
    private static final double MAX_PHOTO_SPACING_METERS = 28.0;

    /**
     * 只有足够长的段才允许定距拍照
     */
    private static final double MIN_PHOTO_SEGMENT_METERS = 18.0;
    private static final double MIN_SEGMENT_TO_SPACING_RATIO = 0.85;

    /**
     * 如果某段明显短于相邻主段，大概率是转弯/连接段
     */
    private static final double SHORT_CONNECTOR_RATIO = 0.55;

    public OrthoMission build(FlightPlan plan) {
        validatePlan(plan);
        plan.resetActionCounters();

        List<Waypoint> skeleton = prepareSkeletonWaypoints(plan);
        markSurveySegmentsAndAttachDistanceActions(plan, skeleton);
        List<WpmlActionGroup> actionGroups = buildDistanceTriggeredActionGroups(plan, skeleton);

        OrthoMission mission = new OrthoMission();
        mission.setPlan(plan);
        mission.setWaypoints(skeleton);
        mission.setActionGroups(actionGroups);
        return mission;
    }

    private void validatePlan(FlightPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan 不能为空");
        }
        if (plan.getWaypoints() == null || plan.getWaypoints().size() < 2) {
            throw new IllegalArgumentException("smartOblique 至少需要 2 个航点");
        }
    }

    /**
     * 单蛇形骨架：
     * - 去重
     * - 姿态归一化
     * - 不在中间插“拍照航点”
     */
    private List<Waypoint> prepareSkeletonWaypoints(FlightPlan plan) {
        List<Waypoint> copied = new ArrayList<>();
        for (Waypoint src : plan.getWaypoints()) {
            if (src != null) {
                copied.add(copyOf(src));
            }
        }

        List<Waypoint> deduped = dedupeConsecutiveWaypoints(copied, DEDUPE_EPS_METERS);
        double obliquePitch = getSmartObliquePitch(plan);

        for (int i = 0; i < deduped.size(); i++) {
            Waypoint wp = deduped.get(i);
            normalizeControlWaypoint(plan, wp, i, deduped.size(), obliquePitch);
            wp.clearActionSpecs();
        }

        reindex(deduped);
        return deduped;
    }

    /**
     * 只标记真正的主测绘直线段：
     * - 长直线段：启用定距触发
     * - 短连接段/转弯段：仅飞行不过拍
     *
     * 双拍版动作序列：
     * - 正射 1 张
     * - 单侧倾斜 1 张
     *
     * 倾斜方向按测线奇偶交替。
     */
    private void markSurveySegmentsAndAttachDistanceActions(FlightPlan plan, List<Waypoint> skeleton) {
        if (skeleton == null || skeleton.size() < 2) {
            return;
        }

        int surveySegmentOrdinal = 0;
        int payloadIndex = resolvePayloadPositionIndex(plan);

        for (int segIndex = 0; segIndex < skeleton.size() - 1; segIndex++) {
            Waypoint start = skeleton.get(segIndex);
            Waypoint end = skeleton.get(segIndex + 1);

            if (!canDensifySegment(plan, skeleton, segIndex)) {
                start.setSegmentType(Waypoint.SegmentType.TRANSITION);
                start.setSegmentStart(false);
                start.setSegmentEnd(false);
                start.setEnableDistanceShooting(false);
                start.setDistanceShootingIntervalMeters(null);
                start.clearActionSpecs();
                continue;
            }

            double spacing = resolvePhotoSpacingMeters(plan, start, end);
            double obliquePitch = getSmartObliquePitch(plan);
            double sideYaw = (surveySegmentOrdinal % 2 == 0) ? FORWARD_LEFT_YAW : FORWARD_RIGHT_YAW;

            start.setSegmentType(Waypoint.SegmentType.OBLIQUE_PASS);
            start.setSegmentStart(true);
            start.setSegmentEnd(false);
            start.setPhotoPoint(true);
            start.setEnableDistanceShooting(true);
            start.setDistanceShootingIntervalMeters(spacing);
            start.clearActionSpecs();

            // 第 1 张：正射
            start.addActionSpec(Waypoint.ActionSpec.gimbalRotate(DEFAULT_ORTHO_PITCH, 0.0, payloadIndex));
            start.addActionSpec(Waypoint.ActionSpec.takePhoto(payloadIndex));

            // 第 2 张：单侧倾斜（左右交替）
            start.addActionSpec(Waypoint.ActionSpec.gimbalRotate(obliquePitch, sideYaw, payloadIndex));
            start.addActionSpec(Waypoint.ActionSpec.takePhoto(payloadIndex));

            // 段终点仅作边界标记
            end.setSegmentType(Waypoint.SegmentType.OBLIQUE_PASS);
            end.setSegmentEnd(true);

            surveySegmentOrdinal++;
        }
    }

    /**
     * 将段起点 waypoint 上挂的动作序列转成真正的 WPML actionGroup：
     * - startIndex = 本段起点
     * - endIndex   = 本段终点
     * - trigger    = multipleDistance
     */
    private List<WpmlActionGroup> buildDistanceTriggeredActionGroups(FlightPlan plan, List<Waypoint> waypoints) {
        List<WpmlActionGroup> groups = new ArrayList<>();
        if (waypoints == null || waypoints.size() < 2) {
            return groups;
        }

        for (int i = 0; i < waypoints.size() - 1; i++) {
            Waypoint start = waypoints.get(i);
            Waypoint end = waypoints.get(i + 1);

            if (!start.isEnableDistanceShooting()) {
                continue;
            }
            if (start.getSegmentType() != Waypoint.SegmentType.OBLIQUE_PASS) {
                continue;
            }
            if (!start.hasActionSpecs()) {
                continue;
            }

            Double spacing = start.getDistanceShootingIntervalMeters();
            if (spacing == null || spacing <= 0) {
                continue;
            }

            WpmlActionGroup g = new WpmlActionGroup();
            g.setActionGroupId(plan.allocateActionGroupId());
            g.setStartIndex(start.getIndex());
            g.setEndIndex(end.getIndex());
            g.setActionGroupMode("sequence");
            g.setTriggerType("multipleDistance");
            g.setTriggerParam(spacing);

            List<WpmlAction> actions = new ArrayList<>();
            for (Waypoint.ActionSpec spec : start.getActionSpecs()) {
                if (spec == null || spec.getType() == null) {
                    continue;
                }
                actions.add(toWpmlAction(plan, spec));
            }
            g.setActions(actions);
            groups.add(g);
        }

        return groups;
    }

    private WpmlAction toWpmlAction(FlightPlan plan, Waypoint.ActionSpec spec) {
        switch (spec.getType()) {
            case GIMBAL_ROTATE:
                return buildGimbalRotateAction(
                        plan,
                        safe(spec.getGimbalPitchAngle(), DEFAULT_SMART_OBLIQUE_PITCH),
                        safe(spec.getGimbalYawAngle(), 0.0),
                        safePayloadIndex(spec.getPayloadPositionIndex(), resolvePayloadPositionIndex(plan))
                );
            case TAKE_PHOTO:
                return buildTakePhotoAction(
                        plan,
                        safePayloadIndex(spec.getPayloadPositionIndex(), resolvePayloadPositionIndex(plan)),
                        spec.getPayloadLensIndex(),
                        Boolean.TRUE.equals(spec.getUseGlobalPayloadLensIndex())
                );
            case HOVER:
            default:
                return buildHoverAction(plan, safe(spec.getHoverTimeSeconds(), 0.2));
        }
    }

    /**
     * 判断某段是否允许作为主测绘段启用定距拍照
     */
    private boolean canDensifySegment(FlightPlan plan, List<Waypoint> skeleton, int segIndex) {
        Waypoint a = skeleton.get(segIndex);
        Waypoint b = skeleton.get(segIndex + 1);

        double len = distanceMeters(a, b);
        double spacing = resolvePhotoSpacingMeters(plan, a, b);

        if (len < Math.max(MIN_PHOTO_SEGMENT_METERS, spacing * MIN_SEGMENT_TO_SPACING_RATIO)) {
            return false;
        }

        double prevLen = segIndex > 0
                ? distanceMeters(skeleton.get(segIndex - 1), skeleton.get(segIndex))
                : len;

        double nextLen = segIndex < skeleton.size() - 2
                ? distanceMeters(skeleton.get(segIndex + 1), skeleton.get(segIndex + 2))
                : len;

        double localRef = Math.max(prevLen, nextLen);

        return !(localRef > 0 && len < localRef * SHORT_CONNECTOR_RATIO);
    }

    private void normalizeControlWaypoint(
            FlightPlan plan,
            Waypoint wp,
            int index,
            int total,
            double obliquePitch
    ) {
        applyBaseSpeedAndHeight(plan, wp);

        if (isBlank(wp.getHeadingMode())) {
            wp.setHeadingMode("followWayline");
        }
        if (isBlank(wp.getTurnMode())) {
            wp.setTurnMode("coordinateTurn");
        }

        if (index == 0 || index == total - 1) {
            wp.setTurnMode("toPointAndStopWithDiscontinuityCurvature");
        }

        wp.setUseStraightLine(index != total - 1);
        wp.setSegmentType(Waypoint.SegmentType.TRANSITION);
        wp.setSegmentStart(false);
        wp.setSegmentEnd(false);
        wp.setEnableDistanceShooting(false);
        wp.setDistanceShootingIntervalMeters(null);

        // 默认给个倾斜姿态，仅供界面/调试参考
        wp.setGimbalPitchAngle(obliquePitch);
        wp.setGimbalYawAngle(0.0);

        wp.setUseGlobalHeading(false);
        wp.setUseGlobalTurnParam(false);
    }

    /**
     * 基于 M3M RGB 相机参数，按短边 footprint 和 80% 前向重叠保守估算
     */
    private double resolvePhotoSpacingMeters(FlightPlan plan, Waypoint a, Waypoint b) {
        double h1 = a.isUseGlobalHeight()
                ? positiveOr(plan.getGlobalShootHeight(), 80.0)
                : positiveOr(a.getHeight(), positiveOr(plan.getGlobalShootHeight(), 80.0));

        double h2 = b.isUseGlobalHeight()
                ? positiveOr(plan.getGlobalShootHeight(), 80.0)
                : positiveOr(b.getHeight(), positiveOr(plan.getGlobalShootHeight(), 80.0));

        double h = (h1 + h2) / 2.0;

        double shortSideSensorMm = Math.min(RGB_SENSOR_WIDTH_MM, RGB_SENSOR_HEIGHT_MM);
        double alongTrackFootprintMeters = h * shortSideSensorMm / RGB_FOCAL_MM;
        double spacing = alongTrackFootprintMeters * (1.0 - DEFAULT_FORWARD_OVERLAP);

        if (Double.isNaN(spacing) || spacing <= 0) {
            spacing = 18.0;
        }

        spacing = Math.max(MIN_PHOTO_SPACING_METERS, spacing);
        spacing = Math.min(MAX_PHOTO_SPACING_METERS, spacing);
        return spacing;
    }

    private WpmlAction buildGimbalRotateAction(
            FlightPlan plan,
            double pitchAngle,
            double yawAngle,
            int payloadIndex
    ) {
        WpmlAction action = new WpmlAction();
        action.setActionId(plan.allocateActionId());
        action.setActionActuatorFunc("gimbalRotate");
        action.setActuatorFuncParamInnerXml(buildGimbalRotateInnerXml(pitchAngle, yawAngle, payloadIndex));
        return action;
    }

    private WpmlAction buildTakePhotoAction(
            FlightPlan plan,
            int payloadIndex,
            String lensIndex,
            boolean useGlobalPayloadLensIndex
    ) {
        WpmlAction action = new WpmlAction();
        action.setActionId(plan.allocateActionId());
        action.setActionActuatorFunc("takePhoto");
        action.setActuatorFuncParamInnerXml(
                "<wpml:payloadPositionIndex>" + payloadIndex + "</wpml:payloadPositionIndex>\n" +
                        "<wpml:useGlobalPayloadLensIndex>" + (useGlobalPayloadLensIndex ? "1" : "0") + "</wpml:useGlobalPayloadLensIndex>\n" +
                        "<wpml:payloadLensIndex>" + safeLensIndex(lensIndex) + "</wpml:payloadLensIndex>\n"
        );
        return action;
    }

    private WpmlAction buildHoverAction(FlightPlan plan, double hoverSeconds) {
        WpmlAction action = new WpmlAction();
        action.setActionId(plan.allocateActionId());
        action.setActionActuatorFunc("hover");
        action.setActuatorFuncParamInnerXml(
                "<wpml:hoverTime>" + fmt(hoverSeconds) + "</wpml:hoverTime>\n"
        );
        return action;
    }

    private String buildGimbalRotateInnerXml(double pitchAngle, double yawAngle, int payloadIndex) {
        boolean yawEnabled = Math.abs(yawAngle) > 1e-6;

        StringBuilder sb = new StringBuilder();
        sb.append("<wpml:gimbalHeadingYawBase>aircraft</wpml:gimbalHeadingYawBase>\n");
        sb.append("<wpml:gimbalRotateMode>absoluteAngle</wpml:gimbalRotateMode>\n");

        sb.append("<wpml:gimbalPitchRotateEnable>1</wpml:gimbalPitchRotateEnable>\n");
        sb.append("<wpml:gimbalPitchRotateAngle>").append(fmt(pitchAngle)).append("</wpml:gimbalPitchRotateAngle>\n");

        sb.append("<wpml:gimbalRollRotateEnable>0</wpml:gimbalRollRotateEnable>\n");
        sb.append("<wpml:gimbalRollRotateAngle>0</wpml:gimbalRollRotateAngle>\n");

        sb.append("<wpml:gimbalYawRotateEnable>").append(yawEnabled ? "1" : "0").append("</wpml:gimbalYawRotateEnable>\n");
        sb.append("<wpml:gimbalYawRotateAngle>").append(fmt(yawAngle)).append("</wpml:gimbalYawRotateAngle>\n");

        sb.append("<wpml:gimbalRotateTimeEnable>0</wpml:gimbalRotateTimeEnable>\n");
        sb.append("<wpml:gimbalRotateTime>0</wpml:gimbalRotateTime>\n");
        sb.append("<wpml:payloadPositionIndex>").append(payloadIndex).append("</wpml:payloadPositionIndex>\n");
        return sb.toString();
    }

    private double getSmartObliquePitch(FlightPlan plan) {
        double v = plan.getObliqueGimbalPitch();
        return v == 0 ? DEFAULT_SMART_OBLIQUE_PITCH : v;
    }

    private int resolvePayloadPositionIndex(FlightPlan plan) {
        if (plan.getPayload() == null) {
            plan.setDefaultPayloadForDrone();
        }
        if (plan.getPayload() == null) {
            return 0;
        }
        return plan.getPayload().getPayloadPositionIndex();
    }

    private void applyBaseSpeedAndHeight(FlightPlan plan, Waypoint wp) {
        if (wp.getSpeed() <= 0) {
            wp.setSpeed(plan.getAutoFlightSpeed() > 0 ? plan.getAutoFlightSpeed() : 8.0);
            wp.setUseGlobalSpeed(false);
        }

        if (!wp.isUseGlobalHeight() && wp.getHeight() <= 0) {
            wp.setHeight(plan.getGlobalShootHeight() > 0 ? plan.getGlobalShootHeight() : 80.0);
        }
    }

    private List<Waypoint> dedupeConsecutiveWaypoints(List<Waypoint> src, double epsMeters) {
        List<Waypoint> out = new ArrayList<>();
        Waypoint prev = null;
        for (Waypoint wp : src) {
            if (prev == null || distanceMeters(prev, wp) > epsMeters) {
                out.add(wp);
                prev = wp;
            }
        }
        return out;
    }

    private double distanceMeters(Waypoint a, Waypoint b) {
        double lon1 = Math.toRadians(a.getLongitude());
        double lat1 = Math.toRadians(a.getLatitude());
        double lon2 = Math.toRadians(b.getLongitude());
        double lat2 = Math.toRadians(b.getLatitude());

        double dlon = lon2 - lon1;
        double dlat = lat2 - lat1;

        double sinDLat = Math.sin(dlat / 2.0);
        double sinDLon = Math.sin(dlon / 2.0);
        double h = sinDLat * sinDLat + Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon;
        double c = 2.0 * Math.atan2(Math.sqrt(h), Math.sqrt(1.0 - h));
        return 6371000.0 * c;
    }

    private void reindex(List<Waypoint> waypoints) {
        for (int i = 0; i < waypoints.size(); i++) {
            waypoints.get(i).setIndex(i);
        }
    }

    private Waypoint copyOf(Waypoint src) {
        return src.shallowCopy();
    }

    private double positiveOr(double v, double def) {
        return v > 0 ? v : def;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private double safe(Double v, double def) {
        return v == null ? def : v;
    }

    private int safePayloadIndex(Integer v, int def) {
        return v == null ? def : v;
    }

    private String safeLensIndex(String s) {
        return isBlank(s) ? "visible" : s;
    }

    private String fmt(double v) {
        return String.format(Locale.US, "%.6f", v)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }
}