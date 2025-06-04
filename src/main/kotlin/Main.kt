package com.aiagent

import com.google.genai.Client
import com.google.genai.types.*
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor


@Target(
    AnnotationTarget.CLASS,          // For describing the data class itself (if needed by a higher-level consumer)
    AnnotationTarget.PROPERTY,       // For describing a property
    AnnotationTarget.VALUE_PARAMETER // For describing a constructor parameter
)
@Retention(AnnotationRetention.RUNTIME) // Essential: Makes it available at runtime via reflection
annotation class ToolDoc(val description: String)

// KClass<*>.toSchemaType() helper function (same as before)
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

data class ShellCommandInput(
    val command: String
)

data class ShellCommandOutput(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

val shellCommandDeclaration = FunctionDeclaration.builder().name("shell_command")
    .description("Execute arbitrary command in shell and get response back").parameters(
        Schema.builder().type(Type.Known.OBJECT).properties(
            mapOf("command" to Schema.builder().type(Type.Known.STRING).description("Command to perform").build())
        ).build()
    ).response(
        Schema.builder().type(Type.Known.OBJECT).properties(
            mapOf(
                "exitCode" to Schema.builder().type(Type.Known.INTEGER).description("exit code of the command").build(),
                "stdout" to Schema.builder().type(Type.Known.STRING).description("stdout of the command").build(),
                "stderr" to Schema.builder().type(Type.Known.STRING).description("stderr of the command").build(),
            )
        ).build()
    ).build()


val writeFileDeclaration = FunctionDeclaration.builder().name("write_file")
    .description("Write content to a specified file.")
    .parameters(
        Schema.builder().type(Type.Known.OBJECT).properties(
            mapOf(
                "file_name" to Schema.builder().type(Type.Known.STRING).description("Name of the file to write.")
                    .build(),
                "content" to Schema.builder().type(Type.Known.STRING).description("Content to write into the file.")
                    .build()
            )
        ).build()
    )
    .response( // Optional: Define a meaningful response
        Schema.builder().type(Type.Known.OBJECT).properties(
            mapOf(
                "status" to Schema.builder().type(Type.Known.STRING).description("Status of the file write operation")
                    .build()
            )
        ).build()
    )
    .build()


fun main() {
    val apiKey = System.getenv("GEMINI_KEY")
//    val modelId = "gemini-2.0-flash"
//    val modelId = "gemini-2.5-flash-preview-05-20"
    val modelId = "gemini-2.5-pro-preview-05-06"
    val client = Client.builder().apiKey(apiKey).httpOptions(HttpOptions.builder().apiVersion("v1beta").build()).build()
    val currentDir = System.getProperty("user.dir")
    val firstPrompt = """
        You are an agent which helps engineers solve technical problems.
        You may be asked to:
            - do a research
            - fix a bug
            - edit code
            - etc

        If user is asking to do something first consider using shell_command with any of known CLI tools.
        
        Your goal is to solve my technical problem. Use any available tools at your discretion whenever you believe it will help you achieve this goal. Don't ask for permission before using a tool; if you think it's necessary, use it.
        
        Your current directory is $currentDir.
        
        When answering a user read entire history of the conversation.
        
        Good luck!
    """.trimIndent()
    val first = Content.builder().role("user").parts(listOf(Part.builder().text(firstPrompt).build())).build()
    val history = mutableListOf(first)
    val tools = listOf(
        Tool.builder().functionDeclarations(
            listOf(
                shellCommandDeclaration,
                writeFileDeclaration,
            )
        ).build(),
    )

    val pendingPrompts = mutableListOf<Content>()
    val config = GenerateContentConfig.builder().tools(tools).build()

    while (true) {

        if (pendingPrompts.isEmpty()) {
            val userInput = readLine()
            if (userInput == "exit") {
                break
            }
            val content = Content.builder().role("user").parts(listOf(Part.builder().text(userInput).build())).build()
            pendingPrompts.add(content)
        }

        val currentPrompt = pendingPrompts.removeFirst()
        history.add(currentPrompt)
        val response = client.models.generateContent(modelId, history, config)
        history.add(response.candidates().get().first().content().get())


        if (response.functionCalls() != null && response.functionCalls()!!.isNotEmpty()) {
            val functionCall = response.functionCalls()!!.first()
            val functionName = functionCall.name().get()
            when (functionName) {
                "shell_command" -> {
                    val command = functionCall.args().get().get("command") as String
                    println("!!! I want to execute a shell command. Do you confirm? $command")
                    val response = readLine()
                    if (response != "yes") {
                        val content = Content.builder().role("user")
                            .parts(listOf(Part.builder().text("I do not allow running this command").build())).build()
                        pendingPrompts.add(content)
                    } else {
                        val process = Runtime.getRuntime().exec(command)
                        val exitCode = process.waitFor()
                        val stdout = process.inputStream.bufferedReader().readText()
                        val stderr = process.errorStream.bufferedReader().readText()
                        val partFunctionResponse = Part.builder().functionResponse(
                            FunctionResponse.builder().name(functionName).response(
                                mapOf(
                                    "exitCode" to exitCode,
                                    "stdout" to stdout,
                                    "stderr" to stderr,
                                )
                            ).build()
                        ).build()
                        val contentFunctionResponse =
                            Content.builder().role("user").parts(listOf(partFunctionResponse)).build()
                        pendingPrompts.add(contentFunctionResponse)
                    }
                }

                "write_file" -> { // 4. Handle the new function call
                    val fileName = functionCall.args().get().get("file_name") as String
                    val fileContent = functionCall.args().get().get("content") as String

                    println("!!! I want to write to the file $fileName. Do you confirm?")
                    val userConfirmation = readLine()

                    var statusMessage: String
                    if (userConfirmation == "yes") {
                        try {
                            java.io.File(fileName).writeText(fileContent)
                            statusMessage = "Successfully wrote to $fileName"
                        } catch (e: Exception) {
                            statusMessage = "Error writing to $fileName: ${e.message}"
                        }
                    } else {
                        statusMessage = "File write operation to $fileName was cancelled by the user."
                    }

                    val partFunctionResponse = Part.builder().functionResponse(
                        FunctionResponse.builder().name(functionName).response(
                            mapOf("status" to statusMessage)
                        ).build()
                    ).build()
                    val contentFunctionResponse =
                        Content.builder().role("user").parts(listOf(partFunctionResponse)).build()
                    pendingPrompts.add(contentFunctionResponse)
                }
            }
        } else if (response.text() != null) {
            println("< ${response.text()}")
            println(
                """
                Usage:
                  tokens total: ${response.usageMetadata().getOrNull()?.totalTokenCount()?.getOrNull()}
                  tokens prompt (in): ${response.usageMetadata().getOrNull()?.promptTokenCount()?.getOrNull()}
                  tokens candidates (out): ${
                    response.usageMetadata().getOrNull()?.cachedContentTokenCount()?.getOrNull()
                }
                  tokens cached: ${response.usageMetadata().getOrNull()?.cachedContentTokenCount()?.getOrNull()}
                  tokens tools: ${response.usageMetadata().getOrNull()?.toolUsePromptTokenCount()?.getOrNull()}
            """.trimIndent()
            )
        }
    }
}