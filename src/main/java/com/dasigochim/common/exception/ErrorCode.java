package com.dasigochim.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "인증 토큰이 올바르지 않습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "관리자를 찾을 수 없습니다."),
    BUILDING_NOT_FOUND(HttpStatus.NOT_FOUND, "청년주택을 찾을 수 없습니다."),
    UNIT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 동·호수의 세대를 찾을 수 없습니다."),
    SPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 공용공간을 찾을 수 없습니다."),
    DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "부서를 찾을 수 없습니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "신고를 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    INVALID_REPORT_OWNER(HttpStatus.FORBIDDEN, "본인의 신고만 변경할 수 있습니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않은 신고 상태 변경입니다."),
    REPORT_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 제출된 신고입니다."),
    PHOTO_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "사진 업로드 개수를 초과했습니다."),
    FILE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장에 실패했습니다."),
    AI_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "AI 분석 서버 호출에 실패했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
