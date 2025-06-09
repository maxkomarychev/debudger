package com.aiagent

import com.aiagent.com.aiagent.tools.ToolName
import com.aiagent.com.aiagent.tools.readFile.ReadFileFunction
import com.aiagent.com.aiagent.tools.shellcommand.ShellCommandFunction
import com.aiagent.com.aiagent.tools.writefile.WriteFileFunction
import com.aiagent.com.aiagent.utils.toFunctionDeclaration
import com.aiagent.utils.dataClassToMap
import com.github.ajalt.mordant.animation.progress.animateOnThread
import com.github.ajalt.mordant.animation.progress.execute
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.StringPrompt
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.YesNoPrompt
import com.github.ajalt.mordant.widgets.Spinner
import com.github.ajalt.mordant.widgets.progress.progressBarLayout
import com.github.ajalt.mordant.widgets.progress.spinner
import com.github.ajalt.mordant.widgets.progress.text
import com.github.ajalt.mordant.widgets.progress.timeElapsed
import com.google.genai.Client
import com.google.genai.types.*
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.full.findAnnotation

fun logging(text: String): String = TextColors.gray(text)
fun highlighted(text: String): String = TextStyles.bold(TextColors.blue(text))

val write = WriteFileFunction()
val shell = ShellCommandFunction()
val read = ReadFileFunction()
val functions = listOf(write, shell, read)
val allowedFunctions = setOf(read, write)

val functionsMap = functions.groupBy { it::class.findAnnotation<ToolName>()!!.name }.mapValues { it.value.first() }

suspend fun main() {
    val terminal = Terminal()
    val apiKey = System.getenv("GEMINI_KEY")
//    val modelId = "gemini-2.0-flash"
    val modelId = "gemini-2.5-flash-preview-05-20"
//    val modelId = "gemini-2.5-pro-preview-06-05"
    val client = Client.builder().apiKey(apiKey).httpOptions(HttpOptions.builder().apiVersion("v1beta").build()).build()
    val currentDir = System.getProperty("user.dir")
    val firstPrompt = """
        Your goal is to solve technical problem, do research or answer any questions.
        Use any available tools at your discretion whenever you believe it will help you achieve this goal.
        Don't ask for permission before using a tool. If you think it's necessary just use it.
        Use multiple tool calls at once if necessary.
        
        When running a shell command chose options which make it non-interactive.
        
        If user denies usage of a command find an alternative way to achieve the goal.
        
        Understand the task you need to solve.
        
        Understand the structure of the current directory and its content.
        Try to understand:
            1. the language the project is using
            2. if there are typical files with dependencies and build scripts
            3. if there are folders with source code
            
        Communicate your finding back to a user in a concise form.
        
        Once you understand the task and the structure of the current directory create a To-Do list.
        The list should contain steps you would need to execute in order to achieve the goal.
        Let the user confirm the list before you start executing the steps.
        Consult your own To-Do list regularly and update it as you proceed.
        
        Your current directory is $currentDir.
    """.trimIndent()
    val first = Content.builder().role("user").parts(listOf(Part.builder().text(firstPrompt).build())).build()
    val history = mutableListOf(first)
    val tools = listOf(
        Tool.builder().functionDeclarations(functionsMap.values.map { it.toFunctionDeclaration() }).build(),
    )

    val pendingPrompts = mutableListOf<Content>()
    val config =
        GenerateContentConfig.builder().tools(tools).httpOptions(HttpOptions.builder().timeout(10e3.toInt()).build())
            .build()

    while (true) {

        if (pendingPrompts.isEmpty()) {
            val userInput = StringPrompt(prompt = TextColors.yellow("Enter your prompt"), terminal = terminal).ask()!!
            val content = Content.builder().role("user").parts(listOf(Part.builder().text(userInput).build())).build()
            pendingPrompts.add(content)
        }

        val currentPrompt = pendingPrompts.removeFirst()
        history.add(currentPrompt)
        val progress = progressBarLayout(alignColumns = false) {
            text(logging("Generating response..."))
            spinner(Spinner.Dots())
            timeElapsed(compact = false, style = TextStyle(color = TextColors.gray))
        }.animateOnThread(terminal)
        progress.execute()
        val response = client.models.generateContent(modelId, history, config)
        progress.stop()
        terminal.println(logging("Got response with ${response.candidates().get().size} candidates"))
        history.add(response.candidates().get().first().content().get())

        if (response.functionCalls() != null && response.functionCalls()!!.isNotEmpty()) {
            val functionCall = response.functionCalls()!!.first()
            val functionName = functionCall.name().get()
            val args = functionCall.args().get()
            val function = functionsMap[functionName] ?: throw IllegalStateException("Unknown function: $functionName")
            val functionPrompt = function.prompt(input = args)
            val yesno = if (allowedFunctions.contains(function)) {
                true
            } else {
                YesNoPrompt(
                    prompt = """
                    ${TextColors.yellow("Function call")}
                    ${TextStyles.bold(TextColors.brightBlue(functionPrompt))}
                    ${TextColors.yellow("Confirm?")}
                    """.trimIndent(), default = true, terminal = terminal
                ).ask()!!
            }
            if (!yesno) {
                val clarification =
                    StringPrompt(prompt = "Clarify your answer (optional).", terminal = terminal).ask() ?: ""
                val content = Content.builder().role("user")
                    .parts(listOf(Part.builder().text("I do not allow running this function. $clarification").build()))
                    .build()
                pendingPrompts.add(content)
            } else {

                val progress = progressBarLayout(alignColumns = false) {
                    text(logging("Executing function $functionName..."))
                    spinner(Spinner.Dots())
                    timeElapsed(compact = false, style = TextStyle(color = TextColors.gray))
                }.animateOnThread(terminal)
                progress.execute()
                val output = function.execute(args, terminal)
                progress.stop()
                val partFunctionResponse = Part.builder().functionResponse(
                    FunctionResponse.builder().name(functionName).response(
                        dataClassToMap(output)
                    ).build()
                ).build()
                val contentFunctionResponse = Content.builder().role("user").parts(listOf(partFunctionResponse)).build()
                pendingPrompts.add(contentFunctionResponse)
            }
        } else if (response.text() != null) {
            val text = response.text()!!
            val md = Markdown(text)
            terminal.println(md)
        }
        terminal.println(
            TextStyles.dim(
                TextColors.green(
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
            )
        )
    }
}