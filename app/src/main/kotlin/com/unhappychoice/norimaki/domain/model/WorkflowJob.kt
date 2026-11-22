package com.unhappychoice.norimaki.domain.model

import com.google.gson.annotations.SerializedName

data class WorkflowJob(
    val id: String,
    val name: String,
    val type: String,
    val status: String,
    @SerializedName("job_number") val jobNumber: Int?,
    @SerializedName("approval_request_id") val approvalRequestId: String?
) {
    val pendingApprovalId: String?
        get() = approvalRequestId?.takeIf { it.isNotBlank() && type == "approval" && status == "on_hold" }
}
