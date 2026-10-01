package jp.co.yaz.kanuchi.domain.work

/**
 * ログイン中のユーザー自身の1日分の記録を保存するユースケース。
 * 入力の検証 ([WorkRecordInput.toWorkRecord]) と保存 ([WorkRecordRepository.saveWorkRecord]) を束ねる。
 * 入力が条件を満たさない場合は [InvalidWorkRecordException] で失敗し、保存しない。
 * 過不足 (稼働時間と配分の合計の差) があっても保存する。
 */
class SaveWorkRecordUseCase(
    private val workRecordRepository: WorkRecordRepository,
) {
    /**
     * @param entry 入力した日の情報 (日付・日の種類・勤務時間設定)
     * @param input 入力内容
     */
    suspend operator fun invoke(
        entry: WorkDayEntry,
        input: WorkRecordInput,
    ): Result<Unit> {
        val record = input.toWorkRecord(entry.day.date, entry.day.dayKind, entry.shiftSettings).getOrElse { return Result.failure(it) }
        return workRecordRepository.saveWorkRecord(record)
    }
}
