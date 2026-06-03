package com.example.djiwaypoint.model;

import lombok.Data;

@Data
public class Action {
    /**
     * 动作 ID (例如 0, 1, 2...)
     */
    private int actionId;

    /**
     * 动作类型功能码 (DJI 定义)
     * 1 = GimbalRotate (云台转动)
     * 2 = TakePhoto (拍照)
     */
    private int actionActuatorFunc;

    /**
     * 动作参数
     * 对于 GimbalRotate，这里通常填角度 (例如 -90)
     */
    private double actionActuatorParam;

    // --- 兼容旧字段（可保留） ---
    private String type;
    private double pitchAngle;
    private double yawAngle;
}