package com.aiagent

import ai.koog.agents.core.tools.*
import ai.koog.agents.ext.agent.simpleSingleRunAgent
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
data class ReadDirInput(val path: String): Tool.Args
data class ReadDirOutput(val files: List<String>): ToolResult {
    override fun toStringDefault(): String {
        return files.joinToString("\n")
    }
}

class ReadDirTool : Tool<ReadDirInput, ReadDirOutput>() {

    override val descriptor = ToolDescriptor(
        name = "readDir", description = "List files in the specified directory", requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "path",
                description = "Path to the directory",
                type = ToolParameterType.String,
            )
        )
    )
    override var argsSerializer = ReadDirInput.serializer()

    override suspend fun execute(args: ReadDirInput): ReadDirOutput {
        return ReadDirOutput(listOf("hello world"))
    }
}

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() = runBlocking {
    val apiKey = System.getenv("GEMINI_API_KEY")

    val tools = ToolRegistry {
        tool(ReadDirTool())
    }

    val agent = simpleSingleRunAgent(
        executor = simpleGoogleAIExecutor(apiKey),
        systemPrompt = "You are a helpful assistant. Answer user questions concisely.",
        llmModel = GoogleModels.Gemini2_5FlashPreview0417,
        toolRegistry = tools,
    )

    val result = agent.runAndGetResult("List files in ~/max/dev")
    println(result)
}