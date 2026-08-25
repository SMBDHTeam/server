package com.server.admin.dto;

/** 추이를 볼 수 있는 지표. 임의 테이블을 받지 않도록 값으로 제한한다. */
public enum StatsMetric {
    USERS("users"),
    POSTS("posts"),
    SCHEDULES("schedules");

    private final String table;

    StatsMetric(String table) {
        this.table = table;
    }

    public String table() {
        return table;
    }
}
