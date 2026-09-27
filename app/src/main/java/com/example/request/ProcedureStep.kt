package com.example.request

data class ProcedureStep(

    val id: String,

    val stepNumber: Int,

    val instruction: String,

    // ==========================================
    // OPTIONAL IMAGE
    // ==========================================

    val imageResource: Int? = null,

    val remoteImageUrl: String? = null,

    val localImagePath: String? = null,

    val imageCaption: String? = null,

    // ==========================================
    // OPTIONAL WEBSITE LINK
    // ==========================================

    val websiteName: String? = null,

    val websiteUrl: String? = null
)