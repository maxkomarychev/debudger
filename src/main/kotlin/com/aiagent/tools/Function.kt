package com.aiagent.com.aiagent.tools

import com.github.ajalt.mordant.terminal.Terminal
import kotlin.reflect.KClass

interface AiFunctionInput
interface AiFunctionOutput

interface AiFunction {
    suspend fun execute(input: Map<String, Any>, terminal: Terminal): AiFunctionOutput

    suspend fun prompt(input: Map<String, Any>): String

    val inputType: KClass<*>
    val outputType: KClass<*>
}