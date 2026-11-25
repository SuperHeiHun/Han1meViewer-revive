package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.util.isNewerVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {
    @Test
    fun stableVersionUpgradesLegacyRelease() {
        assertTrue(isNewerVersion("v1.0.0", "0.16.0-ci+261122150", 261122150))
    }

    @Test
    fun sameStableVersionDoesNotPromptAgain() {
        assertFalse(isNewerVersion("v1.0.0", "1.0.0", 261125100))
    }

    @Test
    fun newerPatchIsDetected() {
        assertTrue(isNewerVersion("v1.0.1", "1.0.0", 261125100))
    }

    @Test
    fun newerMinorIsDetectedNumerically() {
        assertTrue(isNewerVersion("v1.10.0", "1.9.9", 261125100))
    }

    @Test
    fun newerMajorIsDetected() {
        assertTrue(isNewerVersion("v2.0.0", "1.99.99", 261125100))
    }

    @Test
    fun olderSemanticVersionCannotDowngradeEvenWithHigherBuildCode() {
        assertFalse(isNewerVersion("v0.16.0+261126100", "1.0.0", 261125100))
    }

    @Test
    fun legacyNewerBuildIsDetected() {
        assertTrue(isNewerVersion("v0.16.0+261122151", "0.16.0-ci+261122150", 261122150))
    }

    @Test
    fun legacySameBuildDoesNotPromptAgain() {
        assertFalse(isNewerVersion("v0.16.0+261122150", "0.16.0-ci+261122150", 261122150))
    }

    @Test
    fun legacyOlderBuildDoesNotPromptAgain() {
        assertFalse(isNewerVersion("v0.16.0+261122149", "0.16.0-release+261122150", 261122150))
    }

    @Test
    fun stableVersionUpgradesNewDebugBuild() {
        assertTrue(isNewerVersion("v1.0.0", "1.0.0-debug", 1))
    }

    @Test
    fun stableVersionUpgradesLegacyDebugBuild() {
        assertTrue(isNewerVersion("v1.0.0", "debug+1", 1))
    }

    @Test
    fun stableVersionUpgradesReleaseCandidate() {
        assertTrue(isNewerVersion("v1.0.0", "1.0.0-rc.1", 261125100))
    }

    @Test
    fun releaseCandidateDoesNotReplaceStableVersion() {
        assertFalse(isNewerVersion("v1.0.0-rc.2", "1.0.0", 261125100))
    }

    @Test
    fun releaseCandidateNumbersAreComparedNumerically() {
        assertTrue(isNewerVersion("v1.0.0-rc.10", "1.0.0-rc.2", 261125100))
    }

    @Test
    fun numericPrereleaseIdentifierPrecedesAlphabeticIdentifier() {
        assertFalse(isNewerVersion("v1.0.0-1", "1.0.0-alpha", 261125100))
    }

    @Test
    fun tagsWithoutVPrefixAreSupported() {
        assertTrue(isNewerVersion("1.0.1", "1.0.0", 261125100))
    }

    @Test
    fun nonNumericBuildMetadataDoesNotChangeStablePrecedence() {
        assertFalse(isNewerVersion("v1.0.0+build.42", "1.0.0", 261125100))
    }

    @Test
    fun invalidReleaseTagDoesNotProduceFalseUpdate() {
        assertFalse(isNewerVersion("not-a-version", "1.0.0", 261125100))
    }

    @Test
    fun oversizedVersionNumberDoesNotCrashComparison() {
        assertFalse(isNewerVersion("v999999999999999999999999.0.0", "1.0.0", 261125100))
    }

    @Test
    fun unknownInstalledVersionDoesNotAssumeAnUpgrade() {
        assertFalse(isNewerVersion("v1.0.0", "unknown", 261125100))
    }
}
