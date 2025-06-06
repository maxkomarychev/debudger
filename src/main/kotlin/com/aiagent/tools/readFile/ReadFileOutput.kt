package com.aiagent.com.aiagent.tools.readFile

import com.aiagent.com.aiagent.tools.AiFunctionOutput
import com.aiagent.com.aiagent.tools.ToolDescription

data class ReadFileOutput(
    @ToolDescription("Lies read from a file") val content: String? = null,
    @ToolDescription("Error message in case of an error") val error: String? = null,
    @ToolDescription("Indicator of success") val success: Boolean = true,
) : AiFunctionOutput