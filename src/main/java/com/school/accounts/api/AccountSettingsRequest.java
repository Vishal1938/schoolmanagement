package com.school.accounts.api;

/**
 * Body of {@code PUT /accounts/settings}: which money account each kind of automatic entry lands
 * in.
 *
 * <p>Each is a {@code money_accounts} id and each is checked to exist, because a typo here would
 * quietly send every future fee collection to an account nobody looks at. Null is allowed and means
 * "not configured" — automatic entries are then written <em>without</em> a money account rather than
 * refused, which is the only choice that cannot lose a line.
 *
 * <p>Wholesale replacement, not a patch: all three are set to what is sent, so omitting one clears
 * it. The whole document is three fields on one screen, so there is nothing a client could sensibly
 * mean by sending half of it.
 *
 * @param cashFeesTo   fee payments taken in cash
 * @param bankFeesTo   fee payments that arrive in a bank account — cheque, UPI, transfer, gateway
 * @param salariesFrom the account salaries and advances are paid out of
 */
public record AccountSettingsRequest(String cashFeesTo, String bankFeesTo, String salariesFrom) {
}
