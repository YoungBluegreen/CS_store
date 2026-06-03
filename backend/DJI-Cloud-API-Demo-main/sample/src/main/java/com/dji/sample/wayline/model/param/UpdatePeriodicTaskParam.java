package com.dji.sample.wayline.model.param;

import lombok.Data;

@Data
public class UpdatePeriodicTaskParam {
    private Integer periodValue;
    private String periodUnit;
    private Integer maxExecutions;
}
