package com.school.common.id;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

import com.school.common.config.AppProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Issues the human-readable IDs from API_CONTRACT.md §4 and the sequence numbers behind them.
 *
 * <p>Every number comes from a single {@code findAndModify} with {@code $inc} and {@code upsert}, so
 * two threads — or two application instances — can never be handed the same value. There is no
 * read-then-write anywhere in this class, and therefore no need for a transaction.
 *
 * <p>IDs are never reused: a counter only moves forward, and deleting a person does not give their ID
 * back. If IDs are ever inserted by hand, bump the counter past them or the next generated ID will
 * collide with one of them:
 * <pre>{@code
 * db.counters.updateOne({ _id: "EMP-26" }, { $max: { seq: 12 } }, { upsert: true })
 * }</pre>
 * A collision is not silent in any case — the {@code uniqueId} indexes are unique, so a duplicate
 * fails the write rather than creating two people with one ID.
 */
@Service
public class IdGenerator {

	private final MongoOperations mongo;
	private final AppProperties properties;
	private final Clock clock;

	public IdGenerator(MongoOperations mongo, AppProperties properties, Clock clock) {
		this.mongo = mongo;
		this.properties = properties;
		this.clock = clock;
	}

	/** Next ID of this type for the current year, e.g. {@code DEMO-STU-26-00001}. */
	public String next(IdType type) {
		return next(type, currentYear());
	}

	/**
	 * Next ID of this type for a specific year — a student admitted in a past session keeps that
	 * session's year in their ID.
	 */
	public String next(IdType type, int year) {
		String counterKey = counterKey(type.name(), year);
		long sequence = nextSequence(counterKey);
		return "%s-%s-%s-%s".formatted(
				properties.schoolCode(), type.name(), twoDigitYear(year), pad(sequence, type.sequenceWidth()));
	}

	/**
	 * Next number for a caller-supplied prefix, e.g. receipt numbers in B12, where the prefix comes
	 * from the school configuration rather than from {@link IdType}:
	 * {@code nextNumber("DVM-RCP", 6)} gives {@code DVM-RCP-26-000001}.
	 */
	public String nextNumber(String prefix, int sequenceWidth) {
		return nextNumber(prefix, sequenceWidth, currentYear());
	}

	public String nextNumber(String prefix, int sequenceWidth, int year) {
		String normalised = prefix.trim().toUpperCase(Locale.ROOT);
		long sequence = nextSequence(counterKey(normalised, year));
		return "%s-%s-%s".formatted(normalised, twoDigitYear(year), pad(sequence, sequenceWidth));
	}

	/**
	 * The primitive every other method is built on: atomically increments {@code counterKey} and
	 * returns the new value, creating the counter on first use.
	 *
	 * @throws DataIntegrityViolationException if the counter cannot be read back, which should be
	 *                                        impossible with {@code returnNew} and {@code upsert}
	 */
	public long nextSequence(String counterKey) {
		Counter counter = mongo.findAndModify(
				new Query(Criteria.where("_id").is(counterKey)),
				new Update().inc("seq", 1L),
				FindAndModifyOptions.options().upsert(true).returnNew(true),
				Counter.class);
		if (counter == null) {
			throw new DataIntegrityViolationException("Counter " + counterKey + " could not be incremented");
		}
		return counter.getSeq();
	}

	/** The counter document key, e.g. {@code STU-26}. One counter per type and year. */
	public static String counterKey(String prefix, int year) {
		return prefix + "-" + twoDigitYear(year);
	}

	private int currentYear() {
		return LocalDate.now(clock).getYear();
	}

	private static String twoDigitYear(int year) {
		return "%02d".formatted(year % 100);
	}

	private static String pad(long sequence, int width) {
		return "%0{width}d".replace("{width}", String.valueOf(width)).formatted(sequence);
	}
}
