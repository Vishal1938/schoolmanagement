/*
 * Backdated fee + payment sample data, written straight into Mongo.
 *
 * Paste into Studio 3T's IntelliShell with the `school` database selected, or:
 *   docker exec -i school-mongo mongosh "mongodb://localhost:27017/school?replicaSet=rs0&directConnection=true" < scripts/seed-demo-fees.js
 *
 * It reads the active session, the classes and the enrolled students that are already there, so
 * there are no hardcoded ids. It writes: fee_heads, fee_structures, fee_invoices, payments,
 * audit_logs, and bumps the receipt counter so the next real payment does not reuse a number.
 *
 * Re-runnable: set FORCE = true to delete what a previous run wrote and start over.
 */

const FORCE = false;

// --- the story ----------------------------------------------------------------------------------
// Today is in October 2026, so Semester 1 (due 31 Jul) is history and Semester 2 (due 15 Jan) is
// not due yet. Every payment date below is in the past; Semester 2 is left outstanding for all.

const TODAY = new Date('2026-10-21T00:00:00Z');

const HEADS = [
  { name: 'Tuition Fee', amount: 2400000 },   // paise — ₹24,000
  { name: 'Library Fee', amount: 150000 },    // ₹1,500
  { name: 'Examination Fee', amount: 200000 },// ₹2,000
  { name: 'Development Fee', amount: 300000 } // ₹3,000
];                                            // ₹30,500 a semester

const LATE_FINE = { type: 'PER_DAY', amount: 1000, graceDays: 14, cap: 50000 };

const SEMESTERS = [
  { name: 'Semester 1', due: '2026-07-31', from: '2026-07-01', to: '2026-12-31' },
  { name: 'Semester 2', due: '2027-01-15', from: '2027-01-01', to: '2027-06-30' }
];

// One of these per student, cycled. Dates are when the money arrived.
// Semester 1 only; `null` means they have not paid at all.
const PATTERNS = [
  { paidOn: '2026-07-25', full: true,  mode: 'CASH',          relation: 'FATHER' },
  { paidOn: '2026-09-10', full: true,  mode: 'UPI_OFFLINE',   relation: 'MOTHER' },
  { paidOn: '2026-08-05', part: 1500000, mode: 'CHEQUE',      relation: 'FATHER' },
  { paidOn: '2026-09-20', part: 2000000, mode: 'BANK_TRANSFER', relation: 'GUARDIAN' },
  null,
  { paidOn: '2026-10-05', full: true,  mode: 'CASH',          relation: 'SELF' }
];

// --- helpers ------------------------------------------------------------------------------------

const day = (iso) => new Date(iso + 'T00:00:00Z');
const daysBetween = (a, b) => Math.floor((b - a) / 86400000);
const addDays = (d, n) => new Date(d.getTime() + n * 86400000);
const oid = () => new ObjectId().toString();

/** The gross fine on a due date as of `asOf` — the same rule as LateFine.accruedOn. */
function accruedFine(dueDate, asOf) {
  const chargeableFrom = addDays(dueDate, LATE_FINE.graceDays);
  const late = daysBetween(chargeableFrom, asOf);
  if (late <= 0) return 0;
  const fine = LATE_FINE.amount * late;
  return LATE_FINE.cap > 0 ? Math.min(fine, LATE_FINE.cap) : fine;
}

/** Principal split across the invoice's heads, last head taking the remainder. */
function headShares(items, principal) {
  if (!items.length || principal <= 0) return [];
  const gross = items.reduce((t, i) => t + i.amount, 0);
  if (gross <= 0) return [];
  let allocated = 0;
  return items.map((item, i) => {
    const share = i === items.length - 1
      ? principal - allocated
      : Math.floor(principal * item.amount / gross);
    allocated += share;
    return { headId: item.headId, headName: item.headName, amount: share };
  });
}

const statusFor = (paid, net) => (paid >= net ? 'PAID' : paid > 0 ? 'PARTIAL' : 'UNPAID');

// --- 0. prerequisites ---------------------------------------------------------------------------

const session = db.academic_sessions.findOne({ active: true });
if (!session) throw new Error('No active session. Activate one first.');

const config = db.school_config.findOne({});
const receiptPrefix = ((config && config.academicSettings && config.academicSettings.receiptPrefix) || '')
  .trim().toUpperCase();
if (!receiptPrefix) throw new Error('academicSettings.receiptPrefix is not set in school_config.');

const admin = db.users.findOne({ role: 'ADMIN' });
const actor = { id: admin ? admin._id.toString() : null, uniqueId: admin ? admin.uniqueId : 'SYSTEM', role: 'ADMIN' };

const classes = db.classes.find({}).toArray();
if (!classes.length) throw new Error('No classes. Create them with POST /classes first.');

const students = db.students
  .find({ status: 'ACTIVE', 'enrollment.sessionId': session._id.toString() })
  .sort({ uniqueId: 1 }).toArray();
if (!students.length) throw new Error('No active students enrolled in ' + session.name + '.');

if (FORCE) {
  const ids = db.fee_structures.find({ sessionId: session._id.toString() }, { _id: 1 })
    .toArray().map((s) => s._id.toString());
  db.payments.deleteMany({ receiptNo: { $regex: '^' + receiptPrefix + '-' } });
  db.fee_invoices.deleteMany({ sessionId: session._id.toString() });
  db.fee_structures.deleteMany({ _id: { $in: ids.map((i) => ObjectId(i)) } });
  db.counters.deleteOne({ _id: receiptPrefix + '-26' });
  print('FORCE: cleared the previous run.');
}

if (db.payments.countDocuments({}) > 0) {
  throw new Error('Payments already exist. Set FORCE = true to replace them, or clear them yourself.');
}

const now = new Date();

// --- 1. fee heads -------------------------------------------------------------------------------

const heads = HEADS.map((h) => {
  const existing = db.fee_heads.findOne({ name: h.name });
  if (existing) return { headId: existing._id.toString(), headName: existing.name, amount: h.amount };
  const id = oid();
  db.fee_heads.insertOne({
    _id: ObjectId(id), name: h.name, active: true, createdAt: now, updatedAt: now
  });
  return { headId: id, headName: h.name, amount: h.amount };
});
print('Fee heads ready: ' + heads.length);

// --- 2. one fee structure per class -------------------------------------------------------------

const structureByClass = {};
classes.forEach((cls) => {
  const id = oid();
  db.fee_structures.insertOne({
    _id: ObjectId(id),
    sessionId: session._id.toString(),
    classId: cls._id.toString(),
    installments: SEMESTERS.map((s) => ({
      name: s.name,
      dueDate: day(s.due),
      items: heads.map((h) => ({ headId: h.headId, amount: h.amount, sectionAmounts: null })),
      kind: 'SEMESTER',
      label: s.name,
      periodStart: day(s.from),
      periodEnd: day(s.to)
    })),
    lateFine: LATE_FINE,
    createdAt: now,
    updatedAt: now
  });
  structureByClass[cls._id.toString()] = id;
  db.audit_logs.insertOne({
    action: 'FEE_STRUCTURE_CREATED', entityType: 'FeeStructure', entityId: id,
    actor: actor, at: now, detail: 'Sample data script'
  });
});
print('Fee structures: ' + classes.length);

// --- 3. invoices ---------------------------------------------------------------------------------

const net = heads.reduce((t, h) => t + h.amount, 0);
const items = heads.map((h) => ({ headId: h.headId, headName: h.headName, amount: h.amount }));
const invoicesByStudent = {};

students.forEach((student) => {
  const classId = student.enrollment.classId;
  const structureId = structureByClass[classId];
  if (!structureId) return;
  invoicesByStudent[student.uniqueId] = SEMESTERS.map((s) => {
    const id = oid();
    db.fee_invoices.insertOne({
      _id: ObjectId(id),
      studentUniqueId: student.uniqueId,
      classId: classId,
      sessionId: session._id.toString(),
      structureId: structureId,
      installmentName: s.name,
      kind: 'SEMESTER',
      label: s.name,
      note: null,
      dueDate: day(s.due),
      periodStart: day(s.from),
      periodEnd: day(s.to),
      items: items,
      concessionAmount: 0,
      netAmount: net,
      paidAmount: 0,
      lateFinePaid: 0,
      status: 'UNPAID',
      createdAt: now,
      updatedAt: now
    });
    return { id: id, name: s.name, dueDate: day(s.due) };
  });
});
print('Invoices: ' + db.fee_invoices.countDocuments({ sessionId: session._id.toString() }));

// --- 4. plan the payments, then write them in date order ----------------------------------------
// Receipt numbers have to ascend with the payment date, so the whole set is planned first, sorted,
// and only then numbered.

const planned = [];
students.forEach((student, i) => {
  const pattern = PATTERNS[i % PATTERNS.length];
  if (!pattern) return;
  const semester1 = (invoicesByStudent[student.uniqueId] || [])[0];
  if (!semester1) return;
  const guardians = student.guardians || {};
  const payerName =
    pattern.relation === 'FATHER' ? (guardians.fatherName || student.name)
      : pattern.relation === 'MOTHER' ? (guardians.motherName || student.name)
        : pattern.relation === 'GUARDIAN' ? (guardians.guardianName || student.name)
          : student.name;
  planned.push({
    studentUniqueId: student.uniqueId,
    invoice: semester1,
    paidOn: day(pattern.paidOn),
    full: !!pattern.full,
    part: pattern.part || 0,
    mode: pattern.mode,
    relation: pattern.relation,
    payerName: payerName
  });
});

planned.sort((a, b) => a.paidOn - b.paidOn);

let seq = 0;
planned.forEach((p) => {
  // Fine as of the day the money arrived, not as of today.
  const fineDue = accruedFine(p.invoice.dueDate, p.paidOn);
  const offered = p.full ? net + fineDue : p.part;

  // Fine first, then principal — the same order as InvoiceService.allocate.
  const fine = Math.min(offered, fineDue);
  const principal = Math.min(offered - fine, net);
  const amount = fine + principal;
  if (amount <= 0) return;

  seq += 1;
  const receiptNo = receiptPrefix + '-26-' + String(seq).padStart(6, '0');
  const paymentId = oid();

  db.payments.insertOne({
    _id: ObjectId(paymentId),
    studentUniqueId: p.studentUniqueId,
    mode: p.mode,
    amount: amount,
    invoiceIds: [p.invoice.id],
    allocations: [{
      invoiceId: p.invoice.id,
      installmentName: p.invoice.name,
      dueDate: p.invoice.dueDate,
      amount: principal,
      lateFine: fine,
      heads: headShares(items, principal)
    }],
    unallocatedAmount: null,
    reference: p.mode === 'CASH' ? null : p.mode + '-' + receiptNo,
    payerName: p.payerName,
    payerRelation: p.relation,
    receiptNo: receiptNo,
    gatewayOrderId: null,
    gatewayPaymentId: null,
    status: 'CAPTURED',
    failureReason: null,
    recordedBy: actor.uniqueId,
    paidAt: p.paidOn,
    createdAt: p.paidOn
  });

  db.fee_invoices.updateOne(
    { _id: ObjectId(p.invoice.id) },
    {
      $set: {
        paidAmount: principal,
        lateFinePaid: fine,
        status: statusFor(principal, net),
        updatedAt: p.paidOn
      }
    }
  );

  db.audit_logs.insertMany([
    {
      action: 'PAYMENT_RECORDED', entityType: 'Payment', entityId: paymentId,
      actor: actor, at: p.paidOn, detail: 'Sample data script'
    },
    {
      action: 'FEE_PAYMENT_APPLIED', entityType: 'Invoice', entityId: p.invoice.id,
      actor: actor, at: p.paidOn, detail: 'Sample data script'
    }
  ]);
});

// --- 5. move the receipt counter so the next real payment does not collide -----------------------

db.counters.updateOne(
  { _id: receiptPrefix + '-26' },
  { $set: { seq: seq } },
  { upsert: true }
);

print('---');
print('Payments written: ' + seq + ', last receipt ' + receiptPrefix + '-26-' + String(seq).padStart(6, '0'));
print('Collected: ₹' + (db.payments.aggregate([
  { $group: { _id: null, t: { $sum: '$amount' } } }
]).toArray()[0].t / 100).toLocaleString());
print('Invoices by status:');
db.fee_invoices.aggregate([
  { $match: { sessionId: session._id.toString() } },
  { $group: { _id: '$status', n: { $sum: 1 } } },
  { $sort: { _id: 1 } }
]).forEach((r) => print('  ' + r._id + ': ' + r.n));
