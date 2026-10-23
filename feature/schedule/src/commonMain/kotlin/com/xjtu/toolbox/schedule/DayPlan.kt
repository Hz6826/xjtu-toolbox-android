package com.xjtu.toolbox.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xjtu.toolbox.agent.InsightSpec
import com.xjtu.toolbox.agent.PidaiCard
import com.xjtu.toolbox.campusmap.CampusMaps
import com.xjtu.toolbox.inbox.SelfTodoRules
import com.xjtu.toolbox.inbox.SelfTodoStore
import com.xjtu.toolbox.ui.components.AppInsetColor
import com.xjtu.toolbox.ui.components.CampusMapView
import com.xjtu.toolbox.ui.components.MapMark
import com.xjtu.toolbox.ui.components.PidaiChip
import com.xjtu.toolbox.ui.components.PidaiChipRow
import com.xjtu.toolbox.ui.components.PidaiInputRow
import com.xjtu.toolbox.ui.components.PidaiInsightCard
import com.xjtu.toolbox.ui.components.rememberInsight
import com.xjtu.toolbox.util.safeStringOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 「一句话排今天」：照今天的课、考试、截止和用户一句话，排出从现在到睡前的安排和路线。 */
private val DAY_PLAN = InsightSpec(
    id = "day_plan_v2",
    tools = setOf("get_schedule", "get_exam_schedule", "campus_map", "my_location", "get_empty_rooms", "get_library", "app_guide", "get_card_info"),
    ttlMs = 2 * 60 * 60 * 1000L,
    thinking = true,
    busyHint = "在排…",
    instruction = """
任务：整理用户明确要求的目标日期日程，保留原意、事项、时间、地点与约束，不自行扩展活动。
- 固定安排以给定快照或工具结果为准；目标不是今天时，查询目标日期的课表和考试，不套用今日快照。
- blocks 只包含已确认的课程、考试以及用户明确要求的事项。空闲时段保持空闲，不依据学籍、临近截止或生活惯例填满全天。
- 用户未要求学习活动时，不添加学习活动。建议与已确认安排分开，只写进 tips；建议未经采纳不进入 blocks。
- 执行所必需的时间或地点不明确时，在 tips 中说明待补充信息，不编造确定值。截止时间不等同于任务开始时间或持续时长。
- 仅在用户要求地点推荐或路线安排时查询位置；画像校区和上课地点不能视为当前位置。无可靠当前位置时保持未知。
- 确需跨地点移动时依据地图结果评估可达性，把时间冲突或尚未确认的路程写入 tips，不挪动用户指定的安排。
- 目标是今天时省略已结束事项；没有符合条件的安排时 blocks 为空，summary 如实说明。
只输出：
{"summary":"不超过 30 字，这一天的主线","blocks":[{"start":"14:00","end":"15:50","title":"不超过 12 字","place":"楼名/食堂/图书馆区域，没有就空串","kind":"class|exam|study|meal|move|sport|rest|other","note":"不超过 20 字，可空"}],"tips":["最多两条，不超过 25 字"]}
    """.trimIndent(),
    card = PidaiCard.DAY_PLAN,
)

/** 交给屁岱的今日快照（本地已有的数据，屁岱就不必再查课表）。 */
internal fun dayPlanSnapshot(today: LocalDate, entries: List<String>, upcoming: List<UpcomingItem>): String = buildString {
    append("日期：$today\n今天的固定安排（开始–结束｜事项｜地点）：\n")
    if (entries.isEmpty()) append("- 无\n") else entries.forEach { append("- $it\n") }
    if (upcoming.isNotEmpty()) {
        append("接下来几天：\n")
        upcoming.take(8).forEach { append("- ${it.title}｜${it.subtitle}\n") }
    }
}

@Composable
internal fun DayPlanCard(snapshot: String, today: LocalDate, modifier: Modifier = Modifier) {
    var draft by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable(today.toString()) { mutableStateOf(today.toString()) }
    var asked by rememberSaveable(today.toString()) { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val targetDate = remember(target) { LocalDate.parse(target) }
    val state = rememberInsight(
        DAY_PLAN,
        asked?.let { said ->
            buildString {
                append("目标日期：$targetDate ${dayLabel(targetDate, today)}\n")
                append("用户说：${said.ifBlank { "（仅整理已确认的固定安排）" }}\n")
                if (targetDate != today) append("（那天的课和考试请自己查；下面是今天的情况，供参考）\n")
                append(snapshot)
            }
        },
    )
    fun submit() {
        val text = draft.trim()
        // 同一句话、同一天再点一次 = 重排（不然只会拿回缓存）
        if (text == asked) state.refresh() else asked = text
    }
    val choices = remember(today) { dayChoices(today) }
    PidaiInsightCard(
        title = "屁岱排日程",
        state = state,
        modifier = modifier,
        subtitle = "选一天、说一句，按你的要求整理",
        askPrompt = { "你排的${dayLabel(targetDate, today)}这个安排，再帮我调一调。" },
        controls = {
            PidaiChipRow {
                choices.forEach { (label, date) ->
                    PidaiChip(label, selected = date == targetDate, onClick = { target = date.toString() })
                }
                PidaiChip(
                    if (targetDate in choices.map { it.second }) "选日期" else "$targetDate",
                    icon = Icons.Filled.CalendarMonth,
                    selected = targetDate !in choices.map { it.second },
                    onClick = { picking = true },
                )
            }
            PidaiInputRow(
                value = draft,
                onValueChange = { draft = it },
                placeholder = if (targetDate == today) "下午复习高数，晚上想打球…" else "那天想做什么？",
                onSubmit = { submit() },
                enabled = !state.loading,
            )
            PidaiChipRow {
                DAY_PLAN_PRESETS.forEach { p ->
                    PidaiChip(p, onClick = { draft = if (draft.isBlank()) p else "$draft，$p" })
                }
            }
        },
    ) { json ->
        json["summary"].safeStringOrNull()?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Medium)
        }
        val blocks = (json["blocks"] as? JsonArray).orEmpty().mapNotNull { runCatching { it.jsonObject }.getOrNull() }
        blocks.forEach { PlanRow(it, targetDate) }
        DayPlanMap(blocks)
        (json["tips"] as? JsonArray).orEmpty().mapNotNull { it.safeStringOrNull() }.filter { it.isNotBlank() }.forEach {
            Text("· $it", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
    com.xjtu.toolbox.ui.components.AppDatePickerDialog(
        show = picking,
        title = "排哪一天",
        date = targetDate,
        minDate = today,
        maxDate = today.plus(60, DateTimeUnit.DAY),
        onDismiss = { picking = false },
        onConfirm = { target = it.toString(); picking = false },
    )
}

/** 今天、明天、后天，加上最近的周六周日（已在前三天里的不重复）。 */
private fun dayChoices(today: LocalDate): List<Pair<String, LocalDate>> {
    val base = listOf("今天" to today, "明天" to today.plus(1, DateTimeUnit.DAY), "后天" to today.plus(2, DateTimeUnit.DAY))
    val weekend = (1..7).map { today.plus(it, DateTimeUnit.DAY) }
        .filter { it.dayOfWeek.isoDayNumber >= 6 && base.none { b -> b.second == it } }
        .take(2)
        .map { (if (it.dayOfWeek.isoDayNumber == 6) "周六" else "周日") to it }
    return base + weekend
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "（今天）"
    today.plus(1, DateTimeUnit.DAY) -> "（明天）"
    today.plus(2, DateTimeUnit.DAY) -> "（后天）"
    else -> "（周${"一二三四五六日"[date.dayOfWeek.isoDayNumber - 1]}）"
}

@Composable
private fun PlanRow(block: JsonObject, today: LocalDate) {
    val start = block["start"].safeStringOrNull().orEmpty()
    val title = block["title"].safeStringOrNull().orEmpty().ifBlank { return }
    val kind = block["kind"].safeStringOrNull().orEmpty()
    val fixed = kind == "class" || kind == "exam"
    var reminded by remember(start, title) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (fixed) MiuixTheme.colorScheme.primary.copy(alpha = 0.08f) else AppInsetColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(52.dp)) {
            Text(start, style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Bold,
                color = if (fixed) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
            block["end"].safeStringOrNull()?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Medium)
            val sub = listOfNotNull(block["place"].safeStringOrNull(), block["note"].safeStringOrNull()).filter { it.isNotBlank() }
            if (sub.isNotEmpty()) {
                Text(sub.joinToString(" · "), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        // 课和考试本来就有提醒；屁岱排进来的那些可以顺手记成到点提醒的待办
        if (!fixed && Regex("""\d{1,2}:\d{2}""").matches(start)) {
            PidaiChip(
                if (reminded) "已提醒" else "提醒我",
                icon = if (reminded) Icons.Filled.Check else Icons.Filled.NotificationsActive,
                enabled = !reminded,
                onClick = {
                    val due = SelfTodoRules.parseDue("$today ${start.padStart(5, '0')}") ?: return@PidaiChip
                    reminded = SelfTodoStore.create(
                        title = title,
                        content = block["place"].safeStringOrNull().orEmpty(),
                        dueDate = due.first,
                        source = "agent",
                        allDay = due.second,
                        remind = true,
                    ) != null
                },
            )
        }
    }
}

/** 今天要去的地方按顺序连成路线；地图上找不到的地点列在下面，不悄悄丢掉。 */
@Composable
private fun DayPlanMap(blocks: List<JsonObject>) {
    val places = remember(blocks) {
        blocks.mapNotNull { b -> b["place"].safeStringOrNull()?.trim()?.takeIf { it.isNotBlank() } }.distinct()
    }
    val resolved = remember(places) { places.associateWith { CampusMaps.find(it) } }
    val stops = remember(blocks, resolved) {
        blocks.mapNotNull { b -> b["place"].safeStringOrNull()?.trim()?.let { resolved[it] } }
            .fold(mutableListOf<Pair<com.xjtu.toolbox.campusmap.CampusMapData, com.xjtu.toolbox.campusmap.CampusPlace>>()) { acc, hit ->
                if (acc.lastOrNull()?.second !== hit.second) acc += hit
                acc
            }
    }
    val missing = resolved.filterValues { it == null }.keys
    val map = stops.groupingBy { it.first }.eachCount().maxByOrNull { it.value }?.key
    if (map != null) {
        val marks = stops.filter { it.first === map }.mapIndexed { i, (_, p) -> MapMark(p, p.name, order = i + 1) }
        CampusMapView(map, marks, route = true, height = 240.dp)
    }
    if (missing.isNotEmpty()) {
        Text(
            "地图上暂时没有：${missing.joinToString("、")}",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 一键填进输入框的常用说法。 */
private val DAY_PLAN_PRESETS = listOf("复习备考", "赶作业", "去图书馆自习", "运动一下", "早点睡", "约朋友吃饭")
