package com.school.notice.infra;

import com.school.notice.domain.Notice;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for {@code notices}. Internal to this module; callers use {@code NoticeService}.
 *
 * <p>There are no finders here. Both feeds are built from criteria that depend on who is asking —
 * the publication window ANDed with an audience clause per role — so {@code NoticeService} assembles
 * them with {@code MongoOperations} instead of this interface growing a derived query per role.
 */
public interface NoticeRepository extends MongoRepository<Notice, String> {
}
