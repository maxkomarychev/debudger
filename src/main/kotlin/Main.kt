package com.aiagent

import com.google.genai.Client
import com.google.genai.types.*

val shellCommandDeclaration = FunctionDeclaration.builder().name("shell_command")
    .description("Execute arbitrary command in shell and get response back").parameters(
        Schema.builder().type(Type.Known.OBJECT).properties(
            mapOf("command" to Schema.builder().type(Type.Known.STRING).description("Command to perform").build())
        ).build()
    ).response(
        Schema.builder().type(Type.Known.OBJECT).properties(
            mapOf(
                "exitCode" to Schema.builder().type(Type.Known.INTEGER).description("exit code of the command").build(),
                "stdout" to Schema.builder().type(Type.Known.STRING).description("stdout of the command").build(),
                "stderr" to Schema.builder().type(Type.Known.STRING).description("stderr of the command").build(),
            )
        ).build()
    ).build()



val writeFileDeclaration = FunctionDeclaration.builder().name("write_file")
    .description("Write content to a specified file.")
    .parameters(
        Schema.builder().type(Type.Known.OBJECT).properties(
            mapOf(
                "file_name" to Schema.builder().type(Type.Known.STRING).description("Name of the file to write.")
                    .build(),
                "content" to Schema.builder().type(Type.Known.STRING).description("Content to write into the file.")
                    .build()
            )
        ).build()
    )
    .response( // Optional: Define a meaningful response
        Schema.builder().type(Type.Known.OBJECT).properties(
            mapOf(
                "status" to Schema.builder().type(Type.Known.STRING).description("Status of the file write operation")
                    .build()
            )
        ).build()
    )
    .build()



fun main() {
    val apiKey = System.getenv("GEMINI_API_KEY")
//    val modelId = "gemini-2.0-flash"
//    val modelId = "gemini-2.5-flash-preview-05-20"
    val modelId = "gemini-2.5-pro-preview-05-06"
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
            when (functionName) {
                "shell_command" -> {
                    val command = functionCall.args().get().get("command") as String
                    println("!!! I want to execute a shell command. Do you confirm? $command")
                    val response = readLine()
                    if (response != "yes") {
                        val content = Content.builder().role("user")
                            .parts(listOf(Part.builder().text("I do not allow running this command").build())).build()
                        pendingPrompts.add(content)
                    } else {
                        val process = Runtime.getRuntime().exec(command)
                        val exitCode = process.waitFor()
                        val stdout = process.inputStream.bufferedReader().readText()
                        val stderr = process.errorStream.bufferedReader().readText()
                        val partFunctionResponse = Part.builder().functionResponse(
                            FunctionResponse.builder().name(functionName).response(
                                mapOf(
                                    "exitCode" to exitCode,
                                    "stdout" to stdout,
                                    "stderr" to stderr,
                                )
                            ).build()
                        ).build()
                        val contentFunctionResponse =
                            Content.builder().role("user").parts(listOf(partFunctionResponse)).build()
                        pendingPrompts.add(contentFunctionResponse)
                    }
                }

                "write_file" -> { // 4. Handle the new function call
                    val fileName = functionCall.args().get().get("file_name") as String
                    val fileContent = functionCall.args().get().get("content") as String

                    println("!!! I want to write to the file $fileName. Do you confirm?")
                    val userConfirmation = readLine()

                    var statusMessage: String
                    if (userConfirmation == "yes") {
                        try {
                            java.io.File(fileName).writeText(fileContent)
                            statusMessage = "Successfully wrote to $fileName"
                        } catch (e: Exception) {
                            statusMessage = "Error writing to $fileName: ${e.message}"
                        }
                    } else {
                        statusMessage = "File write operation to $fileName was cancelled by the user."
                    }

                    val partFunctionResponse = Part.builder().functionResponse(
                        FunctionResponse.builder().name(functionName).response(
                            mapOf("status" to statusMessage)
                        ).build()
                    ).build()
                    val contentFunctionResponse =
                        Content.builder().role("user").parts(listOf(partFunctionResponse)).build()
                    pendingPrompts.add(contentFunctionResponse)
                }
            }
        } else if (response.text() != null) {
            println("< ${response.text()}")
        }
    }
}