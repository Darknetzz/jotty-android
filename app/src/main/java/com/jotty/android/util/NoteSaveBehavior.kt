package com.jotty.android.util

private val RAW_HTML_TAG_PATTERN =
    Regex(
        """(?is)<\s*(img|table|figure|picture|tbody|thead|tr|td|th|div|span|font|p)\b""",
    )

/** True when note body still contains HTML tags that the display pipeline would convert for viewing. */
fun noteContentContainsRawHtml(content: String): Boolean = RAW_HTML_TAG_PATTERN.containsMatchIn(content)
