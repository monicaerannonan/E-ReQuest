package com.example.request.model

data class Transaction(

    val id: String = "",

    // ID ng School Service kung saan kabilang
    val serviceId: String = "",

    val name: String = "",

    val description: String = "",

    val isActive: Boolean = true,

    val displayOrder: Int = 0

)