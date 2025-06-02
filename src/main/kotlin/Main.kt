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

val shellCommandTool = Tool.builder().functionDeclarations(mutableListOf(shellCommandDeclaration)).build()


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
        
        Your current directory is $currentDir.
        
        When answering a user read entire history of the conversation.
        
        Do not ask permission to use tools, just use them.
        
        
        Good luck!
    """.trimIndent()
    val first = Content.builder().role("user").parts(listOf(Part.builder().text(firstPrompt).build())).build()
    val history = mutableListOf(first)
    val tools = listOf(
        shellCommandTool
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


        if (response.functionCalls() != null && response.functionCalls()!!.isNotEmpty()) {
            val functionCall = response.functionCalls()!!.first()
            val functionName = functionCall.name().get()
            when (functionName) {
                "shell_command" -> {
                    val command = functionCall.args().get().get("command") as String
                    println("!!! I want to execute a shell command. Do you confirm? $command")
                    val response = readLine()
                    if (response != "yes") {
                        val response = client.models.generateContent(
                            modelId, "I do not allow running this command", config
                        )
                        val text = response.text()
                        println(text)
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
            }
        } else if (response.text() != null) {
            println("< ${response.text()}")
        }
    }
}