package com.popstack.mvoter2015.feature.candidate.listing.lowerhouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.popstack.mvoter2015.domain.candidate.usecase.GetMyLowerHouseCandidateList
import com.popstack.mvoter2015.exception.GlobalExceptionHandler
import com.popstack.mvoter2015.feature.candidate.listing.CandidateListViewItem
import com.popstack.mvoter2015.feature.candidate.listing.toSmallCandidateViewItem
import com.popstack.mvoter2015.helper.asyncviewstate.AsyncViewStateLiveData
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class LowerHouseCandidateListViewModel @Inject constructor(
  private val getMyLowerHouseCandidateList: GetMyLowerHouseCandidateList,
  private val globalExceptionHandler: GlobalExceptionHandler
) : ViewModel() {

  val viewItemLiveData = AsyncViewStateLiveData<CandidateListViewItem>()

  fun loadCandidates() {
    viewModelScope.launch {
      viewItemLiveData.postLoading()
      kotlin.runCatching {
        val candidateList = getMyLowerHouseCandidateList.execute(Unit)
        val smallCandidateList = candidateList
          .sortedBy {
            it.sortingName
          }
          .map {
            it.toSmallCandidateViewItem()
          }
        val candidateListViewItem = CandidateListViewItem(smallCandidateList)
        viewItemLiveData.postSuccess(candidateListViewItem)
      }
        .exceptionOrNull()
        ?.let { exception ->
          Timber.e(exception)
          viewItemLiveData.postError(exception, globalExceptionHandler.getMessageForUser(exception))
        }
    }
  }

}