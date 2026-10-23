package com.xjtu.toolbox.nav

import android.content.Intent
import android.net.Uri
import com.xjtu.toolbox.agent.AgentArticleLink

/** xjtu 深链与“用屁岱阅读”入口。 */
object DeepLinkRouter {
    private const val SCHEME = "xjtu"
    const val PIDAI_LINK_ALIAS = "com.xjtu.toolbox.agent.ReadWithPidai"

    data class DeepLinkTarget(
        val route: AppRoute,
        /** 额外的 prompt，agent 场景会用到。 */
        val prompt: String? = null,
        val autoSend: Boolean = false,
    )

    fun resolve(intent: Intent): DeepLinkTarget? {
        if (intent.component?.className == PIDAI_LINK_ALIAS) {
            val item = intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)
            return articleTarget(intent.component?.className, intent.action, intent.dataString,
                listOfNotNull(intent.getCharSequenceExtra(Intent.EXTRA_TEXT), item?.text, item?.uri?.toString()))
        }
        val uri: Uri = intent.data ?: return null
        if (uri.scheme != SCHEME) return null
        return when (uri.host) {
            "agent" -> {
                val q = uri.getQueryParameter("q")?.takeIf { it.isNotBlank() }
                    ?: return DeepLinkTarget(AppRoute.Agent)
                DeepLinkTarget(AppRoute.Agent, q)
            }
            "schedule", "course" -> DeepLinkTarget(AppRoute.Schedule)
            "empty_room" -> DeepLinkTarget(AppRoute.EmptyRoom)
            "campus_card" -> DeepLinkTarget(AppRoute.CampusCard)
            "payment_code", "pay" -> DeepLinkTarget(AppRoute.PaymentCode)
            "notification" -> DeepLinkTarget(AppRoute.Notification)
            "score", "grade" -> DeepLinkTarget(AppRoute.JwappScore)
            else -> null
        }
    }

    internal fun articleTarget(component: String?, action: String?, data: String?, texts: List<CharSequence> = emptyList()): DeepLinkTarget? {
        if (component != PIDAI_LINK_ALIAS) return null
        val url = when (action) {
            Intent.ACTION_VIEW -> AgentArticleLink.fromUrl(data)
            Intent.ACTION_SEND -> texts.firstNotNullOfOrNull(AgentArticleLink::fromText)
            else -> null
        } ?: return null
        return DeepLinkTarget(AppRoute.Agent, AgentArticleLink.prompt(url), autoSend = true)
    }
}
