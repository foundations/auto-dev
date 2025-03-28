package cc.unitmesh.devti.observer.plan

import cc.unitmesh.devti.gui.AutoDevPlanerTooWindow
import cc.unitmesh.devti.gui.AutoDevToolWindowFactory
import cc.unitmesh.devti.gui.chat.message.ChatActionType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import cc.unitmesh.devti.observer.agent.AgentStateService

class EditPlanAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        val agentStateService = project.getService(AgentStateService::class.java)
        val currentPlan = agentStateService.getPlan()
        val planString = MarkdownPlanParser.formatPlanToMarkdown(currentPlan)

        AutoDevPlanerTooWindow.showPlanEditor(project, planString) { newPlan ->
            if (newPlan.isNotEmpty()) {
                /// todo: modify old messages
                AutoDevToolWindowFactory.sendToSketchToolWindow(project, ChatActionType.CHAT) { ui, _ ->
                    ui.setInput("Please follow the new plan to complete the task:\n# Plan\n```plan\n$newPlan\n```")
                }
            }
        }
    }
}
