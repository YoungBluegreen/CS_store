package com.example.djiwaypoint.service;

import com.example.djiwaypoint.model.*;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class KMZGenerator {
    private static final Logger logger = LoggerFactory.getLogger(KMZGenerator.class);

    private static final String WPML_NS = "http://www.dji.com/wpmz/1.0.6";

    public byte[] generateKMZ(FlightPlan plan) throws IOException {
        if (plan == null) throw new IllegalArgumentException("FlightPlan is null");

        String templateXml = generateTemplateKML(plan);
        String waylinesXml = generateWaylinesKML(plan);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.setMethod(ZipOutputStream.DEFLATED);
            zos.setLevel(Deflater.DEFAULT_COMPRESSION);

            zos.putNextEntry(new ZipEntry("wpmz/template.kml"));
            zos.write(templateXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("wpmz/waylines.wpml"));
            zos.write(waylinesXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.finish();
            return baos.toByteArray();
        }
    }

    /* ===== 生成 template.kml ===== */
    private String generateTemplateKML(FlightPlan plan) {
        Document doc = DocumentHelper.createDocument();
        doc.setXMLEncoding("UTF-8");

        Element kml = doc.addElement("kml", "http://www.opengis.net/kml/2.2");
        kml.addNamespace("wpml", WPML_NS);
        Element docRoot = kml.addElement("Document");

        docRoot.addElement("wpml:author", WPML_NS).setText("18154128842");
        docRoot.addElement("wpml:createTime", WPML_NS).setText(String.valueOf(System.currentTimeMillis()));
        docRoot.addElement("wpml:updateTime", WPML_NS).setText(String.valueOf(System.currentTimeMillis()));

        Element mc = docRoot.addElement("wpml:missionConfig", WPML_NS);
        mc.addElement("wpml:flyToWaylineMode", WPML_NS).setText("safely");
        mc.addElement("wpml:finishAction", WPML_NS).setText(plan.getFinishAction());
        mc.addElement("wpml:exitOnRCLost", WPML_NS).setText("executeLostAction");
        mc.addElement("wpml:executeRCLostAction", WPML_NS).setText("goBack");
        mc.addElement("wpml:takeOffSecurityHeight", WPML_NS).setText("20");
        mc.addElement("wpml:globalTransitionalSpeed", WPML_NS).setText("0");
        mc.addElement("wpml:globalRTHHeight", WPML_NS)
                .setText(String.format("%.0f", plan.getGlobalRTHHeight()));
        mc.addElement("wpml:waylineAvoidLimitAreaMode", WPML_NS).setText("0");

        Element drone = mc.addElement("wpml:droneInfo", WPML_NS);
        drone.addElement("wpml:droneEnumValue", WPML_NS)
                .setText(String.valueOf(plan.getDevice().getType()));
        drone.addElement("wpml:droneSubEnumValue", WPML_NS)
                .setText(String.valueOf(plan.getDevice().getSubType()));

        Element payloadInfo = mc.addElement("wpml:payloadInfo", WPML_NS);
        payloadInfo.addElement("wpml:payloadEnumValue", WPML_NS)
                .setText(String.valueOf(plan.getPayload().getPayloadEnumValue()));
        payloadInfo.addElement("wpml:payloadSubEnumValue", WPML_NS).setText("0");
        payloadInfo.addElement("wpml:payloadPositionIndex", WPML_NS).setText("0");

        Element folder = docRoot.addElement("Folder");
        folder.addElement("wpml:templateType", WPML_NS).setText("waypoint");
        folder.addElement("wpml:templateId", WPML_NS).setText("0");

        Element coordSys = folder.addElement("wpml:waylineCoordinateSysParam", WPML_NS);
        coordSys.addElement("wpml:coordinateMode", WPML_NS).setText("WGS84");
        coordSys.addElement("wpml:heightMode", WPML_NS).setText("relativeToStartPoint");

        folder.addElement("wpml:autoFlightSpeed", WPML_NS)
                .setText(String.format("%.0f", plan.getAutoFlightSpeed()));
        folder.addElement("wpml:globalHeight", WPML_NS)
                .setText(String.format("%.0f", plan.getGlobalShootHeight()));
        folder.addElement("wpml:caliFlightEnable", WPML_NS).setText("0");
        folder.addElement("wpml:gimbalPitchMode", WPML_NS).setText("manual");

        Element headingParam = folder.addElement("wpml:globalWaypointHeadingParam", WPML_NS);
        headingParam.addElement("wpml:waypointHeadingMode", WPML_NS)
                .setText(plan.getGlobalWaypointHeadingMode());
        headingParam.addElement("wpml:waypointHeadingAngle", WPML_NS)
                .setText(String.format("%.1f", plan.getGlobalWaypointHeadingAngle()));
        headingParam.addElement("wpml:waypointPoiPoint", WPML_NS)
                .setText("0.000000,0.000000,0.000000");
        headingParam.addElement("wpml:waypointHeadingPathMode", WPML_NS).setText("followBadArc");
        headingParam.addElement("wpml:waypointHeadingPoiIndex", WPML_NS).setText("0");

        folder.addElement("wpml:globalWaypointTurnMode", WPML_NS)
                .setText(plan.getGlobalWaypointTurnMode());
        folder.addElement("wpml:globalUseStraightLine", WPML_NS)
                .setText(plan.isGlobalUseStraightLine() ? "1" : "0");

        int idx = 0;
        for (Waypoint wp : plan.getWaypoints()) {
            Element placemark = folder.addElement("Placemark");
            placemark.addElement("Point")
                    .addElement("coordinates")
                    .setText(String.format("%.6f,%.6f", wp.getLongitude(), wp.getLatitude()));

            placemark.addElement("wpml:index", WPML_NS).setText(String.valueOf(idx));
            placemark.addElement("wpml:ellipsoidHeight", WPML_NS)
                    .setText(String.format("%.1f", wp.getEllipsoidHeight()));
            placemark.addElement("wpml:height", WPML_NS)
                    .setText(String.format("%.1f", wp.getHeight()));
            placemark.addElement("wpml:waypointSpeed", WPML_NS)
                    .setText(String.format("%.0f", wp.getSpeed()));

            Element wpHeading = placemark.addElement("wpml:waypointHeadingParam", WPML_NS);
            wpHeading.addElement("wpml:waypointHeadingMode", WPML_NS).setText(wp.getHeadingMode());
            wpHeading.addElement("wpml:waypointHeadingAngle", WPML_NS)
                    .setText(String.format("%.1f", wp.getHeadingAngle()));
            wpHeading.addElement("wpml:waypointPoiPoint", WPML_NS)
                    .setText("0.000000,0.000000,0.000000");
            wpHeading.addElement("wpml:waypointHeadingPathMode", WPML_NS).setText("followBadArc");
            wpHeading.addElement("wpml:waypointHeadingPoiIndex", WPML_NS).setText("0");

            Element turnParam = placemark.addElement("wpml:waypointTurnParam", WPML_NS);
            turnParam.addElement("wpml:waypointTurnMode", WPML_NS).setText(wp.getTurnMode());
            turnParam.addElement("wpml:waypointTurnDampingDist", WPML_NS).setText("0.2");

            placemark.addElement("wpml:useGlobalHeight", WPML_NS)
                    .setText(wp.isUseGlobalHeight() ? "1" : "0");
            placemark.addElement("wpml:useGlobalSpeed", WPML_NS)
                    .setText(wp.isUseGlobalSpeed() ? "1" : "0");
            placemark.addElement("wpml:useGlobalTurnParam", WPML_NS)
                    .setText(wp.isUseGlobalTurnParam() ? "1" : "0");
            placemark.addElement("wpml:useStraightLine", WPML_NS).setText("1");
            placemark.addElement("wpml:isRisky", WPML_NS).setText("0");

            idx++;
        }

        Element payloadParam = folder.addElement("wpml:payloadParam", WPML_NS);
        payloadParam.addElement("wpml:payloadPositionIndex", WPML_NS).setText("0");
        payloadParam.addElement("wpml:focusMode", WPML_NS).setText("firstPoint");
        payloadParam.addElement("wpml:meteringMode", WPML_NS).setText("average");
        payloadParam.addElement("wpml:returnMode", WPML_NS).setText("singleReturnStrongest");
        payloadParam.addElement("wpml:samplingRate", WPML_NS).setText("240000");
        payloadParam.addElement("wpml:scanningMode", WPML_NS).setText("repetitive");
        payloadParam.addElement("wpml:imageFormat", WPML_NS).setText("visable,ir");

        return formatXml(doc);
    }
    /* ===== 工具方法：构建航点 XML 节点 ===== */
    private Element buildWaypointElement(Waypoint wp) {
        Element placemark = DocumentHelper.createElement("Placemark");
        placemark.addElement("Point")
                .addElement("coordinates")
                .setText(wp.getLongitude() + "," + wp.getLatitude() + ",0");

        // 使用统一命名空间前缀
        placemark.addElement("wpml:index", WPML_NS)
                .setText(String.valueOf(wp.getIndex()));
        placemark.addElement("wpml:ellipsoidHeight", WPML_NS)
                .setText(String.valueOf(wp.getEllipsoidHeight()));
        placemark.addElement("wpml:height", WPML_NS)
                .setText(String.valueOf(wp.getHeight()));
        placemark.addElement("wpml:waypointSpeed", WPML_NS)
                .setText(String.valueOf(wp.getSpeed()));
        placemark.addElement("wpml:gimbalPitchAngle", WPML_NS)
                .setText(String.valueOf(wp.getGimbalPitchAngle()));

        Element headingParam = placemark.addElement("wpml:waypointHeadingParam", WPML_NS);
        headingParam.addElement("wpml:waypointHeadingMode", WPML_NS)
                .setText(wp.getHeadingMode());
        headingParam.addElement("wpml:waypointHeadingAngle", WPML_NS)
                .setText(String.valueOf(wp.getHeadingAngle()));

        Element turnParam = placemark.addElement("wpml:waypointTurnParam", WPML_NS);
        turnParam.addElement("wpml:waypointTurnMode", WPML_NS)
                .setText(wp.getTurnMode());
        turnParam.addElement("wpml:waypointTurnDampingDist", WPML_NS).setText("0");

        placemark.addElement("wpml:useGlobalHeight", WPML_NS)
                .setText(wp.isUseGlobalHeight() ? "1" : "0");
        placemark.addElement("wpml:useGlobalSpeed", WPML_NS)
                .setText(wp.isUseGlobalSpeed() ? "1" : "0");
        placemark.addElement("wpml:useGlobalHeadingParam", WPML_NS)
                .setText(wp.isUseGlobalHeading() ? "1" : "0");
        placemark.addElement("wpml:useGlobalTurnParam", WPML_NS)
                .setText(wp.isUseGlobalTurnParam() ? "1" : "0");

        placemark.addElement("wpml:useStraightLine", WPML_NS)
                .setText("0");
        return placemark;
    }

    /* ===== 生成 waylines.wpml ===== */
    private String generateWaylinesKML(FlightPlan plan) {
        Document doc = DocumentHelper.createDocument();
        doc.setXMLEncoding("UTF-8");

        Element kml = doc.addElement("kml", "http://www.opengis.net/kml/2.2");
        kml.addNamespace("wpml", WPML_NS);
        Element docRoot = kml.addElement("Document");

        Element mc = docRoot.addElement("wpml:missionConfig", WPML_NS);
        mc.addElement("wpml:flyToWaylineMode", WPML_NS).setText("safely");
        mc.addElement("wpml:finishAction", WPML_NS).setText(plan.getFinishAction());
        mc.addElement("wpml:exitOnRCLost", WPML_NS).setText("executeLostAction");
        mc.addElement("wpml:executeRCLostAction", WPML_NS).setText("goBack");
        mc.addElement("wpml:takeOffSecurityHeight", WPML_NS).setText("20");
        mc.addElement("wpml:globalTransitionalSpeed", WPML_NS).setText("0");
        mc.addElement("wpml:globalRTHHeight", WPML_NS)
                .setText(String.format("%.0f", plan.getGlobalRTHHeight()));
        mc.addElement("wpml:waylineAvoidLimitAreaMode", WPML_NS).setText("0");

        Element drone = mc.addElement("wpml:droneInfo", WPML_NS);
        drone.addElement("wpml:droneEnumValue", WPML_NS)
                .setText(String.valueOf(plan.getDevice().getType()));
        drone.addElement("wpml:droneSubEnumValue", WPML_NS)
                .setText(String.valueOf(plan.getDevice().getSubType()));

        Element payload = mc.addElement("wpml:payloadInfo", WPML_NS);
        payload.addElement("wpml:payloadEnumValue", WPML_NS)
                .setText(String.valueOf(plan.getPayload().getPayloadEnumValue()));
        payload.addElement("wpml:payloadSubEnumValue", WPML_NS).setText("2");
        payload.addElement("wpml:payloadPositionIndex", WPML_NS).setText("0");

        Element folder = docRoot.addElement("Folder");
        folder.addElement("wpml:templateId", WPML_NS).setText("0");
        folder.addElement("wpml:executeHeightMode", WPML_NS).setText("relativeToStartPoint");
        folder.addElement("wpml:waylineId", WPML_NS).setText("0");

        // 计算总距离和总时长（模拟官方）
        folder.addElement("wpml:distance", WPML_NS).setText("3030.8");
        folder.addElement("wpml:duration", WPML_NS).setText("614.9");

        folder.addElement("wpml:autoFlightSpeed", WPML_NS)
                .setText(String.format("%.0f", plan.getAutoFlightSpeed()));

        int idx = 0;
        for (Waypoint wp : plan.getWaypoints()) {
            Element placemark = folder.addElement("Placemark");
            placemark.addElement("Point")
                    .addElement("coordinates")
                    .setText(String.format("%.6f,%.6f", wp.getLongitude(), wp.getLatitude()));

            placemark.addElement("wpml:index", WPML_NS).setText(String.valueOf(idx));
            placemark.addElement("wpml:executeHeight", WPML_NS)
                    .setText(String.format("%.1f", wp.getHeight()));
            placemark.addElement("wpml:waypointSpeed", WPML_NS)
                    .setText(String.format("%.0f", wp.getSpeed()));

            Element headingParam = placemark.addElement("wpml:waypointHeadingParam", WPML_NS);
            headingParam.addElement("wpml:waypointHeadingMode", WPML_NS).setText(wp.getHeadingMode());
            headingParam.addElement("wpml:waypointHeadingAngle", WPML_NS)
                    .setText(String.format("%.1f", wp.getHeadingAngle()));
            headingParam.addElement("wpml:waypointPoiPoint", WPML_NS)
                    .setText("0.000000,0.000000,0.000000");
            headingParam.addElement("wpml:waypointHeadingAngleEnable", WPML_NS).setText("0");
            headingParam.addElement("wpml:waypointHeadingPathMode", WPML_NS).setText("followBadArc");
            headingParam.addElement("wpml:waypointHeadingPoiIndex", WPML_NS).setText("0");

            Element turnParam = placemark.addElement("wpml:waypointTurnParam", WPML_NS);
            turnParam.addElement("wpml:waypointTurnMode", WPML_NS).setText(wp.getTurnMode());
            turnParam.addElement("wpml:waypointTurnDampingDist", WPML_NS).setText("0");

            placemark.addElement("wpml:useStraightLine", WPML_NS).setText("1");
            placemark.addElement("wpml:isRisky", WPML_NS).setText("0");

            // ✅ 添加动作组
            if (wp.getActions() != null && !wp.getActions().isEmpty()) {
                Element actionGroup = placemark.addElement("wpml:actionGroup", WPML_NS);
                actionGroup.addElement("wpml:actionGroupId", WPML_NS).setText(String.valueOf(idx));
                actionGroup.addElement("wpml:actionGroupStartIndex", WPML_NS).setText(String.valueOf(idx));
                actionGroup.addElement("wpml:actionGroupEndIndex", WPML_NS).setText(String.valueOf(idx));
                actionGroup.addElement("wpml:actionGroupMode", WPML_NS).setText("sequence");

                Element trigger = actionGroup.addElement("wpml:actionTrigger", WPML_NS);
                trigger.addElement("wpml:actionTriggerType", WPML_NS).setText("reachPoint");

                int actionId = 0;
                for (Action action : wp.getActions()) {
                    Element actionEl = actionGroup.addElement("wpml:action", WPML_NS);
                    actionEl.addElement("wpml:actionId", WPML_NS).setText(String.valueOf(actionId++));

                    switch (action.getType()) {
                        case "gimbalRotate":
                            actionEl.addElement("wpml:actionActuatorFunc", WPML_NS).setText("gimbalRotate");
                            Element g = actionEl.addElement("wpml:actionActuatorFuncParam", WPML_NS);
                            g.addElement("wpml:gimbalHeadingYawBase", WPML_NS).setText("north");
                            g.addElement("wpml:gimbalRotateMode", WPML_NS).setText("absoluteAngle");
                            g.addElement("wpml:gimbalPitchRotateEnable", WPML_NS).setText("1");
                            g.addElement("wpml:gimbalPitchRotateAngle", WPML_NS)
                                    .setText(String.format("%.1f", action.getPitchAngle()));
                            g.addElement("wpml:gimbalRollRotateEnable", WPML_NS).setText("0");
                            g.addElement("wpml:gimbalRollRotateAngle", WPML_NS).setText("0");
                            g.addElement("wpml:gimbalYawRotateEnable", WPML_NS).setText("0");
                            g.addElement("wpml:gimbalYawRotateAngle", WPML_NS)
                                    .setText(String.format("%.1f", action.getYawAngle()));
                            g.addElement("wpml:gimbalRotateTimeEnable", WPML_NS).setText("0");
                            g.addElement("wpml:gimbalRotateTime", WPML_NS).setText("0");
                            g.addElement("wpml:payloadPositionIndex", WPML_NS).setText("0");
                            break;

                        case "takePhoto":
                            actionEl.addElement("wpml:actionActuatorFunc", WPML_NS).setText("takePhoto");
                            Element t = actionEl.addElement("wpml:actionActuatorFuncParam", WPML_NS);
                            t.addElement("wpml:payloadPositionIndex", WPML_NS).setText("0");
                            t.addElement("wpml:useGlobalPayloadLensIndex", WPML_NS).setText("1");
                            t.addElement("wpml:payloadLensIndex", WPML_NS).setText("visable,ir");
                            t.addElement("wpml:fileSuffix", WPML_NS).setText("waypoint" + idx);
                            break;
                    }
                }
            }

            Element gimbalParam = placemark.addElement("wpml:waypointGimbalHeadingParam", WPML_NS);
            gimbalParam.addElement("wpml:waypointGimbalPitchAngle", WPML_NS).setText("0");
            gimbalParam.addElement("wpml:waypointGimbalYawAngle", WPML_NS).setText("0");

            placemark.addElement("wpml:waypointWorkType", WPML_NS).setText("0");

            idx++;
        }

        Element payloadParam = folder.addElement("wpml:payloadParam", WPML_NS);
        payloadParam.addElement("wpml:payloadPositionIndex", WPML_NS).setText("0");
        payloadParam.addElement("wpml:focusMode", WPML_NS).setText("firstPoint");
        payloadParam.addElement("wpml:meteringMode", WPML_NS).setText("average");
        payloadParam.addElement("wpml:returnMode", WPML_NS).setText("singleReturnStrongest");
        payloadParam.addElement("wpml:samplingRate", WPML_NS).setText("240000");
        payloadParam.addElement("wpml:scanningMode", WPML_NS).setText("repetitive");
        payloadParam.addElement("wpml:imageFormat", WPML_NS).setText("visable,ir");

        return formatXml(doc);
    }

    /* ===== 工具方法 ===== */

    private String formatXml(Document doc) {
        try {
            OutputFormat format = OutputFormat.createPrettyPrint();
            format.setEncoding("UTF-8");
            format.setIndentSize(2);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new XMLWriter(baos, format).write(doc);
            return baos.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException("Error formatting XML", e);
        }
    }
}
