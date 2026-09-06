# CLAUDE.md

Android app: a personal maintenance logbook and CRS generator for holders of EASA
Part-66 L1, L1C, L2 and L2C aircraft maintenance licences.

**Read `docs/part66l-logbook-spec.md` before proposing anything structural.** It is
the contract. If a change contradicts it, say so and ask — do not quietly diverge.

## What this app is not

These are settled decisions, not gaps waiting to be filled. Do not add them, and do
not propose them as improvements:

- **No airworthiness status of any kind.** No due lists, no AD/SB compliance
  tracking, no lifed-component monitoring, no ARC dates, no maintenance programme.
  This is an engineer's logbook, not a CAMO tool.
- **No running totals of airframe hours or launches.** They are readings recorded
  per work entry, never counters maintained by the app.
- **No gating of certification.** The app shows recency status; it never blocks a
  signature. It cannot see work logged on paper or before install, so it must not
  act as though its picture is complete.
- **No backend.** No server holds user data, ever. Local storage plus the user's
  own Google Drive.
- **No two-way sync.** One-way upload; a second device performs an explicit restore.
- **No third-party trust provider** for signing. Hardware-backed self-signed key,
  with the competent authority as trust anchor.

## Invariants

Violating any of these produces an invalid legal record.

1. **A signed CRS is immutable.** No update path exists in the DAO layer and none
   should be added. Corrections are new certificates referencing the old one.
   Migrations must never alter a row in `crs` that has been signed, nor any
   attachment its manifest references.
2. **CRS numbers are allocated at signing, never at draft creation.** Abandoned
   drafts must leave no gaps.
3. **The certification statement is not a translatable string.** It lives in
   catalogue data, keyed by certification basis. Never route it through the i18n
   resource system.
4. **Photo hashes are computed once, after any downscaling.** Never re-encode a
   stored photo — it breaks every CRS manifest referencing it.
5. **Recency counters are derived, never stored.** Rules change and differ by
   authority; a stored boolean freezes yesterday's interpretation into a permanent
   record.
6. **The signing certificate is archived with every CRS**, not only embedded in the
   PDF. Hardware keys cannot be backed up.
7. **Records are never gated behind payment.** Any future paid tier may gate
   conveniences, never access to the user's own legal records.
8. **Single profile per installation.** One licence holder, one signing key.

## Regulatory grounding

Every rule in the code traces to a provision. Cite it in comments when adding one.
Source: Easy Access Rules for Continuing Airworthiness (Regulation (EU) No
1321/2014), September 2025 revision. Do **not** commit that PDF.

- `66.A.20(b)(2)` and its AMC — recency: 100 days, or 50 where the competent
  authority has agreed in advance
- `AMC 66.A.45(h)` — 50% of Appendix II tasks relevant to the category and ratings,
  covering tasks from each paragraph
- `Appendix II to AMC to Annex III`, Table B (pp. 891–892) plus the engine blocks of
  Table A it cross-references (pp. 885–889) — the task catalogue
- `ML.A.801(d)`, `(e)`, `(f)`, `(g)` and `AMC1 ML.A.801(e)` — CRS content, assistance,
  limitations, and the computer release-to-service system
- `ML.A.803` — pilot-owner, deferred but seamed

The 20% substitution allowance in AMC 66.A.20(b)(2) is deliberately not modelled.
Every logged day counts. Explained in help text only.

## Build order

Data layer and recency evaluator with real unit tests first — the lapse-date maths
and section-coverage rule are subtle and testable. Then the CRS renderer, checked
against `docs/crs/sample-crs.pdf` and `sample-crs-overflow.pdf`. UI last.

## Conventions

- Kotlin, Jetpack Compose, Room, WorkManager, PdfBox-Android, BouncyCastle.
- **PdfBox, never iText** — iText 7 is AGPL and this is a closed-source app.
- Full i18n from the first commit; NL and EN at launch.
- Room schemas are exported to `app/schemas/` and **committed**. They are the
  migration history for legal records.
- Dates are `LocalDate`, never `Instant`, wherever a calendar day is meant.
