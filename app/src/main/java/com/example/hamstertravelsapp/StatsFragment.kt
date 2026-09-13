package com.example.hamstertravelsapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.hamstertravelsapp.databinding.FragmentStatsBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HamsterViewModel by activityViewModels()

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

        setupChart()
        
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

        viewModel.history.observe(viewLifecycleOwner) { history ->
            binding.loadingProgress.visibility = View.GONE
            updateChart(history)
        }
        
        // Initial fetch
        viewModel.fetchHistory(1)
        binding.loadingProgress.visibility = View.VISIBLE
    }

    private fun setupChart() {
        val textColor = ContextCompat.getColor(requireContext(), R.color.chart_text)
        val gridColor = ContextCompat.getColor(requireContext(), R.color.chart_grid)
        binding.lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(true)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = textColor
            xAxis.gridColor = gridColor
            axisLeft.textColor = textColor
            axisLeft.gridColor = gridColor
            axisRight.isEnabled = false
            legend.textColor = textColor
            
            xAxis.valueFormatter = object : ValueFormatter() {
                private val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                override fun getFormattedValue(value: Float): String {
                    return sdf.format(Date(value.toLong()))
                }
            }
        }
    }

    private fun updateChart(history: List<HamsterViewModel.HistoryPoint>) {
        val entries = history.map { Entry(it.timestamp.toFloat(), it.distance.toFloat()) }
        val lineColor = ContextCompat.getColor(requireContext(), R.color.chart_line)
        val fillColor = ContextCompat.getColor(requireContext(), R.color.chart_fill)
        
        val dataSet = LineDataSet(entries, "Distance (m)").apply {
            color = lineColor
            lineWidth = 2.5f
            setDrawCircles(false)
            valueTextSize = 0f
            setDrawFilled(true)
            this.fillColor = fillColor
            fillAlpha = 51 // 20% transparency (0.2 * 255)
        }

        binding.lineChart.data = LineData(dataSet)
        binding.lineChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}