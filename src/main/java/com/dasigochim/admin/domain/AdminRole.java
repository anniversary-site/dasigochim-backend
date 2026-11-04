package com.dasigochim.admin.domain;

/** 관리자 역할. 담당 범위는 admin_scopes 에 둔다. */
public enum AdminRole {
    /** 전국 전체 */
    SUPER("전체 관리자"),
    /** 시·도 또는 시·군·구 */
    REGION("지역 담당자"),
    /** 특정 청년주택 */
    BUILDING("단지 관리인");

    private final String label;

    AdminRole(String label) { this.label = label; }

    public String label() { return label; }
}
