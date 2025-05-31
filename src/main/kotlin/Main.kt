package com.aiagent

import com.google.genai.Client
import com.google.genai.types.*
import kotlinx.coroutines.runBlocking

val lsDeclaration =
    FunctionDeclaration.builder().name("ls").description("List files in a specified directory")
        .parameters(
            Schema.builder().type(Type.Known.OBJECT).properties(
                mapOf("path" to Schema.builder().type(Type.Known.STRING).description("Path to the directory").build())
            ).build()
        )
        .response(
            Schema.builder().type(Type.Known.OBJECT).properties(
                mapOf(
                    "output" to Schema.builder().type(Type.Known.STRING).description("Output of the ls command").build()
                )
            ).build()
        )
        .build()

val lsTool = Tool.builder().functionDeclarations(mutableListOf(lsDeclaration)).build()


fun main() = runBlocking {
    val apiKey = System.getenv("GEMINI_API_KEY")
    val modelId = "gemini-2.0-flash"
    val client = Client.builder().apiKey(apiKey).httpOptions(HttpOptions.builder().apiVersion("v1beta").build()).build()
    val systemPrompt = """
        You are a chat bot. My questions will start with ">" and your answers start with "<".
    """.trimIndent()
    var history = systemPrompt
    while (true) {
        val userInput = readLine()
        if (userInput == "exit") {
            break
        }
        history = history + "\n" + userInput
        val config = GenerateContentConfig.builder().tools(mutableListOf(lsTool)).build()
        val response = client.models.generateContent(modelId, history, config)
        if (response.functionCalls() != null && response.functionCalls()!!.isNotEmpty()) {
            val f = response.functionCalls()!!.first()
            val path = f.args().get().get("path") as String
            val lsResult = Runtime.getRuntime().exec("ls $path")
            val lsOutput = lsResult.inputStream.bufferedReader().readText()

            val part = Part.builder().functionResponse(
                FunctionResponse.builder().name("ls").response(
                    mapOf("output" to lsOutput)
                ).build()
            ).build()
            val response =
                client.models.generateContent(modelId, Content.builder().parts(mutableListOf(part)).build(), config)
            val text = response.text()
            history = history + "\n" + text
            println(text)
        } else {
            val text = response.text()
            history = history + "\n" + text
            println(text)
        }
    }
}