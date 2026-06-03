package com.example.djiwaypoint.model;

import lombok.Data;

@Data
public class WpmlAction {
    /** 允许像遥控器那样在每个 group 内从 0/1 开始 */
    private int actionId;

    /** 例如：gimbalRotate / startContinuousShooting / stopContinuousShooting */
    private String actionActuatorFunc;

    /** <wpml:actionActuatorFuncParam> 的内部 XML（不含外层标签） */
    private String actuatorFuncParamInnerXml;
}
