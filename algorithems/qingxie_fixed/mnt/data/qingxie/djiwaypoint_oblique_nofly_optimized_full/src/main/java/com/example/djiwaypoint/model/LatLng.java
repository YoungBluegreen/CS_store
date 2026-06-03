package com.example.djiwaypoint.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor // <--- 生成全参构造函数 new LatLng(lon, lat)
@NoArgsConstructor  // <--- 生成无参构造函数 new LatLng()，防止 JSON 反序列化报错
public class LatLng {
    private double longitude; // 经度
    private double latitude;  // 纬度
}