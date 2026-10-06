package com.school.schoolconfig.domain;

import java.util.List;

/**
 * The academic overview shown to visitors: which board the school follows and how its stages are
 * organised. Optional, and {@code levels} may be absent or empty.
 *
 * <p>Not to be confused with {@link AcademicSettings}, which is internal and never public: this is
 * marketing copy, that is how the school actually runs.
 *
 * @param board   the affiliating board or council, as the school words it
 * @param summary a paragraph about the academic programme
 * @param levels  the stages of schooling, in the order they should be displayed
 */
public record Academics(String board, String summary, List<AcademicLevel> levels) {
}
