package com.example.djiwaypoint.model;

import lombok.Data;

@Data
public class PayloadInfo {
    private int payloadEnumValue;
    private int payloadPositionIndex = 0; // 默认主云台位置

    // 支持的负载类型枚举
    public enum PayloadType {
        // M300/M350系列负载
        H20(42), H20T(43), H20N(61),
        H30(82), H30T(83),

        // M30系列负载
        M30_CAMERA(52), M30T_CAMERA(53),

        // M3系列负载
        M3E_CAMERA(66), M3T_CAMERA(67), M3M_CAMERA(68),
        M3D_CAMERA(80), M3TD_CAMERA(81),

        // M4系列负载
        M4E_CAMERA(88), M4T_CAMERA(89),
        M4D_CAMERA(98), M4TD_CAMERA(99);

        private final int value;

        PayloadType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    // 获取负载类型名称
    public String getPayloadName() {
        for (PayloadType type : PayloadType.values()) {
            if (type.getValue() == this.payloadEnumValue) {
                return type.name().replace("_", " ");
            }
        }
        return "未知负载";
    }
}
