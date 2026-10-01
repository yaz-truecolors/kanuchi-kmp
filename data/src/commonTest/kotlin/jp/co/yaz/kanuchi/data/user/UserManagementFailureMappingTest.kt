package jp.co.yaz.kanuchi.data.user

import jp.co.yaz.kanuchi.domain.user.AlreadyInvitedException
import jp.co.yaz.kanuchi.domain.user.LastActiveAdminRequiredException
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

class UserManagementFailureMappingTest {
    @Test
    fun `unique violation is mapped to already invited`() {
        assertIs<AlreadyInvitedException>(toUserManagementFailure(code = "23505", message = "duplicate key value"))
    }

    @Test
    fun `last active admin guard is mapped to last active admin required`() {
        assertIs<LastActiveAdminRequiredException>(
            toUserManagementFailure(code = "P0001", message = "last_active_admin_required"),
        )
    }

    @Test
    fun `other errors are not mapped`() {
        assertNull(toUserManagementFailure(code = "P0001", message = "cannot_suspend_self"))
        assertNull(toUserManagementFailure(code = "42501", message = "Only admins can suspend or reactivate users"))
        assertNull(toUserManagementFailure(code = null, message = null))
    }
}
