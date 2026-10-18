package com.xjtu.toolbox.agent

import kotlinx.serialization.json.JsonObject

/**
 * 页面里的一块「屁岱卡」要做的事：屁岱读 [AgentInsights.run] 给的页面数据，必要时调 [tools] 里的工具，
 * 只回一个 JSON 对象，由页面自己画。
 *
 * 各 feature 模块不依赖 `:feature:agent`，只认这里的接口；实现由端的入口挂到 [AgentInsightHub]。
 */
class InsightSpec(
    /** 缓存键的一部分，也是日志里的名字，如 `notice_pick`。 */
    val id: String,
    /** 这块卡片要屁岱干什么、输出 JSON 的形状。 */
    val instruction: String,
    /** 允许调用的工具名；空 = 只看给它的数据。 */
    val tools: Set<String> = emptySet(),
    /** 同样的输入在这段时间内直接用缓存，不再花钱。 */
    val ttlMs: Long = 6 * 60 * 60 * 1000L,
    /** 要看的图（`data:image/…;base64,…`）：真要调模型时才在后台线程里取，模型不认图时不取。 */
    val images: () -> List<String> = { emptyList() },
    /** 开不开思考模式。挑选、汇总这类卡片关掉快得多；要排计划的才开。 */
    val thinking: Boolean = false,
    /** 加载时第一句状态（「在看平面图…」）。 */
    val busyHint: String = "在看…",
)

sealed interface InsightOutcome {
    /** [at] 是生成时刻（epoch 毫秒）；[cached] 表示这次没调模型。 */
    data class Ok(val json: JsonObject, val at: Long, val cached: Boolean) : InsightOutcome

    /** 没配 Key，或这一端没有屁岱。 */
    data object Unavailable : InsightOutcome

    data class Failed(val message: String) : InsightOutcome
}

interface AgentInsights {
    /** 这一端有没有屁岱卡（没有的端整块不画）。 */
    val available: Boolean

    /**
     * [input] 是页面交给屁岱的数据快照（纯文本）。同 [spec] + 同 [input] 在 TTL 内直接回缓存。
     * [onProgress] 报屁岱正在做什么（「查询通知公告…」），给加载态显示。
     */
    suspend fun run(spec: InsightSpec, input: String, force: Boolean = false, onProgress: (String) -> Unit = {}): InsightOutcome

    /** 只读缓存，不调模型（页面一进来先画上次的结果）。 */
    fun cached(spec: InsightSpec, input: String): InsightOutcome.Ok? = null

    /** 「接着问屁岱」：带上这块卡片的上下文跳进对话。[snapshot] 只发给模型，不进聊天气泡。 */
    fun ask(prompt: String, snapshot: String? = null) {}

    companion object {
        val None: AgentInsights = object : AgentInsights {
            override val available = false
            override suspend fun run(spec: InsightSpec, input: String, force: Boolean, onProgress: (String) -> Unit) =
                InsightOutcome.Unavailable
        }
    }
}

/** 端的入口在启动时挂上实现；桌面、网页不挂，屁岱卡整块不出现。 */
object AgentInsightHub {
    var insights: AgentInsights = AgentInsights.None
}
