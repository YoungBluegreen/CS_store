package com.example.djiwaypoint.model;

import lombok.Data;

@Data
public class DroneInfo {
    private int domain = 0; // 固定为0表示飞行器
    private int type;       // 主类型
    private int subType;    // 子类型

    // 支持的无人机类型枚举
    public enum DroneType {
        M300_RTK(60, 0),
        M350_RTK(89, 0),
        M30(67, 0),
        M30T(67, 1),
        M3E(77, 0),
        M3T(77, 1),
        M3M(77, 2),
        M3D(91, 0),
        M3TD(91, 1),
        M4E(99, 0),
        M4T(99, 1),
        M4D(100, 0),
        M4TD(100, 1);

        private final int type;
        private final int subType;

        DroneType(int type, int subType) {
            this.type = type;
            this.subType = subType;
        }

        public int getType() {
            return type;
        }

        public int getSubType() {
            return subType;
        }
    }
}
