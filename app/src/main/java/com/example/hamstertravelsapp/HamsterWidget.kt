package com.example.hamstertravelsapp

import android.content.Context
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
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

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val lastNightPeak = prefs[HamsterWidgetWorker.KEY_LAST_NIGHT_PEAK] ?: 0.0
            val totalDistance = prefs[HamsterWidgetWorker.KEY_TOTAL_DISTANCE] ?: 0.0
            val percentage = prefs[HamsterWidgetWorker.KEY_PERCENTAGE] ?: 100.0
            val lastUpdate = prefs[HamsterWidgetWorker.KEY_LAST_UPDATE] ?: "--:--"

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

            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(R.color.widget_background)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable(actionRunCallback<HamsterWidgetClickAction>()),
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

                Text(
                    text = lastUpdate,
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text_secondary),
                        fontSize = 9.sp,
                    ),
                )
            }
        }
    }
}
