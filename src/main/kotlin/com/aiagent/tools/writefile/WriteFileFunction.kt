package com.aiagent.com.aiagent.tools.writefile

import com.aiagent.WriteFileInput
import com.aiagent.com.aiagent.tools.AiFunction
import com.aiagent.com.aiagent.tools.AiFunctionOutput
import com.aiagent.com.aiagent.tools.ToolDescription
import com.aiagent.com.aiagent.tools.ToolName
import com.aiagent.tools.writefile.WriteFileOutput
import com.aiagent.tools.writefile.writeFile
import com.aiagent.utils.createInstanceFromMapWithJackson

@ToolName("write_file")
@ToolDescription("Write text to a file.")
class WriteFileFunction : AiFunction {
    override val inputType = WriteFileInput::class
    override val outputType = WriteFileOutput::class

    override suspend fun execute(input: Map<String, Any>): AiFunctionOutput {
        val typedInput = createInstanceFromMapWithJackson(WriteFileInput::class, input)
        return writeFile(typedInput)
    }

    override suspend fun prompt(input: Map<String, Any>): String {
        val typedInput = createInstanceFromMapWithJackson(WriteFileInput::class, input)
        return "Write file to ${typedInput.fileName} with content: ${typedInput.content}."
    }
}