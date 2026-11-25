package com.yenaly.han1meviewer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.NetworkRepo
import com.yenaly.han1meviewer.logic.model.CommentPlace
import com.yenaly.han1meviewer.logic.model.VideoCommentArgs
import com.yenaly.han1meviewer.logic.model.VideoComments
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.ui.viewmodel.AppViewModel.csrfToken
import com.yenaly.yenaly_libs.base.YenalyViewModel
import com.yenaly.yenaly_libs.utils.showShortToast
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2022/06/28 028 14:18
 */
class CommentViewModel(application: Application) : YenalyViewModel(application) {

    lateinit var code: String

    var currentUserId: String? = null

    private var commentType: String? = null
    private var commentCode: String? = null
    private var nextCommentOffset: Int? = null
    private var hasMoreComments = false
    private var isLoadingMoreComments = false
    private var commentRequestVersion = 0
    private var replyRequestVersion = 0
    private var videoVersion = 0
    private var commentJob: Job? = null
    private var replyJob: Job? = null

    private val _videoCommentStateFlow =
        MutableStateFlow<WebsiteState<VideoComments>>(WebsiteState.Loading)
    val videoCommentStateFlow = _videoCommentStateFlow.asStateFlow()

    private val _videoReplyStateFlow =
        MutableStateFlow<WebsiteState<VideoComments>>(WebsiteState.Loading)
    val videoReplyStateFlow = _videoReplyStateFlow.asStateFlow()

    private val _videoCommentFlow = MutableStateFlow(emptyList<VideoComments.VideoComment>())
    val videoCommentFlow = _videoCommentFlow.asStateFlow()

    private val _videoReplyFlow = MutableStateFlow(emptyList<VideoComments.VideoComment>())
    val videoReplyFlow = _videoReplyFlow.asStateFlow()

    private val _postCommentFlow =
        MutableSharedFlow<WebsiteState<Unit>>(replay = 0)
    val postCommentFlow = _postCommentFlow.asSharedFlow()

    private val _postReplyFlow =
        MutableSharedFlow<WebsiteState<Unit>>(replay = 0)
    val postReplyFlow = _postReplyFlow.asSharedFlow()

    private val _commentLikeFlow =
        MutableSharedFlow<WebsiteState<VideoCommentArgs>>(replay = 0)
    val commentLikeFlow = _commentLikeFlow.asSharedFlow()

    fun switchVideo(videoCode: String) {
        if (code == videoCode) return
        code = videoCode
        videoVersion++
        commentRequestVersion++
        replyRequestVersion++
        commentJob?.cancel()
        replyJob?.cancel()
        currentUserId = null
        nextCommentOffset = null
        hasMoreComments = false
        isLoadingMoreComments = false
        _videoCommentFlow.value = emptyList()
        _videoReplyFlow.value = emptyList()
        _videoCommentStateFlow.value = WebsiteState.Loading
        _videoReplyStateFlow.value = WebsiteState.Loading
        commentType?.let { getComment(it, videoCode) }
    }

    fun getComment(type: String, code: String) {
        commentJob?.cancel()
        commentType = type
        commentCode = code
        nextCommentOffset = null
        hasMoreComments = false
        isLoadingMoreComments = false
        val version = ++commentRequestVersion
        commentJob = viewModelScope.launch {
            _videoCommentStateFlow.value = WebsiteState.Loading
            NetworkRepo.getComments(type, code).collect { state ->
                if (version != commentRequestVersion) return@collect
                if (state is WebsiteState.Success) {
                    nextCommentOffset = state.info.nextOffset
                    hasMoreComments = state.info.hasMore
                }
                _videoCommentStateFlow.value = state
                _videoCommentFlow.update { prevList ->
                    when (state) {
                        is WebsiteState.Success -> state.info.videoComment
                        is WebsiteState.Loading -> emptyList()
                        else -> prevList
                    }
                }
            }
        }
    }

    /**
     * 加載評論區下一頁（新版介面使用 offset 分頁）。
     */
    fun loadMoreComments() {
        val type = commentType ?: return
        val code = commentCode ?: return
        val offset = nextCommentOffset ?: return
        if (!hasMoreComments || isLoadingMoreComments) return

        isLoadingMoreComments = true
        val version = commentRequestVersion
        viewModelScope.launch {
            NetworkRepo.getMoreComments(type, code, offset).collect { state ->
                if (version != commentRequestVersion) return@collect
                when (state) {
                    is WebsiteState.Success -> {
                        nextCommentOffset = state.info.nextOffset
                        hasMoreComments = state.info.hasMore
                        _videoCommentFlow.update { it + state.info.videoComment }
                        _videoCommentStateFlow.update { prev ->
                            if (prev is WebsiteState.Success) {
                                WebsiteState.Success(
                                    prev.info.copy(
                                        videoComment = (prev.info.videoComment + state.info.videoComment)
                                            .toMutableList(),
                                        hasMore = state.info.hasMore,
                                        nextOffset = state.info.nextOffset,
                                    )
                                )
                            } else {
                                prev
                            }
                        }
                        isLoadingMoreComments = false
                    }

                    is WebsiteState.Error -> isLoadingMoreComments = false
                    WebsiteState.Loading -> Unit
                }
            }
        }
    }

    fun updateComments(comments: List<VideoComments.VideoComment>) {
        _videoCommentFlow.update { comments }
    }

    fun getCommentReply(commentId: String) {
        replyJob?.cancel()
        val version = ++replyRequestVersion
        replyJob = viewModelScope.launch {
            // 每次获取评论回复时，都会重新加载
            _videoReplyStateFlow.value = WebsiteState.Loading
            NetworkRepo.getCommentReply(commentId).collect { state ->
                if (version != replyRequestVersion) return@collect
                _videoReplyStateFlow.value = state
                _videoReplyFlow.update { prevList ->
                    when (state) {
                        is WebsiteState.Success -> state.info.videoComment
                        is WebsiteState.Loading -> emptyList()
                        else -> prevList
                    }
                }
            }
        }
    }

    fun postComment(
        currentUserId: String,
        targetUserId: String,
        type: String,
        text: String,
    ) {
        val version = videoVersion
        viewModelScope.launch {
            NetworkRepo.postComment(csrfToken, currentUserId, targetUserId, type, text)
                .collect { if (version == videoVersion) _postCommentFlow.emit(it) }
        }
    }

    fun postReply(
        replyCommentId: String,
        text: String,
    ) {
        val version = videoVersion
        viewModelScope.launch {
            NetworkRepo.postCommentReply(csrfToken, replyCommentId, text)
                .collect { if (version == videoVersion) _postReplyFlow.emit(it) }
        }
    }

    fun likeComment(
        isPositive: Boolean, commentPosition: Int,
        comment: VideoComments.VideoComment, likeCommentStatus: Boolean = false,
        unlikeCommentStatus: Boolean = false,
    ) = likeCommentInternal(
        CommentPlace.COMMENT, isPositive, commentPosition,
        comment, likeCommentStatus, unlikeCommentStatus
    )

    fun likeChildComment(
        isPositive: Boolean, commentPosition: Int,
        comment: VideoComments.VideoComment, likeCommentStatus: Boolean = false,
        unlikeCommentStatus: Boolean = false,
    ) = likeCommentInternal(
        CommentPlace.CHILD_COMMENT, isPositive, commentPosition,
        comment, likeCommentStatus, unlikeCommentStatus
    )

    private fun likeCommentInternal(
        commentPlace: CommentPlace,
        isPositive: Boolean,
        commentPosition: Int,
        comment: VideoComments.VideoComment,
        likeCommentStatus: Boolean = false,
        unlikeCommentStatus: Boolean = false,
    ) {
        val version = videoVersion
        viewModelScope.launch {
            NetworkRepo.likeComment(
                csrfToken,
                commentPlace,
                comment.post.foreignId,
                isPositive,
                comment.post.likeUserId,
                comment.post.commentLikesCount ?: 0,
                comment.post.commentLikesSum ?: 0,
                likeCommentStatus,
                unlikeCommentStatus,
                commentPosition, comment
            ).collect { argState ->
                if (version != videoVersion) return@collect
                _commentLikeFlow.emit(argState)
                if (version != videoVersion) return@collect
                if (argState is WebsiteState.Success) {
                    when (commentPlace) {
                        CommentPlace.COMMENT -> _videoCommentFlow.update { prevList ->
                            prevList.toMutableList().apply {
                                if (commentPosition !in indices) return@apply
                                this[commentPosition] =
                                    this[commentPosition].handleCommentLike(argState.info)
                            }
                        }

                        CommentPlace.CHILD_COMMENT -> _videoReplyFlow.update { prevList ->
                            prevList.toMutableList().apply {
                                if (commentPosition !in indices) return@apply
                                this[commentPosition] =
                                    this[commentPosition].handleCommentLike(argState.info)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun VideoComments.VideoComment.handleCommentLike(
        args: VideoCommentArgs,
    ) = if (args.isPositive) {
        this.incLikesCount(cancel = post.likeCommentStatus)
    } else {
        this.decLikesCount(cancel = post.unlikeCommentStatus)
    }

    fun handleCommentLike(args: VideoCommentArgs) {
        if (args.isPositive) {
            if (args.comment.post.likeCommentStatus) {
                showShortToast(R.string.cancel_thumb_up_success)
            } else {
                showShortToast(R.string.thumb_up_success)
            }
        } else {
            if (args.comment.post.unlikeCommentStatus) {
                showShortToast(R.string.cancel_thumb_down_success)
            } else {
                showShortToast(R.string.thumb_down_success)
            }
        }
    }
}
