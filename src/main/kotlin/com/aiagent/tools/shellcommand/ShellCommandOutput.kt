package com.aiagent.tools.shellcommand

import com.aiagent.com.aiagent.tools.AiFunctionOutput
import com.aiagent.com.aiagent.tools.ToolDescription

data class ShellCommandOutput(
    @ToolDescription("Exit code of the command. 0 means success. If non-zero - investigate stdout and stderr.") val exitCode: Int,
    @ToolDescription("Standard output of the command") val stdout: String,
    @ToolDescription("Error output of the command") val stderr: String,
) : AiFunctionOutput