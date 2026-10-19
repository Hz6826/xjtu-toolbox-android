package com.xjtu.toolbox.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xjtu.toolbox.bulletin.BulletinNoticePanel
import com.xjtu.toolbox.nav.AppRoute
import com.xjtu.toolbox.nav.expandOriginSource
import com.xjtu.toolbox.nav.rememberExpandOriginSource
import com.xjtu.toolbox.settings.SettingsStore
import com.xjtu.toolbox.ui.components.AppCardColor
import com.xjtu.toolbox.ui.components.ExpressiveIcon
import com.xjtu.toolbox.ui.components.GradientAppIcon
import com.xjtu.toolbox.ui.components.MeshBackground
import com.xjtu.toolbox.ui.components.RollingNumberText
import com.xjtu.toolbox.ui.components.appCardShadow
import com.xjtu.toolbox.ui.components.enterOnce
import com.xjtu.toolbox.ui.glass.LocalGlassStyle
import com.xjtu.toolbox.ui.glass.glassBarSurface
import com.xjtu.toolbox.ui.glass.glassBarTint
import com.xjtu.toolbox.ui.isWideLayout
import com.xjtu.toolbox.ui.theme.Editorial
import com.xjtu.toolbox.ui.theme.EditorialRule
import com.xjtu.toolbox.ui.theme.LocalIsDarkTheme
import com.xjtu.toolbox.ui.theme.legacyColor
import com.xjtu.toolbox.ui.theme.serviceColor
import com.xjtu.toolbox.util.todayOnCampus
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** 首页屏。 */
@Composable
fun HomeScreen(
    host: HomeHost,
    modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null,
    /** 悬浮底栏的总占位高度：底栏浮在内容之上，页面末尾得自己留出来。 */
    extraBottomPadding: Dp = 0.dp,
    /** 玻璃顶栏的高度：内容铺到顶栏下面，这段留白放进滚动内容里。经典风格为 0。 */
    contentTopPadding: Dp = 0.dp,
    /** 首页没有顶栏：消息、扫一扫、搜索这几个按钮由宿主给，放在页面最上面跟着内容一起滚。 */
    topActions: (@Composable () -> Unit)? = null,
) {
    val honored = host.honoredCards
    // 用目录全量：图标配色按它在目录里的下标取，过滤会让配色挪位。
    val catalog = remember(host.audience) { AppServices.homeFor(host.audience) }
    // `serviceColor` / `legacyColor` 是 @Composable，不能放进 `remember`。
    val iconColorByKey = catalog.mapIndexed { index, svc -> svc.route.id to serviceColor(index, catalog.size) }.toMap()
    val legacyColorByKey = catalog.associate { it.route.id to legacyColor(it.route.id) }
    val isIconTheme = host.homeTheme == SettingsStore.THEME_ICON

    fun rowOf(service: AppService, stat: HomeStat?): HomeServiceRow {
        val id = service.route.id
        return HomeServiceRow(
            key = service.route.id,
            icon = HomeIcons.of(service.route),
            title = service.title,
            subtitle = service.subtitle,
            color = if (isIconTheme) iconColorByKey[id] ?: legacyColorByKey[id] ?: Color.Unspecified
            else legacyColorByKey[id] ?: Color.Unspecified,
            onClick = { host.onRoute(service.route) },
            stat = stat?.value,
            statDetail = stat?.detail,
        )
    }

    val quickRows = host.quickRoutes.mapNotNull { route ->
        catalog.firstOrNull { it.route == route }?.let { rowOf(it, null) }
    }
    val groups = HomeCards.groups(host.audience, host.stats, host.openableRouteIds)
    // 杂志版式的头版：各功能此刻的状态都收进拼版，和速览、下一节课重复的不再列
    val frontFigures = if (isIconTheme || HomeCard.CATEGORY_STATS !in honored) emptyList() else {
        val covered = buildSet {
            host.glances.takeIf { HomeCard.GLANCE in honored }.orEmpty().forEach { g -> g.route?.let { add(it.id) } }
            if (HomeCard.NEXT_UP in honored && host.nextUp != null) add(AppRoute.Schedule.id)
        }
        groups.flatMap { it.rows }
            .filter { it.stat != null && it.key !in covered }
            .map { rowOf(it.service, HomeStat(it.stat!!, it.statDetail)) }
    }
    val frontInbox = host.inbox.takeIf { !isIconTheme && HomeCard.INBOX in honored }

    val scroll = rememberScrollState()
    // 有宿主给的按钮才有折叠顶栏（桌面、Web 的壳有自己的顶栏）
    val collapsible = topActions != null
    val masthead = !isIconTheme && HomeCard.GREETING in honored
    val barPx = with(LocalDensity.current) { BAR_HEIGHT.toPx() }
    var mastheadPx by remember { mutableIntStateOf(0) }
    // 刊头底边碰到顶栏底边时折叠完成；没有刊头时只让顶栏底色在一栏高度内浮出来
    val rangePx = if (masthead && mastheadPx > 0) (mastheadPx - barPx).coerceAtLeast(barPx) else barPx
    val progress = { (scroll.value / rangePx).coerceIn(0f, 1f) }
    if (collapsible) SnapCollapse(scroll, rangePx)
    // 顶栏的玻璃采这一页自己的滚动层：顶栏本身在壳的采样层里，采壳的层会采到自己
    val pageBackdrop = rememberLayerBackdrop()
    val glass = LocalGlassStyle.current && collapsible

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .then(if (glass) Modifier.layerBackdrop(pageBackdrop) else Modifier)
                .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier)
                .overScrollVertical()
                .verticalScroll(scroll)
        ) {
            Spacer(Modifier.height(contentTopPadding))
            if (masthead) {
                EditorialMasthead(
                    greeting = Greeting.of(),
                    name = host.greetingName,
                    weekNumber = if (HomeCard.WEEK_NUMBER in honored) host.weekNumber else 0,
                    status = host.statusLine,
                    onStatus = host::onOpenStatus,
                    underBar = collapsible,
                    progress = progress,
                    modifier = Modifier.enterOnce(0).onSizeChanged { mastheadPx = it.height },
                )
            } else if (collapsible) {
                Spacer(Modifier.height(BAR_HEIGHT))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = if (masthead) 6.dp else 8.dp)
            ) {
                if (HomeCard.BULLETIN_BAR in honored && host.bulletins.isNotEmpty()) {
                    BulletinNoticePanel(
                        bulletins = host.bulletins,
                        onTap = host::onBulletinTap,
                        onDismiss = host::onBulletinDismiss,
                    )
                    Spacer(Modifier.height(SECTION_GAP))
                }
                if (isIconTheme) {
                    HomeHeroCard(
                        modifier = Modifier.enterOnce(0),
                        host = host,
                        dateLabel = host.dateLabel,
                        weekNumber = if (HomeCard.WEEK_NUMBER in honored) host.weekNumber else 0,
                        quickRows = quickRows,
                    )
                } else {
                    EditorialFrontPage(
                        modifier = Modifier.enterOnce(0),
                        host = host,
                        quickRows = quickRows,
                        figures = frontFigures,
                        inbox = frontInbox,
                    )
                }
            }

            host.inbox?.let { summary ->
                if (HomeCard.INBOX in honored && isIconTheme) {
                    Spacer(Modifier.height(SECTION_GAP))
                    HomeInboxCard(summary) { host.onRoute(AppRoute.Inbox) }
                }
            }

            if (HomeCard.SERVICE_GRID in honored && groups.isNotEmpty()) {
                Spacer(Modifier.height(if (isIconTheme) SECTION_GAP else 20.dp))
                if (isIconTheme) {
                    CategoryCards(count = groups.size, spacing = SECTION_GAP) { index ->
                        val group = groups[index]
                        HomeCategoryCard(
                            title = group.category.title,
                            subtitle = group.category.subtitle,
                            accent = legacyColor(HomeIcons.accentKey(group.category)),
                            rows = group.rows.map { rowOf(it.service, null) },
                        )
                    }
                } else {
                    HomeIndexCard(
                        sections = groups.map { group ->
                            IndexCategorySection(
                                title = group.category.title,
                                subtitle = group.category.subtitle,
                                rows = group.rows.map { rowOf(it.service, null) },
                            )
                        },
                        modifier = Modifier.enterOnce(1),
                    )
                }
            }
            Spacer(Modifier.height(SECTION_GAP + extraBottomPadding + 8.dp))
        }
        if (topActions != null) {
            val scope = rememberCoroutineScope()
            HomeCollapsingBar(
                progress = progress,
                topInset = contentTopPadding,
                backdrop = if (glass) pageBackdrop else null,
                onTapCollapsed = { scope.launch { scroll.animateScrollTo(0) } },
                actions = topActions,
            )
        }
    }
}

/** 停手时折叠到一半的顶栏吸到最近的一端，和 MIUIX 大标题的手感一致。 */
@Composable
private fun SnapCollapse(scroll: ScrollState, rangePx: Float) {
    LaunchedEffect(scroll, rangePx) {
        snapshotFlow { scroll.isScrollInProgress }.collect { moving ->
            val v = scroll.value
            if (moving || v <= 0 || v >= rangePx || rangePx > scroll.maxValue) return@collect
            scroll.animateScrollTo(if (v < rangePx / 2) 0 else rangePx.roundToInt())
        }
    }
}

/**
 * 首页的折叠顶栏，叠在滚动内容上面。三个圆钮一直在右上角；往上滑过刊头后，
 * 玻璃底色、底边发丝线和左边的方印一起浮出来，内容从它后面模糊着滑过。折叠后点空白处回到顶部。
 */
@Composable
private fun HomeCollapsingBar(
    progress: () -> Float,
    topInset: Dp,
    backdrop: LayerBackdrop?,
    onTapCollapsed: () -> Unit,
    actions: @Composable () -> Unit,
) {
    val collapsed by remember { derivedStateOf { progress() > 0.5f } }
    val tint = glassBarTint()
    val surface = MiuixTheme.colorScheme.surface
    Box(Modifier.fillMaxWidth().height(topInset + BAR_HEIGHT)) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = ramp(progress(), 0.55f) }
                .then(if (backdrop != null) Modifier.glassBarSurface(backdrop, tint) else Modifier.background(surface))
                .then(
                    if (collapsed) Modifier.clickable(interactionSource = null, indication = null, onClick = onTapCollapsed)
                    else Modifier
                ),
        )
        EditorialRule(Modifier.align(Alignment.BottomStart).graphicsLayer { alpha = ramp(progress(), 0.7f) })
        Row(
            Modifier.fillMaxWidth().padding(top = topInset).height(BAR_HEIGHT).padding(start = 20.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                HomeSeal.Square,
                contentDescription = "岱宗盒子",
                modifier = Modifier.size(26.dp).graphicsLayer {
                    val f = ramp(progress(), 0.6f)
                    alpha = f
                    scaleX = 0.78f + 0.22f * f
                    scaleY = scaleX
                },
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.graphicsLayer { translationY = -BUTTON_LIFT.toPx() * (1f - progress()) }) { actions() }
        }
    }
}

/** 展开时圆钮比顶栏正中上移的距离：顶栏里圆钮上方正好留这么多，不会顶进状态栏。 */
private val BUTTON_LIFT get() = (BAR_HEIGHT - 36.dp) / 2 - 2.dp

/** [p] 从 [start] 走到 1 时线性地从 0 升到 1。 */
private fun ramp(p: Float, start: Float): Float = ((p - start) / (1f - start)).coerceIn(0f, 1f)

// ── 杂志版式（THEME_CARD）──

/** 杂志版式的纸面：暖白底加极细描边，不画投影。 */
@Composable
private fun Modifier.editorialPaper(
    radius: Dp = EDITORIAL_RADIUS,
    tinted: Boolean = false,
): Modifier {
    val dark = LocalIsDarkTheme.current
    val base = AppCardColor
    val fill = when {
        dark && tinted -> lerp(base, HomeSeal.Red, 0.045f)
        dark -> base
        tinted -> lerp(base, Color(0xFFF6EFE4), 0.55f)
        else -> lerp(base, Color(0xFFFAF7F2), 0.35f)
    }
    val border = MiuixTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.09f else 0.065f)
    return this
        .squircleClip(radius)
        .background(fill)
        .squircleBorder(0.6.dp, border, radius)
}

/**
 * 杂志版式的刊头：眉栏是星期、周次和连接状态，和右上角圆钮同高；
 * 主体左边竖印，中间日期与问候，右边大号日数。
 */
@Composable
private fun EditorialMasthead(
    greeting: String,
    name: String,
    weekNumber: Int,
    status: HomeStatusLine?,
    onStatus: () -> Unit,
    underBar: Boolean,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    today: LocalDate = todayOnCampus(),
) {
    val dark = LocalIsDarkTheme.current
    val ink = MiuixTheme.colorScheme.onSurface
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val topPad = if (underBar) (BAR_HEIGHT - 36.dp) / 2 - 2.dp else 14.dp

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = topPad, bottom = 10.dp)
            .graphicsLayer {
                val p = progress()
                alpha = (1f - p * 1.35f).coerceIn(0f, 1f)
                scaleX = 1f - 0.03f * p
                scaleY = scaleX
                transformOrigin = TransformOrigin(0.5f, 0f)
            },
    ) {
        // 右边空出来给顶栏圆钮
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (underBar) 36.dp else 24.dp)
                .padding(end = if (underBar) 132.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(11.dp)
                    .background(HomeSeal.Red, RoundedCornerShape(1.dp))
            )
            Spacer(Modifier.width(7.dp))
            Text(
                chineseWeekday(today.dayOfWeek),
                style = TextStyle(
                    fontFamily = Editorial.Serif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 1.5.sp,
                ),
                color = ink.copy(alpha = 0.82f),
                maxLines = 1,
            )
            if (weekNumber in 1..25) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "第${weekNumber}周",
                    style = TextStyle(
                        fontFamily = Editorial.Serif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        letterSpacing = 1.sp,
                    ),
                    color = HomeSeal.Red,
                    maxLines = 1,
                    modifier = Modifier
                        .border(0.6.dp, HomeSeal.Red.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if (status != null) {
                Spacer(Modifier.width(8.dp))
                Text("·", style = MiuixTheme.textStyles.footnote2, color = muted.copy(alpha = 0.5f))
                Spacer(Modifier.width(6.dp))
                HeroStatusLine(status, onStatus, compact = true)
            }
        }

        Spacer(Modifier.height(10.dp))
        EditorialRule()
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                HomeSeal.Tall,
                contentDescription = "岱宗盒子",
                modifier = Modifier.size(width = 19.dp, height = 68.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${today.year} · ${today.month.number}月${today.day}日",
                    style = TextStyle(
                        fontFamily = Editorial.Serif,
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        letterSpacing = 1.8.sp,
                    ),
                    color = muted,
                    maxLines = 1,
                )
                Text(
                    if (name.isBlank()) greeting else "$greeting，$name",
                    style = TextStyle(
                        fontFamily = Editorial.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        letterSpacing = 1.2.sp,
                    ),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(16.dp)
                            .height(1.5.dp)
                            .background(HomeSeal.Red.copy(alpha = 0.85f), RoundedCornerShape(1.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "XJTU ALMANAC · 今日卷",
                        style = Editorial.kicker.copy(fontSize = 9.5.sp, letterSpacing = 1.8.sp),
                        color = muted.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxHeight(),
            ) {
                Box(
                    Modifier
                        .width(0.6.dp)
                        .height(44.dp)
                        .background(ink.copy(alpha = if (dark) 0.10f else 0.08f))
                )
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        today.day.toString().padStart(2, '0'),
                        style = Editorial.figure.copy(
                            fontSize = 36.sp,
                            lineHeight = 38.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = ink.copy(alpha = if (dark) 0.22f else 0.16f),
                    )
                    Text(
                        monthAbbr(today.month.number),
                        style = Editorial.kicker.copy(fontSize = 9.5.sp, letterSpacing = 2.sp),
                        color = muted.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}

private fun monthAbbr(month: Int): String = when (month) {
    1 -> "JAN."
    2 -> "FEB."
    3 -> "MAR."
    4 -> "APR."
    5 -> "MAY"
    6 -> "JUN."
    7 -> "JUL."
    8 -> "AUG."
    9 -> "SEP."
    10 -> "OCT."
    11 -> "NOV."
    12 -> "DEC."
    else -> ""
}

/** 杂志版式的头版：上面一张主卡（下一项安排、待办、快捷条），下面双栏拼版放各功能的状态。 */
@Composable
private fun EditorialFrontPage(
    modifier: Modifier = Modifier,
    host: HomeHost,
    quickRows: List<HomeServiceRow>,
    figures: List<HomeServiceRow>,
    inbox: HomeInboxSummary?,
) {
    val honored = host.honoredCards
    val nextUp = host.nextUp.takeIf { HomeCard.NEXT_UP in honored }
    val glances = host.glances.takeIf { HomeCard.GLANCE in honored }.orEmpty()
    val showQuick = HomeCard.QUICK_ACTIONS in honored && quickRows.isNotEmpty()
    val openable = host.openableRouteIds
    val glanceRouteIds = remember(glances) { glances.mapNotNull { it.route?.id }.toSet() }
    val invites = remember(openable, glanceRouteIds) {
        listOf(
            EditorialInviteSpec(AppRoute.Agent, "AI 顾问", "问屁岱", "「明天早八在哪？」"),
            EditorialInviteSpec(AppRoute.Community, "读者来信", "社区", "说说哪儿不好用"),
        ).filter { (openable == null || it.route.id in openable) && it.route.id !in glanceRouteIds }
    }

    val bentoItems = buildList {
        glances.forEach { g ->
            add(
                EditorialBentoItem.Metric(
                    icon = g.icon,
                    label = g.label,
                    value = g.value,
                    caption = g.caption,
                    number = g.number,
                    tone = g.tone,
                    accent = g.route?.let { legacyColor(it.id) } ?: MiuixTheme.colorScheme.primary,
                    onClick = { g.route?.let { host.onRoute(it) } },
                )
            )
        }
        figures.forEach { row ->
            add(
                EditorialBentoItem.Metric(
                    icon = row.icon,
                    label = row.title,
                    value = row.stat.orEmpty(),
                    caption = row.statDetail,
                    number = null,
                    tone = HomeTone.NORMAL,
                    accent = row.color,
                    onClick = row.onClick,
                )
            )
        }
        invites.forEach { inv ->
            add(
                EditorialBentoItem.Invite(
                    spec = inv,
                    onClick = { host.onRoute(inv.route) },
                )
            )
        }
    }

    if (nextUp == null && inbox == null && !showQuick && bentoItems.isEmpty()) return

    Column(modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (nextUp != null || inbox != null || showQuick) {
            Column(Modifier.fillMaxWidth().editorialPaper()) {
                if (nextUp != null) {
                    EditorialNextUpLead(
                        nextUp = nextUp,
                        onClick = { nextUp.route?.let { route -> host.onRoute(route) } ?: host.onProfile() },
                    )
                }
                if (inbox != null) {
                    if (nextUp != null) EditorialRule(Modifier.padding(horizontal = 16.dp))
                    InboxBrief(inbox) { host.onRoute(AppRoute.Inbox) }
                }
                if (showQuick) {
                    if (nextUp != null || inbox != null) EditorialRule()
                    EditorialQuickStrip(quickRows)
                }
            }
        }

        if (bentoItems.isNotEmpty()) {
            EditorialStaggeredMosaic(bentoItems)
        }
    }
}

private data class EditorialInviteSpec(
    val route: AppRoute,
    val kicker: String,
    val title: String,
    val hint: String,
)

private sealed interface EditorialBentoItem {
    data class Metric(
        val icon: ImageVector,
        val label: String,
        val value: String,
        val caption: String?,
        val number: Double?,
        val tone: HomeTone,
        val accent: Color,
        val onClick: () -> Unit,
    ) : EditorialBentoItem

    data class Invite(
        val spec: EditorialInviteSpec,
        val onClick: () -> Unit,
    ) : EditorialBentoItem
}

/** 头版的下一项安排：左边钟点，右边课名与地点，眉栏右侧挂倒计时。 */
@Composable
private fun EditorialNextUpLead(
    nextUp: HomeNextUp,
    onClick: () -> Unit,
) {
    val ink = MiuixTheme.colorScheme.onSurface
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val timeLabel = nextUp.timeLabel

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .background(
                        if (timeLabel != null) HomeSeal.Red else muted.copy(alpha = 0.45f),
                        CircleShape,
                    )
            )
            Spacer(Modifier.width(7.dp))
            Text(
                if (timeLabel != null) "NEXT SCHEDULE · 下一项安排" else "SCHEDULE · 日程状态",
                style = Editorial.kicker.copy(fontSize = 10.sp, letterSpacing = 1.6.sp),
                color = muted,
                modifier = Modifier.weight(1f),
            )
            if (!nextUp.eta.isNullOrBlank()) {
                Text(
                    nextUp.eta,
                    style = TextStyle(
                        fontFamily = Editorial.Serif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        letterSpacing = 0.5.sp,
                    ),
                    color = HomeSeal.Red,
                    modifier = Modifier
                        .background(HomeSeal.Red.copy(alpha = 0.09f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = muted.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        if (timeLabel != null) {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        timeLabel,
                        style = Editorial.figure.copy(
                            fontSize = 28.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = ink,
                    )
                    if (!nextUp.timeCaption.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            nextUp.timeCaption,
                            style = MiuixTheme.textStyles.footnote2,
                            color = muted,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Box(
                    Modifier
                        .width(0.6.dp)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp)
                        .background(ink.copy(alpha = 0.12f))
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        nextUp.title,
                        style = TextStyle(
                            fontFamily = Editorial.Serif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            letterSpacing = 0.4.sp,
                        ),
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!nextUp.detail.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            nextUp.detail,
                            style = MiuixTheme.textStyles.footnote1,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    nextUp.icon,
                    contentDescription = null,
                    tint = muted.copy(alpha = 0.75f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        nextUp.title,
                        style = TextStyle(
                            fontFamily = Editorial.Serif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            letterSpacing = 0.4.sp,
                        ),
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!nextUp.detail.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            nextUp.detail,
                            style = MiuixTheme.textStyles.footnote2,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 主卡底沿的快捷条：几格横排，细竖线分隔。 */
@Composable
private fun EditorialQuickStrip(quickRows: List<HomeServiceRow>) {
    val dark = LocalIsDarkTheme.current
    val ink = MiuixTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(ink.copy(alpha = if (dark) 0.035f else 0.02f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        quickRows.forEachIndexed { index, row ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(0.6.dp)
                        .fillMaxHeight()
                        .padding(vertical = 10.dp)
                        .background(ink.copy(alpha = if (dark) 0.08f else 0.06f))
                )
            }
            val iconTone = lerp(row.color, ink, if (dark) 0.45f else 0.55f)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .opensRow(row, 0.dp)
                    .padding(vertical = 11.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    row.icon,
                    contentDescription = null,
                    tint = iconTone,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    row.title,
                    style = TextStyle(
                        fontFamily = Editorial.Serif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                        letterSpacing = 0.5.sp,
                    ),
                    color = ink.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 双栏拼版：左栏高矮高、右栏矮高矮，两栏的接缝错开。 */
@Composable
private fun EditorialStaggeredMosaic(items: List<EditorialBentoItem>) {
    if (items.size == 1) {
        RenderBentoItem(items[0], featured = true, modifier = Modifier.fillMaxWidth())
        return
    }
    val leftItems = mutableListOf<Pair<EditorialBentoItem, Boolean>>()
    val rightItems = mutableListOf<Pair<EditorialBentoItem, Boolean>>()
    items.forEachIndexed { idx, item ->
        val col = idx % 2
        val rowInCol = idx / 2
        if (col == 0) leftItems += item to (rowInCol % 2 == 0)
        else rightItems += item to (rowInCol % 2 == 1)
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            Modifier.weight(1.06f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            leftItems.forEach { (item, featured) ->
                RenderBentoItem(item, featured = featured, modifier = Modifier.fillMaxWidth())
            }
        }
        Column(
            Modifier.weight(0.94f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rightItems.forEach { (item, featured) ->
                RenderBentoItem(item, featured = featured, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun RenderBentoItem(
    item: EditorialBentoItem,
    featured: Boolean,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is EditorialBentoItem.Metric -> EditorialMetricCard(item, featured = featured, modifier = modifier)
        is EditorialBentoItem.Invite -> EditorialInviteCard(item.spec, onClick = item.onClick, modifier = modifier)
    }
}

/** 拼版里的一张数据卡；[featured] 的留白和字号更大，数值与说明间加一道短线。 */
@Composable
private fun EditorialMetricCard(
    item: EditorialBentoItem.Metric,
    featured: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = LocalIsDarkTheme.current
    val ink = MiuixTheme.colorScheme.onSurface
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val iconTint = lerp(item.accent, ink, if (dark) 0.45f else 0.58f)
    val valueColor = when (item.tone) {
        HomeTone.ALERT -> HomeSeal.Red
        HomeTone.ACCENT -> ink
        HomeTone.NORMAL -> ink
        HomeTone.MUTED -> muted
    }
    val isAlert = item.tone == HomeTone.ALERT || item.tone == HomeTone.ACCENT

    Column(
        modifier
            .editorialPaper(radius = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = item.onClick,
            )
            .padding(horizontal = 14.dp, vertical = if (featured) 15.dp else 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = if (isAlert) HomeSeal.Red.copy(alpha = 0.85f) else iconTint,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                item.label,
                style = MiuixTheme.textStyles.footnote2,
                color = if (isAlert) HomeSeal.Red.copy(alpha = 0.9f) else muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(if (featured) 10.dp else 6.dp))
        val valueStyle = if (featured) {
            Editorial.figure.copy(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Editorial.figure.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
        }
        if (item.number != null) {
            RollingNumberText(
                value = item.number,
                format = { yuanText(it) },
                style = valueStyle,
                fontWeight = if (featured) FontWeight.SemiBold else FontWeight.Medium,
                color = valueColor,
            )
        } else {
            Text(
                item.value,
                style = valueStyle,
                color = valueColor,
                maxLines = if (featured) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!item.caption.isNullOrBlank()) {
            if (featured) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .width(14.dp)
                        .height(0.8.dp)
                        .background(ink.copy(alpha = if (dark) 0.16f else 0.12f))
                )
                Spacer(Modifier.height(5.dp))
            } else {
                Spacer(Modifier.height(3.dp))
            }
            Text(
                item.caption,
                style = MiuixTheme.textStyles.footnote2,
                color = muted,
                maxLines = if (featured) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 头版的专栏小块（「问屁岱」「社区」）：暖色纸底、眉标与一句引语。 */
@Composable
private fun EditorialInviteCard(
    spec: EditorialInviteSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MiuixTheme.colorScheme.onSurface
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(
        modifier
            .editorialPaper(radius = 16.dp, tinted = true)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                spec.kicker,
                style = Editorial.kicker.copy(fontSize = 9.5.sp, letterSpacing = 1.5.sp),
                color = HomeSeal.Red.copy(alpha = 0.82f),
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = muted.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            spec.title,
            style = TextStyle(
                fontFamily = Editorial.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.5.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.6.sp,
            ),
            color = ink,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            spec.hint,
            style = MiuixTheme.textStyles.footnote2,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 头版的待办一行：最要紧的一件，右边是件数。 */
@Composable
private fun InboxBrief(summary: HomeInboxSummary, onClick: () -> Unit) {
    val ink = MiuixTheme.colorScheme.onSurface
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "待办",
            style = Editorial.kicker.copy(fontSize = 10.5.sp, letterSpacing = 1.5.sp),
            color = HomeSeal.Red,
            modifier = Modifier
                .border(0.6.dp, HomeSeal.Red.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.5.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            summary.nextTodo ?: "去收纳里看看",
            style = TextStyle(
                fontFamily = Editorial.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp,
                lineHeight = 19.sp,
                letterSpacing = 0.3.sp,
            ),
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            listOfNotNull(
                summary.todoCount.takeIf { it > 0 }?.let { "$it 件没办" },
                summary.unreadCount.takeIf { it > 0 }?.let { "$it 组未读" },
            ).joinToString(" · "),
            style = MiuixTheme.textStyles.footnote2,
            color = muted,
            maxLines = 1,
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = muted.copy(alpha = 0.6f),
            modifier = Modifier.size(15.dp),
        )
    }
}

private data class IndexCategorySection(
    val title: String,
    val subtitle: String,
    val rows: List<HomeServiceRow>,
)

/** 杂志版式的目录：每栏一行栏头，下面等宽栅格排入口，栏间一道发丝线。 */
@Composable
private fun HomeIndexCard(
    sections: List<IndexCategorySection>,
    modifier: Modifier = Modifier,
) {
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val columns = if (isWideLayout()) 4 else 3

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "DIRECTORY · 栏目索引",
                style = Editorial.kicker.copy(fontSize = 10.sp, letterSpacing = 2.sp),
                color = muted,
            )
            Spacer(Modifier.width(12.dp))
            EditorialRule(Modifier.weight(1f))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .editorialPaper()
                .padding(horizontal = 10.dp, vertical = 14.dp),
        ) {
            sections.forEachIndexed { index, section ->
                if (index > 0) EditorialRule(Modifier.padding(horizontal = 6.dp, vertical = 12.dp))
                IndexSectionHead(index + 1, section)
                Spacer(Modifier.height(4.dp))
                section.rows.chunked(columns).forEach { line ->
                    Row(Modifier.fillMaxWidth()) {
                        line.forEach { IndexEntry(it, Modifier.weight(1f)) }
                        repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** 栏头：朱砂序号、栏目名，同一条基线上跟一句说明。 */
@Composable
private fun IndexSectionHead(number: Int, section: IndexCategorySection) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Text(
            number.toString().padStart(2, '0'),
            style = Editorial.figure.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
            color = HomeSeal.Red.copy(alpha = 0.85f),
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            section.title,
            style = TextStyle(
                fontFamily = Editorial.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.8.sp,
            ),
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            section.subtitle,
            style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
    }
}

/** 目录项：去饱和的小图标加名字，不另画底色，纸面是唯一容器。 */
@Composable
private fun IndexEntry(row: HomeServiceRow, modifier: Modifier = Modifier) {
    val ink = MiuixTheme.colorScheme.onSurface
    val iconTone = lerp(row.color, ink, if (LocalIsDarkTheme.current) 0.48f else 0.60f)
    Row(
        modifier
            .opensRow(row, 10.dp)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(row.icon, contentDescription = null, tint = iconTone, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            row.title,
            style = EntryName,
            color = ink.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val EntryName = TextStyle(
    fontFamily = Editorial.Serif,
    fontWeight = FontWeight.Medium,
    fontSize = 13.5.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.4.sp,
)

// ── 彩虹图标主题（THEME_ICON）──

/** 彩虹主题的「今日」卡。 */
@Composable
private fun HomeHeroCard(
    modifier: Modifier = Modifier,
    host: HomeHost,
    dateLabel: String,
    weekNumber: Int,
    quickRows: List<HomeServiceRow>,
) {
    val honored = host.honoredCards
    val greeting = Greeting.of()
    val headline = if (host.greetingName.isBlank()) greeting else "$greeting，${host.greetingName}"
    val meta = buildString {
        append(dateLabel)
        if (HomeCard.WEEK_NUMBER in honored && weekNumber in 1..25) append(" · 第${weekNumber}周")
    }
    val primary = MiuixTheme.colorScheme.primary
    val status = host.statusLine.takeIf { HomeCard.GREETING in honored }
    val nextUp = host.nextUp.takeIf { HomeCard.NEXT_UP in honored }
    val glances = host.glances.takeIf { HomeCard.GLANCE in honored }.orEmpty()
    val showQuick = HomeCard.QUICK_ACTIONS in honored && quickRows.isNotEmpty()

    Box(
        modifier
            .fillMaxWidth()
            .appCardShadow(shape = RoundedCornerShape(CARD_RADIUS), strong = true)
            .squircleClip(CARD_RADIUS)
            .background(AppCardColor),
    ) {
        Box(Modifier.matchParentSize()) {
            val cardBase = AppCardColor
            val heroMesh = remember(primary, cardBase) {
                HeroMeshWeights.map { line -> line.map { t -> lerp(cardBase, primary, t) } }
            }
            MeshBackground(
                modifier = Modifier.matchParentSize(),
                lightVertexColors = heroMesh,
                darkVertexColors = heroMesh,
                runForMillis = 6_000L,
            )
        }
        host.heroArt?.invoke(this)
        Column(Modifier.fillMaxWidth().animateContentSize().padding(14.dp)) {
            if (HomeCard.GREETING in honored) {
                Column(Modifier.padding(start = 6.dp, top = 8.dp, end = HERO_ART_SIZE)) {
                    Text(
                        meta,
                        style = MiuixTheme.textStyles.footnote1,
                        color = primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        headline,
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (status != null) {
                        Spacer(Modifier.height(8.dp))
                        HeroStatusLine(status, host::onOpenStatus)
                    }
                }
                Spacer(Modifier.height(if (status != null) 18.dp else 30.dp))
            }
            if (HomeCard.NEXT_UP in honored && nextUp != null) {
                HeroNextUp(
                    nextUp = nextUp,
                    onClick = { nextUp.route?.let { route -> host.onRoute(route) } ?: host.onProfile() },
                )
            }
            if (glances.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    glances.forEach { glance ->
                        HeroGlance(
                            icon = glance.icon,
                            label = glance.label,
                            value = glance.value,
                            caption = glance.caption,
                            number = glance.number,
                            valueColor = when (glance.tone) {
                                HomeTone.ACCENT -> primary
                                HomeTone.ALERT -> MiuixTheme.colorScheme.error
                                HomeTone.NORMAL -> MiuixTheme.colorScheme.onSurface
                                HomeTone.MUTED -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                            },
                            onClick = { glance.route?.let { host.onRoute(it) } },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
            if (showQuick) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    quickRows.forEach { row ->
                        HomeQuickAction(
                            row.icon,
                            row.title,
                            row.color,
                            onClick = row.onClick,
                            modifier = Modifier.weight(1f),
                            originKey = row.key,
                            iconSize = 44.dp,
                            solidIcon = true,
                        )
                    }
                }
            }
        }
    }
}

private fun chineseWeekday(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "星期一"
    DayOfWeek.TUESDAY -> "星期二"
    DayOfWeek.WEDNESDAY -> "星期三"
    DayOfWeek.THURSDAY -> "星期四"
    DayOfWeek.FRIDAY -> "星期五"
    DayOfWeek.SATURDAY -> "星期六"
    DayOfWeek.SUNDAY -> "星期日"
}

/** Hero 里半透明的内嵌面板：叠在 Mesh 上像一层磨砂，比再套一张实心卡轻。 */
@Composable
private fun Modifier.heroInset(radius: Dp = 18.dp): Modifier {
    val dark = LocalIsDarkTheme.current
    return this
        .squircleClip(radius)
        .background(AppCardColor.copy(alpha = if (dark) 0.55f else 0.72f))
        .squircleBorder(1.dp, Color.White.copy(alpha = if (dark) 0.06f else 0.6f), radius)
}

@Composable
private fun HeroStatusLine(
    status: HomeStatusLine,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val tint = when (status.tone) {
        HomeTone.ACCENT -> STATUS_BLUE
        HomeTone.ALERT -> MiuixTheme.colorScheme.error
        HomeTone.NORMAL -> STATUS_GREEN
        HomeTone.MUTED -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val dot = @Composable {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tint)
        )
    }
    val click = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = SinkFeedback(),
        onClick = onClick,
    )
    if (compact) {
        Row(click, verticalAlignment = Alignment.CenterVertically) {
            dot()
            Spacer(Modifier.width(5.dp))
            Text(
                "${status.label} · ${status.detail}",
                style = MiuixTheme.textStyles.footnote2,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Default.ChevronRight, contentDescription = "查看子系统连接状态", tint = muted, modifier = Modifier.size(13.dp))
        }
        return
    }
    Row(click, verticalAlignment = Alignment.CenterVertically) {
        dot()
        Spacer(Modifier.width(6.dp))
        Text(
            "${status.label} · ${status.detail}",
            style = MiuixTheme.textStyles.footnote1,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "查看子系统连接状态",
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** 彩虹主题的下一项安排：左边钟点，右边名称与倒计时，整条点进日程。 */
@Composable
private fun HeroNextUp(
    nextUp: HomeNextUp?,
    onClick: () -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        Modifier
            .fillMaxWidth()
            .heroInset()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val item = nextUp ?: return
        val timeLabel = item.timeLabel
        var detail: AnnotatedString? = null
        if (timeLabel != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    timeLabel,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                    color = primary,
                )
                Text(
                    item.timeCaption.orEmpty(),
                    style = MiuixTheme.textStyles.footnote2,
                    color = muted,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.18f))
            )
            Spacer(Modifier.width(12.dp))
            detail = buildAnnotatedString {
                val eta = item.eta
                if (!eta.isNullOrBlank()) {
                    pushStyle(SpanStyle(color = primary, fontWeight = FontWeight.Medium))
                    append(eta)
                    pop()
                }
                val tail = item.detail
                if (!tail.isNullOrBlank()) append((if (eta.isNullOrBlank()) "" else " · ") + tail)
            }
        } else {
            ExpressiveIcon(icon = item.icon, color = primary, size = 38.dp, iconSize = 20.dp)
            Spacer(Modifier.width(12.dp))
            item.detail?.takeIf { it.isNotBlank() }?.let { detail = AnnotatedString(it) }
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MiuixTheme.textStyles.footnote1,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
    }
}

/** 彩虹主题的速览格：小标签在上、数值在下。 */
@Composable
private fun HeroGlance(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: Color = MiuixTheme.colorScheme.onSurface,
    number: Double? = null,
    caption: String? = null,
) {
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val valueStyle = MiuixTheme.textStyles.body1
    Column(
        modifier
            .heroInset(16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = muted, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MiuixTheme.textStyles.footnote2, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        if (number != null) {
            RollingNumberText(
                value = number,
                format = { yuanText(it) },
                style = valueStyle,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
        } else {
            Text(
                value,
                style = valueStyle,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (caption != null) {
            Text(caption, style = MiuixTheme.textStyles.footnote2, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 「待办与消息」那一格（彩虹主题）。 */
@Composable
private fun HomeInboxCard(summary: HomeInboxSummary, onClick: () -> Unit) {
    val accent = MiuixTheme.colorScheme.primary
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .appCardShadow(shape = RoundedCornerShape(CARD_RADIUS))
            .squircleClip(CARD_RADIUS)
            .background(AppCardColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = onClick,
            )
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dark = LocalIsDarkTheme.current
            Box(
                Modifier
                    .size(32.dp)
                    .squircleBackground(accent.copy(alpha = if (dark) 0.24f else 0.12f), 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("待办与消息", style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        summary.isEmpty -> "没有要办的事，也没有未读"
                        summary.todoCount > 0 && summary.unreadCount > 0 ->
                            "还有 ${summary.todoCount} 件没办 · ${summary.unreadCount} 组没读"
                        summary.todoCount > 0 -> "还有 ${summary.todoCount} 件没办"
                        else -> "${summary.unreadCount} 组消息没读"
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
        }
        summary.nextTodo?.let { todo ->
            Spacer(Modifier.height(12.dp))
            Text("最要紧的一件", style = MiuixTheme.textStyles.footnote2, color = muted, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                todo,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 分类卡的排布（彩虹主题）。 */
@Composable
private fun CategoryCards(
    count: Int,
    spacing: Dp,
    card: @Composable (Int) -> Unit,
) {
    if (!isWideLayout()) {
        for (i in 0 until count) {
            Box(Modifier.enterOnce(i + 1)) { card(i) }
            if (i != count - 1) Spacer(Modifier.height(spacing))
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
        for (col in 0..1) {
            Column(Modifier.weight(1f)) {
                var first = true
                var i = col
                while (i < count) {
                    if (!first) Spacer(Modifier.height(spacing))
                    Box(Modifier.enterOnce(i + 1)) { card(i) }
                    first = false
                    i += 2
                }
            }
        }
    }
}

/** 两个主题共用的服务条目数据。 */
private data class HomeServiceRow(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String = "",
    val color: Color,
    val onClick: () -> Unit,
    val stat: String? = null,
    val statDetail: String? = null,
)

/** 彩虹主题的分类卡。 */
@Composable
private fun HomeCategoryCard(
    title: String,
    subtitle: String,
    accent: Color,
    rows: List<HomeServiceRow>,
) {
    val dark = LocalIsDarkTheme.current
    val glow = accent.copy(alpha = if (dark) 0.16f else 0.10f)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .appCardShadow(shape = RoundedCornerShape(CARD_RADIUS))
            .squircleClip(CARD_RADIUS)
            .background(AppCardColor)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(glow, Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = size.maxDimension * 0.75f,
                    )
                )
            }
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                subtitle,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(bottom = 2.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        rows.chunked(4).forEach { group ->
            Row(Modifier.fillMaxWidth()) {
                group.forEach { row ->
                    HomeServiceTile(row, Modifier.weight(1f), solidIcon = true)
                }
                repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** 宫格里的一个入口：图标 + 名称，没有独立底色，卡片是唯一容器。 */
@Composable
private fun HomeServiceTile(
    row: HomeServiceRow,
    modifier: Modifier = Modifier,
    solidIcon: Boolean = false,
) {
    val origin = rememberExpandOriginSource()
    val density = LocalDensity.current
    Column(
        modifier = modifier
            .expandOriginSource(origin)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = {
                    origin.arm(row.key, 14.dp, density)
                    row.onClick()
                }
            )
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (solidIcon) {
            GradientAppIcon(row.icon, row.color)
        } else {
            ExpressiveIcon(icon = row.icon, color = row.color, size = 42.dp, iconSize = 21.dp)
        }
        Spacer(Modifier.height(7.dp))
        Text(
            row.title,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 点开功能页，并让功能页从这一块放大出来。 */
@Composable
private fun Modifier.opensRow(row: HomeServiceRow, radius: Dp = 12.dp): Modifier {
    val origin = rememberExpandOriginSource()
    val density = LocalDensity.current
    return this
        .expandOriginSource(origin)
        .clip(RoundedCornerShape(radius))
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = SinkFeedback(),
            onClick = {
                origin.arm(row.key, radius, density)
                row.onClick()
            },
        )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeQuickAction(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    originKey: String? = null,
    iconSize: Dp = 54.dp,
    solidIcon: Boolean = false,
) {
    val origin = rememberExpandOriginSource()
    val density = LocalDensity.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .then(if (originKey != null) Modifier.expandOriginSource(origin) else Modifier)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                onClick = {
                    originKey?.let { origin.arm(it, 18.dp, density) }
                    onClick()
                }
            )
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        if (solidIcon) GradientAppIcon(icon, color, size = iconSize, iconSize = iconSize * 0.5f)
        else ExpressiveIcon(icon = icon, color = color, size = iconSize, iconSize = iconSize * 0.5f)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Medium)
    }
}

/** 首页各卡之间统一的间距。 */
private val SECTION_GAP = 14.dp

/** 首页折叠顶栏（状态栏以下）的高度，三个 36dp 圆钮在里面居中。 */
private val BAR_HEIGHT = 52.dp

/** 杂志版式的卡片圆角。 */
private val EDITORIAL_RADIUS = 18.dp

/** 彩虹主题分类卡圆角。 */
private val CARD_RADIUS = 26.dp

/** Hero 右上角插画占的宽度（`:app` 那张校区图是 104dp，文字列得给它让开）。 */
private val HERO_ART_SIZE = 104.dp

private val STATUS_GREEN = Color(0xFF2E7D32)
private val STATUS_BLUE = Color(0xFF1565C0)

/** `¥42.50` —— 自己拼两位小数。 */
private fun yuanText(value: Float): String {
    val cents = (value * 100 + 0.5).toLong()
    return "¥${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

/** 首页 Hero 卡 Mesh 渐变的 3x3 顶点：每格是「主题色混进卡片底色的比例」。 */
private val HeroMeshWeights = listOf(
    listOf(0.15f, 0.08f, 0.22f),
    listOf(0.07f, 0.11f, 0.08f),
    listOf(0.03f, 0.02f, 0.05f),
)
