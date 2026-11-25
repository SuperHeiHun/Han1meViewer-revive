package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.blocking.BlockKind
import com.yenaly.han1meviewer.logic.blocking.BlockRules
import com.yenaly.han1meviewer.logic.blocking.VideoBlockMetadata
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class BlockRulesTest {
    @Test fun emptyRulesDoNotBlock() {
        assertTrue(BlockRules().isEmpty)
        assertFalse(BlockRules().blocks(VideoBlockMetadata(setOf("tag"), "author")))
    }

    @Test fun matchingTagBlocksRegardlessOfAuthor() {
        assertTrue(BlockRules(tags = setOf("tag")).blocks(VideoBlockMetadata(setOf("other", "tag"), "ok")))
    }

    @Test fun matchingAuthorBlocksRegardlessOfTags() {
        assertTrue(BlockRules(authors = setOf("Author")).blocks(VideoBlockMetadata(emptySet(), "author")))
    }

    @Test fun exactNamesDoNotMatchSubstrings() {
        val rules = BlockRules(setOf("tag"), setOf("studio"))
        assertFalse(rules.blocks(VideoBlockMetadata(setOf("tagged"), "studio plus")))
    }

    @Test fun whitespaceWidthCaseAndTagPrefixAreNormalized() {
        val rules = BlockRules(setOf(" #ＴＡＧ "), setOf(" Ａｕｔｈｏｒ "))
        assertTrue(rules.contains(BlockKind.TAG, "tag"))
        assertTrue(rules.contains(BlockKind.AUTHOR, "author"))
    }

    @Test fun normalizationIsNotAffectedByTurkishLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertTrue(BlockRules(authors = setOf("STUDIO")).contains(BlockKind.AUTHOR, "studio"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun tagAndAuthorRulesAreIndependent() {
        val rules = BlockRules(tags = setOf("same"))
        assertFalse(rules.blocks(VideoBlockMetadata(emptySet(), "same")))
    }

    @Test fun removingTagLeavesAuthorIntact() {
        val rules = BlockRules(setOf("same"), setOf("same")).updated(BlockKind.TAG, "same", false)
        assertTrue(rules.tags.isEmpty())
        assertEquals(setOf("same"), rules.authors)
    }

    @Test fun equivalentNamesAreDeduplicated() {
        val rules = BlockRules().updated(BlockKind.TAG, " #Tag ", true)
            .updated(BlockKind.TAG, "ＴＡＧ", true)
        assertEquals(1, rules.tags.size)
        assertTrue(rules.contains(BlockKind.TAG, "tag"))
    }

    @Test fun removingOneRuleDoesNotOverrideOtherMatchingRules() {
        val rules = BlockRules(setOf("tag"), setOf("author")).updated(BlockKind.TAG, "tag", false)
        assertTrue(rules.blocks(VideoBlockMetadata(setOf("tag"), "author")))
    }

    @Test fun missingAuthorDoesNotCrash() {
        assertFalse(BlockRules(authors = setOf("author")).blocks(VideoBlockMetadata(emptySet(), null)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyTagIsRejected() { BlockRules().updated(BlockKind.TAG, " # ", true) }
}
