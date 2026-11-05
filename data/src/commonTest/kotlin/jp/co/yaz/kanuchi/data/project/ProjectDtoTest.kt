package jp.co.yaz.kanuchi.data.project

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.project.DuplicateProjectNameException
import jp.co.yaz.kanuchi.domain.project.PersonalProjectInUseException
import jp.co.yaz.kanuchi.domain.project.Project
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class ProjectDtoTest {
    @Test
    fun `postgrest response is decoded and mapped to the domain model`() {
        val json = """{"id":"project-1","name":"案件A","is_active":false}"""

        val project = Json.decodeFromString<ProjectDto>(json).toDomain()

        assertEquals(Project(id = "project-1", name = "案件A", isActive = false), project)
    }

    @Test
    fun `personal project is decoded with the owner and the deletion`() {
        val json =
            """{"id":"project-2","name":"個人案件","is_active":true,"owner_id":"user-1","deleted_at":"2026-10-21T01:23:45.678+00:00"}"""

        val project = Json.decodeFromString<ProjectDto>(json).toDomain()

        assertEquals(Project(id = "project-2", name = "個人案件", isActive = true, ownerId = "user-1", isDeleted = true), project)
    }

    @Test
    fun `shared project with null owner is decoded as not personal and not deleted`() {
        val json = """{"id":"project-1","name":"案件A","is_active":true,"owner_id":null,"deleted_at":null}"""

        val project = Json.decodeFromString<ProjectDto>(json).toDomain()

        assertEquals(Project(id = "project-1", name = "案件A", isActive = true), project)
    }

    @Test
    fun `new personal project is encoded with the name and the owner`() {
        assertEquals(
            """{"name":"個人案件","owner_id":"user-1"}""",
            Json.encodeToString(NewPersonalProjectDto(name = "個人案件", ownerId = "user-1")),
        )
    }

    @Test
    fun `in use error on deleting a personal project is converted to the in use failure`() {
        val cause = RuntimeException("personal_project_in_use")

        val failure =
            personalProjectDeleteFailure(
                GenericDataFailureException(cause),
                postgresErrorCode = "P0001",
                message = "personal_project_in_use",
            )

        assertIs<PersonalProjectInUseException>(failure)
        assertSame(cause, failure.cause)
    }

    @Test
    fun `other failures on deleting a personal project are kept as generic failure`() {
        val original = GenericDataFailureException(RuntimeException("not found"))

        assertSame(original, personalProjectDeleteFailure(original, postgresErrorCode = "P0002", message = "personal_project_not_found"))
        assertSame(original, personalProjectDeleteFailure(original, postgresErrorCode = "P0001", message = "other"))
        assertSame(original, personalProjectDeleteFailure(original, postgresErrorCode = null, message = null))
    }

    @Test
    fun `new project is encoded with the name only`() {
        assertEquals("""{"name":"案件A"}""", Json.encodeToString(NewProjectDto(name = "案件A")))
    }

    @Test
    fun `assignment is encoded and decoded with snake case columns`() {
        val json = """{"user_id":"user-1","project_id":"project-1"}"""

        assertEquals(json, Json.encodeToString(UserProjectDto(userId = "user-1", projectId = "project-1")))
        assertEquals(UserProjectDto(userId = "user-1", projectId = "project-1"), Json.decodeFromString<UserProjectDto>(json))
    }

    @Test
    fun `unique violation on saving a project name is converted to duplicate name failure`() {
        val cause = RuntimeException("duplicate key value violates unique constraint")

        val failure = projectNameSaveFailure(GenericDataFailureException(cause), postgresErrorCode = "23505")

        assertIs<DuplicateProjectNameException>(failure)
        assertSame(cause, failure.cause)
    }

    @Test
    fun `other failures on saving a project name are kept as generic failure`() {
        val original = GenericDataFailureException(RuntimeException("permission denied"))

        assertSame(original, projectNameSaveFailure(original, postgresErrorCode = "42501"))
        assertSame(original, projectNameSaveFailure(original, postgresErrorCode = null))
    }
}
