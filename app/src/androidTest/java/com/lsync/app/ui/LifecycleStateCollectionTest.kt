package com.lsync.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Runs with the real ComponentActivity lifecycle/CompositionLocal, no cloud or user data. */
class LifecycleStateCollectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun collectionStopsInBackgroundAndResumesWithLatestValue() {
        val source = MutableStateFlow("initial")
        compose.setContent {
            val value by source.collectAsStateWithLifecycle()
            Text(value)
        }
        compose.onNodeWithText("initial").assertIsDisplayed()
        compose.waitUntil(5_000) { source.subscriptionCount.value == 1 }

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil(5_000) { source.subscriptionCount.value == 0 }
        source.value = "updated while stopped"
        assertEquals(0, source.subscriptionCount.value)

        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("updated while stopped").assertIsDisplayed()
        compose.waitUntil(5_000) { source.subscriptionCount.value == 1 }
    }

    @Test
    fun removingContentCancelsItsSubscription() {
        val source = MutableStateFlow("visible")
        var show by mutableStateOf(true)
        compose.setContent {
            if (show) {
                val value by source.collectAsStateWithLifecycle()
                Text(value)
            }
        }
        compose.onNodeWithText("visible").assertIsDisplayed()
        compose.waitUntil(5_000) { source.subscriptionCount.value == 1 }
        compose.runOnIdle { show = false }
        compose.waitUntil(5_000) { source.subscriptionCount.value == 0 }
    }
}
