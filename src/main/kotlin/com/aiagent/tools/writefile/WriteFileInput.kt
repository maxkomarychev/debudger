package com.aiagent

data class WriteFileInput(
    @ToolDoc("Name of the file to write") val fileName: String,
    @ToolDoc("Content of the file") val content: String,
)