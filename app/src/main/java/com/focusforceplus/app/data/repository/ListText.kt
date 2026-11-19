package com.focusforceplus.app.data.repository

/**
 * Reads what was typed into a list.
 *
 * An amount becomes the item's quantity: "2 milk", "2x milk", "milk x2",
 * "500 g flour", "flour 500 g". The amount is shown next to the item, and the
 * item itself stays "milk", so it is still recognised next time. A bare number at
 * the end is left alone ("Route 66", "Size 42"): only a number at the start, or
 * one with a unit or an "x", counts as an amount.
 */
object ListText {

    data class Parsed(val text: String, val quantity: String?)

    private const val UNITS =
        "x|×|g|kg|mg|ml|cl|dl|l|liter|litre|pcs|pc|pack|packs|stk|stück|pck|bund|dose|dosen|fl|flasche|flaschen|tl|el"

    /** "2x milk", "3 x eggs" */
    private val LEADING_TIMES = Regex("""^(\d+)\s*[x×]\s+(\S.*)$""", RegexOption.IGNORE_CASE)

    /** "2x milk" without a space after the x */
    private val LEADING_TIMES_TIGHT = Regex("""^(\d+)[x×](\S.*)$""", RegexOption.IGNORE_CASE)

    /** "2 milk", "500 g flour", "1,5 l milk" */
    private val LEADING = Regex("""^(\d+(?:[.,]\d+)?)\s*($UNITS)?\.?\s+(\S.*)$""", RegexOption.IGNORE_CASE)

    /** "milk x2", "flour 500 g", "milk 1,5 l" */
    private val TRAILING = Regex("""^(\S.*?)\s+(?:([x×])\s*(\d+)|(\d+(?:[.,]\d+)?)\s*($UNITS)\.?)$""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Parsed {
        val t = raw.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return Parsed("", null)

        LEADING_TIMES.matchEntire(t)?.let { m ->
            return Parsed(m.groupValues[2].trim(), "${m.groupValues[1]}x")
        }
        LEADING_TIMES_TIGHT.matchEntire(t)?.let { m ->
            // "7up" has no x, "2xl shirt" would be odd either way; accept only a letter after.
            if (m.groupValues[2].first().isLetter()) return Parsed(m.groupValues[2].trim(), "${m.groupValues[1]}x")
        }
        LEADING.matchEntire(t)?.let { m ->
            val unit = m.groupValues[2]
            val amount = if (unit.isEmpty()) m.groupValues[1] else "${m.groupValues[1]} $unit"
            return Parsed(m.groupValues[3].trim(), amount)
        }
        TRAILING.matchEntire(t)?.let { m ->
            val amount = if (m.groupValues[3].isNotEmpty()) "${m.groupValues[3]}x"
                         else "${m.groupValues[4]} ${m.groupValues[5]}"
            return Parsed(m.groupValues[1].trim(), amount)
        }
        return Parsed(t, null)
    }

    // A comma or semicolon separates items, unless the comma sits between two
    // digits: "1,5 l" is one and a half litres, not two items.
    private val SEPARATOR = Regex("""[;\n\r]+|(?<!\d),|,(?!\d)""")

    /** "- milk", "* milk", "• milk", "1. milk", "[ ] milk": what a pasted list brings along. */
    private val LIST_MARK = Regex("""^(?:[-*•–·]\s+|\d{1,2}[.)]\s+|\[[ xX]?]\s*)+""")

    /**
     * Splits what was typed or pasted into single items: one per line, or
     * separated by commas or semicolons. The marks of a pasted list are dropped.
     */
    fun splitItems(raw: String): List<String> =
        raw.split(SEPARATOR)
            .map { it.trim().replace(LIST_MARK, "").trim() }
            .filter { it.isNotEmpty() }

    /**
     * How an item is written on the list: "milk" becomes "Milk", so a list typed
     * in a hurry still reads tidily. A word with a capital of its own somewhere
     * ("iPhone cable", "USB stick") is left exactly as it was typed.
     */
    fun tidy(text: String): String {
        val first = text.substringBefore(' ')
        val plain = first.isNotEmpty() && first.first().isLetter() && first.none { it.isUpperCase() }
        return if (plain) text.replaceFirstChar { it.titlecase() } else text
    }

    private val COUNT = Regex("""^(\d{1,4})\s*([x×])?$""", RegexOption.IGNORE_CASE)

    /**
     * How many, when the quantity is a plain count: nothing means one, "3" and
     * "3x" mean three. Null for an amount with a unit ("500 g"), which plus and
     * minus cannot sensibly change.
     */
    fun countOf(quantity: String?): Int? {
        val q = quantity?.trim().orEmpty()
        if (q.isEmpty()) return 1
        return COUNT.matchEntire(q)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** The quantity for [count], written the way [previous] was ("3x" stays "4x"); one is not written at all. */
    fun withCount(previous: String?, count: Int): String? {
        val n = count.coerceIn(1, 999)
        if (n == 1) return null
        val times = previous?.trim()?.lowercase()?.let { it.endsWith("x") || it.endsWith("×") } == true
        return if (times) "${n}x" else n.toString()
    }
}
