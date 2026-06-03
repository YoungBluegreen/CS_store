package com.example.djiwaypoint.model;

import lombok.Data;
import java.util.List;

@Data
public class FlightPlan {
    private List<Waypoint> waypoints;
    private DroneInfo device;
    private PayloadInfo payload;
    private String templateType = "waypoint"; // waypoint, mapping2d, mapping3d, strip
    private double autoFlightSpeed = 10;
    private double globalShootHeight = 100;
    private double surfaceRelativeHeight = 100;

    // 新增全局参数（航点航线必需）
    private double globalRTHHeight = 50; // 全局返航高度
    private String globalWaypointTurnMode = "toPointAndStopWithDiscontinuityCurvature";
    private boolean globalUseStraightLine = false;
    private String globalWaypointHeadingMode = "followWayline";
    private double globalWaypointHeadingAngle = 0;
    private String finishAction = "goHome"; // 航线结束动作：goHome, noAction, autoLand, gotoFirstWaypoint

    // 新增起飞点信息
    private TakeoffPoint takeoffPoint = new TakeoffPoint();

    // 起飞点内部类
    @Data
    public static class TakeoffPoint {
        private double latitude;
        private double longitude;
        private double altitude;
        private double AGLHeight = 30; // 离地高度，默认30米

        public TakeoffPoint() {
            // 默认构造函数
        }

        // 添加getter和setter方法以确保JSON序列化正确
        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        public double getAltitude() {
            return altitude;
        }

        public void setAltitude(double altitude) {
            this.altitude = altitude;
        }

        public double getAGLHeight() {
            return AGLHeight;
        }

        public void setAGLHeight(double AGLHeight) {
            this.AGLHeight = AGLHeight;
        }
    }

    // 构造函数
    public FlightPlan() {}

    // Getter 和 Setter 方法
    public List<Waypoint> getWaypoints() {
        return waypoints;
    }

    public void setWaypoints(List<Waypoint> waypoints) {
        this.waypoints = waypoints;
    }

    public DroneInfo getDevice() {
        return device;
    }

    public void setDevice(DroneInfo device) {
        this.device = device;
    }

    public PayloadInfo getPayload() {
        return payload;
    }

    public void setPayload(PayloadInfo payload) {
        this.payload = payload;
    }

    public String getTemplateType() {
        return templateType;
    }

    public void setTemplateType(String templateType) {
        this.templateType = templateType;
    }

    public double getAutoFlightSpeed() {
        return autoFlightSpeed;
    }

    public void setAutoFlightSpeed(double autoFlightSpeed) {
        this.autoFlightSpeed = autoFlightSpeed;
    }

    public double getGlobalShootHeight() {
        return globalShootHeight;
    }

    public void setGlobalShootHeight(double globalShootHeight) {
        this.globalShootHeight = globalShootHeight;
    }

    public double getSurfaceRelativeHeight() {
        return surfaceRelativeHeight;
    }

    public void setSurfaceRelativeHeight(double surfaceRelativeHeight) {
        this.surfaceRelativeHeight = surfaceRelativeHeight;
    }

    public double getGlobalRTHHeight() {
        return globalRTHHeight;
    }

    public void setGlobalRTHHeight(double globalRTHHeight) {
        this.globalRTHHeight = globalRTHHeight;
    }

    public String getGlobalWaypointTurnMode() {
        return globalWaypointTurnMode;
    }

    public void setGlobalWaypointTurnMode(String globalWaypointTurnMode) {
        this.globalWaypointTurnMode = globalWaypointTurnMode;
    }

    public boolean isGlobalUseStraightLine() {
        return globalUseStraightLine;
    }

    public void setGlobalUseStraightLine(boolean globalUseStraightLine) {
        this.globalUseStraightLine = globalUseStraightLine;
    }

    public String getGlobalWaypointHeadingMode() {
        return globalWaypointHeadingMode;
    }

    public void setGlobalWaypointHeadingMode(String globalWaypointHeadingMode) {
        this.globalWaypointHeadingMode = globalWaypointHeadingMode;
    }

    public double getGlobalWaypointHeadingAngle() {
        return globalWaypointHeadingAngle;
    }

    public void setGlobalWaypointHeadingAngle(double globalWaypointHeadingAngle) {
        this.globalWaypointHeadingAngle = globalWaypointHeadingAngle;
    }

    public String getFinishAction() {
        return finishAction;
    }

    public void setFinishAction(String finishAction) {
        this.finishAction = finishAction;
    }

    public TakeoffPoint getTakeoffPoint() {
        return takeoffPoint;
    }

    public void setTakeoffPoint(TakeoffPoint takeoffPoint) {
        this.takeoffPoint = takeoffPoint;
    }

    public boolean getGlobalUseStraightLine() {
        return this.globalUseStraightLine;
    }
    public void setDefaultPayloadForDrone() {
        if (device == null) return;

        PayloadInfo payload = new PayloadInfo();
        switch (device.getType()) {
            case 60: // M300 RTK
            case 89: // M350 RTK
                payload.setPayloadEnumValue(PayloadInfo.PayloadType.H20T.getValue());
                break;
            case 67: // M30/M30T
                payload.setPayloadEnumValue(device.getSubType() == 0 ?
                        PayloadInfo.PayloadType.M30_CAMERA.getValue() :
                        PayloadInfo.PayloadType.M30T_CAMERA.getValue());
                break;
            case 77: // M3E/M3T/M3M
                payload.setPayloadEnumValue(
                        device.getSubType() == 0 ? PayloadInfo.PayloadType.M3E_CAMERA.getValue() :
                                device.getSubType() == 1 ? PayloadInfo.PayloadType.M3T_CAMERA.getValue() :
                                        PayloadInfo.PayloadType.M3M_CAMERA.getValue());
                break;
            case 91: // M3D/M3TD
                payload.setPayloadEnumValue(
                        device.getSubType() == 0 ? PayloadInfo.PayloadType.M3D_CAMERA.getValue() :
                                PayloadInfo.PayloadType.M3TD_CAMERA.getValue());
                break;
            case 99: // M4E/M4T
            case 100: // M4D/M4TD
                payload.setPayloadEnumValue(
                        device.getSubType() == 0 ? PayloadInfo.PayloadType.M4E_CAMERA.getValue() :
                                PayloadInfo.PayloadType.M4T_CAMERA.getValue());
                break;
            default:
                throw new IllegalArgumentException("不支持的无人机类型");
        }
        this.payload = payload;
    }
}

