package com.example.request

object TransactionRepository {

    data class Transaction(
        val id: String,
        val serviceId: String,
        val name: String,
        val serviceName: String,
        val requirements: List<String>,
        val procedures: List<ProcedureStep>
    )

    private val transactions = listOf(

        // =========================================
        // GETTING PROSPECTUS
        // =========================================
        Transaction(
            id = "transaction_001",
            serviceId = "service_001",
            name = "Getting Prospectus",
            serviceName = "Registrar Services",
            requirements = listOf(
                "Request Letter",
                "Valid School ID",
                "Required School Document",
                "Completed Request Form",
                "Other Supporting Requirement"
            ),
            procedures = listOf(
                ProcedureStep(
                    id = "transaction_001_step_1",
                    stepNumber = 1,
                    instruction = "Prepare all required documents."
                ),
                ProcedureStep(
                    id = "transaction_001_step_2",
                    stepNumber = 2,
                    instruction = "Prepare the required request letter using the sample format below.",
                    imageResource = R.drawable.sample_request_letter,
                    imageCaption = "Sample Request Letter Format"
                ),
                ProcedureStep(
                    id = "transaction_001_step_3",
                    stepNumber = 3,
                    instruction = "Proceed to the Registrar's Office and submit the requirements."
                )
            )
        ),

        // =========================================
        // UPDATING PROSPECTUS
        // =========================================
        Transaction(
            id = "transaction_002",
            serviceId = "service_001",
            name = "Updating Prospectus",
            serviceName = "Registrar Services",
            requirements = listOf(
                "Summary of Grades (SOG)",
                "Completed Grades",
                "Supporting Academic Document"
            ),
            procedures = listOf(
                ProcedureStep(
                    id = "transaction_002_step_1",
                    stepNumber = 1,
                    instruction = "Prepare your Summary of Grades (SOG)."
                ),
                ProcedureStep(
                    id = "transaction_002_step_2",
                    stepNumber = 2,
                    instruction = "Prepare the required supporting academic documents."
                ),
                ProcedureStep(
                    id = "transaction_002_step_3",
                    stepNumber = 3,
                    instruction = "Proceed to the Registrar's Office for verification."
                )
            )
        ),

        // =========================================
        // ACADEMIC CONCERN
        // =========================================
        Transaction(
            id = "transaction_003",
            serviceId = "service_002",
            name = "Academic Concern",
            serviceName = "Academic Services",
            requirements = listOf(
                "Valid School ID",
                "Concern Details",
                "Supporting Document",
                "Completed Request Form"
            ),
            procedures = listOf(
                ProcedureStep(
                    id = "transaction_003_step_1",
                    stepNumber = 1,
                    instruction = "Prepare the details of your academic concern."
                ),
                ProcedureStep(
                    id = "transaction_003_step_2",
                    stepNumber = 2,
                    instruction = "Prepare any supporting documents related to your concern."
                ),
                ProcedureStep(
                    id = "transaction_003_step_3",
                    stepNumber = 3,
                    instruction = "Proceed to the appropriate academic office."
                )
            )
        )
    )

    // =========================================
    // GET ALL TRANSACTIONS
    // =========================================
    fun getAllTransactions(): List<Transaction> {
        return transactions
    }

    // =========================================
    // GET ONE TRANSACTION
    // =========================================
    fun getTransaction(transactionId: String): Transaction? {
        return transactions.find {
            it.id == transactionId
        }
    }

    // =========================================
    // GET TRANSACTIONS BY SERVICE
    // =========================================
    fun getTransactionsByService(serviceId: String): List<Transaction> {
        return transactions.filter {
            it.serviceId == serviceId
        }
    }

    // =========================================
    // GET REQUIREMENTS
    // =========================================
    fun getRequirements(transactionId: String): List<String> {
        return getTransaction(transactionId)
            ?.requirements
            ?: emptyList()
    }

    // =========================================
    // GET REQUIREMENT COUNT
    // =========================================
    fun getRequirementCount(transactionId: String): Int {
        return getRequirements(transactionId).size
    }

    // =========================================
    // GET PROCEDURES
    // =========================================
    fun getProcedures(transactionId: String): List<ProcedureStep> {
        return getTransaction(transactionId)
            ?.procedures
            ?: emptyList()
    }
}