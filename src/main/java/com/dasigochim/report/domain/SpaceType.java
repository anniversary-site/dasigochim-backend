package com.dasigochim.report.domain;

/** 신고 위치가 세대 안인지 공용공간인지. 세대 내부 신고는 다른 입주민에게 동·호수를 가린다. */
public enum SpaceType {
    UNIT, COMMON
}
