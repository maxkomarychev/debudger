package com.aiagent.com.aiagent.tools.shellcommand

import com.aiagent.com.aiagent.tools.AiFunction
import com.aiagent.com.aiagent.tools.AiFunctionOutput
import com.aiagent.com.aiagent.tools.ToolDescription
import com.aiagent.com.aiagent.tools.ToolName
import com.aiagent.tools.shellcommand.ShellCommandInput
import com.aiagent.tools.shellcommand.ShellCommandOutput
import com.aiagent.utils.createInstanceFromMapWithJackson
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal

@ToolName("shell_command")
@ToolDescription(
    """
Execute any arbitrary command and get back status code, stdout and stderr.
IMPORTANT: Note about `cd`. The current directory is not preserved between commands.
If you need to perform various commands in a certain directory as part of different function calls
you must have `cd <specific directory>` in each command.
"""
)
class ShellCommandFunction : AiFunction {
    override val inputType = ShellCommandInput::class
    override val outputType = ShellCommandOutput::class
    override suspend fun execute(input: Map<String, Any>, terminal: Terminal): AiFunctionOutput {
        val typedInput = createInstanceFromMapWithJackson(ShellCommandInput::class, input)
        terminal.println(TextColors.gray("Executing command: ${typedInput.command}"))
        val process = ProcessBuilder("sh", "-c", typedInput.command).start()
        val exitCode = process.waitFor()
        terminal.println(TextColors.gray("EXIT CODE: $exitCode"))
        val stdout = process.inputStream.bufferedReader().readText()
        terminal.println(
            TextColors.gray(
                """
        |STDOUT
        |$stdout
    """.trimMargin("|")
            )
        )
        val stderr = process.errorStream.bufferedReader().readText()
        terminal.println(
            TextColors.gray(
                """
        |STDERR
        |$stderr
    """.trimMargin("|")
            )
        )
        return ShellCommandOutput(exitCode, stdout, stderr)
    }

    override suspend fun prompt(input: Map<String, Any>): String {
        val typedInput = createInstanceFromMapWithJackson(ShellCommandInput::class, input)
        return "Execute shell command ${typedInput.command}."
    }

}