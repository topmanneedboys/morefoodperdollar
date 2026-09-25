package com.valuepilot.app

import java.io.File

/**
 * Reads source-boundary fixtures with a platform-independent newline shape.
 *
 * The assertions still inspect the complete file contents; only CRLF/CR is
 * normalized so the same structural contract is tested on Windows and Linux.
 */
internal fun File.readSourceText(): String =
    readText()
        .replace("\r\n", "\n")
        .replace('\r', '\n')
