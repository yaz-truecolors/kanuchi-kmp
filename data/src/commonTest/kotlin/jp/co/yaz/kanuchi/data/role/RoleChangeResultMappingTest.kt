package jp.co.yaz.kanuchi.data.role

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.user.LastActiveAdminRequiredException
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoleChangeResultMappingTest {
    @Test
    fun `last active admin guard is mapped to last active admin required`() {
        assertIs<LastActiveAdminRequiredException>(toRoleChangeFailure(code = "P0001", message = "last_active_admin_required"))
    }

    @Test
    fun `other errors are not mapped`() {
        assertNull(toRoleChangeFailure(code = "P0001", message = "cannot_demote_self"))
        assertNull(toRoleChangeFailure(code = "P0001", message = "Only admins can change user roles"))
        assertNull(toRoleChangeFailure(code = "23505", message = "duplicate key value"))
        assertNull(toRoleChangeFailure(code = null, message = null))
    }

    @Test
    fun `update of a row succeeds`() {
        assertTrue(roleChangeResult(updatedRowCount = 1).isSuccess)
    }

    @Test
    fun `update of no rows fails`() {
        assertIs<GenericDataFailureException>(roleChangeResult(updatedRowCount = 0).exceptionOrNull())
    }
}
