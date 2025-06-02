package com.aiagent

import com.google.genai.Client
import com.google.genai.types.*
import kotlinx.coroutines.runBlocking

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


fun main() = runBlocking {
    val apiKey = System.getenv("GEMINI_API_KEY")
//    val modelId = "gemini-2.0-flash"
//    val modelId = "gemini-2.5-flash-preview-05-20"
    val modelId = "gemini-2.5-pro-preview-05-06"
    val client = Client.builder().apiKey(apiKey).httpOptions(HttpOptions.builder().apiVersion("v1beta").build()).build()
    val currentDir = System.getProperty("user.dir")
    val systemPrompt = """
        You are a chat bot.
        Questions of a user wrapped in <user> and answers wrapped in <bot>.
        Function calls look like:
          <function name="xxx" args={...}>
            <stdout></stdout>
            <stderr></stderr>
            <exitCode></exitCode>
          </function>
        If user is asking to do something first consider using shell_command with any of known CLI tools.
        Your current directory is $currentDir.
        When answering a user read entire history of the conversation.
        Do not ask permission to use tools, just use them.
    """.trimIndent()
    var history = "" //systemPrompt
    while (true) {
        val userInput = readLine()
        if (userInput == "exit") {
            break
        }
        history = """
            $history
            <user>$userInput</user>"
        """.trimIndent()
        val tools = listOf(
            shellCommandTool
        )
        val config = GenerateContentConfig.builder().tools(tools).build()
        val response = client.models.generateContent(modelId, history, config)
        if (response.functionCalls() != null && response.functionCalls()!!.isNotEmpty()) {
            val f = response.functionCalls()!!.first()
            when (f.name().get()) {
                "shell_command" -> {
                    val command = f.args().get().get("command") as String
                    println("!!! I want to execute a shell command. Do you confirm? $command")
                    val response = readLine()
                    if (response != "yes") {
                        val response = client.models.generateContent(
                            modelId, "I do not allow running this command", config
                        )
                        val text = response.text()
                        history = "$history\n<bot>$userInput</bot>"
                        println(text)
                    } else {
                        val process = Runtime.getRuntime().exec(command)
                        val exitCode = process.waitFor()
                        val stdout = process.inputStream.bufferedReader().readText()
                        val stderr = process.errorStream.bufferedReader().readText()
                        val partFunctionResponse = Part.builder().functionResponse(
                            FunctionResponse.builder().name(f.name().get()).response(
                                mapOf(
                                    "exitCode" to exitCode,
                                    "stdout" to stdout,
                                    "stderr" to stderr,
                                )
                            ).build()
                        ).build()
                        val partFunctionCall = Part.builder().functionCall(f).build()
                        history = """
                            $history
                            <function name="shell_command" command="$command">
                                <stdout>$stdout</stdout>
                                <stderr>$stderr</stderr>
                                <exitCode>$exitCode</exitCode>
                            </function>
                        """.trimIndent()
                        val historyPart =
                            Content.builder().role("user").parts(listOf(Part.builder().text(history).build())).build()
                        val contentFunctionCall =
                            Content.builder().role("model").parts(listOf(partFunctionCall)).build()
                        val contentFunctionResponse =
                            Content.builder().role("user").parts(listOf(partFunctionResponse)).build()
                        val response = client.models.generateContent(
                            modelId,
                            listOf(historyPart, contentFunctionCall, contentFunctionResponse),
                            config
                        )
                        val text = response.text()
                        history = """
                             "$history
                             <bot>$userInput</bot>"
                        """.trimIndent()
                        println(text)
                    }
                }
            }
        } else {
            val text = response.text()
            history = """
                $history
                <bot>$userInput</bot>"
                """.trimIndent()
            println(text)
        }
    }
}