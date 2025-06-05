package com.aiagent.tools.shellcommand

suspend fun shellCommand(input: ShellCommandInput): ShellCommandOutput {
    val process = Runtime.getRuntime().exec(input.command)
    val exitCode = process.waitFor()
    val stdout = process.inputStream.bufferedReader().readText()
    println("""
        STDOUT
        $stdout
    """.trimIndent())
    val stderr = process.errorStream.bufferedReader().readText()
    println("""
        STDERR
        $stderr
    """.trimIndent())
    return ShellCommandOutput(exitCode, stdout, stderr)
}