package io.therapyflow.data.repository

import io.therapyflow.data.db.publicTransaction
import io.therapyflow.data.table.WorkspaceTable
import io.therapyflow.domain.model.Workspace
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.*

class WorkspaceRepositoryImpl : WorkspaceRepository {

    override suspend fun findById(id: UUID): Workspace? = publicTransaction {
        WorkspaceTable.selectAll()
            .where { WorkspaceTable.id eq id }
            .map { it.toWorkspace() }
            .singleOrNull()
    }

    override suspend fun findBySlug(slug: String): Workspace? = publicTransaction {
        WorkspaceTable.selectAll()
            .where { WorkspaceTable.slug eq slug }
            .map { it.toWorkspace() }
            .singleOrNull()
    }

    override suspend fun create(workspace: Workspace): Workspace = publicTransaction {
        val now = Clock.System.now()
        WorkspaceTable.insert {
            it[id] = workspace.id
            it[name] = workspace.name
            it[slug] = workspace.slug
            it[plan] = workspace.plan
            it[status] = workspace.status
            it[createdAt] = now
            it[updatedAt] = now
        }
        workspace.copy(createdAt = now.toJavaInstant(), updatedAt = now.toJavaInstant())
    }

    override suspend fun slugExists(slug: String): Boolean = publicTransaction {
        WorkspaceTable.selectAll()
            .where { WorkspaceTable.slug eq slug }
            .count() > 0
    }

    private fun ResultRow.toWorkspace() = Workspace(
        id = this[WorkspaceTable.id],
        name = this[WorkspaceTable.name],
        slug = this[WorkspaceTable.slug],
        plan = this[WorkspaceTable.plan],
        status = this[WorkspaceTable.status],
        createdAt = this[WorkspaceTable.createdAt].toJavaInstant(),
        updatedAt = this[WorkspaceTable.updatedAt].toJavaInstant()
    )
}
