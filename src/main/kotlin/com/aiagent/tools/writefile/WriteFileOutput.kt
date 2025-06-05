package com.aiagent.tools.writefile

import com.aiagent.com.aiagent.tools.AiFunctionOutput
import com.aiagent.com.aiagent.tools.ToolDescription

data class WriteFileOutput(
    @ToolDescription("Indicator of success") val success: Boolean,
    @ToolDescription("Error message in case of an error") val error: String?,
) : AiFunctionOutput