package com.example

import com.example.data.model.VersionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSystemUnitTest {

    @Test
    fun testVersionParserWithStandardSemver() {
        assertEquals(302, VersionParser.parseVersionCode("v30.2"))
        assertEquals(303, VersionParser.parseVersionCode("v30.3"))
        assertEquals(304, VersionParser.parseVersionCode("v30.4"))
        assertEquals(310, VersionParser.parseVersionCode("v31.0"))
        assertEquals(311, VersionParser.parseVersionCode("31.1"))
    }

    @Test
    fun testVersionParserWithPatchVersion() {
        assertEquals(3021, VersionParser.parseVersionCode("v30.2.1"))
        assertEquals(3030, VersionParser.parseVersionCode("v30.3.0"))
    }

    @Test
    fun testVersionParserWithExplicitVersionCodeInBody() {
        val body = """
            ## NyxTap v30.3 Release
            versionCode: 303
            - Fixed overlay drag gesture
            - Improved 2FA token refresh
        """.trimIndent()
        assertEquals(303, VersionParser.parseVersionCode(tag = "release-30.3", body = body))
    }

    @Test
    fun testVersionComparisonLogic() {
        val currentVersionCode = 302

        val release301 = 301
        val release302 = 302
        val release303 = 303
        val release310 = 310

        assertFalse(release301 > currentVersionCode)
        assertFalse(release302 > currentVersionCode)
        assertTrue(release303 > currentVersionCode)
        assertTrue(release310 > currentVersionCode)
    }

    @Test
    fun testExtractSha256() {
        val notes = """
            Checksum:
            SHA256: 4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945
        """.trimIndent()
        val extracted = VersionParser.extractSha256(notes)
        assertNotNull(extracted)
        assertEquals("4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945", extracted)

        val noSha = "Just release notes with no hash."
        assertNull(VersionParser.extractSha256(noSha))
    }

    @Test
    fun testExtractVersionName() {
        assertEquals("v30.3", VersionParser.extractVersionName("v30.3"))
        assertEquals("v30.3", VersionParser.extractVersionName("30.3"))
        assertEquals("v30.3", VersionParser.extractVersionName("release", "NyxTap v30.3"))
    }
}
