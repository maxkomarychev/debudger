package com.aiagent.tools.shellcommand

suspend fun shellCommand(input: ShellCommandInput): ShellCommandOutput {
    val process = Runtime.getRuntime().exec(input.command)
    val exitCode = process.waitFor()
    val stdout = process.inputStream.bufferedReader().readText()
    val stderr = process.errorStream.bufferedReader().readText()
    return ShellCommandOutput(exitCode, stdout, stderr)
}