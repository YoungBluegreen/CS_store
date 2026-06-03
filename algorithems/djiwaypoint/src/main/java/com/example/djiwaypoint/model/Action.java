package com.example.djiwaypoint.model;

import lombok.Data;

@Data
public class Action {
    private String type; // gimbalRotate, takePhoto, etc.
    private double pitchAngle;
    private double yawAngle;

    // 构造函数
    public Action() {}

    // Getter 和 Setter 方法
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getPitchAngle() {
        return pitchAngle;
    }

    public void setPitchAngle(double pitchAngle) {
        this.pitchAngle = pitchAngle;
    }

    public double getYawAngle() {
        return yawAngle;
    }

    public void setYawAngle(double yawAngle) {
        this.yawAngle = yawAngle;
    }
}
