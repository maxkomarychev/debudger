package com.aiagent.tools.shellcommand

import com.aiagent.com.aiagent.tools.AiFunctionInput
import com.aiagent.com.aiagent.tools.ToolDescription

data class ShellCommandInput(
    @ToolDescription("Command to perform in shell") val command: String
) : AiFunctionInput