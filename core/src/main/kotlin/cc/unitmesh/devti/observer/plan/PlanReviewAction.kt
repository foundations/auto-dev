package cc.unitmesh.devti.observer.plan

import cc.unitmesh.devti.AutoDevNotifications
import cc.unitmesh.devti.llms.LlmFactory
import cc.unitmesh.devti.observer.agent.AgentStateService
import cc.unitmesh.devti.statusbar.AutoDevStatus
import cc.unitmesh.devti.statusbar.AutoDevStatusService
import cc.unitmesh.devti.template.GENIUS_CODE
import cc.unitmesh.devti.template.TemplateRender
import cc.unitmesh.devti.util.AutoDevCoroutineScope
import cc.unitmesh.devti.util.parser.CodeFence
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.visitors.RecursiveVisitor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

class PlanReviewAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project ?: return
        val agentStateService = project.getService(AgentStateService::class.java)

        val currentPlan = agentStateService.getPlan()
        val plan = MarkdownPlanParser.formatPlanToMarkdown(currentPlan)

        val allMessages = agentStateService.getAllMessages()
        val withoutCodeMsgs = allMessages.map {
            it.copy(role = it.role, content = removeAllMarkdownCode(it.content))
        }

        val templateRender = TemplateRender(GENIUS_CODE)
        val systemPrompt = templateRender.getTemplate("plan-reviewer.vm")
        val history = withoutCodeMsgs.joinToString {
            "# Role ${it.role}\nMessage:\n${it.content}"
        } + "\nLastPlan: \n$plan\n"


        val stream = LlmFactory.create(project).stream(history, systemPrompt)
        AutoDevCoroutineScope.scope(project).launch {
            val llmResult = StringBuilder()
            AutoDevStatusService.notifyApplication(AutoDevStatus.InProgress, "review the plan")
            runBlocking {
                stream.collect {
                    llmResult.append(it)
                }
            }

            val result = llmResult.toString()
            AutoDevStatusService.notifyApplication(AutoDevStatus.Done, "review the plan")
            AutoDevNotifications.notify(project, result)
            val plan = CodeFence.parseAll(result).firstOrNull {
                it.originLanguage == "plan"
            }

            if (plan !== null) {
                agentStateService.updatePlan(plan.text)
            }
        }
    }
}

fun removeAllMarkdownCode(markdownContent: String): String {
    if (markdownContent.isEmpty()) return markdownContent

    val flavour = GFMFlavourDescriptor()
    val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdownContent)

    val codeBlockReplacements = mutableListOf<Pair<IntRange, String>>()

    parsedTree.accept(object : RecursiveVisitor() {
        override fun visitNode(node: ASTNode) {
            if (node.type == MarkdownElementTypes.CODE_FENCE) {
                val language = extractCodeFenceLanguage(node, markdownContent)
                val replacement = "```$language\n// you can skip this part of the code.\n```"
                codeBlockReplacements.add(node.startOffset..node.endOffset to replacement)
            } else if (node.type == MarkdownElementTypes.CODE_BLOCK) {
                val replacement = "```\n// you can skip this part of the code.\n```"
                codeBlockReplacements.add(node.startOffset..node.endOffset to replacement)
            } else {
                super.visitNode(node)
            }
        }
    })

    codeBlockReplacements.sortByDescending { it.first.first }

    val result = StringBuilder(markdownContent)
    for ((range, replacement) in codeBlockReplacements) {
        result.replace(range.first, range.last, replacement)
    }

    return result.toString()
}

private fun extractCodeFenceLanguage(node: ASTNode, markdownContent: String): String {
    val nodeText = node.getTextInNode(markdownContent).toString()
    val firstLine = nodeText.lines().firstOrNull() ?: ""

    val languageMatch = Regex("^```(.*)$").find(firstLine.trim())
    return languageMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
}
