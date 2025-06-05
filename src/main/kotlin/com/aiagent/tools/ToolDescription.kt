package com.aiagent.com.aiagent.tools

@Target(
    AnnotationTarget.CLASS,          // For describing the data class itself (if needed by a higher-level consumer)
    AnnotationTarget.PROPERTY,       // For describing a property
    AnnotationTarget.VALUE_PARAMETER // For describing a constructor parameter
)
@Retention(AnnotationRetention.RUNTIME) // Essential: Makes it available at runtime via reflection
annotation class ToolDescription(val description: String)

@Target(
    AnnotationTarget.CLASS,          // For describing the data class itself (if needed by a higher-level consumer)
    AnnotationTarget.PROPERTY,       // For describing a property
    AnnotationTarget.VALUE_PARAMETER // For describing a constructor parameter
)
@Retention(AnnotationRetention.RUNTIME) // Essential: Makes it available at runtime via reflection
annotation class ToolName(val name: String)