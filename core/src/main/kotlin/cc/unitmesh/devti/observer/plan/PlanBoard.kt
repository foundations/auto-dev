package cc.unitmesh.devti.observer.plan

import cc.unitmesh.devti.observer.agent.AgentStateService
import cc.unitmesh.devti.sketch.ui.PlanSketch
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory

@Service(Service.Level.PROJECT)
class PlanBoard(private val project: Project) : Disposable {
    var popup: JBPopup? = null
    var connection = ApplicationManager.getApplication().messageBus.connect(this)
    var planSketch: PlanSketch = PlanSketch(project, "", mutableListOf(), true)

    init {
        createPopup()
        connection.subscribe(PlanUpdateListener.TOPIC, object : PlanUpdateListener {
            override fun onPlanUpdate(items: MutableList<AgentPlan>) {
                planSketch.updatePlan(items)
                popup?.content?.updateUI()
            }
        })
    }

    private fun createPopup(): JBPopup? {
        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(planSketch, null)
            .setProject(project)
            .setResizable(true)
            .setMovable(true)
            .setTitle("Thought Plan")
            .setCancelButton(object: IconButton("Close", AllIcons.Actions.Cancel){})
            .setCancelCallback {
                popup?.cancel()
                true
            }
            .setFocusable(true)
            .setRequestFocus(true)
            .setCancelOnClickOutside(false)
            .setCancelOnOtherWindowOpen(false)
            .setCancelOnWindowDeactivation(false)
            .createPopup()

        return popup
    }

    fun updateShow() {
        val planLists = project.getService(AgentStateService::class.java).getPlan()
        planSketch.updatePlan(planLists)

        if (popup?.isVisible == true) return

        try {
            if (popup?.isDisposed == true) {
                createPopup()
            }

            popup?.content?.updateUI()
            popup?.showInFocusCenter()
        } catch (e: Exception) {
            logger<PlanBoard>().error("Failed to show popup", e)
        }
    }

    override fun dispose() {
        connection.disconnect()
    }
}