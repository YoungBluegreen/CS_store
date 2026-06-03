package com.example.djiwaypoint.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class Waypoint {
    private int index;
    private double longitude;
    private double latitude;

    // 高度信息（官方规范要求两种高度）
    private double ellipsoidHeight; // WGS84椭球高度
    private double height;          // EGM96海拔高度/相对高度

    private double speed = 10;

    // 航向参数
    private String headingMode = "followWayline"; // 航向模式
    private double headingAngle = 0;              // 航向角度

    // 转弯参数
    private String turnMode = "toPointAndStopWithDiscontinuityCurvature"; // 转弯模式

    // 全局设置标志
    private boolean useGlobalHeight = true;       // 是否使用全局高度
    private boolean useGlobalSpeed = true;         // 是否使用全局速度
    private boolean useGlobalHeading = true;        // 是否使用全局航向
    private boolean useGlobalTurnParam = true;      // 是否使用全局转弯参数

    // 云台俯仰角
    private double gimbalPitchAngle = -90;        // 默认俯视角度

    // 航点动作
    private List<Action> actions = new ArrayList<>();

    // 构造函数
    public Waypoint() {}

    // Getter 和 Setter 方法
    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getEllipsoidHeight() {
        return ellipsoidHeight;
    }

    public void setEllipsoidHeight(double ellipsoidHeight) {
        this.ellipsoidHeight = ellipsoidHeight;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public String getHeadingMode() {
        return headingMode;
    }

    public void setHeadingMode(String headingMode) {
        this.headingMode = headingMode;
    }

    public double getHeadingAngle() {
        return headingAngle;
    }

    public void setHeadingAngle(double headingAngle) {
        this.headingAngle = headingAngle;
    }

    public String getTurnMode() {
        return turnMode;
    }

    public void setTurnMode(String turnMode) {
        this.turnMode = turnMode;
    }

    public boolean isUseGlobalHeight() {
        return useGlobalHeight;
    }

    public void setUseGlobalHeight(boolean useGlobalHeight) {
        this.useGlobalHeight = useGlobalHeight;
    }

    public boolean isUseGlobalSpeed() {
        return useGlobalSpeed;
    }

    public void setUseGlobalSpeed(boolean useGlobalSpeed) {
        this.useGlobalSpeed = useGlobalSpeed;
    }

    public boolean isUseGlobalHeading() {
        return useGlobalHeading;
    }

    public void setUseGlobalHeading(boolean useGlobalHeading) {
        this.useGlobalHeading = useGlobalHeading;
    }

    public boolean isUseGlobalTurnParam() {
        return useGlobalTurnParam;
    }

    public void setUseGlobalTurnParam(boolean useGlobalTurnParam) {
        this.useGlobalTurnParam = useGlobalTurnParam;
    }

    public double getGimbalPitchAngle() {
        return gimbalPitchAngle;
    }

    public void setGimbalPitchAngle(double gimbalPitchAngle) {
        this.gimbalPitchAngle = gimbalPitchAngle;
    }

    public List<Action> getActions() {
        return actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }

    // 添加动作的便捷方法
    public void addAction(Action action) {
        if (this.actions == null) {
            this.actions = new ArrayList<>();
        }
        this.actions.add(action);
    }

    // 移除动作的便捷方法
    public void removeAction(int index) {
        if (this.actions != null && index >= 0 && index < this.actions.size()) {
            this.actions.remove(index);
        }
    }
}
