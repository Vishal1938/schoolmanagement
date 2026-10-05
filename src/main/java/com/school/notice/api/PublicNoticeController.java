package com.school.notice.api;

import com.school.common.pagination.PageResponse;
import com.school.notice.app.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The notices on the landing page, for visitors who are not logged in.
 *
 * <p>No {@code @PreAuthorize}: everything under {@code /public/**} is open in
 * {@link com.school.common.security.SecurityConfig}. What keeps this safe is the projection rather
 * than a permission — {@link PublicNoticeResponse} carries no attachments and no author — and the
 * query, which returns only notices an admin explicitly flagged public.
 */
@RestController
@RequestMapping("/public/notices")
@Tag(name = "Public", description = "Endpoints an anonymous visitor may call")
public class PublicNoticeController {

	private final NoticeService notices;

	public PublicNoticeController(NoticeService notices) {
		this.notices = notices;
	}

	@GetMapping
	@Operation(summary = "Public notices",
			description = "Notices flagged public that are published and not expired, pinned first then "
					+ "newest. No attachments and no author information.")
	public PageResponse<PublicNoticeResponse> list(Pageable pageable) {
		return PageResponse.of(notices.publicFeed(pageable), PublicNoticeResponse::of);
	}
}
