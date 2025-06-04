package com.aiagent

@Target(
    AnnotationTarget.CLASS,          // For describing the data class itself (if needed by a higher-level consumer)
    AnnotationTarget.PROPERTY,       // For describing a property
    AnnotationTarget.VALUE_PARAMETER // For describing a constructor parameter
)
@Retention(AnnotationRetention.RUNTIME) // Essential: Makes it available at runtime via reflection
annotation class ToolDoc(val description: String)