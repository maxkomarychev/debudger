package com.aiagent

import com.google.genai.Client
import com.google.genai.types.HttpOptions
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val apiKey = System.getenv("GEMINI_API_KEY")

    val modelId = "gemini-2.0-flash"
    val client = Client.builder().apiKey(apiKey).httpOptions(HttpOptions.builder().apiVersion("v1").build()).build()
    val userInput = readLine()
    val response = client.models.generateContent(modelId, userInput, null)
    println(response)
}