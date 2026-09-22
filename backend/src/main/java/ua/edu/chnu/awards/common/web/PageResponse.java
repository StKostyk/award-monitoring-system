package ua.edu.chnu.awards.common.web;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * One page of results in the shape the API contract describes.
 *
 * @param content       the rows
 * @param totalElements rows over all pages
 * @param totalPages    number of pages
 * @param size          page size
 * @param number        page number, 0-based
 * @param first         whether this is the first page
 * @param last          whether this is the last page
 * @param <T>           row type
 */
public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int size, int number,
                              boolean first, boolean last) {

    /**
     * Wraps a Spring Data page.
     *
     * @param page the page
     * @param <T>  row type
     * @return the response
     */
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getTotalPages(),
            page.getSize(), page.getNumber(), page.isFirst(), page.isLast());
    }
}
