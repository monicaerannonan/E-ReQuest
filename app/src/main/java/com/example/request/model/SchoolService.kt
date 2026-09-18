package com.example.request.model

data class SchoolService(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val iconLetter: String = "",
    val isActive: Boolean = true,
    val displayOrder: Int = 0
)