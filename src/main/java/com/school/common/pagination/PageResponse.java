package com.school.common.pagination;

import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The single paged-response shape returned by every list endpoint. Spring's {@code Page} is never
 * serialised directly, because its JSON shape is unstable across versions.
 *
 * <p>The field names are the ones in API_CONTRACT.md §1 — {@code items} and {@code totalItems}, not
 * Spring's {@code content} and {@code totalElements} — because the contract is what the frontend
 * generates its types from. {@code first}, {@code last} and {@code empty} are additive conveniences.
 *
 * @param items      the page's items, already projected to DTOs
 * @param page       zero-based page index
 * @param size       requested page size
 * @param totalItems total number of matching items
 * @param totalPages total number of pages
 * @param first      whether this is the first page
 * @param last       whether this is the last page
 * @param empty      whether this page has no items
 */
@Schema(name = "PageResponse", description = "Paged list response")
public record PageResponse<T>(
		List<T> items,
		int page,
		int size,
		long totalItems,
		int totalPages,
		boolean first,
		boolean last,
		boolean empty) {

	public static <T> PageResponse<T> of(Page<T> page) {
		return new PageResponse<>(
				page.getContent(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.isFirst(),
				page.isLast(),
				page.isEmpty());
	}

	/** Maps the page's entities to DTOs, so controllers never expose raw documents. */
	public static <S, T> PageResponse<T> of(Page<S> page, Function<? super S, ? extends T> mapper) {
		return of(page.map(mapper));
	}

	/** For aggregation results, where the items and the total count are fetched separately. */
	public static <T> PageResponse<T> of(List<T> items, Pageable pageable, long totalItems) {
		int size = pageable.isPaged() ? pageable.getPageSize() : Math.max(items.size(), 1);
		int pageNumber = pageable.isPaged() ? pageable.getPageNumber() : 0;
		int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalItems / (double) size);
		return new PageResponse<>(
				items,
				pageNumber,
				size,
				totalItems,
				totalPages,
				pageNumber == 0,
				pageNumber >= totalPages - 1,
				items.isEmpty());
	}
}
