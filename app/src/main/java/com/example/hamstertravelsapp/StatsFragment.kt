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
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HamsterViewModel by activityViewModels()

    private var chartReferenceTime: Long = 0L
    private var barTimestamps: List<Long> = emptyList()

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
                    val days = when (tab?.position) {
                        0 -> 1
                        1 -> 7
                        2 -> 30
                        3 -> 365
                        else -> 1
                    }
                    viewModel.fetchHistory(days)
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
        viewModel.fetchHistory(1)
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
        
        if (isTotal) {
            binding.lineChart.visibility = View.VISIBLE
            binding.barChart.visibility = View.GONE
            updateLineChart(history)
        } else {
            binding.lineChart.visibility = View.GONE
            binding.barChart.visibility = View.VISIBLE
            updateBarChart(history)
        }
    }

    private fun updateLineChart(history: List<HamsterViewModel.HistoryPoint>) {
        val maxTotalSeen = history.asSequence().mapNotNull { (_, totalDistance) -> totalDistance }.maxOrNull() ?: 0.0
        val threshold = maxTotalSeen * 0.5

        var runningMax = threshold
        val validHistory = history.mapNotNull { point ->
            val total = point.totalDistance
            if ((total != null) && (total >= runningMax) && (total > 0.0)) {
                runningMax = total
                point
            } else {
                null
            }
        }

        if (validHistory.isEmpty()) {
            binding.lineChart.clear()
            return
        }

        chartReferenceTime = validHistory.first().timestamp
        
        val entries = validHistory.map { (timestamp, totalDistance) ->
            Entry((timestamp - chartReferenceTime).toFloat(), totalDistance!!.toFloat())
        }
        
        val lineColor = ContextCompat.getColor(requireContext(), R.color.chart_line)
        val fillColor = ContextCompat.getColor(requireContext(), R.color.chart_fill)
        
        val dataSet = LineDataSet(entries, getString(R.string.chart_label_total)).apply {
            color = lineColor
            lineWidth = 1.5f
            setDrawCircles(false)
            valueTextSize = 0f
            setDrawFilled(true)
            this.fillColor = fillColor
            fillAlpha = 51
        }

        binding.lineChart.data = LineData(dataSet)
        binding.lineChart.axisLeft.resetAxisMinimum()
        val labelCount = when (binding.tabLayout.selectedTabPosition) {
            0 -> 3
            1 -> 4
            else -> 4
        }
        binding.lineChart.xAxis.setLabelCount(labelCount, false)
        binding.lineChart.invalidate()
    }

    private fun updateBarChart(history: List<HamsterViewModel.HistoryPoint>) {
        val dailyMaxes = history.asSequence()
            .filter { (_, _, dailyDistance) -> (dailyDistance != null) && (dailyDistance > 0.0) }
            .groupBy { (timestamp) -> 
                val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { 
                    timeInMillis = timestamp
                    this[Calendar.HOUR_OF_DAY] = 0
                    this[Calendar.MINUTE] = 0
                    this[Calendar.SECOND] = 0
                    this[Calendar.MILLISECOND] = 0
                }
                cal.timeInMillis
            }
            .mapValues { (_, points) -> points.maxOf { (_, _, dailyDistance) -> dailyDistance!! } }
            .toSortedMap()

        if (dailyMaxes.isEmpty()) {
            binding.barChart.clear()
            return
        }

        barTimestamps = dailyMaxes.keys.toList()

        val entries = barTimestamps.indices.map { index ->
            val maxDistance = dailyMaxes[barTimestamps[index]] ?: 0.0
            BarEntry(index.toFloat(), maxDistance.toFloat())
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
        binding.barChart.xAxis.setLabelCount(minOf(dailyMaxes.size, 5), false)
        binding.barChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
