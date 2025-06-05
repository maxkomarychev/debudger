package com.aiagent.com.aiagent.tools

import kotlin.reflect.KClass

interface AiFunctionInput
interface AiFunctionOutput

interface AiFunction {
    suspend fun execute(input: Map<String, Any>): AiFunctionOutput

    suspend fun prompt(input: Map<String, Any>): String

    val inputType: KClass<*>
    val outputType: KClass<*>
}