package jp.co.yaz.kanuchi.domain.work

import kotlinx.datetime.LocalDate

/**
 * ログイン中のユーザー自身の1日分の記録 (案件ごとの配分を含む) を削除するユースケース。
 * 削除した日は「何も入力していない日」に戻る (稼働日なら定時どおり働いたとみなす)。
 */
class DeleteWorkRecordUseCase(
    private val workRecordRepository: WorkRecordRepository,
) {
    suspend operator fun invoke(date: LocalDate): Result<Unit> = workRecordRepository.deleteWorkRecord(date)
}
