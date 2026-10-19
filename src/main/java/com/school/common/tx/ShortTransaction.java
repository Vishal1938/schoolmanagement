package com.school.common.tx;

import java.util.function.Supplier;

import com.mongodb.MongoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One unit of work in its own transaction, retried when MongoDB says the attempt was transient.
 *
 * <p>This exists because of a hard limit: a MongoDB transaction may not stay open for longer than
 * {@code transactionLifetimeLimitSeconds}, which is <strong>60 seconds</strong> on Atlas and not
 * configurable there. Past it the server aborts the transaction, and the next statement on it comes
 * back as {@code NoSuchTransaction} labelled {@code TransientTransactionError}. Any loop that writes
 * a few hundred documents inside a single {@code @Transactional} method is therefore a time bomb that
 * goes off the first time somebody uploads a real-sized spreadsheet. The fix is not a longer
 * transaction — there is no longer one to be had — but many short ones.
 *
 * <p>Short transactions in a loop give up all-or-nothing across the loop, so a caller must be able to
 * say what happened when row 180 of 300 fails. What they buy is that each unit either lands whole or
 * not at all, which is the guarantee that actually matters: a student without a login, or a login
 * with no student behind it, is what a transaction is here to prevent.
 *
 * <p><strong>Everything expensive belongs outside.</strong> BCrypt hashing at strength 12 costs a few
 * hundred milliseconds per password; doing it inside the transaction is what put the 60-second limit
 * within reach in the first place. Compute, then open the transaction, then write.
 *
 * <p>{@code REQUIRES_NEW} rather than {@code REQUIRED}: "its own transaction" is the whole promise of
 * this class, and a caller that happens to sit inside an outer one should not quietly re-join it and
 * reintroduce the single long transaction this is here to break up.
 */
@Component
public class ShortTransaction {

	/**
	 * The driver's own advice for a transaction that failed transiently is to run it again from the
	 * start. Three attempts covers an election or a write conflict; a fourth would only delay telling
	 * the caller that something is actually wrong.
	 */
	public static final int MAX_ATTEMPTS = 3;

	/** Multiplied by the attempt number, so the waits are 50ms then 100ms. */
	private static final long BACKOFF_MILLIS = 50;

	private static final Logger log = LoggerFactory.getLogger(ShortTransaction.class);

	private final TransactionTemplate transactions;

	public ShortTransaction(PlatformTransactionManager transactionManager) {
		this.transactions = new TransactionTemplate(transactionManager);
		this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * Runs {@code work} in a transaction, retrying the whole of it up to {@value #MAX_ATTEMPTS} times
	 * while MongoDB reports the failure as transient.
	 *
	 * <p>{@code work} has to be safe to run twice. That is not a free assumption — a unit that
	 * committed and then lost the acknowledgement is retried as well — so a unit whose second attempt
	 * would be wrong should check for its own effect first, the way admitting a student re-checks the
	 * admission number.
	 *
	 * @throws RuntimeException whatever the last attempt threw, once there is no retry left or the
	 *                          failure was never transient. Business failures — a conflict, a
	 *                          validation error — carry no transient label and so are never retried
	 */
	public <T> T execute(Supplier<T> work) {
		RuntimeException last = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				return transactions.execute(status -> work.get());
			}
			catch (RuntimeException ex) {
				if (!isTransient(ex)) {
					throw ex;
				}
				last = ex;
				log.warn("Transaction attempt {} of {} failed transiently ({}); retrying",
						attempt, MAX_ATTEMPTS, ex.getMessage());
				if (attempt < MAX_ATTEMPTS) {
					backOff(attempt);
				}
			}
		}
		throw last;
	}

	/** The same, for work that has nothing to hand back. */
	public void run(Runnable work) {
		execute(() -> {
			work.run();
			return null;
		});
	}

	/**
	 * Whether MongoDB labelled this failure as worth another attempt.
	 *
	 * <p>The labels are read off the driver exception rather than matched on message text, and the
	 * whole cause chain is walked because Spring wraps the driver's exception twice over: a
	 * {@code MongoCommandException} arrives as a {@code DataAccessException} on a statement and as a
	 * {@code TransactionSystemException} on a commit.
	 *
	 * <ul>
	 * <li>{@code TransientTransactionError} — the transaction never committed. Nothing was written, so
	 * running it again is plainly safe. This is the label an aborted, timed-out or write-conflicted
	 * transaction carries.</li>
	 * <li>{@code UnknownTransactionCommitResult} — the commit was sent and the answer was lost. It may
	 * have landed. Retried as the driver documentation prescribes, which is why the unit of work has
	 * to tolerate a second run.</li>
	 * </ul>
	 */
	private static boolean isTransient(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof MongoException mongo
					&& (mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
							|| mongo.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL))) {
				return true;
			}
			if (cause.getCause() == cause) {
				return false;
			}
		}
		return false;
	}

	private static void backOff(int attempt) {
		try {
			Thread.sleep(BACKOFF_MILLIS * attempt);
		}
		catch (InterruptedException ex) {
			// Somebody is shutting the request down; stop retrying and let the original failure stand.
			Thread.currentThread().interrupt();
		}
	}
}
