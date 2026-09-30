package jp.co.yaz.kanuchi.presentation.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * ViewModel のテストの基底クラス。viewModelScope が使う Dispatchers.Main をテスト用に差し替える。
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest {
    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }
}
