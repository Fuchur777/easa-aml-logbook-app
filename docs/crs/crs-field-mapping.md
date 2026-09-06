# CRS template — field mapping

Every block on the certificate, and the provision that puts it there. Sample
rendering: `sample-crs.pdf`.

## Required by ML.A.801(e)

| Provision | Requirement | Where it appears |
|---|---|---|
| (e)(1) | Basic details of the maintenance carried out | "Maintenance carried out" — free prose |
| (e)(2) | Date on which the maintenance was completed | "Maintenance completed", in the work period block |
| (e)(3) | Identity of the person issuing the release, including licence number for independent certifying staff | Certification block, name inline in the statement plus a separate licence number field |
| (e)(4) | Limitations to airworthiness or operations, if any | "Limitations" block, always printed — "None." when empty |
| (f) | Where maintenance could not be completed, the CRS says so with applicable limitations | Rendered inside the limitations block, not as a separate section |

## Required by AMC1 ML.A.801(e)

| Point | Requirement | Where |
|---|---|---|
| (a) | The prescribed statement | Certification block, verbatim, keyed by certification basis |
| (b) | Revision status of the maintenance instruction used | "Maintenance data used" — reference, revision, revision date |
| (c) | Date relative to life or overhaul limitations in date, hours, cycles or landings | Aircraft block — airframe hours and launches at maintenance |
| (d) | Unique cross-reference to the work pack where maintenance is summarised | "Work order reference" |
| (e) | Normal signature, or a computer release-to-service system where the CA is satisfied only that person may issue | Signature area — blank for print, or the digital signature. The regulatory basis is printed directly beneath it, including the statement that the CA has been satisfied only this person may issue |

## Not required, included deliberately

**Work period — start date, dates worked and day count.** Only the completion date
is required by ML.A.801(e)(2); the rest is added deliberately. A three-day job
recorded solely by its completion date understates the work and loses the evidence
of when it happened. It also makes the certificate self-evidencing for Route A
recency: a CRS stating three days worked is itself proof of three days, derived from
the session records rather than asserted separately.

The block is one four-column row: work started, maintenance completed, days worked,
work order reference. Individual dates are **not** printed — the day count is the
useful figure and the dates themselves are in the logbook if ever needed.

**Parts and materials installed.** Part number, batch or serial, and the release
document reference. Not in ML.A.801(e), but traceability is what makes the record
worth keeping, and an inspector will ask.

**Personnel who carried out the work.** Basis is ML.A.801(d) — assistance by
persons under direct and continuous control. Printed under a caption reading
*record purposes only; certification is by the signatory above*, structurally
separated from the certification block by a heavy rule and placed **after** the
signature so it can never be read as a second release. Licence numbers of helpers
appear in their own column, nowhere near the certification statement.

**Photographic record.** Filenames and truncated hashes only — photographs are
never embedded (§8). The footer states that the photographs are held separately and
bound to the certificate by these hashes.

## Layout rules

**Certification is last and visually heaviest.** Heavy rule above it, bold heading,
the statement in full sentence form with the issuer's name inline. Everything above
is description; this block is the legal act.

**The limitations block always prints**, even when empty, because "except as
otherwise specified" in the statement is meaningless if the reader cannot see
whether anything was specified. An absent section reads as an omission; "None."
reads as a decision.

**Dates are unambiguous** — `14 March 2026`, never `14-03-2026`. The document
travels to countries that read the numeric form differently.

**Entire document in English**, including field labels. The UI is localised; the
certificate is not. Users' free text appears in whatever language they wrote it,
which is normal.

**The annual-inspection flag is not printed** (§5.8). It is recency metadata, and
printing it would freeze a correctable mistake into an immutable document.

## Signature area, three states

- **`ISSUED_UNSIGNED_PRINT`** — a ruled line and the caption. For printing and
  wet-signing.
- **`SIGNED_LOCAL` / `SIGNED_QES`** — the signature widget, plus a visible
  annotation naming the method, the signing time and the certificate subject, so a
  reader without a PDF validator can still see what was done.
- **`TIMESTAMP_PENDING`** — signed but not yet timestamped. The document is valid;
  only long-term verifiability is deferred. Not visually distinguished on the
  certificate, but flagged in the app until resolved.

## Pilot-owner variant (deferred)

Same template, three substitutions: the ML.A.803 statement variant; pilot licence
number in place of the AML number; and the personnel block suppressed entirely,
since AMC1 ML.A.803 limits the pilot-owner CRS to maintenance personally performed.
ML.A.803(c) makes the maintenance data used a requirement of the rule itself rather
than the AMC, so that block is mandatory rather than conditional in this variant.

## Output format

**PDF/A** for the archived certificate. Over a sixty-year logbook the cryptographic
signature is a ten-to-twenty year proposition at best; the readable document and
the hash manifest are what survive. Worth stating in the system description
document — a competent authority that understands the distinction will trust the
design more, not less.
