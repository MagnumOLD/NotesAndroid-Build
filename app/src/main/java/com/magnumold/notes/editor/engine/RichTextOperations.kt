package com.magnumold.notes.editor.engine

import com.magnumold.notes.editor.model.InlineSpan
import com.magnumold.notes.editor.model.InlineStyle
import com.magnumold.notes.editor.model.RichText

internal data class TextSelection(
    val anchor: Int,
    val focus: Int
) {
    init {
        require(anchor >= 0 && focus >= 0) { "Selection offsets must be non-negative" }
    }

    val start: Int get() = minOf(anchor, focus)
    val end: Int get() = maxOf(anchor, focus)
    val isCollapsed: Boolean get() = anchor == focus
}

internal data class TextRange(
    val start: Int,
    val end: Int
) {
    init {
        require(start >= 0 && end >= start) { "Invalid text range" }
    }

    val length: Int get() = end - start
}

internal fun TextSelection.asRange(): TextRange = TextRange(start, end)

internal object RichTextOperations {
    fun replaceRange(value: RichText, range: TextRange, replacement: String): RichText {
        require(range.end <= value.text.length) { "Replacement range exceeds text length" }
        if (range.length == 0 && replacement.isEmpty()) return value

        val delta = replacement.length - range.length
        val spans = value.spans.mapNotNull { span ->
            when {
                // Insertion inside a formatted run inherits that run's formatting.
                range.length == 0 && span.start < range.start && span.end > range.start ->
                    span.copy(end = span.end + replacement.length)
                span.end <= range.start -> span
                span.start >= range.end -> span.copy(
                    start = span.start + delta,
                    end = span.end + delta
                )
                else -> {
                    val prefix = if (span.start < range.start) {
                        span.copy(end = range.start)
                    } else null
                    val suffix = if (span.end > range.end) {
                        span.copy(
                            start = range.start + replacement.length,
                            end = span.end + delta
                        )
                    } else null
                    listOfNotNull(prefix, suffix).takeIf { it.isNotEmpty() }
                        ?.let { pieces -> pieces }
                        ?.let { pieces -> return@mapNotNull pieces.first() }
                    null
                }
            }
        }.toMutableList()

        // A replaced span can split into a prefix and suffix. Add the suffixes
        // separately so formatting outside the edited range is retained.
        value.spans.forEach { span ->
            if (range.length > 0 && span.start < range.start && span.end > range.end) {
                spans += span.copy(
                    start = range.start + replacement.length,
                    end = span.end + delta
                )
            }
        }

        return RichText(
            text = value.text.replaceRange(range.start, range.end, replacement),
            spans = normalize(spans)
        )
    }

    fun setStyle(
        value: RichText,
        range: TextRange,
        style: InlineStyle,
        enabled: Boolean
    ): RichText {
        require(range.end <= value.text.length) { "Formatting range exceeds text length" }
        if (range.length == 0) return value

        val boundaries = sortedSetOf(0, value.text.length, range.start, range.end)
        value.spans.forEach { span ->
            boundaries += span.start
            boundaries += span.end
        }
        val points = boundaries.toList()
        val result = mutableListOf<InlineSpan>()

        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            if (start == end) continue

            val covering = value.spans.filter { it.start <= start && it.end >= end }
            val styles = covering.flatMap { it.styles }.toMutableSet()
            val foreground = covering.lastNotNullOfOrNull { it.foregroundArgb }
            val background = covering.lastNotNullOfOrNull { it.backgroundArgb }
            val link = covering.lastNotNullOfOrNull { it.link }

            if (start >= range.start && end <= range.end) {
                if (enabled) styles += style else styles -= style
            }

            if (styles.isNotEmpty() || foreground != null || background != null || link != null) {
                result += InlineSpan(start, end, styles, foreground, background, link)
            }
        }

        return value.copy(spans = normalize(result))
    }

    private fun normalize(spans: List<InlineSpan>): List<InlineSpan> {
        val sorted = spans.filter { it.end > it.start }.sortedWith(
            compareBy<InlineSpan> { it.start }.thenBy { it.end }
        )
        val result = mutableListOf<InlineSpan>()
        sorted.forEach { span ->
            val previous = result.lastOrNull()
            if (previous != null && previous.end == span.start &&
                previous.styles == span.styles &&
                previous.foregroundArgb == span.foregroundArgb &&
                previous.backgroundArgb == span.backgroundArgb &&
                previous.link == span.link
            ) {
                result[result.lastIndex] = previous.copy(end = span.end)
            } else {
                result += span
            }
        }
        return result
    }
}
