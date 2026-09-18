package com.example.request

import androidx.room.withTransaction

object DatabaseSeeder {

    suspend fun seedIfEmpty(database: ERequestDatabase) {
        database.withTransaction {
            val dao = database.eRequestDao()

            if (dao.getAllServices().isNotEmpty()) {
                return@withTransaction
            }

            seedServices(dao)
            seedTransactions(dao)
            seedRequirements(dao)
            seedProcedureSteps(dao)
            seedDepartmentGuidelines(dao)
        }
    }

    private suspend fun seedServices(dao: ERequestDao) {
        dao.insertServices(
            listOf(
                ServiceEntity(
                    id = "service_001",
                    name = "Registrar Services",
                    description = "Documents, records and student requests"
                ),
                ServiceEntity(
                    id = "service_002",
                    name = "Academic Services",
                    description = "Academic procedures and student concerns"
                )
            )
        )
    }

    private suspend fun seedTransactions(dao: ERequestDao) {
        dao.insertTransactions(
            listOf(
                TransactionEntity(
                    id = "transaction_001",
                    serviceId = "service_001",
                    name = "Getting Prospectus",
                    description = "View the requirements and step-by-step procedure for getting a prospectus.",
                    officeName = "Registrar's Office",
                    officeSchedule = "Schedule will be provided by the administrator."
                ),
                TransactionEntity(
                    id = "transaction_002",
                    serviceId = "service_001",
                    name = "Updating Prospectus",
                    description = "View the requirements and step-by-step procedure for updating a prospectus.",
                    officeName = "Registrar's Office",
                    officeSchedule = "Schedule will be provided by the administrator."
                ),
                TransactionEntity(
                    id = "transaction_003",
                    serviceId = "service_002",
                    name = "Academic Concern",
                    description = "View the information and procedure for submitting an academic concern.",
                    officeName = "Academic Office",
                    officeSchedule = "Schedule will be provided by the administrator."
                )
            )
        )
    }

    private suspend fun seedRequirements(dao: ERequestDao) {
        dao.insertRequirements(
            listOf(
                RequirementEntity(
                    id = "requirement_001",
                    transactionId = "transaction_001",
                    requirementText = "Request Letter",
                    displayOrder = 1
                ),
                RequirementEntity(
                    id = "requirement_002",
                    transactionId = "transaction_001",
                    requirementText = "Valid School ID",
                    displayOrder = 2
                ),
                RequirementEntity(
                    id = "requirement_003",
                    transactionId = "transaction_001",
                    requirementText = "Required School Document",
                    displayOrder = 3
                ),
                RequirementEntity(
                    id = "requirement_004",
                    transactionId = "transaction_001",
                    requirementText = "Completed Request Form",
                    displayOrder = 4
                ),
                RequirementEntity(
                    id = "requirement_005",
                    transactionId = "transaction_001",
                    requirementText = "Other Supporting Requirement",
                    displayOrder = 5
                ),
                RequirementEntity(
                    id = "requirement_006",
                    transactionId = "transaction_002",
                    requirementText = "Summary of Grades (SOG)",
                    displayOrder = 1
                ),
                RequirementEntity(
                    id = "requirement_007",
                    transactionId = "transaction_002",
                    requirementText = "Completed Grades",
                    displayOrder = 2
                ),
                RequirementEntity(
                    id = "requirement_008",
                    transactionId = "transaction_002",
                    requirementText = "Supporting Academic Document",
                    displayOrder = 3
                ),
                RequirementEntity(
                    id = "requirement_009",
                    transactionId = "transaction_003",
                    requirementText = "Valid School ID",
                    displayOrder = 1
                ),
                RequirementEntity(
                    id = "requirement_010",
                    transactionId = "transaction_003",
                    requirementText = "Concern Details",
                    displayOrder = 2
                ),
                RequirementEntity(
                    id = "requirement_011",
                    transactionId = "transaction_003",
                    requirementText = "Supporting Document",
                    displayOrder = 3
                ),
                RequirementEntity(
                    id = "requirement_012",
                    transactionId = "transaction_003",
                    requirementText = "Completed Request Form",
                    displayOrder = 4
                )
            )
        )
    }

    private suspend fun seedProcedureSteps(dao: ERequestDao) {
        dao.insertProcedureSteps(
            listOf(
                ProcedureStepEntity(
                    id = "transaction_001_step_1",
                    transactionId = "transaction_001",
                    stepNumber = 1,
                    instruction = "Prepare all required documents."
                ),
                ProcedureStepEntity(
                    id = "transaction_001_step_2",
                    transactionId = "transaction_001",
                    stepNumber = 2,
                    instruction = "Prepare the required request letter using the sample format below.",
                    imageCaption = "Sample Request Letter Format"
                ),
                ProcedureStepEntity(
                    id = "transaction_001_step_3",
                    transactionId = "transaction_001",
                    stepNumber = 3,
                    instruction = "Proceed to the Registrar's Office and submit the requirements."
                ),
                ProcedureStepEntity(
                    id = "transaction_002_step_1",
                    transactionId = "transaction_002",
                    stepNumber = 1,
                    instruction = "Prepare your Summary of Grades (SOG)."
                ),
                ProcedureStepEntity(
                    id = "transaction_002_step_2",
                    transactionId = "transaction_002",
                    stepNumber = 2,
                    instruction = "Prepare the required supporting academic documents."
                ),
                ProcedureStepEntity(
                    id = "transaction_002_step_3",
                    transactionId = "transaction_002",
                    stepNumber = 3,
                    instruction = "Proceed to the Registrar's Office for verification."
                ),
                ProcedureStepEntity(
                    id = "transaction_003_step_1",
                    transactionId = "transaction_003",
                    stepNumber = 1,
                    instruction = "Prepare the details of your academic concern."
                ),
                ProcedureStepEntity(
                    id = "transaction_003_step_2",
                    transactionId = "transaction_003",
                    stepNumber = 2,
                    instruction = "Prepare any supporting documents related to your concern."
                ),
                ProcedureStepEntity(
                    id = "transaction_003_step_3",
                    transactionId = "transaction_003",
                    stepNumber = 3,
                    instruction = "Proceed to the appropriate academic office."
                )
            )
        )
    }

    private suspend fun seedDepartmentGuidelines(dao: ERequestDao) {
        dao.insertDepartmentGuidelines(
            listOf(
                DepartmentGuidelineEntity(
                    id = "guideline_001",
                    transactionId = "transaction_001",
                    guidelineText = "Make sure all required documents are complete before proceeding.",
                    displayOrder = 1
                ),
                DepartmentGuidelineEntity(
                    id = "guideline_002",
                    transactionId = "transaction_001",
                    guidelineText = "Follow the guidelines provided by the Registrar's Office.",
                    displayOrder = 2
                ),
                DepartmentGuidelineEntity(
                    id = "guideline_003",
                    transactionId = "transaction_001",
                    guidelineText = "Check the Registrar's Office schedule before processing the transaction.",
                    displayOrder = 3
                ),
                DepartmentGuidelineEntity(
                    id = "guideline_004",
                    transactionId = "transaction_002",
                    guidelineText = "Review your prospectus information before requesting an update.",
                    displayOrder = 1
                ),
                DepartmentGuidelineEntity(
                    id = "guideline_005",
                    transactionId = "transaction_002",
                    guidelineText = "Prepare all documents required by the Registrar's Office.",
                    displayOrder = 2
                ),
                DepartmentGuidelineEntity(
                    id = "guideline_006",
                    transactionId = "transaction_002",
                    guidelineText = "Follow the guidelines provided by the Registrar's Office.",
                    displayOrder = 3
                ),
                DepartmentGuidelineEntity(
                    id = "guideline_007",
                    transactionId = "transaction_003",
                    guidelineText = "Prepare the necessary information related to your academic concern.",
                    displayOrder = 1
                ),
                DepartmentGuidelineEntity(
                    id = "guideline_008",
                    transactionId = "transaction_003",
                    guidelineText = "Follow the guidelines provided by the Academic Office.",
                    displayOrder = 2
                )
            )
        )
    }
}