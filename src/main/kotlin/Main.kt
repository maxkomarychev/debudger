package com.aiagent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.genai.Client
import com.google.genai.types.*
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass

val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

fun <T : Any> createInstanceFromMapWithJackson(dataClass: KClass<T>, map: Map<String, Any?>): T {
    // Jackson's convertValue is very powerful for this.
    return objectMapper.convertValue(map, dataClass.java) // Uses Java Class here
}


data class ShellCommandInput(
    @ToolDoc("Command to perform in shell") val command: String
)

data class ShellCommandOutput(
    @ToolDoc("Exit code of the command. 0 means success.") val exitCode: Int,
    @ToolDoc("Standard output of the command") val stdout: String,
    @ToolDoc("Error output of the command") val stderr: String,
)

suspend fun shellCommand(input: ShellCommandInput): ShellCommandOutput {
    val process = Runtime.getRuntime().exec(input.command)
    val exitCode = process.waitFor()
    val stdout = process.inputStream.bufferedReader().readText()
    val stderr = process.errorStream.bufferedReader().readText()
    return ShellCommandOutput(exitCode, stdout, stderr)
}

suspend fun writeFile(input: WriteFileInput): WriteFileOutput {
    return try {
        java.io.File(input.fileName).writeText(input.content)
        WriteFileOutput(success = true, error = null)
    } catch (e: Exception) {
        val errorMessage = "Error writing to ${input.fileName}: ${e.message}"
        WriteFileOutput(success = false, error = errorMessage)
    }
}

val shellCommandDeclaration = FunctionDeclaration.builder().name("shell_command")
    .description("Execute arbitrary command in shell and get response back").parameters(
        generateSchemaFromDataClass(ShellCommandInput::class)
    ).response(
        generateSchemaFromDataClass(ShellCommandOutput::class)

    ).build()


data class WriteFileInput(
    @ToolDoc("Name of the file to write") val fileName: String,
    @ToolDoc("Content of the file") val content: String,
)

data class WriteFileOutput(
    @ToolDoc("Indicator of success") val success: Boolean,
    @ToolDoc("Error message in case of an error") val error: String?,
) {}

val writeFileDeclaration =
    FunctionDeclaration.builder().name("write_file").description("Write content to a specified file.")
        .parameters(generateSchemaFromDataClass(WriteFileInput::class)).response(
            generateSchemaFromDataClass(WriteFileOutput::class)
        ).build()


suspend fun main() {
    val apiKey = System.getenv("GEMINI_KEY")
//    val modelId = "gemini-2.0-flash"
    val modelId = "gemini-2.5-flash-preview-05-20"
//    val modelId = "gemini-2.5-pro-preview-05-06"
    val client = Client.builder().apiKey(apiKey).httpOptions(HttpOptions.builder().apiVersion("v1beta").build()).build()
    val currentDir = System.getProperty("user.dir")
    val firstPrompt = """
        You are an agent which helps engineers solve technical problems.
        You may be asked to:
            - do a research
            - fix a bug
            - edit code
            - etc

        If user is asking to do something first consider using shell_command with any of known CLI tools.
        
        Your goal is to solve my technical problem. Use any available tools at your discretion whenever you believe it will help you achieve this goal. Don't ask for permission before using a tool; if you think it's necessary, use it.
        
        Your current directory is $currentDir.
        
        When answering a user read entire history of the conversation.
        
        Good luck!
    """.trimIndent()
    val first = Content.builder().role("user").parts(listOf(Part.builder().text(firstPrompt).build())).build()
    val history = mutableListOf(first)
    val tools = listOf(
        Tool.builder().functionDeclarations(
            listOf(
                shellCommandDeclaration,
                writeFileDeclaration,
            )
        ).build(),
    )

    val pendingPrompts = mutableListOf<Content>()
    val config = GenerateContentConfig.builder().tools(tools).build()

    while (true) {

        if (pendingPrompts.isEmpty()) {
            val userInput = readLine()
            if (userInput == "exit") {
                break
            }
            val content = Content.builder().role("user").parts(listOf(Part.builder().text(userInput).build())).build()
            pendingPrompts.add(content)
        }

        val currentPrompt = pendingPrompts.removeFirst()
        history.add(currentPrompt)
        val response = client.models.generateContent(modelId, history, config)
        history.add(response.candidates().get().first().content().get())


        if (response.functionCalls() != null && response.functionCalls()!!.isNotEmpty()) {
            val functionCall = response.functionCalls()!!.first()
            val functionName = functionCall.name().get()
            val args = functionCall.args().get()
            when (functionName) {
                "shell_command" -> {
                    val input = createInstanceFromMapWithJackson(ShellCommandInput::class, args)
                    println("!!! I want to execute a shell command. Do you confirm? ${input.command}")
                    val response = readLine()
                    if (response != "yes") {
                        val content = Content.builder().role("user")
                            .parts(listOf(Part.builder().text("I do not allow running this command").build())).build()
                        pendingPrompts.add(content)
                    } else {
                        val output = shellCommand(input)
                        val partFunctionResponse = Part.builder().functionResponse(
                            FunctionResponse.builder().name(functionName).response(
                                mapOf(
                                    "exitCode" to output.exitCode,
                                    "stdout" to output.stdout,
                                    "stderr" to output.stderr,
                                )
                            ).build()
                        ).build()
                        val contentFunctionResponse =
                            Content.builder().role("user").parts(listOf(partFunctionResponse)).build()
                        pendingPrompts.add(contentFunctionResponse)
                    }
                }

                "write_file" -> {
                    val input = createInstanceFromMapWithJackson(WriteFileInput::class, args)

                    println("!!! I want to write to the file ${input.fileName}. Do you confirm?")
                    val userConfirmation = readLine()

                    val result = if (userConfirmation == "yes") {
                        writeFile(input)
                    } else {
                        val statusMessage = "File write operation to ${input.fileName} was cancelled by the user."
                        WriteFileOutput(success = false, error = statusMessage)
                    }

                    val partFunctionResponse = Part.builder().functionResponse(
                        FunctionResponse.builder().name(functionName).response(
                            mapOf(
                                "success" to result.success,
                                "error" to result.error,
                            )
                        ).build()
                    ).build()
                    val contentFunctionResponse =
                        Content.builder().role("user").parts(listOf(partFunctionResponse)).build()
                    pendingPrompts.add(contentFunctionResponse)
                }
            }
        } else if (response.text() != null) {
            println("< ${response.text()}")
            println(
                """
                Usage:
                  tokens total: ${response.usageMetadata().getOrNull()?.totalTokenCount()?.getOrNull()}
                  tokens prompt (in): ${response.usageMetadata().getOrNull()?.promptTokenCount()?.getOrNull()}
                  tokens candidates (out): ${
                    response.usageMetadata().getOrNull()?.cachedContentTokenCount()?.getOrNull()
                }
                  tokens cached: ${response.usageMetadata().getOrNull()?.cachedContentTokenCount()?.getOrNull()}
                  tokens tools: ${response.usageMetadata().getOrNull()?.toolUsePromptTokenCount()?.getOrNull()}
            """.trimIndent()
            )
        }
    }
}