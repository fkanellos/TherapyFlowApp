package io.therapyflow.domain.service

import io.therapyflow.data.repository.FeatureRepository
import io.therapyflow.domain.error.AppError
import io.therapyflow.domain.model.PLAN_DEFAULTS
import io.therapyflow.domain.model.Plan
import java.util.*

class FeatureService(private val featureRepository: FeatureRepository) {

    suspend fun isEnabled(workspaceId: UUID, featureKey: String): Boolean {
        return featureRepository.findByWorkspaceAndKey(workspaceId, featureKey)
            ?.isEnabled ?: false
    }

    suspend fun requireFeature(workspaceId: UUID, featureKey: String) {
        if (!isEnabled(workspaceId, featureKey)) {
            throw AppError.FeatureNotEnabled(featureKey)
        }
    }

    /**
     * Provisions default features for a workspace based on its plan.
     * Called at workspace registration.
     */
    suspend fun provisionDefaults(workspaceId: UUID, plan: Plan) {
        val features = PLAN_DEFAULTS[plan] ?: emptySet()
        features.forEach { featureKey ->
            featureRepository.enable(workspaceId, featureKey, enabledBy = null)
        }
    }
}
