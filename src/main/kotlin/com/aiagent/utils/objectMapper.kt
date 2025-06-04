package com.aiagent.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlin.reflect.KClass

fun <T : Any> createInstanceFromMapWithJackson(dataClass: KClass<T>, map: Map<String, Any?>): T {
    // Jackson's convertValue is very powerful for this.
    return objectMapper.convertValue(map, dataClass.java) // Uses Java Class here
}

fun <T: Any> dataClassToMap(value: T): Map<String, Any> {
    return objectMapper.convertValue(value, Map::class.java) as Map<String, Any>
}

val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())