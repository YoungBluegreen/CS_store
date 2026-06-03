package com.example.djiwaypoint.controller;

import com.example.djiwaypoint.model.Action;
import com.example.djiwaypoint.model.DroneInfo;
import com.example.djiwaypoint.model.FlightPlan;
import com.example.djiwaypoint.model.LatLng;
import com.example.djiwaypoint.model.Waypoint;
import com.example.djiwaypoint.service.AreaCoveragePlanner;
import com.example.djiwaypoint.service.KMZGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@RestController
public class FlightPlanController {

    private static final Logger log = LoggerFactory.getLogger(FlightPlanController.class);

    // ================== 可调参数 ==================
    private static final double DEFAULT_SPEED_MPS = 10.0;

    // 默认重叠率
    private static final double DEFAULT_SIDE_OVERLAP = 0.70;   // 旁向
    private static final double DEFAULT_FRONT_OVERLAP = 0.80;  // 航向

    // footprint 经验系数
    private static final double DEFAULT_COVER_WIDTH_FACTOR = 1.15;
    private static final double DEFAULT_COVER_HEIGHT_FACTOR = 0.85;

    // 禁飞区安全外扩（米）
    private static final double DEFAULT_NO_FLY_MARGIN_M = 5.0;

    @Autowired
    private KMZGenerator kmzGenerator;

    @Autowired
    private AreaCoveragePlanner areaCoveragePlanner;

    @PostMapping("/generate-kmz")
    public ResponseEntity<byte[]> generateKMZ(@RequestBody FlightPlan plan) {
        try {
            if (plan == null) {
                return badRequest("请求体为空，未收到 FlightPlan 数据");
            }

            ensureDefaultDeviceAndPayload(plan);

            double height = plan.getGlobalShootHeight() > 0 ? plan.getGlobalShootHeight() : 100.0;

            // ======= 疏密逻辑（spacing/interval）=======
            double coverWidth = DEFAULT_COVER_WIDTH_FACTOR * height;
            double coverHeight = DEFAULT_COVER_HEIGHT_FACTOR * height;

            double spacing = coverWidth * (1.0 - DEFAULT_SIDE_OVERLAP);
            if (spacing < 8.0) {
                spacing = 8.0;
            }

            double interval = coverHeight * (1.0 - DEFAULT_FRONT_OVERLAP);
            if (interval < 2.0) {
                interval = 2.0;
            }

            log.info("Spacing={}m, Interval={}m", f2(spacing), f2(interval));
            log.info("[CTRL] flightAreaPts={}", plan.getFlightArea() == null ? -1 : plan.getFlightArea().size());
            log.info("[CTRL] noFlyAreas={}", plan.getNoFlyAreas() == null ? 0 : plan.getNoFlyAreas().size());

            // ======= 任务类型统一收敛 =======
            // 前端传 oblique 时，这里强制转成 smartOblique。
            // 不再走旧的多段 oblique 逻辑，避免生成 cross / transfer 航线。
            String requestMissionType = normalizeMissionType(plan.getMissionType());
            if ("oblique".equalsIgnoreCase(requestMissionType)) {
                plan.setMissionType("smartOblique");
            } else {
                plan.setMissionType(requestMissionType);
            }

            // ======= 生成航点（如果前端没直接传 waypoints）=======
            if (plan.getWaypoints() == null || plan.getWaypoints().isEmpty()) {
                if (plan.getFlightArea() == null || plan.getFlightArea().size() < 3) {
                    return badRequest("缺少 waypoints 且 flightArea 顶点不足（>=3）");
                }

                List<LatLng> boundary = toLatLngList(plan.getFlightArea());
                List<List<LatLng>> noFly = toNoFlyLatLngs(plan.getNoFlyAreas());
                log.info("[CTRL] boundaryPts={}, noFlyPolys={}", boundary.size(), (noFly == null ? 0 : noFly.size()));

                double speed = plan.getAutoFlightSpeed() > 0 ? plan.getAutoFlightSpeed() : DEFAULT_SPEED_MPS;
                double margin = DEFAULT_NO_FLY_MARGIN_M;

                // 只生成一套主蛇形骨架
                List<Waypoint> wps = areaCoveragePlanner.generateCoverage(
                        boundary,
                        noFly,
                        spacing,
                        height,
                        margin
                );

                if (wps == null || wps.isEmpty()) {
                    return badRequest("生成蛇形航线失败：请检查 flightArea / noFlyAreas 是否有效");
                }

                for (Waypoint wp : wps) {
                    wp.setSpeed(speed);
                }

                plan.setWaypoints(wps);
            }

            // ======= 强制清空旧倾斜补线，避免上游/旧逻辑残留污染 =======
            plan.setObliqueCrossWaypoints(new ArrayList<>());
            plan.setObliqueTransferWaypoints(new ArrayList<>());

            // ======= 任务动作参数 =======
            configureMissionActions(plan, interval);

            // ======= 校验并重设 index =======
            validateFlightPlan(plan);

            log.info("[CTRL] kmzExportWaypoints={}",
                    plan.getWaypoints() == null ? 0 : plan.getWaypoints().size());

            log.info("[CTRL] requestMissionType={}", requestMissionType);
            log.info("[CTRL] finalPlanMissionType={}", plan.getMissionType());
            log.info("[CTRL] obliqueCrossWaypoints={}",
                    plan.getObliqueCrossWaypoints() == null ? 0 : plan.getObliqueCrossWaypoints().size());
            log.info("[CTRL] obliqueTransferWaypoints={}",
                    plan.getObliqueTransferWaypoints() == null ? 0 : plan.getObliqueTransferWaypoints().size());

            System.out.println("========== FlightPlanController before generateKMZ ==========");
            System.out.println("request missionType = " + requestMissionType);
            System.out.println("plan missionType = " + plan.getMissionType());
            System.out.println("waypoints.size = " + (plan.getWaypoints() == null ? 0 : plan.getWaypoints().size()));
            System.out.println("obliqueCrossWaypoints.size = " + (plan.getObliqueCrossWaypoints() == null ? 0 : plan.getObliqueCrossWaypoints().size()));
            System.out.println("obliqueTransferWaypoints.size = " + (plan.getObliqueTransferWaypoints() == null ? 0 : plan.getObliqueTransferWaypoints().size()));

            // ======= 生成 KMZ =======
            byte[] kmzBytes = kmzGenerator.generateKMZ(plan);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + buildFilename(plan) + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.google-earth.kmz"))
                    .body(kmzBytes);

        } catch (IllegalArgumentException e) {
            log.warn("参数错误: {}", e.getMessage());
            return badRequest(resolveErrorMessage(e));
        } catch (Exception e) {
            log.error("Unhandled error", e);
            return error500("Internal server error: " + resolveErrorMessage(e));
        }
    }

    // ============================================================
    // 任务动作配置
    // ============================================================

    private void configureMissionActions(FlightPlan plan, double shootInterval) {
        if (plan.getWaypoints() == null || plan.getWaypoints().isEmpty()) {
            return;
        }

        Waypoint first = plan.getWaypoints().get(0);
        if (first.getActions() == null) {
            first.setActions(new ArrayList<>());
        }

        boolean hasGimbal = false;
        for (Action a : first.getActions()) {
            if (a.getActionActuatorFunc() == 1) { // 1 = GimbalRotate（沿用你现有约定）
                hasGimbal = true;
                break;
            }
        }

        if (!hasGimbal) {
            Action g = new Action();
            g.setActionId(1);
            g.setActionActuatorFunc(1);
            g.setActionActuatorParam(-90);
            first.getActions().add(g);
        }

        plan.setPhotoIntervalMeter(shootInterval);

        // smartOblique 默认 -45 倾斜角
        if ("smartOblique".equals(plan.getMissionType()) && plan.getObliqueGimbalPitch() == 0.0) {
            plan.setObliqueGimbalPitch(-45.0);
        }
    }

    private String normalizeMissionType(String missionType) {
        if (missionType == null || missionType.isBlank()) {
            return "ortho";
        }

        String mt = missionType.trim();

        if ("oblique".equalsIgnoreCase(mt)
                || "smartOblique".equalsIgnoreCase(mt)
                || "smart_oblique".equalsIgnoreCase(mt)
                || "smart-oblique".equalsIgnoreCase(mt)
                || "smartoblique".equalsIgnoreCase(mt)) {
            return "oblique";
        }

        return "ortho";
    }

    private String buildFilename(FlightPlan plan) {
        return "smartOblique".equals(plan.getMissionType())
                ? "dji_mission_m3m_smart_oblique.kmz"
                : "dji_mission_m3m_ortho.kmz";
    }

    // ============================================================
    // 默认值/校验
    // ============================================================

    private void ensureDefaultDeviceAndPayload(FlightPlan plan) {
        if (plan.getDevice() == null) {
            DroneInfo d = new DroneInfo();
            d.setDomain(0);
            d.setType(77);
            d.setSubType(0);
            plan.setDevice(d);
        }

        if (plan.getPayload() == null) {
            plan.setDefaultPayloadForDrone();
        }

        if (plan.getAutoFlightSpeed() <= 0) {
            plan.setAutoFlightSpeed(DEFAULT_SPEED_MPS);
        }
    }

    private void validateFlightPlan(FlightPlan plan) {
        if (plan.getWaypoints() == null || plan.getWaypoints().isEmpty()) {
            throw new IllegalArgumentException("航点列表为空");
        }

        for (int i = 0; i < plan.getWaypoints().size(); i++) {
            Waypoint wp = plan.getWaypoints().get(i);
            validateCoordinates(wp.getLatitude(), wp.getLongitude(), "航点[" + i + "]");
            wp.setIndex(i);
        }
    }

    private void validateCoordinates(double latitude, double longitude, String context) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException(context + " 纬度不合法");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException(context + " 经度不合法");
        }
    }

    private ResponseEntity<byte[]> badRequest(String msg) {
        return ResponseEntity.badRequest()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(resolveErrorMessage(msg).getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<byte[]> error500(String msg) {
        return ResponseEntity.status(500)
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(resolveErrorMessage(msg).getBytes(StandardCharsets.UTF_8));
    }

    private String resolveErrorMessage(Throwable e) {
        if (e == null) {
            return "Unknown error";
        }
        return resolveErrorMessage(e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getName()
                : e.getMessage());
    }

    private String resolveErrorMessage(String msg) {
        return (msg == null || msg.isBlank()) ? "Unknown error" : msg;
    }

    // ============================================================
    // DTO/Model 转换
    // ============================================================

    private List<LatLng> toLatLngList(List<Waypoint> waypoints) {
        List<LatLng> list = new ArrayList<>();
        for (Waypoint wp : waypoints) {
            list.add(new LatLng(wp.getLongitude(), wp.getLatitude()));
        }
        return list;
    }

    private List<List<LatLng>> toNoFlyLatLngs(List<List<Waypoint>> noFlyAreas) {
        if (noFlyAreas == null || noFlyAreas.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<LatLng>> out = new ArrayList<>();
        for (List<Waypoint> poly : noFlyAreas) {
            if (poly == null || poly.size() < 3) {
                continue;
            }
            out.add(toLatLngList(poly));
        }
        return out;
    }

    private String f2(double v) {
        return String.format(Locale.US, "%.2f", v);
    }
}
