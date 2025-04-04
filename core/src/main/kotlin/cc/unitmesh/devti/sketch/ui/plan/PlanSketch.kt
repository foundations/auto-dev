package cc.unitmesh.devti.sketch.ui.plan

import cc.unitmesh.devti.observer.agent.AgentStateService
import cc.unitmesh.devti.observer.plan.AgentTaskEntry
import cc.unitmesh.devti.observer.plan.MarkdownPlanParser
import cc.unitmesh.devti.observer.plan.TaskStatus
import cc.unitmesh.devti.sketch.ui.ExtensionLangSketch
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/**
 * Controller class for managing the plan data and UI updates
 */
class PlanController(
    private val project: Project,
    private val contentPanel: JPanel,
    private var agentTaskItems: MutableList<AgentTaskEntry>
) {
    fun renderPlan() {
        contentPanel.removeAll()
        agentTaskItems.forEachIndexed { index, planItem ->
            val sectionPanel = SectionPanel(project, index, planItem) {
                contentPanel.revalidate()
                contentPanel.repaint()
            }

            contentPanel.add(sectionPanel)
        }

        contentPanel.revalidate()
        contentPanel.repaint()
    }

    fun updatePlan(newPlanItems: List<AgentTaskEntry>) {
        if (newPlanItems.isEmpty()) return

        val taskStateMap = mutableMapOf<String, Pair<Boolean, TaskStatus>>()
        agentTaskItems.forEach { planItem ->
            planItem.steps.forEach { task ->
                taskStateMap[task.step] = Pair(task.completed, task.status)
            }
        }

        agentTaskItems.clear()

        newPlanItems.forEach { newItem ->
            agentTaskItems.add(newItem)
            newItem.updateCompletionStatus()
        }

        renderPlan()
    }

    fun savePlanToService() {
        project.getService(AgentStateService::class.java).updatePlan(agentTaskItems)
    }
}

/**
 * Main PlanSketch class that integrates all components
 */
class PlanSketch(
    private val project: Project,
    private var content: String,
    private var agentTaskItems: MutableList<AgentTaskEntry>,
    private val isInToolwindow: Boolean = false
) : JBPanel<PlanSketch>(BorderLayout(JBUI.scale(8), 0)), ExtensionLangSketch {
    private val contentPanel = JPanel(VerticalLayout(JBUI.scale(0)))
    val scrollPane: JBScrollPane
    private val toolbarFactory = PlanToolbarFactory(project)
    private val planController = PlanController(project, contentPanel, agentTaskItems)

    init {
        if (!isInToolwindow) {
            add(toolbarFactory.createToolbar(this), BorderLayout.NORTH)
            border = JBUI.Borders.empty(8)
        }

        planController.renderPlan()

        scrollPane = JBScrollPane(contentPanel).apply {
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            border = JBUI.Borders.empty()

            viewport.isOpaque = false
            viewport.view = contentPanel
        }
        
        // Use a wrapper panel to ensure proper scroll behavior
        val wrapperPanel = JPanel(BorderLayout())
        wrapperPanel.add(scrollPane, BorderLayout.CENTER)
        wrapperPanel.background = JBUI.CurrentTheme.ToolWindow.background()
        
        add(wrapperPanel, BorderLayout.CENTER)

        minimumSize = Dimension(200, 0)
        background = JBUI.CurrentTheme.ToolWindow.background()
    }

    override fun getExtensionName(): String = "ThoughtPlan"

    override fun getViewText(): String = content

    override fun updateViewText(text: String, complete: Boolean) {
        this.content = text
        val agentPlans = MarkdownPlanParser.parse(text)
        planController.updatePlan(agentPlans)
    }

    override fun onComplete(context: String) {
        if (!isInToolwindow) {
            val agentPlans = MarkdownPlanParser.parse(content).toMutableList()
            planController.updatePlan(agentPlans)
            planController.savePlanToService()
        }
    }

    fun updatePlan(newPlanItems: List<AgentTaskEntry>) {
        planController.updatePlan(newPlanItems)
    }

    override fun getComponent(): JComponent = this

    override fun updateLanguage(language: Language?, originLanguage: String?) {}

    override fun dispose() {}
}

