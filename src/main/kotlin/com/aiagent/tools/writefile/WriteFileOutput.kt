package com.aiagent.tools.writefile

import com.aiagent.ToolDoc

data class WriteFileOutput(
    @ToolDoc("Indicator of success") val success: Boolean,
    @ToolDoc("Error message in case of an error") val error: String?,
) {}