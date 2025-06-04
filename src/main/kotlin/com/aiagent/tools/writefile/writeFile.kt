package com.aiagent.tools.writefile

import com.aiagent.WriteFileInput
import java.io.File

suspend fun writeFile(input: WriteFileInput): WriteFileOutput {
    return try {
        File(input.fileName).writeText(input.content)
        WriteFileOutput(success = true, error = null)
    } catch (e: Exception) {
        val errorMessage = "Error writing to ${input.fileName}: ${e.message}"
        WriteFileOutput(success = false, error = errorMessage)
    }
}