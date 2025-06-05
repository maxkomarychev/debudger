package com.aiagent

import com.aiagent.com.aiagent.tools.ToolDescription
import com.aiagent.com.aiagent.utils.generateSchemaFromDataClass
import com.google.genai.types.Schema
import com.google.genai.types.Type
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SchemaGeneratorTest {

    // --- Test Data Classes with @ToolDoc ---
    @ToolDescription("A simple collection of primitive types for testing.")
    data class PrimitivesWithDocs(
        @ToolDescription("A string value.")
        val S_str: String,

        @ToolDescription("An integer value.")
        val S_int: Int,

        val S_bool: Boolean, // No doc on property

        @param:ToolDescription("A long value, doc on constructor param.") // Doc on constructor param
        val S_long: Long,

        @ToolDescription("Property doc takes precedence.")
//        @param:ToolDoc("This constructor param doc should be ignored.")
        val S_double: Double
    )

    data class NestedItemWithDocs(
        @ToolDescription("Identifier for the nested item.")
        val NI_id: Int,
        @ToolDescription("Value of the nested item.")
        val NI_value: String
    )

    @ToolDescription("Represents an entity with a nested item, demonstrating documentation inheritance.")
    data class NestingWithDocs(
        @ToolDescription("Name of the main entity.")
        val WN_name: String,

        @ToolDescription("The nested item itself. This describes the field holding the item.")
        val WN_item: NestedItemWithDocs, // @ToolDoc here describes the 'WN_item' field

        @param:ToolDescription("A list of tags for this entity.")
        val WN_tags: List<String>
    )

    // --- Test Methods ---

    @Test
    fun `test PrimitivesWithDocs data class for descriptions`() {
        val expectedSchema = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "S_str" to Schema.builder().type(Type.Known.STRING).description("A string value.").build(),
                    "S_int" to Schema.builder().type(Type.Known.INTEGER).description("An integer value.").build(),
                    "S_bool" to Schema.builder().type(Type.Known.BOOLEAN).build(), // No description
                    "S_long" to Schema.builder().type(Type.Known.INTEGER)
                        .description("A long value, doc on constructor param.").build(),
                    "S_double" to Schema.builder().type(Type.Known.NUMBER).description("Property doc takes precedence.")
                        .build()
                )
            )
            .build()

        val actualSchema = generateSchemaFromDataClass(PrimitivesWithDocs::class)
        Assertions.assertEquals(expectedSchema, actualSchema)
    }

    @Test
    fun `test NestingWithDocs for field and nested property descriptions`() {
        val expectedNestedItemSchemaProperties = mapOf(
            "NI_id" to Schema.builder().type(Type.Known.INTEGER).description("Identifier for the nested item.").build(),
            "NI_value" to Schema.builder().type(Type.Known.STRING).description("Value of the nested item.").build()
        )

        val expectedSchema = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "WN_name" to Schema.builder().type(Type.Known.STRING).description("Name of the main entity.")
                        .build(),
                    "WN_item" to Schema.builder()
                        .type(Type.Known.OBJECT)
                        .description("The nested item itself. This describes the field holding the item.")
                        .properties(expectedNestedItemSchemaProperties)
                        .build(),
                    "WN_tags" to Schema.builder().type(Type.Known.ARRAY)
                        .items(Schema.builder().type(Type.Known.STRING).build()) // Item schema for List<String>
                        .description("A list of tags for this entity.") // Description for the list property itself
                        .build()
                )
            )
            .build()

        val actualSchema = generateSchemaFromDataClass(NestingWithDocs::class)
        Assertions.assertEquals(expectedSchema, actualSchema)
    }

    @Test
    fun `test WithDataClassList and item descriptions from nested type`() {
        // Test data class from previous example, assuming NestedItem has @ToolDoc
        data class NestedItemForList(
            @ToolDescription("ID of the list item") val id: Int,
            @ToolDescription("Value of the list item") val value: String
        )

        data class ContainerOfList(
            @ToolDescription("A list of documented items") val items: List<NestedItemForList>
        )

        val expectedItemSchema = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "id" to Schema.builder().type(Type.Known.INTEGER).description("ID of the list item").build(),
                    "value" to Schema.builder().type(Type.Known.STRING).description("Value of the list item").build()
                )
            ).build()

        val expectedSchema = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "items" to Schema.builder().type(Type.Known.ARRAY)
                        .description("A list of documented items")
                        .items(expectedItemSchema)
                        .build()
                )
            ).build()

        val actualSchema = generateSchemaFromDataClass(ContainerOfList::class)
        Assertions.assertEquals(expectedSchema, actualSchema)
    }

}