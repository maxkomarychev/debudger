package com.aiagent

import com.aiagent.com.aiagent.tools.AiFunctionInput
import com.aiagent.com.aiagent.tools.ToolDescription

data class WriteFileInput(
    @ToolDescription("Name of the file to write") val path: String,
    @ToolDescription("Content of the file") val content: String,
) : AiFunctionInput