package com.example.request

data class ProcedureStep(
    val id: String,
    val stepNumber: Int,
    val instruction: String,
    val imageResource: Int? = null,
    val imageCaption: String? = null
)