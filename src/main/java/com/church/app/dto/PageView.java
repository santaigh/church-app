package com.church.app.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * One page of results, in the shape a template needs.
 *
 * <p>Spring's {@link Page} is zero-based and carries far more than a screen wants; this
 * exposes the handful of values the pagination control actually renders, page numbers
 * counted from one as people count them.
 *
 * @param rows        the rows on this page
 * @param page        the current page, counting from 1
 * @param totalPages  at least 1, so "page 1 of 1" reads properly on an empty list
 * @param totalRows   how many matched the search -- not how many exist
 * @param size        rows per page
 * @param firstRow    the position of the first row shown, counting from 1
 * @param lastRow     the position of the last row shown
 */
public record PageView<T>(List<T> rows,
                          int page,
                          int totalPages,
                          long totalRows,
                          int size,
                          long firstRow,
                          long lastRow) {

    /** Page sizes offered on screen. */
    public static final List<Integer> SIZES = List.of(25, 50, 100);

    public static final int DEFAULT_SIZE = 50;

    public static <S, T> PageView<T> of(Page<S> page, List<T> rows) {
        int number = page.getNumber() + 1;
        long first = page.getTotalElements() == 0 ? 0 : (long) page.getNumber() * page.getSize() + 1;
        return new PageView<>(
                rows,
                number,
                Math.max(page.getTotalPages(), 1),
                page.getTotalElements(),
                page.getSize(),
                first,
                first == 0 ? 0 : first + rows.size() - 1);
    }

    public boolean hasPrevious() {
        return page > 1;
    }

    public boolean hasNext() {
        return page < totalPages;
    }

    /**
     * The page numbers worth drawing: a window around the current one.
     *
     * <p>Forty-eight pages of members would otherwise produce forty-eight links.
     */
    public List<Integer> window() {
        int from = Math.max(1, page - 2);
        int to = Math.min(totalPages, from + 4);
        from = Math.max(1, to - 4);
        return java.util.stream.IntStream.rangeClosed(from, to).boxed().toList();
    }

    /** True when the caller is looking at everything there is. */
    public boolean isSinglePage() {
        return totalPages <= 1;
    }
}
