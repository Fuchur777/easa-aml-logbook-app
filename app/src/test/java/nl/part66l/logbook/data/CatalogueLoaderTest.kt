package nl.part66l.logbook.data

import nl.part66l.logbook.domain.RuleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueLoaderTest {

    // Deliberately small and hand-written rather than the full 102-task seed file,
    // but the same shape: see app/src/main/assets/catalogue/appendix-ii-2026.1.json.
    private val fixture = """
        {
          "catalogueId": "easa-amc-part66-appendix-ii",
          "version": "2026.1",
          "status": "IN_FORCE",
          "source": "Appendix II to AMC to Annex III (Part-66)",
          "sourceRevision": "Easy Access Rules for Continuing Airworthiness, September 2025",
          "displayName": "Appendix II to AMC to Annex III (Part-66)",
          "thresholds": { "L1": 0.5, "L1C": 0.5, "L2": 0.5, "L2C": 0.5, "rule": "AMC 66.A.45(h)" },
          "tasks": [
            {
              "id": "B.GEN.01",
              "table": "B",
              "section": "General activities",
              "sectionCode": "GEN",
              "text": "Placards check or replace",
              "appliesTo": { "L1": true, "L1C": true, "L2": true, "L2C": true },
              "reference": "Appendix II to AMC to Annex III (Part-66), Table B — General activities"
            },
            {
              "id": "B.WFAB.02",
              "table": "B",
              "section": "Wood and fabric structures",
              "sectionCode": "WFAB",
              "text": "Repair local skin damage",
              "appliesTo": { "L1": true, "L1C": false, "L2": true, "L2C": false },
              "reference": "Appendix II to AMC to Annex III (Part-66), Table B — Wood and fabric structures",
              "supersedes": "B.WFAB.01"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses one row per task, tagged with the document's catalogue version`() {
        val rows = CatalogueLoader.parse(fixture)

        assertEquals(2, rows.size)
        assertTrue(rows.all { it.catalogueVersion == "2026.1" })
    }

    @Test
    fun `maps table, section, sectionCode, text and reference through unchanged`() {
        val row = CatalogueLoader.parse(fixture).first { it.id == "B.GEN.01" }

        assertEquals("B", row.table)
        assertEquals("General activities", row.section)
        assertEquals("GEN", row.sectionCode)
        assertEquals("Placards check or replace", row.text)
        assertEquals(
            "Appendix II to AMC to Annex III (Part-66), Table B — General activities",
            row.reference,
        )
    }

    @Test
    fun `maps per-subcategory applicability`() {
        val row = CatalogueLoader.parse(fixture).first { it.id == "B.WFAB.02" }

        assertTrue(row.appliesToL1)
        assertFalse(row.appliesToL1C)
        assertTrue(row.appliesToL2)
        assertFalse(row.appliesToL2C)
    }

    @Test
    fun `maps the document-level status onto every row`() {
        val rows = CatalogueLoader.parse(fixture)

        assertTrue(rows.all { it.status == RuleStatus.IN_FORCE })
    }

    @Test
    fun `supersedes is null when absent and carried through when present`() {
        val rows = CatalogueLoader.parse(fixture)

        assertNull(rows.first { it.id == "B.GEN.01" }.supersedes)
        assertEquals("B.WFAB.01", rows.first { it.id == "B.WFAB.02" }.supersedes)
    }
}
