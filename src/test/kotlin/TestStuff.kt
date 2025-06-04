package com.aiagent // Or your project's package

import com.google.genai.types.Schema
import com.google.genai.types.Type
import kotlin.reflect.KClass
import org.junit.jupiter.api.Test // For JUnit 5
import org.junit.jupiter.api.Assertions.* // For JUnit 5 assertions

// --- The generateSchemaFromDataClass and KClass<*>.toSchemaType() functions from above go here ---
// To keep this block clean, I'm assuming they are in the same file or accessible.
// You would copy the two functions (generateSchemaFromDataClass and KClass<*>.toSchemaType) here.

class SchemaGeneratorTest {

    // --- Test Data Classes with @ToolDoc ---
    @ToolDoc("A simple collection of primitive types for testing.")
    data class PrimitivesWithDocs(
        @ToolDoc("A string value.")
        val S_str: String,

        @ToolDoc("An integer value.")
        val S_int: Int,

        val S_bool: Boolean, // No doc on property

        @param:ToolDoc("A long value, doc on constructor param.") // Doc on constructor param
        val S_long: Long,

        @ToolDoc("Property doc takes precedence.")
//        @param:ToolDoc("This constructor param doc should be ignored.")
        val S_double: Double
    )

    data class NestedItemWithDocs(
        @ToolDoc("Identifier for the nested item.")
        val NI_id: Int,
        @ToolDoc("Value of the nested item.")
        val NI_value: String
    )

    @ToolDoc("Represents an entity with a nested item, demonstrating documentation inheritance.")
    data class NestingWithDocs(
        @ToolDoc("Name of the main entity.")
        val WN_name: String,

        @ToolDoc("The nested item itself. This describes the field holding the item.")
        val WN_item: NestedItemWithDocs, // @ToolDoc here describes the 'WN_item' field

        @param:ToolDoc("A list of tags for this entity.")
        val WN_tags: List<String>
    )

    // --- Test Methods ---

    @Test
    fun `test PrimitivesWithDocs data class for descriptions`() {
        val expectedSchema = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(mapOf(
                "S_str" to Schema.builder().type(Type.Known.STRING).description("A string value.").build(),
                "S_int" to Schema.builder().type(Type.Known.INTEGER).description("An integer value.").build(),
                "S_bool" to Schema.builder().type(Type.Known.BOOLEAN).build(), // No description
                "S_long" to Schema.builder().type(Type.Known.INTEGER).description("A long value, doc on constructor param.").build(),
                "S_double" to Schema.builder().type(Type.Known.NUMBER).description("Property doc takes precedence.").build()
            ))
            .build()

        val actualSchema = generateSchemaFromDataClass(PrimitivesWithDocs::class)
        assertEquals(expectedSchema, actualSchema)
    }

    @Test
    fun `test NestingWithDocs for field and nested property descriptions`() {
        val expectedNestedItemSchemaProperties = mapOf(
            "NI_id" to Schema.builder().type(Type.Known.INTEGER).description("Identifier for the nested item.").build(),
            "NI_value" to Schema.builder().type(Type.Known.STRING).description("Value of the nested item.").build()
        )

        val expectedSchema = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(mapOf(
                "WN_name" to Schema.builder().type(Type.Known.STRING).description("Name of the main entity.").build(),
                "WN_item" to Schema.builder()
                    .type(Type.Known.OBJECT)
                    .description("The nested item itself. This describes the field holding the item.")
                    .properties(expectedNestedItemSchemaProperties)
                    .build(),
                "WN_tags" to Schema.builder().type(Type.Known.ARRAY)
                    .items(Schema.builder().type(Type.Known.STRING).build()) // Item schema for List<String>
                    .description("A list of tags for this entity.") // Description for the list property itself
                    .build()
            ))
            .build()

        val actualSchema = generateSchemaFromDataClass(NestingWithDocs::class)
        assertEquals(expectedSchema, actualSchema)
    }

    @Test
    fun `test WithDataClassList and item descriptions from nested type`() {
        // Test data class from previous example, assuming NestedItem has @ToolDoc
        data class NestedItemForList(
            @ToolDoc("ID of the list item") val id: Int,
            @ToolDoc("Value of the list item") val value: String
        )
        data class ContainerOfList(
            @ToolDoc("A list of documented items") val items: List<NestedItemForList>
        )

        val expectedItemSchema = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(mapOf(
                "id" to Schema.builder().type(Type.Known.INTEGER).description("ID of the list item").build(),
                "value" to Schema.builder().type(Type.Known.STRING).description("Value of the list item").build()
            )).build()

        val expectedSchema = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(mapOf(
                "items" to Schema.builder().type(Type.Known.ARRAY)
                    .description("A list of documented items")
                    .items(expectedItemSchema)
                    .build()
            )).build()

        val actualSchema = generateSchemaFromDataClass(ContainerOfList::class)
        assertEquals(expectedSchema, actualSchema)
    }

}

//class SchemaGeneratorTest {
//
//    // --- Test Data Classes ---
//    data class SimplePrimitives(
//        val S_str: String,
//        val S_int: Int,
//        val S_bool: Boolean,
//        val S_long: Long,
//        val S_double: Double
//    )
//
//    data class WithNullable(
//        val N_reqStr: String,
//        val N_optStr: String?
//    )
//
//    data class NestedItem(
//        val NI_id: Int,
//        val NI_value: String
//    )
//
//    data class WithNesting(
//        val WN_name: String,
//        val WN_item: NestedItem
//    )
//
//    data class WithPrimitiveList(
//        val L_name: String,
//        val L_tags: List<String>,
//        val L_counts: List<Int>
//    )
//
//    data class WithDataClassList(
//        val LDC_listName: String,
//        val LDC_items: List<NestedItem>
//    )
//
//
//    // --- Test Methods ---
//
//    @Test
//    fun `test SimplePrimitives data class`() {
//        val expectedSchema = Schema.builder()
//            .type(Type.Known.OBJECT)
//            .properties(mapOf(
//                "S_str" to Schema.builder().type(Type.Known.STRING).build(),
//                "S_int" to Schema.builder().type(Type.Known.INTEGER).build(),
//                "S_bool" to Schema.builder().type(Type.Known.BOOLEAN).build(),
//                "S_long" to Schema.builder().type(Type.Known.INTEGER).build(),
//                "S_double" to Schema.builder().type(Type.Known.NUMBER).build()
//            ))
//            .build()
//
//        val actualSchema = generateSchemaFromDataClass(SimplePrimitives::class)
//        assertEquals(expectedSchema, actualSchema)
//    }
//
//    @Test
//    fun `test WithNullable data class`() {
//        // Current implementation doesn't explicitly mark optionality in schema,
//        // but reflection can see it (prop.returnType.isMarkedNullable)
//        // For now, it will treat String? the same as String for schema type.
//        val expectedSchema = Schema.builder()
//            .type(Type.Known.OBJECT)
//            .properties(mapOf(
//                "N_reqStr" to Schema.builder().type(Type.Known.STRING).build(),
//                "N_optStr" to Schema.builder().type(Type.Known.STRING).build() // Type is String
//            ))
//            .build()
//        val actualSchema = generateSchemaFromDataClass(WithNullable::class)
//        assertEquals(expectedSchema, actualSchema)
//        // You could add a check here for isMarkedNullable if you extend the function
//        // val optStrProp = WithNullable::class.memberProperties.find { it.name == "N_optStr" }
//        // assertTrue(optStrProp?.returnType?.isMarkedNullable ?: false)
//    }
//
//    @Test
//    fun `test NestedItem data class schema`() {
//        val expectedSchema = Schema.builder()
//            .type(Type.Known.OBJECT)
//            .properties(mapOf(
//                "NI_id" to Schema.builder().type(Type.Known.INTEGER).build(),
//                "NI_value" to Schema.builder().type(Type.Known.STRING).build()
//            ))
//            .build()
//        val actualSchema = generateSchemaFromDataClass(NestedItem::class)
//        assertEquals(expectedSchema, actualSchema)
//    }
//
//
//    @Test
//    fun `test WithNesting data class`() {
//        val expectedSchema = Schema.builder()
//            .type(Type.Known.OBJECT)
//            .properties(mapOf(
//                "WN_name" to Schema.builder().type(Type.Known.STRING).build(),
//                "WN_item" to Schema.builder() // This is the schema for NestedItem
//                    .type(Type.Known.OBJECT)
//                    .properties(mapOf(
//                        "NI_id" to Schema.builder().type(Type.Known.INTEGER).build(),
//                        "NI_value" to Schema.builder().type(Type.Known.STRING).build()
//                    ))
//                    .build()
//            ))
//            .build()
//
//        val actualSchema = generateSchemaFromDataClass(WithNesting::class)
//        assertEquals(expectedSchema, actualSchema)
//    }
//
//    @Test
//    fun `test WithPrimitiveList data class`() {
//        val expectedSchema = Schema.builder()
//            .type(Type.Known.OBJECT)
//            .properties(mapOf(
//                "L_name" to Schema.builder().type(Type.Known.STRING).build(),
//                "L_tags" to Schema.builder().type(Type.Known.ARRAY)
//                    .items(Schema.builder().type(Type.Known.STRING).build()).build(),
//                "L_counts" to Schema.builder().type(Type.Known.ARRAY)
//                    .items(Schema.builder().type(Type.Known.INTEGER).build()).build()
//            ))
//            .build()
//
//        val actualSchema = generateSchemaFromDataClass(WithPrimitiveList::class)
//        assertEquals(expectedSchema, actualSchema)
//    }
//
//    @Test
//    fun `test WithDataClassList data class`() {
//        val expectedNestedItemSchema = Schema.builder()
//            .type(Type.Known.OBJECT)
//            .properties(mapOf(
//                "NI_id" to Schema.builder().type(Type.Known.INTEGER).build(),
//                "NI_value" to Schema.builder().type(Type.Known.STRING).build()
//            )).build()
//
//        val expectedSchema = Schema.builder()
//            .type(Type.Known.OBJECT)
//            .properties(mapOf(
//                "LDC_listName" to Schema.builder().type(Type.Known.STRING).build(),
//                "LDC_items" to Schema.builder().type(Type.Known.ARRAY)
//                    .items(expectedNestedItemSchema) // Items are schemas of NestedItem
//                    .build()
//            ))
//            .build()
//        val actualSchema = generateSchemaFromDataClass(WithDataClassList::class)
//        assertEquals(expectedSchema, actualSchema)
//    }
//
//    // Helper for pretty printing schema for debugging - not a test itself
//    // fun printSchema(schema: Schema, indent: String = "") {
//    //     println("${indent}Type: ${schema.type}")
//    //     schema.description?.let { println("${indent}Description: $it") }
//    //     schema.format?.let { println("${indent}Format: $it") }
//    //     schema.enumList?.let { println("${indent}Enum: ${it.joinToString()}") }
//    //     schema.properties?.takeIf { it.isNotEmpty() }?.let {
//    //         println("${indent}Properties:")
//    //         it.forEach { (key, value) ->
//    //             println("${indent}  $key:")
//    //             printSchema(value, "$indent    ")
//    //         }
//    //     }
//    //     schema.items?.let {
//    //         println("${indent}Items:")
//    //         printSchema(it, "$indent  ")
//    //     }
//    //     schema.required?.let { println("${indent}Required: ${it.joinToString()}")}
//    // }
//}