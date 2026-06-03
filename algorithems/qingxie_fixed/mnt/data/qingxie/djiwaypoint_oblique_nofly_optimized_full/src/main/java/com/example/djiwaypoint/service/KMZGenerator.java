package com.example.djiwaypoint.service;

import com.example.djiwaypoint.model.DroneInfo;
import com.example.djiwaypoint.model.FlightPlan;
import com.example.djiwaypoint.model.OrthoMission;
import com.example.djiwaypoint.model.PayloadInfo;
import com.example.djiwaypoint.model.Waypoint;
import com.example.djiwaypoint.model.WpmlAction;
import com.example.djiwaypoint.model.WpmlActionGroup;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class KMZGenerator {

    private static final String WPML_NS = "http://www.dji.com/wpmz/1.0.6";

    private final OrthoMissionBuilder orthoMissionBuilder;
    private final SmartObliqueMissionBuilder smartObliqueMissionBuilder;

    public KMZGenerator(OrthoMissionBuilder orthoMissionBuilder,
                        SmartObliqueMissionBuilder smartObliqueMissionBuilder) {
        this.orthoMissionBuilder = orthoMissionBuilder;
        this.smartObliqueMissionBuilder = smartObliqueMissionBuilder;
    }

    public byte[] generateKMZ(FlightPlan plan) throws IOException {
        if (plan == null) {
            throw new IllegalArgumentException("FlightPlan 不能为空");
        }

        normalizeMissionType(plan);

        System.out.println("========== generateKMZ begin ==========");
        System.out.println("missionType = " + plan.getMissionType());
        System.out.println("waypoints.size = " + (plan.getWaypoints() == null ? 0 : plan.getWaypoints().size()));
        System.out.println("obliqueCrossWaypoints.size = " + (plan.getObliqueCrossWaypoints() == null ? 0 : plan.getObliqueCrossWaypoints().size()));
        System.out.println("obliqueTransferWaypoints.size = " + (plan.getObliqueTransferWaypoints() == null ? 0 : plan.getObliqueTransferWaypoints().size()));

        OrthoMission mission = buildMission(plan);
        validateMission(mission);

        String templateKml = buildTemplateKml(mission);
        String waylinesWpml = buildWaylinesWpml(mission);

        validateXml(templateKml, "template.kml");
        validateXml(waylinesWpml, "waylines.wpml");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("template.kml"));
            zos.write(templateKml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("waylines.wpml"));
            zos.write(waylinesWpml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    /**
     * smartOblique 必须稳定走 SmartObliqueMissionBuilder。
     * 不然前端一个拼写手滑，又开始乱飞两遍，机器很无辜，主要是人不行。
     */
    private void normalizeMissionType(FlightPlan plan) {
        String mt = plan.getMissionType();
        if (mt == null || mt.trim().isEmpty()) {
            plan.setMissionType("ortho");
            return;
        }

        String normalized = mt.trim();

        if ("smart_oblique".equalsIgnoreCase(normalized)
                || "smart-oblique".equalsIgnoreCase(normalized)
                || "smartoblique".equalsIgnoreCase(normalized)) {
            plan.setMissionType("smartOblique");
            return;
        }

        if ("oblique".equalsIgnoreCase(normalized)) {
            plan.setMissionType("oblique");
            return;
        }

        if ("ortho".equalsIgnoreCase(normalized)) {
            plan.setMissionType("ortho");
            return;
        }

        plan.setMissionType("ortho");
    }

    private OrthoMission buildMission(FlightPlan plan) {
        if (isSmartOblique(plan)) {
            System.out.println("buildMission -> SmartObliqueMissionBuilder");
            return smartObliqueMissionBuilder.build(plan);
        }
        System.out.println("buildMission -> OrthoMissionBuilder");
        return orthoMissionBuilder.build(plan);
    }

    private void validateMission(OrthoMission mission) {
        if (mission == null) {
            throw new IllegalArgumentException("mission 不能为空");
        }
        if (mission.getPlan() == null) {
            throw new IllegalArgumentException("mission.plan 不能为空");
        }
        if (mission.getWaypoints() == null || mission.getWaypoints().size() < 2) {
            throw new IllegalArgumentException("waypoints 至少需要 2 个点");
        }

        List<Waypoint> wps = mission.getWaypoints();
        for (int i = 0; i < wps.size(); i++) {
            Waypoint wp = wps.get(i);

            if (i > 0 && haversineMeters(wps.get(i - 1), wp) < 0.05) {
                throw new IllegalStateException("存在重复连续航点: " + (i - 1) + " 和 " + i);
            }
            if (wp.getLongitude() == 0 && wp.getLatitude() == 0) {
                throw new IllegalStateException("航点坐标异常，index=" + i);
            }
            if (isBlank(wp.getHeadingMode())) {
                wp.setHeadingMode("followWayline");
            }
            if (isBlank(wp.getTurnMode())) {
                wp.setTurnMode("coordinateTurn");
            }
        }
    }

    private void validateXml(String xml, String name) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new IllegalStateException(name + " 不是合法 XML: " + e.getMessage(), e);
        }
    }

    // =========================
    // template.kml
    // =========================
    private String buildTemplateKml(OrthoMission mission) {
        FlightPlan plan = mission.getPlan();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:wpml=\"").append(WPML_NS).append("\">\n");
        sb.append("  <Document>\n");
        sb.append("    <wpml:createTime>").append(System.currentTimeMillis()).append("</wpml:createTime>\n");
        sb.append("    <wpml:updateTime>").append(System.currentTimeMillis()).append("</wpml:updateTime>\n");

        appendMissionConfig(sb, plan);

        sb.append("    <Folder>\n");
        sb.append("      <wpml:templateType>").append(resolveTemplateType(plan)).append("</wpml:templateType>\n");
        sb.append("      <wpml:templateId>0</wpml:templateId>\n");
        sb.append("      <wpml:waylineCoordinateSysParam>\n");
        sb.append("        <wpml:coordinateMode>WGS84</wpml:coordinateMode>\n");
        sb.append("        <wpml:heightMode>EGM96</wpml:heightMode>\n");
        sb.append("      </wpml:waylineCoordinateSysParam>\n");
        sb.append("      <wpml:autoFlightSpeed>").append(fmtFloat(resolveAutoSpeed(plan))).append("</wpml:autoFlightSpeed>\n");
        sb.append("      <wpml:globalTransitionalSpeed>").append(fmtFloat(resolveAutoSpeed(plan))).append("</wpml:globalTransitionalSpeed>\n");

        /**
         * 不输出 DJI 内建 smartObliqueEnable。
         * 否则遥控器/飞控有可能自己脑补第二套倾斜逻辑。
         */
        sb.append("    </Folder>\n");
        sb.append("  </Document>\n");
        sb.append("</kml>\n");
        return sb.toString();
    }

    // =========================
    // waylines.wpml
    // =========================
    private String buildWaylinesWpml(OrthoMission mission) {
        FlightPlan plan = mission.getPlan();
        List<Waypoint> wps = mission.getWaypoints();

        double autoSpeed = resolveAutoSpeed(plan);
        double totalDist = computeTotalDistanceMeters(wps);
        double duration = totalDist / Math.max(0.1, autoSpeed);

        TurnParam[] turns = computeTurnParamsLikeRC(wps);
        List<WpmlActionGroup> allGroups = collectAllActionGroups(mission);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:wpml=\"").append(WPML_NS).append("\">\n");
        sb.append("  <Document>\n");

        appendMissionConfig(sb, plan);

        sb.append("    <Folder>\n");
        sb.append("      <wpml:templateId>0</wpml:templateId>\n");
        sb.append("      <wpml:executeHeightMode>relativeToStartPoint</wpml:executeHeightMode>\n");
        sb.append("      <wpml:waylineId>0</wpml:waylineId>\n");
        sb.append("      <wpml:distance>").append(fmtFloat(totalDist)).append("</wpml:distance>\n");
        sb.append("      <wpml:duration>").append(fmtFloat(duration)).append("</wpml:duration>\n");
        sb.append("      <wpml:autoFlightSpeed>").append(fmtFloat(autoSpeed)).append("</wpml:autoFlightSpeed>\n");

        for (int i = 0; i < wps.size(); i++) {
            Waypoint wp = wps.get(i);

            sb.append("      <Placemark>\n");
            sb.append("        <Point>\n");
            sb.append("          <coordinates>")
                    .append(fmtCoord(wp.getLongitude()))
                    .append(",")
                    .append(fmtCoord(wp.getLatitude()))
                    .append("</coordinates>\n");
            sb.append("        </Point>\n");

            sb.append("        <wpml:index>").append(i).append("</wpml:index>\n");

            double h = wp.isUseGlobalHeight()
                    ? plan.getGlobalShootHeight()
                    : (wp.getHeight() > 0 ? wp.getHeight() : plan.getGlobalShootHeight());
            sb.append("        <wpml:executeHeight>").append(fmtFloat(h)).append("</wpml:executeHeight>\n");

            double spd = wp.isUseGlobalSpeed()
                    ? autoSpeed
                    : (wp.getSpeed() > 0 ? wp.getSpeed() : autoSpeed);
            sb.append("        <wpml:waypointSpeed>").append(fmtFloat(spd)).append("</wpml:waypointSpeed>\n");

            String headingMode = wp.isUseGlobalHeading()
                    ? nullToDefault(plan.getGlobalWaypointHeadingMode(), "followWayline")
                    : nullToDefault(wp.getHeadingMode(), "followWayline");

            double headingAngle;
            if ("followWayline".equalsIgnoreCase(headingMode)) {
                headingAngle = computeHeadingAngleDeg(wps, i);
            } else {
                headingAngle = wp.isUseGlobalHeading()
                        ? plan.getGlobalWaypointHeadingAngle()
                        : wp.getHeadingAngle();
            }

            sb.append("        <wpml:waypointHeadingParam>\n");
            sb.append("          <wpml:waypointHeadingMode>").append(headingMode).append("</wpml:waypointHeadingMode>\n");
            sb.append("          <wpml:waypointHeadingAngle>").append(fmtFloat(headingAngle)).append("</wpml:waypointHeadingAngle>\n");
            sb.append("          <wpml:waypointPoiPoint>0.000000,0.000000,0.000000</wpml:waypointPoiPoint>\n");
            sb.append("          <wpml:waypointHeadingAngleEnable>1</wpml:waypointHeadingAngleEnable>\n");
            sb.append("          <wpml:waypointHeadingPathMode>followBadArc</wpml:waypointHeadingPathMode>\n");
            sb.append("          <wpml:waypointHeadingPoiIndex>0</wpml:waypointHeadingPoiIndex>\n");
            sb.append("        </wpml:waypointHeadingParam>\n");

            String turnMode = wp.isUseGlobalTurnParam()
                    ? nullToDefault(plan.getGlobalWaypointTurnMode(), "coordinateTurn")
                    : nullToDefault(wp.getTurnMode(), "coordinateTurn");

            double damping = turns[i].dampingDist;
            if (!"coordinateTurn".equalsIgnoreCase(turnMode)) {
                damping = 0.0;
            }

            sb.append("        <wpml:waypointTurnParam>\n");
            sb.append("          <wpml:waypointTurnMode>").append(turnMode).append("</wpml:waypointTurnMode>\n");
            sb.append("          <wpml:waypointTurnDampingDist>").append(fmtFloat(damping)).append("</wpml:waypointTurnDampingDist>\n");
            sb.append("        </wpml:waypointTurnParam>\n");

            int useStraightLine = (wp.isUseStraightLine() && plan.isGlobalUseStraightLine()) ? 1 : 0;
            if (i == wps.size() - 1) {
                useStraightLine = 0;
            }
            sb.append("        <wpml:useStraightLine>").append(useStraightLine).append("</wpml:useStraightLine>\n");

            // 既支持 mission 自带 actionGroups，也支持 waypoint.actionSpecs 派生 actionGroup
            for (WpmlActionGroup g : groupsStartingAt(allGroups, i)) {
                appendActionGroup(sb, g);
            }

            sb.append("        <wpml:waypointGimbalHeadingParam>\n");
            sb.append("          <wpml:waypointGimbalPitchAngle>").append(fmtFloat(wp.getGimbalPitchAngle())).append("</wpml:waypointGimbalPitchAngle>\n");
            sb.append("          <wpml:waypointGimbalYawAngle>").append(fmtFloat(wp.getGimbalYawAngle())).append("</wpml:waypointGimbalYawAngle>\n");
            sb.append("        </wpml:waypointGimbalHeadingParam>\n");
            sb.append("        <wpml:isRisky>0</wpml:isRisky>\n");
            sb.append("        <wpml:waypointWorkType>0</wpml:waypointWorkType>\n");

            sb.append("      </Placemark>\n");
        }

        sb.append("    </Folder>\n");
        sb.append("  </Document>\n");
        sb.append("</kml>\n");
        return sb.toString();
    }

    // =========================
    // missionConfig
    // =========================
    private void appendMissionConfig(StringBuilder sb, FlightPlan plan) {
        sb.append("    <wpml:missionConfig>\n");
        sb.append("      <wpml:flyToWaylineMode>").append(nullToDefault(plan.getFlyToWaylineMode(), "safely")).append("</wpml:flyToWaylineMode>\n");
        sb.append("      <wpml:finishAction>").append(nullToDefault(plan.getFinishAction(), "noAction")).append("</wpml:finishAction>\n");
        sb.append("      <wpml:exitOnRCLost>").append(nullToDefault(plan.getExitOnRCLost(), "goContinue")).append("</wpml:exitOnRCLost>\n");
        sb.append("      <wpml:takeOffSecurityHeight>").append(fmtFloat(plan.getTakeOffSecurityHeight() > 0 ? plan.getTakeOffSecurityHeight() : 20.0)).append("</wpml:takeOffSecurityHeight>\n");
        sb.append("      <wpml:globalTransitionalSpeed>").append(fmtFloat(resolveAutoSpeed(plan))).append("</wpml:globalTransitionalSpeed>\n");

        DroneInfo drone = plan.getDevice();
        if (drone == null) {
            throw new IllegalArgumentException("FlightPlan.device(DroneInfo) 不能为空");
        }

        sb.append("      <wpml:droneInfo>\n");
        sb.append("        <wpml:droneEnumValue>").append(drone.getType()).append("</wpml:droneEnumValue>\n");
        sb.append("        <wpml:droneSubEnumValue>").append(drone.getSubType()).append("</wpml:droneSubEnumValue>\n");
        sb.append("      </wpml:droneInfo>\n");

        PayloadInfo payload = plan.getPayload();
        if (payload == null) {
            plan.setDefaultPayloadForDrone();
            payload = plan.getPayload();
        }
        if (payload == null) {
            payload = new PayloadInfo();
            payload.setPayloadEnumValue(0);
            payload.setPayloadSubEnumValue(0);
            payload.setPayloadPositionIndex(0);
        }

        sb.append("      <wpml:payloadInfo>\n");
        sb.append("        <wpml:payloadEnumValue>").append(payload.getPayloadEnumValue()).append("</wpml:payloadEnumValue>\n");
        sb.append("        <wpml:payloadSubEnumValue>").append(payload.getPayloadSubEnumValue()).append("</wpml:payloadSubEnumValue>\n");
        sb.append("        <wpml:payloadPositionIndex>").append(payload.getPayloadPositionIndex()).append("</wpml:payloadPositionIndex>\n");
        sb.append("      </wpml:payloadInfo>\n");

        sb.append("    </wpml:missionConfig>\n");
    }

    private void appendActionGroup(StringBuilder sb, WpmlActionGroup g) {
        sb.append("        <wpml:actionGroup>\n");
        sb.append("          <wpml:actionGroupId>").append(g.getActionGroupId()).append("</wpml:actionGroupId>\n");
        sb.append("          <wpml:actionGroupStartIndex>").append(g.getStartIndex()).append("</wpml:actionGroupStartIndex>\n");
        sb.append("          <wpml:actionGroupEndIndex>").append(g.getEndIndex()).append("</wpml:actionGroupEndIndex>\n");
        sb.append("          <wpml:actionGroupMode>").append(nullToDefault(g.getActionGroupMode(), "sequence")).append("</wpml:actionGroupMode>\n");
        sb.append("          <wpml:actionTrigger>\n");
        sb.append("            <wpml:actionTriggerType>").append(nullToDefault(g.getTriggerType(), "reachPoint")).append("</wpml:actionTriggerType>\n");
        if (g.getTriggerParam() != null) {
            sb.append("            <wpml:actionTriggerParam>")
                    .append(fmtFloat(g.getTriggerParam().doubleValue()))
                    .append("</wpml:actionTriggerParam>\n");
        }
        sb.append("          </wpml:actionTrigger>\n");

        if (g.getActions() != null) {
            for (WpmlAction a : g.getActions()) {
                sb.append("          <wpml:action>\n");
                sb.append("            <wpml:actionId>").append(a.getActionId()).append("</wpml:actionId>\n");
                sb.append("            <wpml:actionActuatorFunc>")
                        .append(nullToDefault(a.getActionActuatorFunc(), "hover"))
                        .append("</wpml:actionActuatorFunc>\n");
                sb.append("            <wpml:actionActuatorFuncParam>\n");
                String inner = sanitizeWpmlInnerXml(a.getActuatorFuncParamInnerXml());
                if (!isBlank(inner)) {
                    sb.append(inner);
                    if (!inner.endsWith("\n")) {
                        sb.append("\n");
                    }
                }
                sb.append("            </wpml:actionActuatorFuncParam>\n");
                sb.append("          </wpml:action>\n");
            }
        }

        sb.append("        </wpml:actionGroup>\n");
    }

    private String sanitizeWpmlInnerXml(String xml) {
        if (xml == null) {
            return null;
        }
        return xml.replace("wpm1:", "wpml:");
    }

    // =========================
    // action group 聚合
    // =========================
    private List<WpmlActionGroup> collectAllActionGroups(OrthoMission mission) {
        List<WpmlActionGroup> out = new ArrayList<>();

        if (mission.getActionGroups() != null && !mission.getActionGroups().isEmpty()) {
            out.addAll(mission.getActionGroups());
            return out;
        }

        // 兜底：如果 mission 没显式给 actionGroups，则尝试从 waypoint.actionSpecs 推导
        FlightPlan plan = mission.getPlan();
        List<Waypoint> wps = mission.getWaypoints();
        if (wps == null || wps.isEmpty()) {
            return out;
        }

        for (int i = 0; i < wps.size(); i++) {
            Waypoint wp = wps.get(i);
            if (wp == null || !wp.hasActionSpecs()) {
                continue;
            }

            WpmlActionGroup g = new WpmlActionGroup();
            g.setActionGroupId(plan.allocateActionGroupId());
            g.setStartIndex(i);

            if (Boolean.TRUE.equals(wp.isEnableDistanceShooting())
                    && wp.getDistanceShootingIntervalMeters() != null
                    && wp.getDistanceShootingIntervalMeters() > 0
                    && i < wps.size() - 1) {
                g.setEndIndex(i + 1);
                g.setTriggerType("multipleDistance");
                g.setTriggerParam(wp.getDistanceShootingIntervalMeters());
            } else {
                g.setEndIndex(i);
                g.setTriggerType("reachPoint");
            }

            g.setActionGroupMode("sequence");
            g.setActions(toWpmlActions(plan, wp.getActionSpecs()));
            out.add(g);
        }

        return out;
    }

    private List<WpmlAction> toWpmlActions(FlightPlan plan, List<Waypoint.ActionSpec> specs) {
        List<WpmlAction> actions = new ArrayList<>();
        if (specs == null) {
            return actions;
        }

        int payloadIndex = resolvePayloadPositionIndex(plan);

        for (Waypoint.ActionSpec spec : specs) {
            if (spec == null || spec.getType() == null) {
                continue;
            }

            WpmlAction action = new WpmlAction();
            action.setActionId(plan.allocateActionId());

            switch (spec.getType()) {
                case GIMBAL_ROTATE:
                    action.setActionActuatorFunc("gimbalRotate");
                    action.setActuatorFuncParamInnerXml(buildGimbalRotateInnerXml(
                            spec.getGimbalPitchAngle() == null ? -45.0 : spec.getGimbalPitchAngle(),
                            spec.getGimbalYawAngle() == null ? 0.0 : spec.getGimbalYawAngle(),
                            spec.getPayloadPositionIndex() == null ? payloadIndex : spec.getPayloadPositionIndex()
                    ));
                    break;

                case TAKE_PHOTO:
                    action.setActionActuatorFunc("takePhoto");
                    action.setActuatorFuncParamInnerXml(
                            "<wpml:payloadPositionIndex>" + (spec.getPayloadPositionIndex() == null ? payloadIndex : spec.getPayloadPositionIndex()) + "</wpml:payloadPositionIndex>\n" +
                                    "<wpml:useGlobalPayloadLensIndex>" + (Boolean.TRUE.equals(spec.getUseGlobalPayloadLensIndex()) ? "1" : "0") + "</wpml:useGlobalPayloadLensIndex>\n" +
                                    "<wpml:payloadLensIndex>" + nullToDefault(spec.getPayloadLensIndex(), "visible") + "</wpml:payloadLensIndex>\n"
                    );
                    break;

                case HOVER:
                default:
                    action.setActionActuatorFunc("hover");
                    action.setActuatorFuncParamInnerXml(
                            "<wpml:hoverTime>" + fmtFloat(spec.getHoverTimeSeconds() == null ? 0.2 : spec.getHoverTimeSeconds()) + "</wpml:hoverTime>\n"
                    );
                    break;
            }

            actions.add(action);
        }

        return actions;
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

    private String buildGimbalRotateInnerXml(double pitchAngle, double yawAngle, int payloadIndex) {
        boolean yawEnabled = Math.abs(yawAngle) > 1e-6;

        StringBuilder sb = new StringBuilder();
        sb.append("<wpml:gimbalHeadingYawBase>aircraft</wpml:gimbalHeadingYawBase>\n");
        sb.append("<wpml:gimbalRotateMode>absoluteAngle</wpml:gimbalRotateMode>\n");

        sb.append("<wpml:gimbalPitchRotateEnable>1</wpml:gimbalPitchRotateEnable>\n");
        sb.append("<wpml:gimbalPitchRotateAngle>").append(fmtFloat(pitchAngle)).append("</wpml:gimbalPitchRotateAngle>\n");

        sb.append("<wpml:gimbalRollRotateEnable>0</wpml:gimbalRollRotateEnable>\n");
        sb.append("<wpml:gimbalRollRotateAngle>0</wpml:gimbalRollRotateAngle>\n");

        sb.append("<wpml:gimbalYawRotateEnable>").append(yawEnabled ? "1" : "0").append("</wpml:gimbalYawRotateEnable>\n");
        sb.append("<wpml:gimbalYawRotateAngle>").append(fmtFloat(yawAngle)).append("</wpml:gimbalYawRotateAngle>\n");

        sb.append("<wpml:gimbalRotateTimeEnable>0</wpml:gimbalRotateTimeEnable>\n");
        sb.append("<wpml:gimbalRotateTime>0</wpml:gimbalRotateTime>\n");
        sb.append("<wpml:payloadPositionIndex>").append(payloadIndex).append("</wpml:payloadPositionIndex>\n");
        return sb.toString();
    }

    private List<WpmlActionGroup> groupsStartingAt(List<WpmlActionGroup> groups, int index) {
        List<WpmlActionGroup> out = new ArrayList<>();
        if (groups == null) {
            return out;
        }
        for (WpmlActionGroup g : groups) {
            if (g != null && g.getStartIndex() == index) {
                out.add(g);
            }
        }
        return out;
    }

    // =========================
    // 计算 / 工具
    // =========================
    private static class TurnParam {
        final double dampingDist;

        TurnParam(double dampingDist) {
            this.dampingDist = dampingDist;
        }
    }

    private static TurnParam[] computeTurnParamsLikeRC(List<Waypoint> wps) {
        int n = wps.size();
        TurnParam[] out = new TurnParam[n];

        for (int i = 0; i < n; i++) {
            if (i == 0 || i == n - 1) {
                out[i] = new TurnParam(0.0);
                continue;
            }

            Waypoint a = wps.get(i - 1);
            Waypoint b = wps.get(i);
            Waypoint c = wps.get(i + 1);

            double d1 = haversineMeters(a, b);
            double d2 = haversineMeters(b, c);

            if (d1 < 1.0 || d2 < 1.0) {
                out[i] = new TurnParam(0.0);
                continue;
            }

            double turnAngle = computeTurnAngleDeg(a, b, c);

            // 近似直线段不需要阻尼转弯
            if (turnAngle >= 175.0) {
                out[i] = new TurnParam(0.0);
                continue;
            }

            double min = Math.min(d1, d2);
            double damping = Math.min(10.0, Math.max(0.0, min * 0.35));
            out[i] = new TurnParam(damping);
        }

        return out;
    }

    private static double computeTurnAngleDeg(Waypoint a, Waypoint b, Waypoint c) {
        double abx = metersX(a, b);
        double aby = metersY(a, b);
        double bcx = metersX(b, c);
        double bcy = metersY(b, c);

        double norm1 = Math.sqrt(abx * abx + aby * aby);
        double norm2 = Math.sqrt(bcx * bcx + bcy * bcy);
        if (norm1 < 1e-6 || norm2 < 1e-6) {
            return 180.0;
        }

        double dot = abx * bcx + aby * bcy;
        double cos = dot / (norm1 * norm2);
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return Math.toDegrees(Math.acos(cos));
    }

    private static double metersX(Waypoint from, Waypoint to) {
        double meanLatRad = Math.toRadians((from.getLatitude() + to.getLatitude()) / 2.0);
        double dLonRad = Math.toRadians(to.getLongitude() - from.getLongitude());
        return 6371000.0 * dLonRad * Math.cos(meanLatRad);
    }

    private static double metersY(Waypoint from, Waypoint to) {
        double dLatRad = Math.toRadians(to.getLatitude() - from.getLatitude());
        return 6371000.0 * dLatRad;
    }

    private static double computeHeadingAngleDeg(List<Waypoint> wps, int idx) {
        if (wps == null || wps.size() < 2) {
            return 0.0;
        }
        Waypoint a;
        Waypoint b;
        if (idx >= wps.size() - 1) {
            a = wps.get(wps.size() - 2);
            b = wps.get(wps.size() - 1);
        } else {
            a = wps.get(idx);
            b = wps.get(idx + 1);
        }
        double bearing = bearingDeg(a.getLongitude(), a.getLatitude(), b.getLongitude(), b.getLatitude());
        double ang = bearing;
        if (ang > 180.0) {
            ang -= 360.0;
        }
        if (ang <= -180.0) {
            ang += 360.0;
        }
        return ang;
    }

    private static double bearingDeg(double lon1, double lat1, double lon2, double lat2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLon);
        double brng = Math.toDegrees(Math.atan2(y, x));
        return (brng + 360.0) % 360.0;
    }

    private static double computeTotalDistanceMeters(List<Waypoint> wps) {
        if (wps == null || wps.size() < 2) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 0; i < wps.size() - 1; i++) {
            sum += haversineMeters(wps.get(i), wps.get(i + 1));
        }
        return sum;
    }

    private static double haversineMeters(Waypoint a, Waypoint b) {
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

    private static boolean isSmartOblique(FlightPlan plan) {
        return plan != null
                && plan.getMissionType() != null
                && "smartOblique".equalsIgnoreCase(plan.getMissionType().trim());
    }

    private static String resolveTemplateType(FlightPlan plan) {
        if (isSmartOblique(plan)) {
            return "mapping2d";
        }
        return nullToDefault(plan.getTemplateType(), "waypoint");
    }

    private static double resolveAutoSpeed(FlightPlan plan) {
        double v = plan.getAutoFlightSpeed();
        return v > 0 ? v : 10.0;
    }

    private static String fmtFloat(double v) {
        return String.format(Locale.US, "%.12f", v)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static String fmtCoord(double v) {
        return String.format(Locale.US, "%.12f", v)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static String nullToDefault(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}