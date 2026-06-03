package com.example.djiwaypoint.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Waypoint {

    public enum SegmentType {
        ORTHO,
        OBLIQUE_PASS,
        TRANSITION
    }

    public enum ActionType {
        GIMBAL_ROTATE,
        TAKE_PHOTO,
        HOVER
    }

    /**
     * 新动作模型：
     * 由 SmartObliqueMissionBuilder 构造，
     * 由 KMZGenerator 直接转成 wpml:action。
     */
    @Data
    public static class ActionSpec {
        private ActionType type;

        /**
         * GIMBAL_ROTATE 用
         */
        private Double gimbalPitchAngle;
        private Double gimbalYawAngle;
        private Integer payloadPositionIndex;

        /**
         * TAKE_PHOTO 用
         */
        private String payloadLensIndex = "visible";
        private Boolean useGlobalPayloadLensIndex = false;

        /**
         * HOVER 用
         */
        private Double hoverTimeSeconds;

        public static ActionSpec gimbalRotate(double pitch, double yaw, Integer payloadPositionIndex) {
            ActionSpec a = new ActionSpec();
            a.setType(ActionType.GIMBAL_ROTATE);
            a.setGimbalPitchAngle(pitch);
            a.setGimbalYawAngle(yaw);
            a.setPayloadPositionIndex(payloadPositionIndex);
            return a;
        }

        public static ActionSpec takePhoto(Integer payloadPositionIndex) {
            ActionSpec a = new ActionSpec();
            a.setType(ActionType.TAKE_PHOTO);
            a.setPayloadPositionIndex(payloadPositionIndex);
            a.setPayloadLensIndex("visible");
            a.setUseGlobalPayloadLensIndex(false);
            return a;
        }

        public static ActionSpec hover(double seconds) {
            ActionSpec a = new ActionSpec();
            a.setType(ActionType.HOVER);
            a.setHoverTimeSeconds(seconds);
            return a;
        }
    }

    /** 航点索引（可选，导出时通常由列表位置决定） */
    @JsonAlias({"idx", "i"})
    private int index;

    /** 经纬度 */
    @JsonAlias({"lng", "lon", "longitude"})
    private double longitude;

    @JsonAlias({"lat", "latitude"})
    private double latitude;

    /** 高度（m，相对起飞点/任务基准高度） */
    @JsonAlias({"alt", "height", "executeHeight", "z"})
    private double height;

    /**
     * 椭球高（m）
     * 用于保存 WGS84 椭球面相关高度（或投影/测高中间值），不一定会写入 WPML。
     */
    private double ellipsoidHeight;

    /** 航点速度（m/s） */
    @JsonAlias({"v", "speed", "waypointSpeed"})
    private double speed;

    // ===== 航点局部参数开关 =====

    /** true=使用 FlightPlan.globalShootHeight；false=用本航点 height */
    private boolean useGlobalHeight = true;

    /** true=使用 FlightPlan.autoFlightSpeed；false=用本航点 speed */
    private boolean useGlobalSpeed = true;

    /** true=使用 FlightPlan 的全局航向；false=用本航点 headingMode/headingAngle */
    private boolean useGlobalHeading = true;

    /** true=使用 FlightPlan 的全局转弯；false=用本航点 turnMode */
    private boolean useGlobalTurnParam = true;

    // ===== 航点局部航向/转弯参数 =====

    /**
     * 航向模式：followWayline / fixedHeading 等
     * - followWayline 时 headingAngle 可忽略
     */
    private String headingMode = "followWayline";

    /**
     * 航向角（度）
     * - 当 headingMode != followWayline 时使用
     */
    private double headingAngle = 0.0;

    /** 转弯模式：toPointAndStopWithDiscontinuityCurvature / coordinateTurn 等 */
    private String turnMode = "toPointAndStopWithDiscontinuityCurvature";

    /**
     * 云台俯仰角（度）
     * - 正射一般 -90
     * - 倾斜一般 -45
     */
    private double gimbalPitchAngle = -90.0;

    /**
     * 云台偏航角（度）
     */
    private double gimbalYawAngle = 0.0;

    /** 是否直线飞行，默认 true */
    private boolean useStraightLine = true;

    // ===== Smart Oblique / 分段元信息 =====

    /**
     * 默认值维持 TRANSITION，避免未显式标注的点被误认成拍摄段。
     */
    private SegmentType segmentType = SegmentType.TRANSITION;

    /** 是否某个拍摄段的首点 */
    private boolean segmentStart = false;

    /** 是否某个拍摄段的尾点 */
    private boolean segmentEnd = false;

    /** 该点是否允许定距拍照 */
    private boolean enableDistanceShooting = false;

    /**
     * 该点是否是拍照控制点
     * - 对 reachPoint 触发仍有意义
     * - 对 multipleDistance 场景主要是辅助语义
     */
    private boolean photoPoint = false;

    /**
     * 段内定距触发间隔（米）
     * 只有当 enableDistanceShooting=true 且该点为拍摄段起点时有效
     */
    private Double distanceShootingIntervalMeters;

    /**
     * 该航点绑定的动作序列
     * - 对 reachPoint：在该点执行
     * - 对 multipleDistance：通常挂在“段起点”上，由 KMZGenerator 生成 actionGroup(startIndex~endIndex)
     */
    private List<ActionSpec> actionSpecs = new ArrayList<>();

    // ===== 旧动作模型（保留兼容，不参与新 WPML 导出） =====
    @JsonIgnore
    private List<Action> actions = new ArrayList<>();

    public void addAction(Action action) {
        if (this.actions == null) this.actions = new ArrayList<>();
        this.actions.add(action);
    }

    public void removeAction(int idx) {
        if (this.actions != null && idx >= 0 && idx < this.actions.size()) {
            this.actions.remove(idx);
        }
    }

    // ===== 新动作模型辅助方法 =====

    public void addActionSpec(ActionSpec actionSpec) {
        if (this.actionSpecs == null) {
            this.actionSpecs = new ArrayList<>();
        }
        this.actionSpecs.add(actionSpec);
    }

    public void clearActionSpecs() {
        if (this.actionSpecs == null) {
            this.actionSpecs = new ArrayList<>();
        } else {
            this.actionSpecs.clear();
        }
    }

    public boolean hasActionSpecs() {
        return this.actionSpecs != null && !this.actionSpecs.isEmpty();
    }

    public Waypoint shallowCopy() {
        Waypoint w = new Waypoint();
        w.setIndex(this.index);
        w.setLongitude(this.longitude);
        w.setLatitude(this.latitude);
        w.setHeight(this.height);
        w.setEllipsoidHeight(this.ellipsoidHeight);
        w.setSpeed(this.speed);

        w.setUseGlobalHeight(this.useGlobalHeight);
        w.setUseGlobalSpeed(this.useGlobalSpeed);
        w.setUseGlobalHeading(this.useGlobalHeading);
        w.setUseGlobalTurnParam(this.useGlobalTurnParam);

        w.setHeadingMode(this.headingMode);
        w.setHeadingAngle(this.headingAngle);
        w.setTurnMode(this.turnMode);

        w.setGimbalPitchAngle(this.gimbalPitchAngle);
        w.setGimbalYawAngle(this.gimbalYawAngle);
        w.setUseStraightLine(this.useStraightLine);

        w.setSegmentType(this.segmentType);
        w.setSegmentStart(this.segmentStart);
        w.setSegmentEnd(this.segmentEnd);
        w.setEnableDistanceShooting(this.enableDistanceShooting);
        w.setPhotoPoint(this.photoPoint);
        w.setDistanceShootingIntervalMeters(this.distanceShootingIntervalMeters);

        if (this.actionSpecs != null) {
            List<ActionSpec> copied = new ArrayList<>();
            for (ActionSpec src : this.actionSpecs) {
                if (src == null) continue;
                ActionSpec dst = new ActionSpec();
                dst.setType(src.getType());
                dst.setGimbalPitchAngle(src.getGimbalPitchAngle());
                dst.setGimbalYawAngle(src.getGimbalYawAngle());
                dst.setPayloadPositionIndex(src.getPayloadPositionIndex());
                dst.setPayloadLensIndex(src.getPayloadLensIndex());
                dst.setUseGlobalPayloadLensIndex(src.getUseGlobalPayloadLensIndex());
                dst.setHoverTimeSeconds(src.getHoverTimeSeconds());
                copied.add(dst);
            }
            w.setActionSpecs(copied);
        } else {
            w.setActionSpecs(new ArrayList<>());
        }

        return w;
    }
}