package com.example.hamstertravelsapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.hamstertravelsapp.databinding.FragmentStatsBinding
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HamsterViewModel by activityViewModels()

    private var chartReferenceTime: Long = 0L
    private var barTimestamps: List<Long> = emptyList()

    /** How the total line is sampled across the selected span. */
    private enum class TotalMode {
        /** Every recorded point, for the shorter spans. */
        ALL_POINTS,

        /** One point per reporting window, for spans where intraday detail isn't needed. */
        LAST_PER_WINDOW,
    }

    /**
     * How many reporting windows each tab covers. Daily and total differ on the Day tab: the bars
     * compare the reporting window against the previous one, while the line shows only the
     * reporting window itself.
     */
    private data class TabSpec(
        val dailyWindows: Int,
        val totalWindows: Int,
        val totalMode: TotalMode,
    )

    private fun specFor(tabPosition: Int): TabSpec = when (tabPosition) {
        TAB_DAY -> TabSpec(dailyWindows = 2, totalWindows = 1, totalMode = TotalMode.ALL_POINTS)
        TAB_WEEK -> TabSpec(dailyWindows = 7, totalWindows = 7, totalMode = TotalMode.ALL_POINTS)
        TAB_MONTH -> TabSpec(dailyWindows = 31, totalWindows = 31, totalMode = TotalMode.LAST_PER_WINDOW)
        TAB_YEAR -> TabSpec(dailyWindows = 351, totalWindows = 351, totalMode = TotalMode.LAST_PER_WINDOW)
        else -> TabSpec(dailyWindows = 2, totalWindows = 1, totalMode = TotalMode.ALL_POINTS)
    }

    private companion object {
        const val TAB_DAY = 0
        const val TAB_WEEK = 1
        const val TAB_MONTH = 2
        const val TAB_YEAR = 3

        /** Above this many points the line reads clearly on its own and markers just clutter. */
        const val MAX_POINTS_WITH_MARKERS = 48
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val initialPaddingTop = binding.root.paddingTop
        val initialPaddingLeft = binding.root.paddingLeft
        val initialPaddingRight = binding.root.paddingRight

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                top = initialPaddingTop + systemBars.top,
                left = initialPaddingLeft + systemBars.left,
                right = initialPaddingRight + systemBars.right,
            )
            insets
        }

        setupLineChart()
        setupBarChart()
        
        binding.tabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    viewModel.fetchHistory(specFor(tab?.position ?: TAB_DAY).dailyWindows)
                    binding.loadingProgress.visibility = View.VISIBLE
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            },
        )

        binding.distanceTypeToggle.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) {
                viewModel.history.value?.let { updateChart(it) }
            }
        }

        viewModel.history.observe(viewLifecycleOwner) { history ->
            binding.loadingProgress.visibility = View.GONE
            updateChart(history)
        }

        binding.btnZoomIn.setOnClickListener {
            getActiveChart().zoomIn()
        }

        binding.btnZoomOut.setOnClickListener {
            getActiveChart().zoomOut()
        }

        binding.btnZoomReset.setOnClickListener {
            getActiveChart().fitScreen()
        }
        
        // Initial fetch
        viewModel.fetchHistory(specFor(binding.tabLayout.selectedTabPosition).dailyWindows)
        binding.loadingProgress.visibility = View.VISIBLE
    }

    private fun getActiveChart(): BarLineChartBase<*> {
        return if (binding.lineChart.isVisible) binding.lineChart else binding.barChart
    }

    private fun setupLineChart() {
        val textColor = ContextCompat.getColor(requireContext(), R.color.chart_text)
        val gridColor = ContextCompat.getColor(requireContext(), R.color.chart_grid)
        binding.lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(true)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = textColor
            xAxis.gridColor = gridColor
            xAxis.setAvoidFirstLastClipping(true)
            axisLeft.textColor = textColor
            axisLeft.gridColor = gridColor
            axisLeft.resetAxisMinimum()
            axisRight.isEnabled = false
            legend.textColor = textColor
            
            xAxis.valueFormatter = object : ValueFormatter() {
                private val sdf = SimpleDateFormat("", Locale.getDefault()).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                override fun getFormattedValue(value: Float): String {
                    val date = Date(value.toLong() + chartReferenceTime)
                    val visibleRangeMillis = binding.lineChart.highestVisibleX - binding.lineChart.lowestVisibleX
                    val visibleRangeDays = visibleRangeMillis / (1000f * 60f * 60f * 24f)
                    
                    val pattern = when {
                        visibleRangeDays > 60f -> "MMM yyyy"
                        (visibleRangeDays in 2f..60f) -> "MMM d"
                        else -> "EEE d, HH:mm"
                    }
                    sdf.applyPattern(pattern)
                    return sdf.format(date)
                }
            }
            xAxis.granularity = 1f
        }
    }

    private fun setupBarChart() {
        val textColor = ContextCompat.getColor(requireContext(), R.color.chart_text)
        val gridColor = ContextCompat.getColor(requireContext(), R.color.chart_grid)
        binding.barChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(true)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = textColor
            xAxis.gridColor = gridColor
            xAxis.setAvoidFirstLastClipping(true)
            axisLeft.textColor = textColor
            axisLeft.gridColor = gridColor
            axisLeft.axisMinimum = 0f
            axisLeft.spaceBottom = 0f
            axisRight.isEnabled = false
            legend.textColor = textColor

            xAxis.valueFormatter = object : ValueFormatter() {
                private val sdf = SimpleDateFormat("", Locale.getDefault()).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt()
                    if (index in barTimestamps.indices) {
                        val date = Date(barTimestamps[index])
                        val visibleRangeDays = binding.barChart.highestVisibleX - binding.barChart.lowestVisibleX
                        
                        val pattern = when {
                            visibleRangeDays > 60f -> "MMM yyyy"
                            (visibleRangeDays in 2f..60f) -> "MMM d"
                            else -> "EEE d, HH:mm"
                        }
                        sdf.applyPattern(pattern)
                        return sdf.format(date)
                    }
                    return ""
                }
            }
            xAxis.granularity = 1f
        }
    }

    private fun updateChart(history: List<HamsterViewModel.HistoryPoint>) {
        if (history.isEmpty()) {
            binding.lineChart.clear()
            binding.barChart.clear()
            return
        }

        val isTotal = binding.distanceTypeToggle.checkedButtonId == R.id.button_total
        val spec = specFor(binding.tabLayout.selectedTabPosition)

        if (isTotal) {
            binding.lineChart.visibility = View.VISIBLE
            binding.barChart.visibility = View.GONE
            updateLineChart(history, spec)
        } else {
            binding.lineChart.visibility = View.GONE
            binding.barChart.visibility = View.VISIBLE
            updateBarChart(history, spec)
        }
    }

    /**
     * Total distance as a line. Short spans plot every point recorded in range; longer spans plot
     * the last total in each reporting window, since intraday detail isn't readable at that zoom.
     */
    private fun updateLineChart(history: List<HamsterViewModel.HistoryPoint>, spec: TabSpec) {
        val windowEnd = viewModel.historyWindowEnd
        val rangeStart = ReportingWindow.startOfWindow(windowEnd, spec.totalWindows - 1)

        val inRange = history.filter { (timestamp, totalDistance) ->
            (totalDistance != null) && (timestamp >= rangeStart)
        }

        val points = when (spec.totalMode) {
            TotalMode.ALL_POINTS -> inRange
            TotalMode.LAST_PER_WINDOW -> inRange
                .groupBy { (timestamp) -> ReportingWindow.windowIndexOf(timestamp, windowEnd) }
                .values
                .mapNotNull { bucket -> bucket.maxByOrNull { (timestamp) -> timestamp } }
                .sortedBy { (timestamp) -> timestamp }
        }

        if (points.isEmpty()) {
            binding.lineChart.clear()
            binding.lineChart.invalidate()
            return
        }

        // Anchor to the start of the requested period rather than the first reading, so the axis
        // spans the whole period and a sparse reading still sits at the right point within it.
        chartReferenceTime = rangeStart

        val entries = points.map { (timestamp, totalDistance) ->
            Entry((timestamp - chartReferenceTime).toFloat(), totalDistance!!.toFloat())
        }

        val lineColor = ContextCompat.getColor(requireContext(), R.color.chart_line)
        val fillColor = ContextCompat.getColor(requireContext(), R.color.chart_fill)

        val dataSet = LineDataSet(entries, getString(R.string.chart_label_total)).apply {
            color = lineColor
            lineWidth = 1.5f
            // A series this short draws no visible line at all - a single reading draws nothing -
            // so mark the points while there are few enough for markers not to clutter.
            val markPoints = entries.size <= MAX_POINTS_WITH_MARKERS
            setDrawCircles(markPoints)
            if (markPoints) {
                setCircleColor(lineColor)
                circleRadius = 2.5f
                setDrawCircleHole(false)
            }
            valueTextSize = 0f
            setDrawFilled(true)
            this.fillColor = fillColor
            fillAlpha = 51
        }

        // Fit the axis to the readings actually present rather than the whole requested period,
        // so a span reaching further back than the channel's history doesn't trail off into empty
        // chart - the same way the daily bars simply omit windows with no reading.
        val windowMs = ReportingWindow.WINDOW_MS.toFloat()
        var axisMin = entries.first().x
        var axisMax = entries.last().x
        if ((axisMax - axisMin) < windowMs) {
            // Too narrow to plot - a single reading has no width at all - so fall back to showing
            // the whole reporting window the readings sit in.
            axisMin = floor(axisMin / windowMs) * windowMs
            axisMax = axisMin + windowMs
        }

        binding.lineChart.data = LineData(dataSet)
        binding.lineChart.axisLeft.resetAxisMinimum()
        binding.lineChart.xAxis.axisMinimum = axisMin
        binding.lineChart.xAxis.axisMaximum = axisMax
        val labelCount = if (binding.tabLayout.selectedTabPosition == TAB_DAY) 3 else 4
        binding.lineChart.xAxis.setLabelCount(labelCount, false)
        binding.lineChart.invalidate()
    }

    /**
     * Max daily distance per reporting window as bars, oldest window first. Windows with no
     * reading are omitted rather than drawn as zero, so spans reaching past the start of the
     * daily channel's history simply show fewer bars.
     */
    private fun updateBarChart(history: List<HamsterViewModel.HistoryPoint>, spec: TabSpec) {
        val windowEnd = viewModel.historyWindowEnd

        val maxPerWindow = history.asSequence()
            .mapNotNull { (timestamp, _, dailyDistance) ->
                if (dailyDistance == null) {
                    null
                } else {
                    val windowIndex = ReportingWindow.windowIndexOf(timestamp, windowEnd)
                    if (windowIndex in 0 until spec.dailyWindows) {
                        windowIndex to dailyDistance
                    } else {
                        null
                    }
                }
            }
            .groupBy({ (windowIndex, _) -> windowIndex }, { (_, distance) -> distance })
            .mapValues { (_, distances) -> distances.max() }

        if (maxPerWindow.isEmpty()) {
            binding.barChart.clear()
            binding.barChart.invalidate()
            return
        }

        // The highest index is the oldest window, so descending order puts bars in time order.
        val orderedWindows = maxPerWindow.keys.sortedDescending()
        barTimestamps = orderedWindows.map { index ->
            ReportingWindow.startOfWindow(windowEnd, index)
        }

        val entries = orderedWindows.mapIndexed { position, windowIndex ->
            BarEntry(position.toFloat(), maxPerWindow.getValue(windowIndex).toFloat())
        }

        val barColor = ContextCompat.getColor(requireContext(), R.color.chart_line)

        val dataSet = BarDataSet(entries, getString(R.string.chart_label_daily)).apply {
            color = barColor
            valueTextSize = 0f
        }

        val barData = BarData(dataSet)
        barData.barWidth = 0.6f

        binding.barChart.data = barData
        binding.barChart.axisLeft.axisMinimum = 0f
        binding.barChart.axisLeft.spaceBottom = 0f
        binding.barChart.xAxis.granularity = 1f
        binding.barChart.xAxis.setLabelCount(minOf(entries.size, 5), false)
        binding.barChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
