package com.dasigochim.common.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int page, int size) {
    public static <T> PageResponse<T> from(Page<T> source) {
        return new PageResponse<>(source.getContent(), source.getTotalElements(), source.getTotalPages(), source.getNumber(), source.getSize());
    }
}
