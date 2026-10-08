package com.school.ai.app;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.school.ai.api.AiChatRequest;
import com.school.ai.api.AiChatResponse;
import com.school.ai.infra.AiGateway;
import com.school.common.config.AppProperties;
import com.school.notice.app.NoticeService;
import com.school.notice.app.PublicNoticeBrief;
import com.school.schoolconfig.app.SchoolConfigService;
import com.school.schoolconfig.domain.AcademicLevel;
import com.school.schoolconfig.domain.ContactDetails;
import com.school.schoolconfig.domain.Highlight;
import com.school.schoolconfig.domain.Identity;
import com.school.schoolconfig.domain.Landing;
import com.school.schoolconfig.domain.SchoolConfig;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

/**
 * The landing page's FAQ bot.
 *
 * <p><strong>The context is the school's own published words and nothing else:</strong> the public
 * projection of {@code school_config} — the same fields {@code GET /public/school} serves — plus the
 * current public notices. Not the receipt prefix, not the grading scheme, not a fee structure, not a
 * single student. A visitor who finds a prompt injection in here finds the school's own website.
 *
 * <p><strong>It is told to refuse rather than to guess.</strong> Fees and dates are the two things a
 * parent asks about and the two things a model will happily invent, so the instruction is explicit:
 * if the figure is not in the context, say to contact the office. A wrong fee quoted by the school's
 * own website is a complaint at the gate; "please contact the school office" is a phone call.
 */
@Service
public class PublicChatService {

	/** How many notices go into the prompt. Enough for "what is happening this month". */
	private static final int NOTICES = 8;

	private static final String INSTRUCTIONS = """

			You are the assistant on this school's own website, talking to a visitor — usually a \
			parent, sometimes a prospective student.

			Rules:
			- Answer only questions about this school: admissions, fees, timings, facilities, \
			academics, contact details, and what the notices above say. For anything else — homework \
			help, general knowledge, another school, your own workings — say politely that you can \
			only help with questions about the school.
			- Be brief. Two or three sentences, and fewer if that will do.
			- Use only the information above. If the answer is not there, say "please contact the \
			school office" and give the phone number or email from the contact details if you have one.
			- Never invent a fee, an amount, a date, a deadline, a result or a name. If a visitor asks \
			what the fees are and no fee is listed above, say that fees depend on the class and ask \
			them to contact the office.
			- Do not promise admission, a seat, a discount or an exception. Those are the school's to \
			give, not yours.
			- Never discuss any individual student, and never ask the visitor for personal details, \
			payment information or passwords.
			- Plain text, no markdown. Reply in the language the visitor used.
			""";

	private final AiFeature feature;
	private final AiGateway ai;
	private final SchoolConfigService schoolConfig;
	private final NoticeService notices;
	private final DateTimeFormatter noticeDate;

	public PublicChatService(AiFeature feature, AiGateway ai, SchoolConfigService schoolConfig, NoticeService notices,
			AppProperties properties) {
		this.feature = feature;
		this.ai = ai;
		this.schoolConfig = schoolConfig;
		this.notices = notices;
		this.noticeDate = DateTimeFormatter.ofPattern("d MMMM yyyy").withZone(ZoneId.of(properties.timezone()));
	}

	public AiChatResponse reply(AiChatRequest request) {
		feature.require();
		String reply = ai.text("answer that question", systemPrompt(), history(request), request.message());
		return new AiChatResponse(reply.trim());
	}

	/**
	 * The history as model messages.
	 *
	 * <p>Built as {@link Message} objects rather than as text so the provider sees who said what, and
	 * so a visitor cannot smuggle a new instruction in: a turn is content, and the only instructions
	 * in the conversation are the ones in the system prompt.
	 */
	private static List<Message> history(AiChatRequest request) {
		if (request.history() == null) {
			return List.of();
		}
		List<Message> messages = new ArrayList<>();
		for (AiChatRequest.Turn turn : request.history()) {
			messages.add(turn.role() == AiChatRequest.Turn.Role.ASSISTANT
					? new AssistantMessage(turn.content())
					: new UserMessage(turn.content()));
		}
		return messages;
	}

	// --- the context ------------------------------------------------------------------------------

	private String systemPrompt() {
		SchoolConfig config = schoolConfig.get();
		StringBuilder prompt = new StringBuilder();
		appendSchool(prompt, config);
		appendNotices(prompt, notices.latestPublic(NOTICES));
		return prompt.append(INSTRUCTIONS).toString();
	}

	private static void appendSchool(StringBuilder prompt, SchoolConfig config) {
		Identity identity = config.getIdentity();
		Landing landing = config.getLanding();
		prompt.append("About the school\n");
		if (identity != null) {
			line(prompt, "Name", identity.name());
			line(prompt, "Tagline", identity.tagline());
		}
		if (landing == null) {
			return;
		}
		line(prompt, "About", landing.about());
		line(prompt, "Vision", landing.vision());
		if (landing.principal() != null) {
			line(prompt, "Principal", landing.principal().name());
			line(prompt, "Principal's designation", landing.principal().designation());
			line(prompt, "Principal's message", landing.principal().message());
		}
		if (landing.academics() != null) {
			line(prompt, "Board", landing.academics().board());
			line(prompt, "Academics", landing.academics().summary());
			if (landing.academics().levels() != null) {
				for (AcademicLevel level : landing.academics().levels()) {
					line(prompt, "Level", join(level.name(), level.range(), level.description()));
				}
			}
		}
		if (landing.highlights() != null) {
			for (Highlight highlight : landing.highlights()) {
				line(prompt, "Highlight", join(highlight.title(), highlight.description()));
			}
		}
		if (landing.facilities() != null && !landing.facilities().isEmpty()) {
			line(prompt, "Facilities", String.join(", ", landing.facilities()));
		}
		if (landing.stats() != null) {
			line(prompt, "Students on the roll", String.valueOf(landing.stats().students()));
			line(prompt, "Teachers", String.valueOf(landing.stats().teachers()));
			line(prompt, "Years since founding", String.valueOf(landing.stats().years()));
		}
		appendContact(prompt, landing.contact());
	}

	private static void appendContact(StringBuilder prompt, ContactDetails contact) {
		if (contact == null) {
			return;
		}
		prompt.append("\nContact details\n");
		line(prompt, "Address", join(contact.addressLine1(), contact.addressLine2(), contact.city(),
				contact.state(), contact.postalCode()));
		line(prompt, "Phone", contact.phone());
		line(prompt, "Alternate phone", contact.alternatePhone());
		line(prompt, "Email", contact.email());
		line(prompt, "Website", contact.websiteUrl());
	}

	private void appendNotices(StringBuilder prompt, List<PublicNoticeBrief> briefs) {
		if (briefs.isEmpty()) {
			prompt.append("\nThere are no current public notices.\n");
			return;
		}
		prompt.append("\nCurrent public notices\n");
		for (PublicNoticeBrief brief : briefs) {
			prompt.append("- ");
			if (brief.publishedAt() != null) {
				prompt.append('(').append(noticeDate.format(brief.publishedAt())).append(") ");
			}
			prompt.append(brief.title());
			if (brief.body() != null && !brief.body().isBlank()) {
				prompt.append(": ").append(brief.body().strip());
			}
			prompt.append('\n');
		}
	}

	/** Skips the line entirely when the school has not filled that field in. */
	private static void line(StringBuilder prompt, String label, String value) {
		if (value != null && !value.isBlank()) {
			prompt.append(label).append(": ").append(value.strip()).append('\n');
		}
	}

	private static String join(String... parts) {
		List<String> present = new ArrayList<>();
		for (String part : parts) {
			if (part != null && !part.isBlank()) {
				present.add(part.strip());
			}
		}
		return present.isEmpty() ? null : String.join(", ", present);
	}
}
