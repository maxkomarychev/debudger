package com.aiagent

import com.aiagent.com.aiagent.tools.AiFunctionInput
import com.aiagent.com.aiagent.tools.ToolDescription

data class ReadFileInput(
    @ToolDescription("Path to the file to read") val path: String,
) : AiFunctionInput