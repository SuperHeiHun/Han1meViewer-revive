package com.yenaly.han1meviewer.util

private val versionPattern = Regex(
    """^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+([0-9A-Za-z.-]+))?$"""
)

private data class ParsedVersion(val numbers: List<Long>, val prerelease: String?)

private fun parseVersion(name: String): ParsedVersion? {
    val match = versionPattern.matchEntire(name.trim()) ?: return null
    val numbers = (1..3).map { match.groupValues[it].toLongOrNull() ?: return null }
    return ParsedVersion(numbers, match.groupValues[4].ifEmpty { null })
}

/** Compare stable release tags while retaining the previous numeric-build tag format. */
internal fun isNewerVersion(
    latestName: String,
    currentName: String,
    currentCode: Int,
): Boolean {
    val latest = parseVersion(latestName) ?: return false
    val current = parseVersion(currentName)
    if (current != null) {
        for (index in 0..2) {
            val order = latest.numbers[index].compareTo(current.numbers[index])
            if (order != 0) return order > 0
        }
    }

    // Legacy releases use tags such as v0.16.0+261122150, whereas the installed
    // app may have a -ci/-release suffix. Their Android versionCode is authoritative.
    val legacyCode = latestName.trim().substringAfter('+', "")
        .takeWhile(Char::isDigit).toLongOrNull()
    if (legacyCode != null) return legacyCode > currentCode

    // Pre-1.0 debug builds did not contain a semantic version at all.
    if (current == null) return currentName.startsWith("debug+")
    return comparePrerelease(latest.prerelease, current.prerelease) > 0
}

private fun comparePrerelease(latest: String?, current: String?): Int {
    if (latest == current) return 0
    if (latest == null) return 1
    if (current == null) return -1
    val left = latest.split('.')
    val right = current.split('.')
    for (index in 0 until minOf(left.size, right.size)) {
        val leftNumber = left[index].toLongOrNull()
        val rightNumber = right[index].toLongOrNull()
        val order = when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> left[index].compareTo(right[index])
        }
        if (order != 0) return order
    }
    return left.size.compareTo(right.size)
}
