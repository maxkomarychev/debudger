package com.aiagent.com.aiagent.tools.readFile

import com.aiagent.ReadFileInput
import com.aiagent.com.aiagent.tools.AiFunction
import com.aiagent.com.aiagent.tools.AiFunctionOutput
import com.aiagent.com.aiagent.tools.ToolDescription
import com.aiagent.com.aiagent.tools.ToolName
import com.aiagent.tools.writefile.WriteFileOutput
import com.aiagent.utils.createInstanceFromMapWithJackson
import java.io.File

@ToolName("read_file")
@ToolDescription("Read text file entirely or in parts.")
class ReadFileFunction : AiFunction {
    override val inputType = ReadFileInput::class
    override val outputType = ReadFileOutput::class

    override suspend fun execute(input: Map<String, Any>): AiFunctionOutput {
        val typedInput = createInstanceFromMapWithJackson(ReadFileInput::class, input)
        return try {
            val content = File(typedInput.path).readText()
            ReadFileOutput(content)
        } catch (e: Exception) {
            val errorMessage = "Error writing to ${typedInput.path}: ${e.message}"
            ReadFileOutput(success = false, error = errorMessage)
        }
    }

    override suspend fun prompt(input: Map<String, Any>): String {
        val typedInput = createInstanceFromMapWithJackson(ReadFileInput::class, input)
        return "Read file ${typedInput.path}."
    }
}