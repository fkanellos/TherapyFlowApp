package io.therapyflow.data.repository

import io.therapyflow.domain.model.Workspace
import java.util.*

interface WorkspaceRepository {
    suspend fun findById(id: UUID): Workspace?
    suspend fun findBySlug(slug: String): Workspace?
    suspend fun create(workspace: Workspace): Workspace
    suspend fun slugExists(slug: String): Boolean
}
