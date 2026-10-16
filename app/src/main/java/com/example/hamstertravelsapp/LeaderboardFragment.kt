package com.example.hamstertravelsapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hamstertravelsapp.databinding.FragmentLeaderboardBinding

class LeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HamsterViewModel by activityViewModels()
    private lateinit var adapter: LeaderboardAdapter
    private var hasScrolledToCurrent = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val initialPaddingTop = binding.root.paddingTop
        val initialPaddingBottom = binding.root.paddingBottom
        val initialPaddingLeft = binding.root.paddingLeft
        val initialPaddingRight = binding.root.paddingRight
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                top = initialPaddingTop + systemBars.top,
                bottom = initialPaddingBottom + systemBars.bottom,
                left = initialPaddingLeft + systemBars.left,
                right = initialPaddingRight + systemBars.right,
            )
            insets
        }

        adapter = LeaderboardAdapter()
        binding.leaderboardRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.leaderboardRecycler.adapter = adapter
        
        viewModel.leaderboard.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list) {
                if (!hasScrolledToCurrent && list.isNotEmpty()) {
                    val currentPosition = list.indexOfFirst { it.isCurrent }
                    if (currentPosition != -1) {
                        binding.leaderboardRecycler.post {
                            val layoutManager = binding.leaderboardRecycler.layoutManager as LinearLayoutManager
                            val centerOffset = binding.leaderboardRecycler.height / 2
                            // Estimate item height: 72dp content + 8dp margin = 80dp
                            val itemHeightApprox = (80 * resources.displayMetrics.density).toInt()
                            layoutManager.scrollToPositionWithOffset(currentPosition, centerOffset - (itemHeightApprox / 2))
                            hasScrolledToCurrent = true
                        }
                    }
                }
            }
        }
        
        viewModel.updateLeaderboard()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}