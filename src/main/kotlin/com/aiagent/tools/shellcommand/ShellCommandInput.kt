package com.aiagent.tools.shellcommand

import com.aiagent.ToolDoc

data class ShellCommandInput(
    @ToolDoc("Command to perform in shell") val command: String
)