"""Renders sample CRS documents for layout review before implementation in
PdfBox-Android. Field order and grouping follow ML.A.801(e) and AMC1 ML.A.801(e).

Pagination rules:
  - The certification block is never split across pages.
  - Tables break freely, repeating their column headers on each page.
  - Continuation pages carry a header naming the certificate and marking it
    continued, so a loose sheet is never orphaned.
  - "Page n of m" requires the total, so the document is built twice: once to
    count, once to render.
"""

from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas

W, H = A4
L, R = 20 * mm, W - 20 * mm
COLW = R - L
TOP = H - 20 * mm
BOTTOM = 22 * mm          # content floor; footer lives below it
GREY = (0.45, 0.45, 0.45)
RULE = (0.75, 0.75, 0.75)

STATEMENT = ("certifies that the work specified, except as otherwise specified, was "
             "carried out in accordance with Part-ML, and in respect to that work, the "
             "aircraft is considered ready for release to service.")

REGULATION = ("Issued under ML.A.801(b)(2) of Regulation (EU) No 1321/2014, Annex Vb (Part-ML). "
              "Certification statement per AMC1 ML.A.801(e). Issued by a computer "
              "release-to-service system; the competent authority has been satisfied that only "
              "the person identified above may electronically issue this certificate.")


class Doc:
    def __init__(self, path, number, total=None):
        self.c = canvas.Canvas(path, pagesize=A4)
        self.c.setTitle("Certificate of Release to Service")
        self.number = number
        self.total = total
        self.page = 1
        self.y = TOP

    # -- page machinery -----------------------------------------------------

    def footer(self):
        self.c.setFillColorRGB(*GREY)
        self.c.setFont("Helvetica", 6.5)
        self.c.drawString(L, 14 * mm, self.number)
        label = f"Page {self.page} of {self.total}" if self.total else f"Page {self.page}"
        self.c.drawRightString(R, 14 * mm, label)

    def new_page(self):
        self.footer()
        self.c.showPage()
        self.page += 1
        self.y = TOP
        self.c.setFillColorRGB(*GREY)
        self.c.setFont("Helvetica-Bold", 9)
        self.c.drawString(L, self.y, "Certificate of Release to Service — continued")
        self.c.setFont("Helvetica-Bold", 9)
        self.c.drawRightString(R, self.y, self.number)
        self.y -= 4 * mm
        self.rule(6.0)

    def need(self, height_mm):
        """Break to a new page unless height_mm of content still fits."""
        if self.y - height_mm * mm < BOTTOM:
            self.new_page()

    # -- primitives ---------------------------------------------------------

    def rule(self, gap=3.0, weight=0.4, dark=False):
        self.c.setStrokeColorRGB(*((0, 0, 0) if dark else RULE))
        self.c.setLineWidth(weight)
        self.c.line(L, self.y, R, self.y)
        self.y -= gap * mm

    def heading(self, text, keep=12):
        self.need(keep)
        self.y -= 1.5 * mm
        self.c.setFillColorRGB(*GREY)
        self.c.setFont("Helvetica-Bold", 7.5)
        self.c.drawString(L, self.y, text.upper())
        self.y -= 1.8 * mm
        self.rule(4.0)

    def fields(self, pairs):
        self.need(11)
        w = COLW / len(pairs)
        for i, (label, value) in enumerate(pairs):
            x = L + i * w
            self.c.setFillColorRGB(*GREY)
            self.c.setFont("Helvetica", 6.5)
            self.c.drawString(x, self.y, label.upper())
            self.c.setFillColorRGB(0, 0, 0)
            self.c.setFont("Helvetica", 9.5)
            self.c.drawString(x, self.y - 4.4 * mm, value)
        self.y -= 9.5 * mm

    def wrap(self, text, font, size):
        lines, line = [], ""
        for word in text.split():
            trial = (line + " " + word).strip()
            if self.c.stringWidth(trial, font, size) > COLW:
                lines.append(line)
                line = word
            else:
                line = trial
        if line:
            lines.append(line)
        return lines

    def para(self, text, font="Helvetica", size=9, leading=4.3, colour=(0, 0, 0)):
        for line in self.wrap(text, font, size):
            self.need(leading + 2)
            self.c.setFillColorRGB(*colour)
            self.c.setFont(font, size)
            self.c.drawString(L, self.y, line)
            self.y -= leading * mm

    def table_header(self, cols, widths):
        self.c.setFillColorRGB(*GREY)
        self.c.setFont("Helvetica-Bold", 6.5)
        x = L
        for head, w in zip(cols, widths):
            self.c.drawString(x, self.y, head.upper())
            x += w
        self.y -= 4 * mm

    def rows(self, cols, data, widths):
        self.need(12)
        self.table_header(cols, widths)
        for row in data:
            if self.y - 4.3 * mm < BOTTOM:
                self.new_page()
                self.table_header(cols, widths)
            self.c.setFillColorRGB(0, 0, 0)
            self.c.setFont("Helvetica", 8.5)
            x = L
            for cell, w in zip(row, widths):
                self.c.drawString(x, self.y, cell)
                x += w
            self.y -= 4.3 * mm
        self.y -= 1.5 * mm

    def save(self):
        self.footer()
        self.c.save()
        return self.page


def certification_height(doc):
    """Height of the whole certification block, so it can be kept together."""
    lines = len(doc.wrap("F. Example, holder of aircraft maintenance licence NL.66.00000, "
                         + STATEMENT, "Helvetica", 9.5))
    reg_lines = len(doc.wrap(REGULATION, "Helvetica", 6.8))
    return 4 + 6 + 6 + lines * 4.8 + 2 + 9.5 + 5 + 3.5 + 5.5 + reg_lines * 3.4 + 3


def build(path, number, data, total=None):
    d = Doc(path, number, total)
    c = d.c

    # -- title ----------------------------------------------------------
    c.setFillColorRGB(0, 0, 0)
    c.setFont("Helvetica-Bold", 15)
    c.drawString(L, d.y, "Certificate of Release to Service")
    c.setFillColorRGB(*GREY)
    c.setFont("Helvetica-Bold", 10)
    c.drawRightString(R, d.y + 1, number)
    d.y -= 5 * mm
    c.setFont("Helvetica", 8)
    c.drawString(L, d.y, "Independent certifying staff — ML.A.801(b)(2)")
    d.y -= 3.5 * mm
    d.rule(6.0)

    d.heading("Aircraft")
    d.fields(data["aircraft"])

    d.heading("Maintenance carried out")
    d.para(data["description"])
    d.y -= 2 * mm

    d.heading("Work period")
    d.fields(data["period"])

    d.heading("Maintenance data used")
    d.rows(["Reference", "Revision", "Date"], data["docs"], [95 * mm, 35 * mm, 35 * mm])

    d.heading("Parts and materials installed")
    d.rows(["Part number", "Batch / serial", "Release document"], data["parts"],
           [70 * mm, 50 * mm, 45 * mm])

    d.heading("Limitations to airworthiness or operations")
    d.para(data["limitations"])

    # -- certification, kept together -----------------------------------
    d.need(certification_height(d))
    d.y -= 4 * mm
    d.rule(6.0, weight=1.0, dark=True)
    c.setFillColorRGB(0, 0, 0)
    c.setFont("Helvetica-Bold", 8)
    c.drawString(L, d.y, "CERTIFICATION")
    d.y -= 6 * mm
    d.para(f"{data['issuer']}, holder of aircraft maintenance licence {data['licence']}, "
           + STATEMENT, size=9.5, leading=4.8)
    d.y -= 2 * mm
    d.fields([("Licence number", data["licence"]), ("Date of issue", data["issued"])])
    d.y -= 5 * mm
    c.setStrokeColorRGB(*RULE)
    c.setLineWidth(0.4)
    c.line(L, d.y, L + 75 * mm, d.y)
    d.y -= 3.5 * mm
    c.setFillColorRGB(*GREY)
    c.setFont("Helvetica", 6.5)
    c.drawString(L, d.y, "SIGNATURE")
    d.y -= 5.5 * mm
    d.para(REGULATION, size=6.8, leading=3.4, colour=GREY)

    # -- after the release ----------------------------------------------
    if data["personnel"]:
        d.y -= 4 * mm
        d.heading("Personnel who carried out the work")
        d.need(8)
        c.setFillColorRGB(*GREY)
        c.setFont("Helvetica-Oblique", 7)
        c.drawString(L, d.y, "Record purposes only — ML.A.801(d). Certification is by the signatory above.")
        d.y -= 5.5 * mm
        d.rows(["Name", "Licence number", "Role"], data["personnel"], [70 * mm, 50 * mm, 45 * mm])

    if data["photos"]:
        d.heading("Photographic record, held separately")
        d.rows(["File", "SHA-256 (first 16)", "Captured"], data["photos"],
               [70 * mm, 60 * mm, 35 * mm])
        d.need(6)
        c.setFillColorRGB(*GREY)
        c.setFont("Helvetica", 6.8)
        c.drawString(L, d.y, "Photographs are bound to this certificate by the hashes listed above.")
        d.y -= 5 * mm

    return d.save()


def render(path, number, data):
    """Two passes: count pages, then render with 'of m' in the footer."""
    total = build("/tmp/_count.pdf", number, data)
    build(path, number, data, total=total)
    return total


# ---------------------------------------------------------------------------
# Sample 1 — the ordinary case
# ---------------------------------------------------------------------------

simple = dict(
    aircraft=[("Registration", "PH-1234"), ("Manufacturer and type", "Schleicher ASK 21"),
              ("Serial number", "21123"), ("Hours / launches", "3412 h / 8907")],
    description=("Annual inspection in accordance with the approved maintenance programme. "
                 "Control system inspected and rigged, control connections checked for correct "
                 "assembly and locking. Main wheel bearing replaced. Pitot-static system leak "
                 "checked. Placards checked and two replaced."),
    period=[("Work started", "11 March 2026"), ("Maintenance completed", "14 March 2026"),
            ("Days worked", "3"), ("Work order", "WO-2026-014")],
    docs=[["ASK 21 Maintenance Manual, chapter 4", "Rev. 7", "12 June 2024"],
          ["TN 826-11", "Issue 2", "3 February 2021"],
          ["AD 2019-0125", "—", "28 May 2019"]],
    parts=[["6204-2RS", "B-77412", "EASA Form 1 ref. 55120"]],
    limitations="None.",
    issuer="F. Example", licence="NL.66.00000", issued="14 March 2026",
    personnel=[["J. de Vries", "NL.66.11111", "Assisted"],
               ["M. Jansen", "—", "Independent inspection"]],
    photos=[["a3f1c0e2-…-9b41.jpg", "9f2a41c7de08b533", "14 March 2026 10:12"],
            ["b7d4e918-…-2c07.jpg", "1c88ba0447fe9d21", "14 March 2026 11:48"]],
)

# ---------------------------------------------------------------------------
# Sample 2 — overflow: winter rebuild with deferred items
# ---------------------------------------------------------------------------

overflow = dict(
    aircraft=[("Registration", "PH-9876"), ("Manufacturer and type", "Schempp-Hirth Duo Discus"),
              ("Serial number", "512"), ("Hours / launches", "1284 h / 2611")],
    description=("Winter inspection and rectification following hangar rash and a heavy landing. "
                 "Right wing shell delamination repaired at the outboard aileron hinge. Aileron "
                 "removed, repaired, refinished and rebalanced. Complete control system "
                 "disconnected, inspected, reassembled and rigged; independent inspection of all "
                 "control connections carried out and recorded. Main undercarriage leg removed, "
                 "crack tested and refitted with new bushes. Gear doors realigned. Wheel brake "
                 "overhauled and bled. Canopy frame bonding repaired and locking mechanism "
                 "adjusted. Pitot-static system leak checked. Instrument panel removed for access "
                 "and refitted; all instruments functionally checked. Batteries load tested and "
                 "one replaced. Transponder functionally checked and altitude encoder correlation "
                 "verified. Complete refinish of upper wing surfaces including gel coat repair. "
                 "Weighing carried out and a new weight and balance amendment prepared. All "
                 "placards checked; three replaced."),
    period=[("Work started", "6 November 2025"), ("Maintenance completed", "27 February 2026"),
            ("Days worked", "23"), ("Work order", "WO-2025-041")],
    docs=[["Duo Discus Maintenance Manual, chapter 2", "Rev. 12", "4 April 2023"],
          ["Duo Discus Maintenance Manual, chapter 4", "Rev. 12", "4 April 2023"],
          ["Duo Discus Maintenance Manual, chapter 6", "Rev. 12", "4 April 2023"],
          ["Duo Discus Repair Manual", "Rev. 5", "18 September 2021"],
          ["Schempp-Hirth TN 396-31", "Issue 3", "22 January 2024"],
          ["Schempp-Hirth TN 396-34", "Issue 1", "9 May 2025"],
          ["AD 2021-0044", "—", "12 February 2021"],
          ["AD 2023-0187", "—", "3 August 2023"],
          ["EASA AD 2024-0091", "—", "17 April 2024"],
          ["Tost wheel brake overhaul instruction", "Rev. 2", "1 June 2019"],
          ["Becker ATC 4401 installation manual", "Rev. 4", "30 November 2022"],
          ["Hoffmann gel coat refinishing procedure", "Rev. 1", "14 March 2018"],
          ["Club AMP, sailplane PH-9876", "Issue 6", "2 January 2026"],
          ["EASA Part-ML Appendix I guidance", "—", "September 2025"]],
    parts=[["V2-1140-05", "SN 41290", "EASA Form 1 ref. 88213"],
           ["Tost 3.00-4 brake shoe set", "B-2291", "EASA Form 1 ref. 88477"],
           ["6005-2RS", "B-90114", "EASA Form 1 ref. 88478"],
           ["Bush 12x16x20", "B-90115", "EASA Form 1 ref. 88478"],
           ["Panasonic LC-R127R2PG", "SN 7741902", "EASA Form 1 ref. 89001"],
           ["Canopy lock pin 4.0", "B-11274", "EASA Form 1 ref. 89114"],
           ["Gel coat, white, 2.5 kg", "Batch 2025-114", "Conformity cert. 2025-114"],
           ["Epoxy laminating resin L285", "Batch 4471", "Conformity cert. 4471"],
           ["Hardener 285", "Batch 4471", "Conformity cert. 4471"],
           ["Glass fabric 92110", "Batch 8871", "Conformity cert. 8871"]],
    limitations=("Maintenance could not be completed in full. The following items remain "
                 "outstanding and are deferred: (1) the transponder altitude encoder is due for "
                 "recalibration within 30 days; (2) the right wing tip wheel bearing is worn but "
                 "within limits and is to be replaced at the next inspection. The aircraft is "
                 "released to service subject to these limitations. Operation under IFR is not "
                 "permitted until item (1) is closed."),
    issuer="F. Example", licence="NL.66.00000", issued="27 February 2026",
    personnel=[["J. de Vries", "NL.66.11111", "Assisted"],
               ["M. Jansen", "—", "Independent inspection"],
               ["P. Willemsen", "NL.66.22222", "Assisted"],
               ["A. Bakker", "—", "Assisted"],
               ["S. Groen", "NL.66.33333", "Independent inspection"],
               ["H. Vermeulen", "—", "Assisted"]],
    photos=[[f"{p}-…-{s}.jpg", h, d] for p, s, h, d in [
        ("c1a0", "77b2", "4a19c0ff2b7d8e01", "8 November 2025 09:41"),
        ("d2b1", "18c3", "b7710e4c9a2f3d55", "8 November 2025 14:02"),
        ("e3c2", "29d4", "2f0c8a91bd47e6a3", "22 November 2025 11:15"),
        ("f4d3", "3ae5", "91de20b7c4a80f16", "6 December 2025 10:33"),
        ("a5e4", "4bf6", "0c47a9e13f8b25dd", "19 December 2025 15:57"),
        ("b6f5", "5c07", "7e13cd88a04f9b62", "10 January 2026 08:24"),
        ("c706", "6d18", "38ba07f2c91e4d70", "24 January 2026 13:46"),
        ("d817", "7e29", "e502419dbc7a3f88", "7 February 2026 09:09"),
        ("e928", "8f3a", "5adc81093e2b7f14", "21 February 2026 16:31"),
        ("fa39", "904b", "c67b3e05a1d29f4e", "27 February 2026 12:02")]],
)

n1 = render("/mnt/user-data/outputs/crs/sample-crs.pdf", "NL66-2026-0007", simple)
n2 = render("/mnt/user-data/outputs/crs/sample-crs-overflow.pdf", "NL66-2026-0011", overflow)
print(f"simple: {n1} page(s)   overflow: {n2} page(s)")
