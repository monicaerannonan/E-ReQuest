package com.example.request.features.transactions

import com.example.request.R
import com.example.request.data.local.*
import com.example.request.data.sync.*
import com.example.request.features.checklist.*
import com.example.request.features.home.*
import com.example.request.features.notifications.*
import com.example.request.features.progress.*
import com.example.request.features.services.*
import com.example.request.features.splash.*
import com.example.request.features.transactions.*
import com.example.request.ui.custom.*

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