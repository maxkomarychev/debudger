package com.aiagent.tools.shellcommand

import com.aiagent.ToolDoc

data class ShellCommandOutput(
    @ToolDoc("Exit code of the command. 0 means success.") val exitCode: Int,
    @ToolDoc("Standard output of the command") val stdout: String,
    @ToolDoc("Error output of the command") val stderr: String,
)