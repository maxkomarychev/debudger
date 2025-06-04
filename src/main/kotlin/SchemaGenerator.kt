package com.aiagent

import com.google.genai.types.Schema
import com.google.genai.types.Type
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

fun KClass<*>.toSchemaType(): Type.Known {
    return when (this) {
        String::class -> Type.Known.STRING
        Int::class, Long::class, Short::class, Byte::class -> Type.Known.INTEGER
        Boolean::class -> Type.Known.BOOLEAN
        Double::class, Float::class -> Type.Known.NUMBER
        List::class -> Type.Known.ARRAY
        else -> if (this.isData) Type.Known.OBJECT else Type.Known.STRING
    }
}

fun generateSchemaFromDataClass(dataClass: KClass<*>): Schema {
    if (!dataClass.isData) {
        throw IllegalArgumentException("Input class '${dataClass.simpleName}' must be a data class.")
    }

    val propertiesSchema = mutableMapOf<String, Schema>()
    val primaryConstructor = dataClass.primaryConstructor

    dataClass.memberProperties.forEach { prop ->
        val propertyName = prop.name
        val returnType = prop.returnType
        val classifier = returnType.classifier as? KClass<*>
            ?: throw IllegalStateException("Could not determine classifier for property ${prop.name}")

        // Find description:
        // 1. From @ToolDoc on the property itself.
        // 2. From @ToolDoc on the corresponding constructor parameter.
        var description: String? = prop.findAnnotation<ToolDoc>()?.description
        if (description == null) {
            primaryConstructor?.parameters?.find { it.name == prop.name }?.let { constructorParam ->
                description = constructorParam.findAnnotation<ToolDoc>()?.description
            }
        }

        val propertySchemaBuilder = Schema.builder()

        // Set type
        propertySchemaBuilder.type(classifier.toSchemaType())

        // Set description if found
        description?.let { propertySchemaBuilder.description(it) }

        if (classifier == List::class) {
            val listItemType = returnType.arguments.firstOrNull()?.type?.classifier as? KClass<*>
            val listItemSchemaBuilder = Schema.builder()
            if (listItemType != null) {
                if (listItemType.isData) {
                    propertySchemaBuilder.items(generateSchemaFromDataClass(listItemType))
                } else {
                    listItemSchemaBuilder.type(listItemType.toSchemaType())
                    // Note: KDoc on list item type's declaration itself (e.g. @ToolDoc class MyListItem)
                    // won't be automatically picked up here for the items schema directly.
                    // For complex item descriptions, the item would likely be a data class.
                    propertySchemaBuilder.items(listItemSchemaBuilder.build())
                }
            } else {
                propertySchemaBuilder.items(listItemSchemaBuilder.type(Type.Known.STRING).build()) // Fallback
            }
        } else if (classifier.isData) {
            // The property 'propertyName' is of a data class type 'classifier'.
            // The schema for this property will be an OBJECT type.
            // The description comes from @ToolDoc on 'propertyName'.
            // The properties of this OBJECT schema come from 'classifier'.
            val nestedObjectClassSchema = generateSchemaFromDataClass(classifier) // Get schema of the nested data class
            val propertySchema = Schema.builder().type(Type.Known.OBJECT) // Property type is OBJECT
            description?.let { propertySchema.description(it) }         // Description for this property
            propertySchema.properties(nestedObjectClassSchema.properties().get()) // Properties from nested class
            propertiesSchema[propertyName] = propertySchema.build()
        } else {
            // For simple properties and lists (lists are handled above)
            propertiesSchema[propertyName] = propertySchemaBuilder.build()
        }
        // else simple property, type and description are already set.

        // Add to map if not already handled by nested data class assignment
        if (!classifier.isData) {
            propertiesSchema[propertyName] = propertySchemaBuilder.build()
        } else {
            // If it's a data class, we've built its schema slightly differently to include the description
            // on the property holding the data class.
            val nestedSchema = generateSchemaFromDataClass(classifier)
            val builderForPropertyHoldingNested = Schema.builder().type(Type.Known.OBJECT)
            description?.let { builderForPropertyHoldingNested.description(it) }
            builderForPropertyHoldingNested.properties(nestedSchema.properties().get()) // Carry over nested properties
            propertiesSchema[propertyName] = builderForPropertyHoldingNested.build()

        }
    }
    // Note: @ToolDoc on the dataClass KClass itself is not used in this function to describe the
    // returned object schema's top level, as the Gemini Schema for OBJECT typically
    // describes its properties. It could be used by the caller (e.g. generateFunctionDeclaration).
    return Schema.builder().type(Type.Known.OBJECT).properties(propertiesSchema).build()
}