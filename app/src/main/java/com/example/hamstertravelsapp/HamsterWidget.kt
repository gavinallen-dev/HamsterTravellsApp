package com.example.hamstertravelsapp

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.util.Locale

@Suppress("RestrictedApi")
class HamsterWidget : GlanceAppWidget() {

    private companion object {
        /** Height of the activity strip. Still an indicator rather than a chart. */
        const val STRIP_HEIGHT_DP = 10f

        /**
         * Hours per group. Glance lays each container out into a generated layout with a fixed
         * number of child slots, so the 24 bars are split across groups rather than parented to a
         * single row.
         */
        const val BARS_PER_GROUP = 6
    }

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val lastNightPeak = prefs[HamsterWidgetWorker.KEY_LAST_NIGHT_PEAK] ?: 0.0
            val totalDistance = prefs[HamsterWidgetWorker.KEY_TOTAL_DISTANCE] ?: 0.0
            val percentage = prefs[HamsterWidgetWorker.KEY_PERCENTAGE] ?: 100.0
            val dataDate = prefs[HamsterWidgetWorker.KEY_DATA_DATE] ?: "--"
            val dataTime = prefs[HamsterWidgetWorker.KEY_DATA_TIME] ?: ""
            val hourlyActivity = parseHourlyActivity(
                prefs[HamsterWidgetWorker.KEY_HOURLY_ACTIVITY],
            )

            val diffPercentage = percentage - 100.0
            val absDiff = kotlin.math.abs(diffPercentage)

            val trendSymbol = when {
                diffPercentage > 0.05 -> "▲ "
                diffPercentage < -0.05 -> "▼ "
                else -> ""
            }

            val trendColorRes = when {
                diffPercentage > 0.05 -> R.color.widget_trend_up
                diffPercentage < -0.05 -> R.color.widget_trend_down
                else -> R.color.widget_text_primary
            }

            val trendText = when {
                absDiff <= 0.05 -> "0%"
                absDiff < 10.0 -> String.format(Locale.US, "%s%.1f%%", trendSymbol, absDiff)
                else -> String.format(Locale.US, "%s%.0f%%", trendSymbol, absDiff)
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(R.color.widget_background)
                    .clickable(actionRunCallback<HamsterWidgetClickAction>()),
            ) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        provider = ImageProvider(R.mipmap.ic_hamster),
                        contentDescription = "Hamster Icon",
                    )

                    Spacer(modifier = GlanceModifier.width(12.dp))

                    Column(
                        modifier = GlanceModifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "LAST NIGHT",
                            style = TextStyle(
                                color = ColorProvider(R.color.widget_text_label),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                        )
                        Text(
                            text = String.format(Locale.US, "%.0f m", lastNightPeak),
                            style = TextStyle(
                                color = ColorProvider(R.color.white),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(16.dp))

                    Column(
                        modifier = GlanceModifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "TOTAL RUN",
                            style = TextStyle(
                                color = ColorProvider(R.color.widget_text_label),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                        )
                        Text(
                            text = String.format(Locale.US, "%.0f m", totalDistance),
                            style = TextStyle(
                                color = ColorProvider(R.color.white),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(16.dp))

                    Column(
                        modifier = GlanceModifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "VS 7-DAY AVG",
                            style = TextStyle(
                                color = ColorProvider(R.color.widget_text_label),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                        )
                        Text(
                            text = trendText,
                            style = TextStyle(
                                color = ColorProvider(trendColorRes),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Column(
                        modifier = GlanceModifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = dataDate,
                            style = TextStyle(
                                color = ColorProvider(R.color.widget_text_secondary),
                                fontSize = 9.sp,
                            ),
                        )
                        Text(
                            text = dataTime,
                            style = TextStyle(
                                color = ColorProvider(R.color.widget_text_secondary),
                                fontSize = 9.sp,
                            ),
                        )
                    }
                }

                if (hourlyActivity.isNotEmpty()) {
                    ActivityStrip(
                        hourlyActivity = hourlyActivity,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 4.dp),
                    )
                }
            }
        }
    }

    /**
     * Hourly activity across the reporting window as a row of bars, oldest hour on the left, each
     * scaled against the busiest hour. Hours with no run keep a faint one pixel bar so the strip
     * reads as a baseline with activity on it rather than as gaps.
     */
    @Composable
    private fun ActivityStrip(hourlyActivity: List<Int>, modifier: GlanceModifier) {
        val busiest = hourlyActivity.maxOrNull() ?: 0

        Row(
            modifier = modifier.height(STRIP_HEIGHT_DP.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (group in hourlyActivity.chunked(BARS_PER_GROUP)) {
                Row(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    for (metres in group) {
                        val isActive = (metres > 0) && (busiest > 0)
                        val height = if (isActive) {
                            // Never round an hour that saw running down to the empty baseline.
                            maxOf(1f, (metres.toFloat() / busiest) * STRIP_HEIGHT_DP)
                        } else {
                            1f
                        }
                        val color = if (isActive) {
                            R.color.widget_spark_bar
                        } else {
                            R.color.widget_spark_track
                        }

                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .height(height.dp)
                                .background(color),
                            content = {},
                        )
                    }
                }
            }
        }
    }

    /**
     * Parses the comma separated hourly metres the worker stored, returning an empty list when the
     * value is missing, blank, or malformed - all of which mean there is nothing to draw.
     */
    private fun parseHourlyActivity(stored: String?): List<Int> {
        if (stored.isNullOrBlank()) return emptyList()
        val hours = stored.split(",").map { value -> value.trim().toIntOrNull() }
        if (hours.any { value -> value == null }) return emptyList()
        return hours.filterNotNull()
    }
}
