package com.popstack.mvoter2015.feature.faq

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bluelinelabs.conductor.RouterTransaction
import com.popstack.mvoter2015.R
import com.popstack.mvoter2015.core.mvp.MvvmController
import com.popstack.mvoter2015.databinding.ControllerFaqBinding
import com.popstack.mvoter2015.domain.faq.model.FaqCategory
import com.popstack.mvoter2015.exception.GlobalExceptionHandler
import com.popstack.mvoter2015.feature.HasRouter
import com.popstack.mvoter2015.feature.about.AboutController
import com.popstack.mvoter2015.feature.analytics.screen.CanTrackScreen
import com.popstack.mvoter2015.feature.browser.OpenBrowserDelegate
import com.popstack.mvoter2015.feature.faq.ballot.BallotExampleController
import com.popstack.mvoter2015.feature.faq.ballot.displayString
import com.popstack.mvoter2015.feature.faq.search.FaqSearchController
import com.popstack.mvoter2015.feature.home.BottomNavigationHostViewModelStore
import com.popstack.mvoter2015.feature.settings.SettingsController
import com.popstack.mvoter2015.feature.share.ShareUrlFactory
import com.popstack.mvoter2015.helper.RecyclerViewMarginDecoration
import com.popstack.mvoter2015.helper.conductor.requireActivity
import com.popstack.mvoter2015.helper.conductor.requireActivityAsAppCompatActivity
import com.popstack.mvoter2015.helper.conductor.requireContext
import com.popstack.mvoter2015.helper.conductor.setSupportActionBar
import com.popstack.mvoter2015.helper.conductor.supportActionBar
import com.popstack.mvoter2015.helper.extensions.isLandScape
import com.popstack.mvoter2015.helper.extensions.isTablet
import com.popstack.mvoter2015.helper.intent.Intents
import com.popstack.mvoter2015.logging.HasTag
import com.popstack.mvoter2015.paging.CommonLoadStateAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class FaqController : MvvmController<ControllerFaqBinding>(), HasTag, CanTrackScreen {

  override val tag: String = "FaqController"

  override val screenName: String = "FaqController"

  private val viewModel: FaqViewModel by viewModels(
    store = BottomNavigationHostViewModelStore.viewModelStore ?: viewModelStore
  )

  override val bindingInflater: (LayoutInflater) -> ControllerFaqBinding =
    ControllerFaqBinding::inflate

  @Inject
  lateinit var openBrowserDelegate: OpenBrowserDelegate

  private val faqPagingAdapter by lazy {
    FaqPagingAdapter(
      ballotExampleClick = { navigateToBallotExample() },
      lawsAndUnfairPractice = {
        lifecycleScope.launch {
          openBrowserDelegate.browserHandler()
            .launchNewsInBrowser(requireActivity(), "https://mvoterapp.com/election-law")
        }
      },
      share = { faqId, _ ->
        val shareIntent = Intents.shareUrl(ShareUrlFactory().faq(faqId))
        startActivity(Intent.createChooser(shareIntent, "Share Faq To..."))
      }
    )
  }

  private fun navigateToBallotExample() {
    router.pushController(RouterTransaction.with(BallotExampleController()))
  }

  private val globalExceptionHandler by lazy {
    GlobalExceptionHandler(requireContext())
  }

  override fun onBindView(savedViewState: Bundle?) {
    super.onBindView(savedViewState)

    setSupportActionBar(binding.toolBar)
    supportActionBar()?.title = requireContext().getString(R.string.title_info)

    setHasOptionsMenu(R.menu.menu_faq, this@FaqController::handleMenuItemClick)
    binding.tvSelectedCategory.setOnClickListener {
      val selectFaqCategoryContract = requireActivityAsAppCompatActivity().registerForActivityResult(
        FaqCategorySelectActivity.SelectFaqCategoryContract()
      ) { selectedCategory ->
        if (selectedCategory != null) {
          selectFaqCategory(selectedCategory)
        }
      }
      viewModel.selectedFaqCategory()?.let {
        selectFaqCategoryContract.launch(it)
      }
    }

    binding.btnRetry.setOnClickListener {
      faqPagingAdapter.retry()
    }

    binding.rvFaq.apply {
      adapter = faqPagingAdapter.withLoadStateHeaderAndFooter(
        header = CommonLoadStateAdapter(faqPagingAdapter::retry),
        footer = CommonLoadStateAdapter(faqPagingAdapter::retry)
      )
      layoutManager = if (requireContext().isTablet() && requireContext().isLandScape()) {
        GridLayoutManager(requireContext(), 2).also {
          it.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
              return when (faqPagingAdapter.getItemViewType(position)) {
                FaqPagingAdapter.VIEW_TYPE_BALLOT_EXAMPLE -> 1
                FaqPagingAdapter.VIEW_TYPE_PROHIBITION -> 1
                else -> 2
              }
            }
          }
        }
      } else {
        LinearLayoutManager(requireContext())
      }
      val dimen =
        requireContext().resources.getDimensionPixelSize(R.dimen.recycler_view_item_margin)
      addItemDecoration(RecyclerViewMarginDecoration(dimen, 1))
    }

    faqPagingAdapter.addLoadStateListener { loadStates ->
      val refreshLoadState = loadStates.refresh
      binding.rvFaq.isVisible = refreshLoadState is LoadState.NotLoading
      if (refreshLoadState is LoadState.Loading) binding.progressIndicator.show()
      else binding.progressIndicator.hide()
      binding.tvErrorMessage.isVisible = refreshLoadState is LoadState.Error
      binding.btnRetry.isVisible = refreshLoadState is LoadState.Error

      if (refreshLoadState is LoadState.Error) {
        binding.tvErrorMessage.text = globalExceptionHandler.getMessageForUser(refreshLoadState.error)
      }
    }

    viewModel.faqCategoryLiveData.observe(
      lifecycleOwner,
      { faqCategory ->
        binding.tvSelectedCategory.text = faqCategory.displayString(requireContext())
      }
    )

    selectFaqCategory(viewModel.selectedFaqCategory() ?: FaqCategory.VOTER_LIST)
    faqPagingAdapter.refresh()

  }

  private var selectFaqJob: Job? = null

  private fun selectFaqCategory(faqCategory: FaqCategory) {
    selectFaqJob?.cancel()
    selectFaqJob = lifecycleScope.launch {
      viewModel.selectFaqCategory(faqCategory).collectLatest { pagingData ->
        faqPagingAdapter.submitData(pagingData)
      }
    }
  }

  private fun handleMenuItemClick(menuItem: MenuItem): Boolean {
    return when (menuItem.itemId) {
      R.id.action_search -> {
        router.pushController(RouterTransaction.with(FaqSearchController()))
        true
      }
      R.id.action_about -> {
        if (requireActivity() is HasRouter) {
          (requireActivity() as HasRouter).router()
            .pushController(RouterTransaction.with(AboutController()))
        }
        true
      }
      R.id.action_settings -> {
        if (requireActivity() is HasRouter) {
          (requireActivity() as HasRouter).router()
            .pushController(RouterTransaction.with(SettingsController()))
        }
        true
      }
      else -> false
    }
  }

}