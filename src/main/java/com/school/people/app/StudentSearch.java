package com.school.people.app;

import com.school.common.fees.FeeStatus;
import com.school.people.domain.StudentStatus;

/**
 * The filters of {@code GET /students}. Every one is optional; all that are given are ANDed.
 *
 * @param classId   filter to one class
 * @param section   filter to one section, normally together with a class
 * @param status    defaults to nothing, i.e. every status — the caller asks for ACTIVE explicitly
 * @param q         free text matched against the name (prefix), the uniqueId (prefix) and the family
 *                  phone number (prefix)
 * @param feeStatus filter to students who stand this way on fees. Unlike the others this is derived
 *                  rather than stored, so it costs an extra round trip — see
 *                  {@link StudentService#search}
 */
public record StudentSearch(String classId, String section, StudentStatus status, String q, FeeStatus feeStatus) {
}
