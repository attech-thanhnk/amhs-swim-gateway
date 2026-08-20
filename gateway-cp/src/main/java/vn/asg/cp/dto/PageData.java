package vn.asg.cp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Standardized Pagination Data Structure for gateway-cp.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageData<T> {

    private List<T> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;

    public static <T> PageData<T> from(Page<T> springPage) {
        return PageData.<T>builder()
                .items(springPage.getContent())
                .page(springPage.getNumber())
                .size(springPage.getSize())
                .totalItems(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .build();
    }

    public static <T> PageData<T> of(List<T> items, int page, int size, long totalItems, int totalPages) {
        return PageData.<T>builder()
                .items(items)
                .page(page)
                .size(size)
                .totalItems(totalItems)
                .totalPages(totalPages)
                .build();
    }
}
