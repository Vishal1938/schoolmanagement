package com.school.accounts.api;

import com.school.accounts.domain.EntryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /accounts/categories} and {@code PUT /accounts/categories/{id}}.
 *
 * <p>{@code type} is read on a create and <strong>ignored on an update</strong>: a year of entries
 * is already filed on one side of the books, and flipping it would move all of them at once. Rename
 * the category or retire it and add a new one.
 *
 * @param active defaults to true on a create, and to whatever it was on an update
 */
public record AccountCategoryRequest(
		@NotBlank @Size(max = 80) String name,
		@NotNull EntryType type,
		Boolean active) {
}
