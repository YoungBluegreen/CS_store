package com.example.djiwaypoint.controller;

import com.example.djiwaypoint.model.*;
import com.example.djiwaypoint.service.KMZGenerator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class FlightPlanController {

    private final KMZGenerator kmzGenerator;

    public FlightPlanController(KMZGenerator kmzGenerator) {
        this.kmzGenerator = kmzGenerator;
    }

    @PostMapping("/generate-kmz")
    public ResponseEntity<byte[]> generateKMZ(@RequestBody FlightPlan plan) {
        try {
            // 验证坐标是否在合理范围内
            validateCoordinates(plan.getTakeoffPoint().getLatitude(),
                    plan.getTakeoffPoint().getLongitude(),
                    "起飞点");

            for (Waypoint wp : plan.getWaypoints()) {
                validateCoordinates(wp.getLatitude(), wp.getLongitude(),
                        "航点 #" + wp.getIndex());
            }
            // 前置验证 - 航点航线必需参数
            validateFlightPlan(plan);

            byte[] kmzBytes = kmzGenerator.generateKMZ(plan);
            String filename = "dji_mission.kmz";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/vnd.google-earth.kmz")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .body(kmzBytes);
        } catch (IllegalArgumentException e) {
            // 处理参数错误
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(e.getMessage().getBytes());
        } catch (IOException e) {
            // 处理IO错误
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("生成KMZ文件失败: " + e.getMessage()).getBytes());
        } catch (Exception e) {
            // 处理其他错误
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("服务器错误: " + e.getMessage()).getBytes());
        }
    }

    private void validateFlightPlan(FlightPlan plan) {
        // 基本参数验证
        if (plan == null) {
            throw new IllegalArgumentException("Flight plan data is required");
        }

        if (plan.getDevice() == null) {
            throw new IllegalArgumentException("Drone information is required");
        }

        if (plan.getPayload() == null) {
            throw new IllegalArgumentException("Payload information is required");
        }

        if (plan.getWaypoints() == null || plan.getWaypoints().isEmpty()) {
            throw new IllegalArgumentException("At least one waypoint is required");
        }

        // 起飞点验证
        if (plan.getTakeoffPoint() == null) {
            throw new IllegalArgumentException("Takeoff point is required");
        }

        // 验证起飞点坐标
        validateCoordinates(plan.getTakeoffPoint().getLatitude(),
                plan.getTakeoffPoint().getLongitude(),
                "起飞点");

        // 验证返航高度
        if (plan.getGlobalRTHHeight() < 2 || plan.getGlobalRTHHeight() > 1500) {
            throw new IllegalArgumentException("返航高度必须在2-1500米之间");
        }

        // 验证结束动作
        String[] validFinishActions = {"goHome", "noAction", "autoLand", "gotoFirstWaypoint"};
        boolean validAction = false;
        for (String action : validFinishActions) {
            if (action.equals(plan.getFinishAction())) {
                validAction = true;
                break;
            }
        }
        if (!validAction) {
            throw new IllegalArgumentException("无效的结束动作: " + plan.getFinishAction());
        }

        // 验证航点转弯模式
        String[] validTurnModes = {
                "coordinateTurn",
                "toPointAndStopWithDiscontinuityCurvature",
                "toPointAndStopWithContinuityCurvature",
                "toPointAndPassWithContinuityCurvature"
        };
        boolean validTurnMode = false;
        for (String mode : validTurnModes) {
            if (mode.equals(plan.getGlobalWaypointTurnMode())) {
                validTurnMode = true;
                break;
            }
        }
        if (!validTurnMode) {
            throw new IllegalArgumentException("无效的转弯模式: " + plan.getGlobalWaypointTurnMode());
        }

        // 验证航向模式
        String[] validHeadingModes = {
                "followWayline",
                "manual",
                "towardPointOfInterest"
        };
        boolean validHeadingMode = false;
        for (String mode : validHeadingModes) {
            if (mode.equals(plan.getGlobalWaypointHeadingMode())) {
                validHeadingMode = true;
                break;
            }
        }
        if (!validHeadingMode) {
            throw new IllegalArgumentException("无效的航向模式: " + plan.getGlobalWaypointHeadingMode());
        }
        // 验证无人机类型是否支持
        boolean validDrone = false;
        for (DroneInfo.DroneType type : DroneInfo.DroneType.values()) {
            if (type.getType() == plan.getDevice().getType() &&
                    type.getSubType() == plan.getDevice().getSubType()) {
                validDrone = true;
                break;
            }
        }
        if (!validDrone) {
            throw new IllegalArgumentException("不支持的无人机类型");
        }

        // 验证负载是否支持
        boolean validPayload = false;
        for (PayloadInfo.PayloadType type : PayloadInfo.PayloadType.values()) {
            if (type.getValue() == plan.getPayload().getPayloadEnumValue()) {
                validPayload = true;
                break;
            }
        }
        if (!validPayload) {
            throw new IllegalArgumentException("不支持的负载类型");
        }

        // 验证航点
        for (int i = 0; i < plan.getWaypoints().size(); i++) {
            Waypoint waypoint = plan.getWaypoints().get(i);
            if (waypoint == null) {
                throw new IllegalArgumentException("航点 #" + (i + 1) + " 不能为空");
            }

            // 验证坐标
            validateCoordinates(waypoint.getLatitude(), waypoint.getLongitude(), "航点 #" + (i + 1));

            if (waypoint.getLongitude() < -180 || waypoint.getLongitude() > 180) {
                throw new IllegalArgumentException("航点 #" + (i + 1) + " 经度超出范围: " + waypoint.getLongitude());
            }

            if (waypoint.getLatitude() < -90 || waypoint.getLatitude() > 90) {
                throw new IllegalArgumentException("航点 #" + (i + 1) + " 纬度超出范围: " + waypoint.getLatitude());
            }
            // 验证高度
            if (waypoint.getHeight() <= 0) {
                throw new IllegalArgumentException("航点 #" + (i + 1) + " 高度必须大于0");
            }

            if (waypoint.getEllipsoidHeight() <= 0) {
                throw new IllegalArgumentException("航点 #" + (i + 1) + " WGS84高度必须大于0");
            }

            // 验证速度
            if (waypoint.getSpeed() < 1 || waypoint.getSpeed() > 15) {
                throw new IllegalArgumentException("航点 #" + (i + 1) + " 速度必须在1-15 m/s之间");
            }

            // 验证动作
            if (waypoint.getActions() != null) {
                for (Action action : waypoint.getActions()) {
                    validateAction(action, i + 1);
                }
            }
        }
        // 验证负载与设备是否匹配
        if (!isPayloadCompatible(plan.getDevice(), plan.getPayload())) {
            throw new IllegalArgumentException("选择的负载与设备不兼容");
        }
    }

    // 添加负载兼容性检查方法
    private boolean isPayloadCompatible(DroneInfo drone, PayloadInfo payload) {
        if (drone == null || payload == null) return false;

        switch (drone.getType()) {
            case 60: // M300 RTK
            case 89: // M350 RTK
                return payload.getPayloadEnumValue() == 42 || // H20
                        payload.getPayloadEnumValue() == 43 || // H20T
                        payload.getPayloadEnumValue() == 61 || // H20N
                        payload.getPayloadEnumValue() == 82 || // H30
                        payload.getPayloadEnumValue() == 83;   // H30T

            case 67: // M30/M30T
                if (drone.getSubType() == 0) { // M30
                    return payload.getPayloadEnumValue() == 52; // M30 Camera
                } else { // M30T
                    return payload.getPayloadEnumValue() == 53; // M30T Camera
                }

            case 77: // M3E/M3T/M3M
                return payload.getPayloadEnumValue() == 66 || // M3E
                        payload.getPayloadEnumValue() == 67 || // M3T
                        payload.getPayloadEnumValue() == 68;   // M3M

            case 91: // M3D/M3TD
                return payload.getPayloadEnumValue() == 80 || // M3D
                        payload.getPayloadEnumValue() == 81;   // M3TD

            case 99: // M4E/M4T
                return payload.getPayloadEnumValue() == 88 || // M4E
                        payload.getPayloadEnumValue() == 89;   // M4T

            case 100: // M4D/M4TD
                return payload.getPayloadEnumValue() == 98 || // M4D
                        payload.getPayloadEnumValue() == 99;   // M4TD

            default:
                return false;
        }
    }

    private void validateCoordinates(double latitude, double longitude, String context) {
        if (Double.isNaN(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException(context + " 纬度无效: " + latitude);
        }

        if (Double.isNaN(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException(context + " 经度无效: " + longitude);
        }
    }

    private void validateAction(Action action, int waypointIndex) {
        if (action == null) {
            throw new IllegalArgumentException("航点 #" + waypointIndex + " 动作不能为空");
        }

        if ("gimbalRotate".equals(action.getType())) {
            // 验证云台角度
            if (action.getPitchAngle() < -90 || action.getPitchAngle() > 0) {
                throw new IllegalArgumentException(
                        "航点 #" + waypointIndex + " 云台俯仰角必须在-90到0度之间: " + action.getPitchAngle()
                );
            }

            if (action.getYawAngle() < -180 || action.getYawAngle() > 180) {
                throw new IllegalArgumentException(
                        "航点 #" + waypointIndex + " 云台偏航角必须在-180到180度之间: " + action.getYawAngle()
                );
            }
        } else if ("takePhoto".equals(action.getType())) {
            // 拍照动作不需要额外参数
        } else {
            throw new IllegalArgumentException(
                    "航点 #" + waypointIndex + " 不支持的动作类型: " + action.getType()
            );
        }
    }
}
