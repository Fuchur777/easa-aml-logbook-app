# Part-66L Logbook — Specification v0.1

Android application. Personal maintenance logbook and CRS generator for holders of
EASA Part-66 L1, L1C, L2 and L2C aircraft maintenance licences.

Status: requirements settled, regulatory basis sourced. Open items listed in §17.

---

## 1. Purpose

A personal engineer's logbook that:

- records maintenance work performed, whether or not it results in a release;
- produces a Certificate of Release to Service compliant with ML.A.801;
- computes recency under 66.A.20(b)(2) across all held subcategories;
- tracks proficiency against Appendix II to AMC to Part-66;
- satisfies ML.A.801(g) — the obligation on independent certifying staff to retain
  the records proving the requirements for issuing a CRS were met.

It is the licence holder's own record. It is not a continuing airworthiness
management system.

## 2. Regulatory basis

All references to *Easy Access Rules for Continuing Airworthiness (Regulation (EU)
No 1321/2014)*, revision September 2025.

| Provision | Governs |
|---|---|
| 66.A.20(a)(6) | L1/L1C/L2/L2C privileges |
| 66.A.20(b)(2) | Recency — the two alternatives |
| AMC 66.A.20(b)(2) | 100-day equivalence, 50% CA reduction, 20% substitution, similarity test, prescribed record fields |
| GM 66.A.20(b)2 | Meaning of "met the provision for the issue of the appropriate privileges" |
| 66.A.45(h) | Endorsement — practical experience |
| AMC 66.A.45(d);(e)3;(f)1;(g)1;(h) | 50% threshold, relevance filtering, coverage from each paragraph, substitution of relevant tasks |
| Appendix II to AMC to Annex III, Table B | Task catalogue for sailplanes and powered sailplanes |
| Appendix II to AMC to Annex III, Table A | Engine task blocks cross-referenced from Table B |
| ML.A.801(d) | Assistance by persons under direct and continuous control |
| ML.A.801(e) | CRS minimum content |
| ML.A.801(f) | Incomplete maintenance — limitations |
| ML.A.801(g) | No CRS where known non-compliance endangers flight safety |
| AMC1 ML.A.801(e) | Prescribed statement, revision status, life limits, work pack cross-reference, computer release-to-service system |
| ML.A.803 | Pilot-owner authorisation (deferred, §16) |
| NPA 2025-12 | Proposed AMC2 66.A.20(b)(2) — Route C, not yet in force |

## 3. Non-goals

Explicitly out of scope. Each is a doorway to a different product.

- Aircraft airworthiness status of any kind: no due lists, no AD/SB compliance
  tracking, no lifed-component monitoring, no ARC dates, no AMP.
- Running totals of airframe hours or launches. Readings are recorded per work
  entry as observed; the app never maintains a counter.
- Determining the user's privileges or gating certification.
- Any server-side component holding user data.
- Two-way multi-device sync.
- Supervisor countersignature for trainees building initial experience.

The app must state its own boundary on the aircraft screen: this is a personal
engineer's logbook and does not track the aircraft's airworthiness status.

## 4. Actors and modes

Single profile per installation. One licence holder. Enforced, and stated in the
EULA — a shared club tablet with one signing key is a compliance failure.

**Certification basis** is an enum on each CRS, present from v1 even though only
the first value is implemented:

- `ML_A_801_B2_INDEPENDENT` — independent certifying staff
- `ML_A_803_PILOT_OWNER` — pilot-owner (deferred, §16)

A user setting holds the preferred basis (AML / pilot-owner / ask each time),
defaulting to AML when licence details are complete. The app narrows the
available bases per CRS from facts it holds, and **never silently switches**: if
the preferred basis is unavailable it says why.

## 5. Data model

### 5.1 Person (the user)

- Name, contact
- Licence: number, issuing authority, subcategories held (set of L1/L1C/L2/L2C),
  expiry, limitations and ratings as free text
- Scanned licence copy — stored in Drive `appdata`, never in the records export
- Recency reduction grant: granted (default false), authority, reference, date
  granted, optional validity, optional scan of the decision

### 5.2 Aircraft

Thin by design. Keyed on manufacturer + serial; registration is a dated attribute
with history, so an old CRS still prints what it said at the time.

- Manufacturer, type, serial number
- Registration history (registration, valid from, valid to)
- Propulsion: unpowered / powered sailplane / ELA1
- Structure: wood and fabric / metal tube and fabric / composite / metal / mixed
- Subcategory override, set once where construction is mixed and cannot be derived
- Ownership relationship to the user (owner / joint owner / designated member of
  owning non-profit / none) — required later for pilot-owner mode

Propulsion and structure determine which subcategory's privileges are exercised
when certifying, and therefore which recency column the work credits.

### 5.3 WorkEntry

The atomic unit. A CRS is one possible outcome of an entry, not its purpose.

- Aircraft (**optional** — bench and component work is still experience)
- Workorder (value object, §5.4)
- Description of work
- Documentation used: reference **and revision status** (required by AMC1 ML.A.801(e)(b))
- Parts and materials: part number, batch/serial, EASA Form 1 reference
- Activity type — controlled vocabulary from AMC 66.A.20(b)(2): servicing;
  inspection; operational and functional testing; troubleshooting; repairing;
  modifying; changing component; supervising these activities; releasing to service
- Role/outcome: certified by me in-app / certified by me on paper / performed by
  me and released by another / performed under supervision / supervised another /
  assisted on ARC / no release
- Airframe reading at time of work: hours, launches (a reading, not a counter)
- Annual-inspection flag (§5.8)
- Substitution flag — entry falls under the AMC's 20% allowance (training,
  technical support/engineering, maintenance management/planning)
- Helpers (§5.5)
- Photos (§8)
- Provenance: `NATIVE` or `IMPORTED`
- Deferred items raised (§5.7)

### 5.4 Workorder

Value object on the entry, not an entity.

- Issuer name (autocompleted from the person directory)
- Date
- Requested work, free text
- Optional attachment: PDF or image, captured via the Play Services document
  scanner, hashed and stored like a photo

### 5.5 WorkSession and helpers

`WorkSession = (entry, date, duration)`. One job spans days; one day covers several
aircraft. Duration is recorded in days or partial-days per AMC 66.A.20(b)(2), and
is **never a required field** — hours do not enter the compliance calculation.

Helpers are a many-to-many join from entry to Person with a role (assisted /
independent inspection). No per-person hours or dates — deliberately. If that ever
changes it is a nullable column, not a remodelling. Basis: ML.A.801(d).

### 5.6 CRS

Immutable once signed. The signed PDF bytes are the record.

- Number (§9)
- Certification basis
- Frozen snapshot of: aircraft identity as at signing, work description,
  documentation references with revisions, parts, helpers, workorder text and
  attachment hash, photo manifest, limitations
- Signature state: `DRAFT` / `SIGNED_LOCAL` / `SIGNED_QES` / `ISSUED_UNSIGNED_PRINT` /
  `TIMESTAMP_PENDING`
- Signature intent record: who, when, device, authentication method, app version
- Void record where applicable, with reason

Corrections are made by issuing a new CRS referencing the old one. Nothing is
edited, nothing is deleted.

### 5.7 DeferredItem

A note in the engineer's record, **not** a statement about the aircraft.

- Description, raising CRS, status, closing entry or CRS, closing date

Hard constraints: no due dates, no reminders, no computed status, no badge on the
aircraft. Scoped to items *the user* raised — another engineer's deferrals are
invisible and must never be implied to be complete.

### 5.8 Annual-inspection flag

Entry metadata, **not printed on the CRS**, so a mis-tick stays correctable. Changes
are audit-logged. Label carries the full condition so the tick is itself the
declaration.

Additional flag: annual inspection performed concurrently with an airworthiness
review — excluded from the Route C count (see §17, open).

### 5.9 TaskCompletion

Links a catalogue task to a work entry. Registration, aircraft type, workorder
reference and date populate from the entry — which is exactly the column set the
regulator's own logbooks use.

- Catalogue task ID, catalogue version, task text snapshot as at completion
- Work entry reference
- Optional: substitute task (user-authored, mapped to a catalogue task, with
  justification) per AMC 66.A.45(h)

## 6. Catalogue

Transcribed from Appendix II to AMC to Annex III as published by EASA — not from a
national rendering.

**Content**: Table B in full, plus the propeller, piston engine, fuel and control,
ignition, engine indication and exhaust blocks of Table A that Table B
cross-references.

**Task IDs** are section-scoped and explicitly assigned. Not derived from text:
"Weighing, weight & balance sheet" appears twice in Table B under different
sections.

**Applicability tags** per task: which of L1/L1C/L2/L2C it counts toward. Authored
rather than transcribed, and reviewed. Denominators: L1 49, L1C 47, L2 97, L2C 95,
from 102 tasks. Metal structures applies to all four — the section title names
all-metal gliders but metal fittings occur in every construction and are creditable
there; wood and fabric stays restricted, since a composite aircraft has no plywood
skin or fabric covering.

**Format**: JSON with stable IDs, `supersedes` relations, per-subcategory
thresholds, section membership, and the certification statement variants.

**Versioning**: a completion stores task ID, catalogue version and the task text as
it read that day. On update the app shows a diff — new tasks unsigned, reworded
tasks flagged for review, removed tasks retained in a historical section. Nothing is
ever deleted.

**The app is EASA-based, not national.** The catalogue is Appendix II to AMC to
Annex III (Part-66), so one catalogue serves every EU L1/L2 holder. National
logbooks are renderings of the same table; where they differ — ILT's includes the
Engine Controls block that Table B does not reference — the app follows the EASA
text and says so.

**The reference is displayed, not just recorded.** Every task carries its source
string, shown on the task list and printed in the recency report:
*Appendix II to AMC to Annex III (Part-66), Table B — Flight controls and flight
control systems*. A user, or an inspector reading their report, can trace any figure
back to the provision it came from.

**Distribution**: seeded in the app, updated by signed fetch (Ed25519) from a URL
the developer controls, verified before acceptance. App update is the fallback.
Users may import a CSV/XLSX catalogue where an authority publishes a genuinely
different list, held in a separate namespace and visibly marked unverified.

**Second catalogue, later**: Appendix II to Part-ML for pilot-owner tasks. Same
machinery, different content.

## 7. Recency engine

Evaluated **per held subcategory**, independently. L1C can lapse while L2 stays
current. A subcategory is current if any route is satisfied.

|  | L1 | L1C | L2 | L2C |
|---|---|---|---|---|
| Route A — days | | | | |
| Route B — 50% of tasks | | | | |
| Route C — annual inspections (proposed) | | | | |

Every rule carries `IN_FORCE` / `PROPOSED` / `SUPERSEDED` and an effective date.

### 7.1 Route A — days

Threshold **100 days** in the preceding 24 months, per AMC 66.A.20(b)(2), which
allows the 6-month period to be replaced by 100 days of experience in accordance
with the privileges. Reduced to **50 days** where the competent authority has
agreed in advance.

- Counted as `COUNT(DISTINCT local_date)` — local dates, never instants. Duration is
  recorded in days or partial-days as the AMC prescribes, but the threshold counts
  distinct days; both figures are displayed.
- **Every logged day counts.** The AMC's 20% allowance for substituting training,
  technical support/engineering or maintenance planning is **not modelled**: it
  would put a flag on every entry to serve a case most independent certifying staff
  never claim, and anyone who does claim it has already agreed it with their
  authority. It is explained in the help text and left to the user.
- Days should be spread over the intended six-month period — surface as an advisory
  where entries are heavily clustered.
- **Similarity test**: experience must be on that aircraft or a similar aircraft
  within the same licence subcategory. AMC 66.A.20(b)(2) frames similarity on
  propulsion, flight controls, avionics and structure; for sailplanes the
  subcategory captures what matters, so similarity is implemented as *same
  subcategory* and the finer attributes are deliberately not modelled.

### 7.2 Route B — 50% of tasks

Two conditions, both required per AMC 66.A.45(h):

1. At least 50% of the Appendix II tasks **relevant to the licence category and the
   applicable ratings** — the filtered denominator.
2. Coverage of tasks **from each paragraph** of the list.

So the UI shows per-section coverage, not a single percentage. Available
indefinitely as a standing alternative (see §17 for the GM tension).

Denominator filtering is not optional: on an unfiltered combined list, an L1
holder's 50% exceeds the number of tasks available to them.

### 7.3 Route C — annual inspections

From proposed AMC2 66.A.20(b)(2), NPA 2025-12. Displayed as *proposed — not yet
applicable*, tracking progress without contributing to the verdict. Flipping a
catalogue field on publication of the ED Decision activates it retroactively over
entries already logged.

### 7.4 Presentation

- Two independent indicators, never merged: **licence validity** and **recency**.
  A valid licence with lapsed recency is a different situation from an expired
  licence.
- The headline number is the **lapse date**: compliant until DD MM YYYY if nothing
  further is logged.
- Show which tasks or days fall out of the window next.
- The recency status appears on the signing screen. It never blocks.
- Imported history contributes but is shown separately as declared, not evidenced.

## 8. Photos and evidence

Captured in-app, stored in app-private storage, mirrored to Drive. Optional —
never required to sign.

- SHA-256 hashed **once, after any downscaling**, and never re-encoded afterwards.
- UUID filenames; human captions live in the manifest, so Drive renames don't break links.
- Downscaled by default; GPS EXIF stripped by default, timestamp retained.
- Never embedded in the CRS PDF. The CRS carries a **photo manifest**: UUID, hash,
  capture timestamp, caption.
- A **verify records** function re-hashes and reports match / missing / mismatch.
  A deleted file is missing, not tampered, and must be reported as such.

## 9. CRS generation

### 9.1 Content

Per ML.A.801(e), at minimum: basic details of the maintenance carried out; the date
maintenance was completed; the identity of the person issuing the release, with
licence number; and limitations to airworthiness or operations, if any.

Per AMC1 ML.A.801(e), additionally: the revision status of the maintenance
instruction used; the date relative to life or overhaul limitations in terms of
date, flying hours, cycles or landings; and, where maintenance is summarised, a
unique cross-reference to the work pack.

Prescribed statement, stored as versioned non-translatable catalogue data keyed by
certification basis:

> certifies that the work specified, except as otherwise specified, was carried out
> in accordance with Part-ML, and in respect to that work, the aircraft is
> considered ready for release to service.

Limitations free text prints adjacent to the statement when non-empty, so "except as
otherwise specified" reads coherently. Where maintenance could not be completed,
ML.A.801(f) requires the CRS to say so, within the limitations block.

Helpers appear in a **clearly separated block** — work carried out by, record
purposes only, certification by the signatory below — structurally distant from the
certification statement and licence number.

Entire document in English. Dates unambiguous (`14 March 2026` or ISO). The UI is
localised; the document is not.

### 9.2 Numbering

App-generated, user-configurable template (e.g. `{PREFIX}-{YYYY}-{SEQ:4}`) with an
explicit annual-reset switch and a start-at-N setting.

- **Allocated at signing, not at draft creation**, so abandoned drafts leave no gaps.
- On signing failure: release the number or record it void with a reason. Every gap
  explainable.
- Format is a dated setting; issued numbers never change. Collision check on format change.
- Imported historical numbers are free text and never enter the live sequence.

### 9.3 Signing

The governing requirement is AMC1 ML.A.801(e): the person should use their normal
signature except where a **computer release-to-service system** is used, in which
case the competent authority must be satisfied that **only that particular person**
may electronically issue the CRS. The example given is a personal card plus PIN.
The bar is sole control, not eIDAS.

**Decision: hardware-backed self-signed, no third-party trust provider.** The key
is generated inside the Android Keystore (StrongBox or TEE), non-exportable by
construction, released only on biometric authentication. The certificate wrapping
it is self-signed. Sole control comes from the hardware, not from the certificate —
which clears the AMC's bar more convincingly than its own worked example of a
personal card plus a PIN.

**The competent authority is the trust anchor.** The certificate fingerprint is
printed in the system description document (§9.4). The authority's satisfaction is
recorded as *this fingerprint belongs to this licence holder*, which is precisely
the relationship AMC1 ML.A.801(e) contemplates — no annual fee, no contract, and no
dependency on a commercial provider that might not outlive the logbook.

Consequences that must be built in from the start:

- **Archive the certificate with every CRS.** Hardware keys cannot be backed up, so
  a lost phone loses the key — but every certificate it signed must still verify.
  The certificate goes in the record and the export bundle, not only in the PDF.
- **Support key rotation.** A new device means a new key and fingerprint, and a
  notification to the authority. The app holds multiple historical certificates,
  each valid for the period it signed in, and regenerates the fingerprint page.
- **Long certificate validity** — thirty years, not one. With no CA there is no
  renewal, and expiry would only break verification.
- **Expect reader warnings.** Adobe will report the signature as valid but the
  identity unverified. Mitigated by a visible signature appearance naming the
  method and fingerprint, alongside the regulatory text already printed beneath the
  signature block.

**Timestamping: public RFC-3161 authority, best-effort, never blocking.** No account
or contract is needed. A CRS signed without signal is valid and stored
`TIMESTAMP_PENDING`; a background worker completes it when connectivity returns.
The authority used and the time obtained are stored with the record. Configure
several fallbacks — a free TSA is unlikely to survive a sixty-year logbook — and
never prevent a release because a timestamp failed.

**`CrsSigner` abstraction** remains, `sign(digest) → CMS`:

1. `LocalKeystoreSigner` — the above. v1 and the intended long-term answer.
2. `RemoteQtspSigner` — CSC / ETSI TS 119 432, if a provider ever offers
   bring-your-own-account terms. Deliberately not pursued now.
3. Print-and-wet-sign fallback.

Use **Apache PDFBox (PdfBox-Android)**, not iText — iText 7 is AGPL and a
closed-source Play Store app would need a commercial licence.

### 9.4 System description document

A generated PDF the user hands their competent authority, describing how the app
enforces access control, sole control, non-repudiation and tamper-evidence. This is
what satisfies the AMC1 ML.A.801(e) test. It is a primary deliverable, not
documentation.

## 10. Storage and sync

Local is the source of truth. Fully functional offline with no Google account.

- Room database on device; upload queue surviving reboots.
- **Google Drive via `drive.file`** — folder per aircraft, created by the app.
  Never a broader scope: `drive` and `drive.readonly` are restricted and trigger an
  annual third-party security assessment.
- **`drive.appdata`** holds the manifest (root and per-aircraft folder IDs, sync
  state) plus identity documents. Without it a reinstall cannot find its own folders,
  because `drive.file` cannot search.
- **The app signing key can never change** — the OAuth client ID derives from it.
  Debug builds see none of the release build's files; brief testers accordingly.
- **One-way sync in v1.** A second device performs an explicit restore, not live sync.
- Signed CRS PDFs are uploaded byte-identical. Never regenerated.
- Local-only is permitted but not recommended: prompted at first run, a persistent
  not-backed-up indicator, a warning at first signing, and periodic export prompts.

Folder layout:

```
/Part-66L Logbook/
  /PH-1234 — ASK 21 — s.n. 21123/
    /2026-03-14 WO-0007 Annual/
      CRS-2026-0007.pdf
      workorder.pdf
      photos/
```

Human-readable names; all references are Drive file IDs, so renames survive.

Sharing a CRS with an owner or CAMO is supported (permissions on app-created files)
behind an explicit confirmation.

## 11. Import and export

One tabular schema serves both.

**Import** — CSV/XLSX of prior logbook history:

- Locale auto-detection for delimiter, decimal separator, date format and encoding,
  with the detected values shown in the preview and overridable. This is the single
  largest source of support load.
- Column mapping UI, saved for re-import.
- Row-level validation, partial import, rejected-rows file for correction.
- Dedupe on an optional external ID, falling back to a hash of date + serial +
  description.
- Thin aircraft auto-created from registration/type/serial, matched on serial, with
  a review step.
- One row per working day, or Route A gains nothing.
- Named **"import your ILT logbook"** path with pre-built column mapping.
- Imported rows are `IMPORTED`: never signable, never able to produce a CRS, always
  rendered separately as declared history.

**Export**, two scopes, permanently separate:

- **Records export** — CSV/XLSX, signed PDFs, photos. Goes to third parties.
  Never contains identity documents.
- **Full backup** (§10) — a raw copy of the Room database file plus every folder it
  references (`crs/`, `attachments/`, `documents/`), zipped and uploaded as-is to a
  "Backups" folder in Drive. Not a hand-rolled export format: restoring is "swap the
  files back and let Room's own migrations run" against the current app version, the
  same path a normal app update already takes. Unencrypted — Drive's own
  account-level access control is the protection, same as the rest of §10's sync;
  there is no passphrase to lose. Drive-only, no local save/share fallback.

## 12. Reports

Three renderings over one query engine.

1. **Recency report** — per subcategory, per route, with the reduction grant
   reference printed where applied, and the regulatory basis cited so the
   calculation explains itself.
2. **Per-aircraft work history** for owners — plain PDF, never signed, headed as
   the user's record of their own work rather than the aircraft's complete
   maintenance history. Explicit confirmation before sharing. Licence scan never included.
3. **CSV export** per §11.

**Search and filter**, designed into the first schema because retrofitting FTS onto
live legal records means migrating them:

- FTS over descriptions, workorder text, issuer names, registration/type/serial,
  CRS numbers, documentation references, helper names, deferred items.
- Structured filters: date range, aircraft, role, annual-inspection flag, CRS issued,
  open deferred items, provenance, signature type.
- Paged queries throughout.

Candidate, not committed: on-device OCR (ML Kit) to index scanned workorders.

## 13. Notifications

Personal only. The app reminds the user about themselves, never about an aircraft.

- Licence expiry, configurable lead times
- Recency lapse approaching, per subcategory
- Proficiency review where catalogue wording changed

## 14. Privacy, security, liability

- No backend. The developer never holds user data. Privacy policy says so.
- Play Data Safety must declare local handling of identification documents.
- Optional biometric app lock.
- EULA: the app does not determine privileges or recency; it records what is entered
  and shows a calculation. The user remains responsible. Single profile. Helper names
  frozen into signed CRSs cannot later be removed.
- Store listing honest about authority coverage.
- To verify before publication: the revised Product Liability Directive's treatment
  of software, and where a free closed-source app sits under the Cyber Resilience Act.

## 15. Platform and delivery

- Kotlin, Jetpack Compose, Room, WorkManager, PdfBox-Android, BouncyCastle,
  ML Kit document scanner.
- Full i18n from the first commit — NL and EN at launch. The certification statement
  is excluded from the resource system entirely.
- **Migrations must never touch a signed CRS.** Automated pre-migration backup,
  schema versioning, export always available.
- Free at launch, donation by outbound link only, unlocking nothing. Entitlement
  abstraction present from v1 so billing later touches one class.
- **Records are never gated.** Any future paid tier may gate conveniences, never
  access to the user's own legal records.

## 16. Deferred: pilot-owner mode (ML.A.803)

Not built in v1. The seam is reserved:

1. `certification_basis` enum present from day one.
2. Both statement variants in the catalogue now.
3. Structured issuer identity (name, licence type, licence number) rather than
   hardcoded AML fields.
4. Catalogue machinery generic enough for Appendix II to Part-ML.
5. Recency and proficiency behind a capability flag.

What differs when it arrives: qualification is a valid pilot licence plus ownership
(including designated membership of a non-profit recreational entity named on the
registration — the club case); scope is limited pilot-owner maintenance per
Appendix II to Part-ML; ML.A.803(c) requires the maintenance data used, identity,
signature and pilot licence number; the pilot-owner statement variant applies; and
**assistants are not permitted** — AMC1 ML.A.803 restricts the CRS to maintenance the
pilot-owner personally performed, so the helper feature is disabled in that mode.

Work performed and released as pilot-owner **does** count toward AML tasks, days and
annual inspections.

## 17. Open items

1. **Route C wording**, once NPA 2025-12 becomes an ED Decision.
2. **GM 66.A.20(b)2 tension.** Decision taken: Route B is a standing alternative,
   consistent with the plain "or" in 66.A.20(b)(2) and with ILT's published position.
   Held as a rules-data flag, not a code assumption.
3. **QTSP API access** — closed for now. Decision taken to avoid dependency on
   third-party trust providers; revisit only if a provider offers
   bring-your-own-account terms.
4. **Denominator arithmetic** — verify against Easy Access Rules (Sep 2025):
   Table B on pages 891–892; the referenced Table A blocks on 885–889 (propeller
   885–886, piston engines 887, fuel and control 888, ignition 888–889, engine
   indicating and exhaust 889). Engine Controls sits on 889 between ignition and
   engine indicating, and is excluded.

## 18. Phasing

- **Phase 1** — data model, work entries, aircraft, workorders, photos, local storage,
  search.
- **Phase 2** — catalogue, task completions, recency engine (Routes A and B),
  reports.
- **Phase 3** — CRS generation, local signing, numbering, limitations, deferred items,
  system description document.
- **Phase 4** — Drive sync, import/export.
- **Phase 5** — Play release: i18n, EULA, Data Safety, privacy policy, store listing.
- **Later** — Route C on adoption, QES signing, pilot-owner mode, OCR.
