package com.school.accounts.domain;

/**
 * Which money account the automatic entries land in.
 *
 * <p>Configured once and then never thought about again, which is the point: an event arrives with
 * no opinion about where the money is, so without this the automatic writer would have nothing to
 * book against. Each is a {@code money_accounts} id, and each may be null — until an administrator
 * sets them, automatic entries are written with no money account and show up as unassigned rather
 * than being silently dropped.
 *
 * @param cashFeesTo  fee payments taken in cash. The drawer
 * @param bankFeesTo  fee payments that arrived in a bank account: cheque, UPI, transfer, gateway
 * @param salariesFrom the account salaries are paid out of
 */
public record AccountDefaults(String cashFeesTo, String bankFeesTo, String salariesFrom) {

	/** Nothing configured yet, which is what a settings document written on first use holds. */
	public static final AccountDefaults NONE = new AccountDefaults(null, null, null);
}
