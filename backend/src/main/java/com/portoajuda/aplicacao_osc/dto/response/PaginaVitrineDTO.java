package com.portoajuda.aplicacao_osc.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable public pagination contract for homepage showcases. */
public record PaginaVitrineDTO<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PaginaVitrineDTO<T> de(Page<T> page) {
        return new PaginaVitrineDTO<>(
                List.copyOf(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
