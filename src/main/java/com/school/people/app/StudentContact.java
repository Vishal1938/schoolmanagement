package com.school.people.app;

/**
 * How to reach a student's family, for pre-filling a payment checkout.
 *
 * <p>Separate from {@link StudentRef} rather than three more fields on it. {@code StudentRef} goes to
 * attendance registers and mark sheets, which have no business holding a phone number; this goes to
 * exactly one caller, which needs one so a parent is not retyping their own number on a card form.
 *
 * @param email the family's address, which may be absent. Not a login — students sign in with their
 *              uniqueId, and siblings often share one address
 * @param phone the family's primary number
 */
public record StudentContact(String uniqueId, String name, String email, String phone) {
}
