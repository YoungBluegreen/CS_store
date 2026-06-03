package com.example.djiwaypoint.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class WpmlActionGroup {
    private int actionGroupId;
    private int startIndex;
    private int endIndex;

    /** sequence */
    private String actionGroupMode = "sequence";

    /** multipleDistance / reachPoint */
    private String triggerType;

    /** multipleDistance 时为定距（米），reachPoint 时可为 null */
    private Double triggerParam;

    private List<WpmlAction> actions = new ArrayList<>();
}
