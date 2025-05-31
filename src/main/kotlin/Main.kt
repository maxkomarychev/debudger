package com.aiagent

import com.google.genai.Client
import com.google.genai.types.HttpOptions
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val apiKey = System.getenv("GEMINI_API_KEY")
    val modelId = "gemini-2.0-flash"
    val client = Client.builder().apiKey(apiKey).httpOptions(HttpOptions.builder().apiVersion("v1").build()).build()
//    val chat = client.chats.create()
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
        val response = client.models.generateContent(modelId, history, null)
        val text = response.text()
        history = history + "\n" + text
        println(text)
    }
}