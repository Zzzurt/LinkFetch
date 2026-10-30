package com.linkfetch.app.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun `newer patch version is detected`() {
        assertTrue(isVersionNewer("1.8.4", "1.8.3"))
    }

    @Test
    fun `newer minor and major versions are detected`() {
        assertTrue(isVersionNewer("1.9.0", "1.8.9"))
        assertTrue(isVersionNewer("2.0.0", "1.99.99"))
    }

    @Test
    fun `same or older version is not newer`() {
        assertFalse(isVersionNewer("1.8.3", "1.8.3"))
        assertFalse(isVersionNewer("1.8.2", "1.8.3"))
        assertFalse(isVersionNewer("1.8", "1.8.0"))
    }

    @Test
    fun `v prefix and prerelease suffix are ignored`() {
        assertTrue(isVersionNewer("v1.9.0-beta.1", "1.8.3"))
        assertFalse(isVersionNewer("v1.8.3", "1.8.3"))
    }

    @Test
    fun `missing segments count as zero`() {
        assertFalse(isVersionNewer("1.9", "1.9.0"))
        assertTrue(isVersionNewer("1.9.1", "1.9"))
    }
}
