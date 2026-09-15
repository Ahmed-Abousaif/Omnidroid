package com.omnidroid.app.tv.games

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.paging.PagingDataAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import com.omnidroid.R
import com.omnidroid.app.shared.GameInteractor
import com.omnidroid.app.tv.shared.GamePresenter
import com.omnidroid.common.coroutines.launchOnState
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.Game
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TVGamesFragment : VerticalGridSupportFragment() {
    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var gameInteractor: GameInteractor

    private val args: TVGamesFragmentArgs by navArgs()

    init {
        val gridPresenter = VerticalGridPresenter()
        gridPresenter.numberOfColumns = 4
        setGridPresenter(gridPresenter)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val factory = TVGamesViewModel.Factory(retrogradeDb)
        val gamesViewModel = ViewModelProvider(this, factory)[TVGamesViewModel::class.java]

        val cardSize = resources.getDimensionPixelSize(com.omnidroid.lib.R.dimen.card_size)
        val pagingAdapter =
            PagingDataAdapter(
                GamePresenter(cardSize, gameInteractor),
                Game.DIFF_CALLBACK,
            )

        this.adapter = pagingAdapter

        launchOnState(Lifecycle.State.RESUMED) {
            gamesViewModel.games
                .collect { pagingAdapter.submitData(lifecycle, it) }
        }

        args.metaSystemId.let {
            gamesViewModel.metaSystemId.value = MetaSystemID.valueOf(it)
        }

        onItemViewClickedListener =
            OnItemViewClickedListener { _, item, _, _ ->
                when (item) {
                    is Game -> gameInteractor.onGamePlay(item)
                }
            }
    }
}
