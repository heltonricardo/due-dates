package app.vercel.heton.duedates

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val OccKey = ActionParameters.Key<String>("occ_key")
private val WidgetRed = Color(0xFFE5484D)

class DueWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = Db.get(context).dao()
        val today = LocalDate.now()
        val items = widgetItems(dao.remindersNow(), dao.statesNow(), today)
        val ctx = localized(context)
        provideContent {
            GlanceTheme {
                WidgetContent(items, today, ctx)
            }
        }
    }
}

class DueWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DueWidget()
}

class MarkDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = parameters[OccKey] ?: return
        val dao = Db.get(context).dao()
        val o = widgetItems(dao.remindersNow(), dao.statesNow(), LocalDate.now()).firstOrNull { it.key == key } ?: return
        Repo(dao).complete(o, true)
        context.getSystemService(NotificationManager::class.java).cancel(o.key.hashCode())
        Notifier.refreshSummary(context)
        DueWidgetUpdater.update(context)
    }
}

@Composable
private fun WidgetContent(items: List<Occ>, today: LocalDate, context: Context) {
    val name = context.getString(R.string.app_name)
    val open = actionStartActivity(Intent(context, MainActivity::class.java))
    val red = ColorProvider(WidgetRed)
    val overdueCount = items.count { it.overdue(today) }
    Column(
        GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(24.dp)
            .padding(14.dp)
    ) {
        Row(
            GlanceModifier.fillMaxWidth().clickable(open),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                name,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
            if (overdueCount > 0) {
                Text(
                    context.resources.plural(R.plurals.count_overdue, overdueCount),
                    style = TextStyle(color = red, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                )
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        if (items.isEmpty()) {
            Text(
                context.getString(R.string.all_caught_up),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp)
            )
        } else {
            LazyColumn {
                items(items.take(15), itemId = { it.key.hashCode().toLong() }) { o -> WidgetRow(o, today, open, context.resources) }
            }
        }
    }
}

@Composable
private fun WidgetRow(o: Occ, today: LocalDate, open: Action, res: Resources) {
    val late = o.overdue(today)
    val sub = buildString {
        append(o.dueDate.format(dateFmt("MMMd")))
        if (late) {
            append(" · ")
            append(res.plural(R.plurals.days_overdue, ChronoUnit.DAYS.between(o.dueDate, today).toInt()))
        }
    }
    Row(
        GlanceModifier.fillMaxWidth().padding(vertical = 6.dp).clickable(open),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(GlanceModifier.size(10.dp).background(ColorProvider(colorFor(o.color))).cornerRadius(5.dp)) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                o.title,
                maxLines = 1,
                style = TextStyle(
                    color = if (late) ColorProvider(WidgetRed) else GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(sub, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
        }
        CheckBox(
            checked = false,
            onCheckedChange = actionRunCallback<MarkDoneAction>(actionParametersOf(OccKey to o.key))
        )
    }
}

object DueWidgetUpdater {
    suspend fun update(context: Context) {
        DueWidget().updateAll(context)
    }
}
