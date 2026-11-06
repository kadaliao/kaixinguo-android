package com.kadaliao.kaixinguo.model

import dev.openhongguo.Season

private const val NUMERALS = "[0-9一二两三四五六七八九十百零〇]+"

/** Reads 3, 三, 十一 or 二十三. */
fun chineseNumber(s: String): Int {
    s.toIntOrNull()?.let { return it }
    val digits = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3,
        '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )
    var n = 0
    var cur = 0
    for (r in s) {
        when (r) {
            '十' -> {
                n += maxOf(cur, 1) * 10
                cur = 0
            }
            '百' -> {
                n += maxOf(cur, 1) * 100
                cur = 0
            }
            else -> cur = digits[r] ?: 0
        }
    }
    return n + cur
}

/** The groups of the last match of `re` in `s`. */
private fun lastMatch(re: Regex, s: String): List<String>? = re.findAll(s).lastOrNull()?.groupValues

/**
 * Gives each season its number and puts them in order. The server sends
 * the numbers to some devices and not to others; without them, the titles
 * tell (持械入宋：第三季), and the one title without a number is the first
 * season (浅浅归聿, 浅浅归聿第二季). Failing that, the seasons keep the
 * server's order and are numbered by it.
 */
fun numberSeasons(input: List<Season>): List<Season> {
    val out = input.toMutableList()
    // A season's number in its title: 第三季, 第 3 部.
    val mark = Regex("第\\s*($NUMERALS)\\s*[季部]")
    for (i in out.indices) {
        if (out[i].index > 0) continue
        val m = lastMatch(mark, out[i].title)
        out[i] = out[i].copy(index = if (m != null) chineseNumber(m[1]) else 0)
    }
    val used = HashSet<Int>()
    var missing = -1
    var distinct = true
    for ((i, se) in out.withIndex()) {
        if (se.index <= 0 && missing == -1) {
            missing = i
        } else if (se.index <= 0) {
            distinct = false // more than one without a number
        } else if (se.index in used) {
            distinct = false
        }
        used += se.index
    }
    if (missing >= 0 && distinct && 1 !in used) {
        out[missing] = out[missing].copy(index = 1)
    }
    val seen = HashSet<Int>()
    if (out.all { it.index > 0 && seen.add(it.index) }) {
        return out.withIndex().sortedWith(compareBy({ it.value.index }, { it.index })).map { it.value }
    }
    return out.mapIndexed { i, se -> se.copy(index = i + 1) }
}

/**
 * Puts the seasons of a series in order, where its first season is, and
 * leaves the other cards where they are.
 */
fun orderSeasons(cards: List<Card>): List<Card> {
    // A title that ends with its season, as 持械入宋：第三季.
    val pattern = Regex("^(.*?)第($NUMERALS)季$")
    val first = HashMap<String, Int>()
    data class Key(val group: Int, val season: Int, val at: Int)
    val keys = cards.mapIndexed { i, k ->
        val m = lastMatch(pattern, k.title) ?: return@mapIndexed Key(i, 0, i)
        val g = first.getOrPut(m[1]) { i }
        Key(g, chineseNumber(m[2]), i)
    }
    return keys.sortedWith(compareBy({ it.group }, { it.season }, { it.at })).map { cards[it.at] }
}
