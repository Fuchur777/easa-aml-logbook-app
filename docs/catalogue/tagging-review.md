# Catalogue tagging — review sheet

Catalogue `easa-amc-part66-appendix-ii` v2026.1, transcribed from Appendix II to
AMC to Annex III (Part-66), ED Decision 2023/019/R, as published in the Easy Access
Rules revision of September 2025.

**102 tasks.** Table B in full (55), plus 47 from Table A.

Transcription is mechanical and should be verified but not debated. The
**applicability tagging is authored** — it is my reading of AMC 66.A.45(h)'s
"relevant to the licence category and to the applicable aircraft type ratings",
not something the Appendix states. That is what needs your eyes.

## Denominators that fall out

| Subcategory | Tasks | 50% threshold | Sections to cover |
|---|---|---|---|
| L1 | 49 | 25 | 8 |
| L1C | 47 | 24 | 8 |
| L2 | 97 | 49 | 15 |
| L2C | 95 | 48 | 15 |

Unfiltered, the list is 102 tasks and the 50% threshold is 51 — more than the 49
tasks an L1 holder can reach. Filtering isn't a refinement; without it the rule is
unsatisfiable for L1.

## Section tagging as built

| Section | Tasks | L1 | L1C | L2 | L2C |
|---|---|---|---|---|---|
| General activities | 7 | ✓ | ✓ | ✓ | ✓ |
| Leveling and weighing | 4 | ✓ | ✓ | ✓ | ✓ |
| Flight controls and flight control systems | 7 | ✓ | ✓ | ✓ | ✓ |
| Electrical systems | 2 | ✓ | ✓ | ✓ | ✓ |
| Avionics systems | 4 | ✓ | ✓ | ✓ | ✓ |
| Cabin equipment/systems | 11 | ✓ | ✓ | ✓ | ✓ |
| Powered sailplane folding system | 1 | — | — | ✓ | ✓ |
| Wooden structures/Metal tubes and fabric | 7 | ✓ | — | ✓ | — |
| Composite structures | 5 | — | ✓ | — | ✓ |
| Metal structures | 7 | ✓ | ✓ | ✓ | ✓ |
| Propeller (Table A) | 11 | — | — | ✓ | ✓ |
| Piston Engines (Table A) | 9 | — | — | ✓ | ✓ |
| Fuel and control, piston (Table A) | 9 | — | — | ✓ | ✓ |
| Ignition systems, piston (Table A) | 9 | — | — | ✓ | ✓ |
| Engine Indicating (Table A) | 5 | — | — | ✓ | ✓ |
| Exhaust, piston (Table A) | 4 | — | — | ✓ | ✓ |

## Five decisions you should check

**1. Engine Controls is excluded — deliberately.** Table B's cross-reference names
propeller, piston engine, fuel and control, ignition, engine indications and
exhaust. It does **not** name Engine Controls. ILT's logbook includes it anyway
(9 tasks). Staying with the EASA text means our L2 denominator is 97 where ILT's
would be 106. Your call: fidelity to the Appendix, or alignment with the national
form your Dutch users will recognise.

**2. Metal structures applies to all four — settled.** The section title refers to
all-metal gliders, but metal fittings occur in every construction and an L1C holder
can carry out crack testing, riveting and anti-corrosion treatment on a composite
aircraft without holding L1. The tasks are therefore reachable and creditable for
the C subcategories. This is a caveat in the EASA drafting rather than something
the Appendix resolves, and the catalogue records the reasoning in its `notes`.

**3. Wood and fabric stays restricted to L1/L2.** Unlike metal, the tasks are not
reachable on a composite aircraft: there is no plywood skin to repair and no fabric
covering to recover. Asymmetric with metal, deliberately.

**4. Cabin equipment applies to all four**, including water ballast and
undercarriage. No powered/unpowered split is stated, and both exist on unpowered
sailplanes, so I left it universal.

**5. Table B lists "Weighing, weight & balance sheet" twice** — under General
activities (`B.GEN.02`) and under Leveling and weighing (`B.LVL.02`). Both are in
the catalogue as separate IDs, faithful to the source. Whether completing the work
once should tick both is a UI question, not a catalogue one. My inclination is that
it ticks both, with a note, since it is plainly one activity.

## Section coverage

AMC 66.A.45(h) requires the experience to cover tasks from **each paragraph** of
the list, not merely 50% overall. So a compliant L1 holder needs at least one task
in each of their 8 sections *and* 25 tasks in total. The UI shows both; a user at
26 tasks with an empty section is not compliant, and the app should say which
section is empty rather than showing a green percentage.

## Substitution

AMC 66.A.45(h) allows other relevant tasks to replace listed ones. Substitutions
are stored on the completion (`substituteText`, `substituteJustification`) and
count toward both the total and their section. They are shown distinctly in the
report, since they are the user's judgement rather than a listed task.
