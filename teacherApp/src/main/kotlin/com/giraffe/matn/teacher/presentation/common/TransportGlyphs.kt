package com.giraffe.matn.teacher.presentation.common

/**
 * Transport glyphs, chosen so the platform renders them as **text** in the current theme colour.
 *
 * `⏸` (U+23F8) carries `Emoji_Presentation=Yes`, so the font stack substitutes a colour emoji: the
 * pause control came out as a blue box that ignored the theme entirely, while the neighbouring play
 * triangle — `▶` (U+25B6), which is emoji-*capable* but defaults to text presentation — looked
 * right. The distinction is the property, not the block, so pick glyphs that default to text rather
 * than trying to talk a colour emoji out of its colour.
 */
internal const val GLYPH_PLAY = "▶"

/** Two `❚` (U+275A) — no emoji presentation, unlike `⏸`. */
internal const val GLYPH_PAUSE = "❚❚"

/** `◼` (U+25FC) is emoji-capable but text by default, so it renders in the theme colour. */
internal const val GLYPH_STOP = "◼"
