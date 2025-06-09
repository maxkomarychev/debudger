package com.aiagent.com.aiagent.tools.writefile

import com.aiagent.ReadFileInput
import com.aiagent.WriteFileInput
import com.aiagent.com.aiagent.tools.AiFunction
import com.aiagent.com.aiagent.tools.AiFunctionOutput
import com.aiagent.com.aiagent.tools.ToolDescription
import com.aiagent.com.aiagent.tools.ToolName
import com.aiagent.tools.writefile.WriteFileOutput
import com.aiagent.utils.createInstanceFromMapWithJackson
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File

@ToolName("write_file")
@ToolDescription("Write text to a file.")
class WriteFileFunction : AiFunction {
    override val inputType = WriteFileInput::class
    override val outputType = WriteFileOutput::class

    override suspend fun execute(input: Map<String, Any>, terminal: Terminal): AiFunctionOutput {
        val typedInput = createInstanceFromMapWithJackson(WriteFileInput::class, input)
        return try {
            terminal.println(TextColors.gray("Writing file ${typedInput.path}"))
            File(typedInput.path).writeText(typedInput.content)
            WriteFileOutput(success = true, error = null)
        } catch (e: Exception) {
            val errorMessage = "Error writing to ${typedInput.path}: ${e.message}"
            terminal.println(TextStyles.dim(TextColors.red(errorMessage)))
            WriteFileOutput(success = false, error = errorMessage)
        }
    }

    override suspend fun prompt(input: Map<String, Any>): String {
        val typedInput = createInstanceFromMapWithJackson(WriteFileInput::class, input)
        return "Write file to ${typedInput.path} with content: ${typedInput.content}."
    }
}