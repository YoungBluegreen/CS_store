package com.example.djiwaypoint.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FlightPlan {

    /**
     * 你项目里有地方会引用 FlightPlan.TakeoffPoint（CoverageRequest 用到了）
     * 所以必须保留这个内部类以兼容旧代码。
     */
    @Data
    public static class TakeoffPoint {
        @JsonAlias({"lng", "lon", "longitude"})
        private double longitude;

        @JsonAlias({"lat", "latitude"})
        private double latitude;

        @JsonAlias({"alt", "height", "executeHeight", "z"})
        private double height;
    }

    // =========================
    // 输入/规划相关
    // =========================

    /** 最终要导出的航线点序列（KMZGenerator / OrthoMissionBuilder 使用） */
    private List<Waypoint> waypoints = new ArrayList<>();

    /** 任务区域边界（有些流程会把它叫 missionBoundary / coverageArea） */
    @JsonAlias({"missionBoundary", "coverageArea", "flightArea"})
    private List<Waypoint> flightArea = new ArrayList<>();

    /** 禁飞区集合：多个多边形 */
    private List<List<Waypoint>> noFlyAreas = new ArrayList<>();

    /** 起飞点（兼容 CoverageRequest） */
    private TakeoffPoint takeoffPoint;

    /** 无人机信息（必填） */
    private DroneInfo device;

    /** 载荷信息（可为空，KMZGenerator 会触发 setDefaultPayloadForDrone() 自动补） */
    private PayloadInfo payload;

    // =========================
    // KMZ / WPML 全局参数
    // =========================

    /** 模板类型：waypoint / mapping2d 等 */
    private String templateType = "waypoint";

    /** 自动飞行速度（m/s） */
    private double autoFlightSpeed = 15.0;

    /** 全局拍摄高度（m，相对起飞点） */
    private double globalShootHeight = 80.0;

    /** 定距拍照间隔（m） */
    private double photoIntervalMeter = 40.0;

    /** 任务类型：ortho=正射，oblique=手工倾斜，smartOblique=智能倾斜 */
    private String missionType = "ortho";

    /** 倾斜摄影云台俯仰角（度） */
    private double obliqueGimbalPitch = -45.0;

    /** 倾斜摄影正交方向补充航线 */
    private List<Waypoint> obliqueCrossWaypoints = new ArrayList<>();

    /** 倾斜任务主骨架与正交骨架之间的安全转场航线 */
    private List<Waypoint> obliqueTransferWaypoints = new ArrayList<>();

    /** 返航高度（m，可选） */
    private Double globalRTHHeight;

    /** 是否全局直线飞行 */
    private boolean globalUseStraightLine = true;

    /** 全局航向模式：followWayline / fixedHeading 等 */
    private String globalWaypointHeadingMode = "followWayline";

    /** 全局航向角（当 headingMode != followWayline 时才用） */
    private double globalWaypointHeadingAngle = 0.0;

    /** 全局转弯模式 */
    private String globalWaypointTurnMode = "coordinateTurn";

    /** 结束动作 */
    private String finishAction = "noAction";

    /** 失联策略 / 飞向航线策略 */
    private String exitOnRCLost = "goContinue";
    private String flyToWaylineMode = "safely";
    private double takeOffSecurityHeight = 20.0;

    // =========================
    // 运行期辅助字段
    // =========================

    private int nextActionGroupId = 0;
    private int nextActionId = 0;

    public int allocateActionGroupId() {
        return nextActionGroupId++;
    }

    public int allocateActionId() {
        return nextActionId++;
    }

    public void resetActionCounters() {
        this.nextActionGroupId = 0;
        this.nextActionId = 0;
    }

    public void reindexWaypoints() {
        if (this.waypoints == null) {
            return;
        }
        for (int i = 0; i < this.waypoints.size(); i++) {
            this.waypoints.get(i).setIndex(i);
        }
    }

    /**
     * 按无人机机型自动填充默认 payload
     * 必须补齐：payloadEnumValue / payloadSubEnumValue / payloadPositionIndex
     */
    public void setDefaultPayloadForDrone() {
        if (this.device == null) return;

        PayloadInfo p = new PayloadInfo();
        p.setPayloadSubEnumValue(0);
        p.setPayloadPositionIndex(0);

        int droneType = device.getType();

        switch (droneType) {
            case 77:
                // Mavic 3M
                p.setPayloadEnumValue(68);
                break;
            default:
                p.setPayloadEnumValue(0);
                break;
        }

        this.payload = p;
    }
}