package br.com.auron.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.auron.MainActivity
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real Activity with Room and DataStore. It adds identifiable test records
 * without deleting existing data. Execute on a dedicated test device/profile.
 * Activity recreation exercises configuration restoration, not process death.
 */
@RunWith(AndroidJUnit4::class)
class AuronSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun everyMainDestinationExposesItsUsableContent() {
        navigate("tasks")
        awaitText("Tarefas & operações")
        compose.onNodeWithTag("task_add").assertIsDisplayed()

        navigate("routine")
        awaitText("Ritmo do dia")
        compose.onNodeWithTag("routine_morning").assertIsDisplayed()

        navigate("chat")
        compose.onNodeWithTag("chat_input").assertIsDisplayed()
        compose.onNodeWithText(
            "Demonstração local. Modo demonstrativo: comandos locais e respostas programadas, sem modelo de IA.",
        ).assertIsDisplayed()

        navigate("history")
        awaitText("O que você concluiu, registrado no seu dispositivo.")

        navigate("modules")
        awaitText("Um sistema. Novas possibilidades.")
        compose.onNodeWithTag("screen_modules").performScrollToNode(hasText("MAPA DE EVOLUÇÃO · AINDA NÃO IMPLEMENTADO"))
        compose.onNodeWithText("MAPA DE EVOLUÇÃO · AINDA NÃO IMPLEMENTADO").assertIsDisplayed()

        navigate("automation")
        compose.onNodeWithTag("screen_automation").assertIsDisplayed()
        compose.onNodeWithTag("automation_access").performScrollTo().assertIsDisplayed()

        navigate("settings")
        awaitText("Do seu jeito")
        compose.onNodeWithTag("settings_name").assertIsDisplayed()

        navigate("dashboard")
        awaitText("CENTRO DE COMANDO")
    }

    @Test
    fun taskCanBeCreatedCompletedAndReadInHistoryAcrossActivityRecreation() {
        val title = "Teste AURON ${UUID.randomUUID()}"
        navigate("tasks")
        awaitText("Tarefas & operações")
        compose.onNodeWithTag("task_add").performClick()
        compose.onNodeWithTag("task_title").performTextInput(title)
        closeSoftKeyboard()
        compose.onNodeWithTag("task_save").performClick()
        revealTask(title)
        compose.onNodeWithContentDescription("Concluir $title").assertIsOff()

        compose.activityRule.scenario.recreate()
        navigate("tasks")
        revealTask(title)
        compose.onNodeWithContentDescription("Concluir $title").assertIsOff().performClick()
        compose.waitUntil(timeoutMillis = TIMEOUT) {
            compose.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty()
        }

        navigate("history")
        awaitText(title)
        compose.onNodeWithText(title).assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        navigate("history")
        awaitText(title)
        compose.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun nightCommandActuallyOpensTheNightChecklist() {
        navigate("routine")
        awaitText("Ritmo do dia")
        compose.onNodeWithTag("routine_morning").performClick().assertIsSelected()

        navigate("chat")
        compose.onNodeWithTag("chat_input").performTextInput("Boa noite, AURON.")
        closeSoftKeyboard()
        compose.onNodeWithTag("chat_send").performClick()

        awaitText("Ritmo do dia")
        compose.onNodeWithTag("routine_night").assertIsSelected()
    }

    private fun navigate(destination: String) {
        compose.waitUntil(timeoutMillis = TIMEOUT) {
            when {
                compose.onAllNodesWithTag("intro_skip").fetchSemanticsNodes().isNotEmpty() -> {
                    runCatching { compose.onNodeWithTag("intro_skip").performClick() }
                    false
                }
                compose.onAllNodesWithTag("auth_local").fetchSemanticsNodes().isNotEmpty() -> {
                    // A fresh installation now opens authentication. These legacy
                    // smoke scenarios explicitly select the isolated local profile.
                    runCatching { compose.onNodeWithTag("auth_local").performScrollTo().performClick() }
                    false
                }
                else -> compose.onAllNodesWithTag("nav_dashboard").fetchSemanticsNodes().isNotEmpty()
            }
        }
        val node = compose.onNodeWithTag("nav_$destination")
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }

    private fun awaitText(value: String) {
        compose.waitUntil(timeoutMillis = TIMEOUT) {
            compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun revealTask(title: String) {
        // Room writes are asynchronous. A large existing list can also place the
        // new task outside the composed viewport, so scroll to the outcome.
        compose.waitUntil(timeoutMillis = TIMEOUT) {
            runCatching {
                compose.onNodeWithTag("tasks_list").performScrollToNode(hasText(title))
            }.isSuccess
        }
        compose.onNodeWithText(title).assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
