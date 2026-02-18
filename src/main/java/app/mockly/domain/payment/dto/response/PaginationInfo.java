package app.mockly.domain.payment.dto.response;

import org.springframework.data.domain.Page;

public record PaginationInfo(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean isFirst,
        boolean isLast
) {
    public static PaginationInfo from(Page<?> page) {
        return new PaginationInfo(
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
