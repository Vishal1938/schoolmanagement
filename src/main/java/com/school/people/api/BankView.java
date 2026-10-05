package com.school.people.api;

import com.school.people.domain.BankDetails;

/**
 * Bank details with the account number decrypted. Only ever returned to an administrator or to the
 * employee whose account it is — never in a list, and never to a third party.
 */
public record BankView(String accountName, String accountNumber, String ifsc, String bankName) {

	/** @param accountNumber already decrypted by the service; this record does no crypto itself */
	public static BankView of(BankDetails bank, String accountNumber) {
		return bank == null ? null
				: new BankView(bank.accountName(), accountNumber, bank.ifsc(), bank.bankName());
	}
}
