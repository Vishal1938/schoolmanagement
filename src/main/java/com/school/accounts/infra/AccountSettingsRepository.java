package com.school.accounts.infra;

import com.school.accounts.domain.AccountSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for {@code account_settings}, which holds exactly one document. Internal to this
 * module; callers use {@code AccountSettingsService}.
 */
public interface AccountSettingsRepository extends MongoRepository<AccountSettings, String> {
}
