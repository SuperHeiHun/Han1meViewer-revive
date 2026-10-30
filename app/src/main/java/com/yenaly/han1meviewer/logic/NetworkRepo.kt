package com.yenaly.han1meviewer.logic

import android.util.Log
import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.Preferences.isAlreadyLogin
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.exception.CloudFlareBlockedException
import com.yenaly.han1meviewer.logic.exception.HanimeNotFoundException
import com.yenaly.han1meviewer.logic.exception.IPBlockedException
import com.yenaly.han1meviewer.logic.exception.ParseException
import com.yenaly.han1meviewer.logic.model.CommentPlace
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.model.ModifiedPlaylistArgs
import com.yenaly.han1meviewer.logic.model.MyListType
import com.yenaly.han1meviewer.logic.model.VideoCommentArgs
import com.yenaly.han1meviewer.logic.model.VideoComments
import com.yenaly.han1meviewer.logic.network.HUpdater
import com.yenaly.han1meviewer.logic.network.HanimeNetwork
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.util.CloudflareVerificationManager
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import javax.net.ssl.SSLHandshakeException

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/08 008 22:38
 */
object NetworkRepo {

    //<editor-fold desc="Hanime">

    fun getHomePage() = flow {
        val requestResult = HanimeNetwork.hanimeService.getHomePage()
        val resultBody = requestResult.body()?.string()
        if (!requestResult.isSuccessful || resultBody == null) {
            requestResult.throwRequestException()
        }
        checkCloudflareChallenge(resultBody)
        val state = Parser.homePageVer2(resultBody ?: EMPTY_STRING)
        if (state is WebsiteState.Success) {
            val homePage = state.info
            // 新版網站首頁砍掉了「中文字幕」「本日排行」「本月排行」三個欄位，
            // 這裡並行地從搜尋頁補齊；任何一個失敗都只是讓該欄位為空，不影響首頁。
            val (chineseSubtitle, hanimeCurrent, hotHanimeMonthly) = coroutineScope {
                val chineseSubtitle = async {
                    getHomeSectionCarousel(tags = setOf("中文字幕"), sort = "最新上傳")
                }
                val hanimeCurrent = async {
                    getHomeSectionCarousel(sort = "本日排行")
                }
                val hotHanimeMonthly = async {
                    getHomeSectionCarousel(sort = "本月排行")
                }
                Triple(
                    chineseSubtitle.await(),
                    hanimeCurrent.await(),
                    hotHanimeMonthly.await()
                )
            }
            emit(
                WebsiteState.Success(
                    homePage.copy(
                        chineseSubtitle = chineseSubtitle,
                        hanimeCurrent = hanimeCurrent,
                        hotHanimeMonthly = hotHanimeMonthly,
                    )
                )
            )
        } else {
            emit(state)
        }
    }.catch { e ->
        emit(WebsiteState.Error(handleException(e)))
    }.flowOn(Dispatchers.IO)

    /**
     * 首頁補齊欄位時使用，失敗時回傳空列表
     */
    private suspend fun getHomeSectionCarousel(
        sort: String? = null,
        tags: Set<String> = emptySet(),
    ): MutableList<HanimeInfo> = try {
        val response = HanimeNetwork.hanimeService.getHanimeSearchResult(
            sort = sort,
            tags = tags,
        )
        val body = response.body()?.string()
        if (response.isSuccessful && body != null) {
            checkCloudflareChallenge(body)
            val state = Parser.hanimeSearch(body)
            // 首頁每個橫向欄位只預覽 12 個，和網站首頁保持一致
            (state as? PageLoadingState.Success)?.info?.take(12)?.toMutableList()
                ?: mutableListOf()
        } else {
            mutableListOf()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.d("home_page_section", "Failed to load home section: ${e.message}")
        mutableListOf()
    }

    fun getHanimeSearchResult(
        page: Int, query: String?, genre: String?,
        sort: String?, broad: Boolean, date: String?,
        duration: String?, tags: Set<String>,
    ) = pageIOFlow(
        request = {
            HanimeNetwork.hanimeService.getHanimeSearchResult(
                page, query, genre, sort,
                if (broad) "on" else null,
                date, duration, tags
            )
        },
        action = Parser::hanimeSearch
    )

    fun getHanimeVideo(videoCode: String) = videoIOFlow(
        request = { HanimeNetwork.hanimeService.getHanimeVideo(videoCode) },
        action = Parser::hanimeVideoVer2
    )

    fun getHanimePreview(date: String) = websiteIOFlow(
        request = { HanimeNetwork.hanimeService.getHanimePreview(date) },
        action = Parser::hanimePreview
    )

    //</editor-fold>

    //<editor-fold desc="My List">

    fun getMyListItems(page: Int, typeOrCode: Any) = pageIOFlow(
        request = {
            when (typeOrCode) {
                is String ->
                    HanimeNetwork.myListService.getMyListItems(page, typeOrCode)

                is MyListType ->
                    HanimeNetwork.myListService.getMyListItems(page, typeOrCode.value)

                else ->
                    throw IllegalArgumentException("typeOrId must be String or MyListType")
            }
        },
        action = Parser::myListItems
    )

    fun getSubscriptions(page: Int) = pageIOFlow(
        request = {
            HanimeNetwork.myListService.getMyListItems(page, MyListType.SUBSCRIPTION.value)
        },
        action = Parser::subscriptionItems
    )

    fun deleteMyListItems(
        typeOrCode: Any,
        videoCode: String,
        position: Int,
        token: String?,
    ) = websiteIOFlow(
        request = {
            when (typeOrCode) {
                is String ->
                    HanimeNetwork.myListService.deleteMyListItems(
                        typeOrCode, videoCode,
                        csrfToken = token
                    )

                is MyListType ->
                    HanimeNetwork.myListService.deleteMyListItems(
                        typeOrCode.value, videoCode,
                        csrfToken = token
                    )

                else ->
                    throw IllegalArgumentException("typeOrId must be String or MyListType")
            }
        }
    ) { deleteBody ->
        val jsonObject = JSONObject(deleteBody)
        val returnVideoCode = jsonObject.get("video_id").toString()
        if (videoCode == returnVideoCode) {
            return@websiteIOFlow WebsiteState.Success(position)
        }

        return@websiteIOFlow WebsiteState.Error(IllegalStateException("cannot delete it ?!"))
    }

    fun getPlaylists() = websiteIOFlow(
        request = HanimeNetwork.myListService::getPlaylists,
        action = Parser::playlists
    )

    fun addToMyFavVideo(
        videoCode: String,
        likeStatus: Boolean, // false => "": add fav; true => "1": cancel fav;
        currentUserId: String?,
        token: String?,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.myListService.addToMyFavVideo(
                videoCode, if (likeStatus) "1" else EMPTY_STRING,
                token, currentUserId
            )
        }
    ) {
        Log.d("add_to_fav_body", it)
        return@websiteIOFlow WebsiteState.Success(likeStatus)
    }

    fun createPlaylist(
        videoCode: String,
        title: String,
        description: String,
        csrfToken: String?,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.myListService.createPlaylist(
                csrfToken, videoCode, title, description
            )
        },
        permittedSuccessCode = intArrayOf(500)
    ) {
        Log.d("create_playlist_body", it)
        return@websiteIOFlow WebsiteState.Success(Unit)
    }

    fun addToMyList(
        listCode: String,
        videoCode: String,
        isChecked: Boolean,
        position: Int,
        csrfToken: String?,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.myListService.addToMyList(
                csrfToken, listCode, videoCode, isChecked
            )
        }
    ) {
        Log.d("add_to_playlist_body", it)
        return@websiteIOFlow WebsiteState.Success(position)
    }

    fun modifyPlaylist(
        listCode: String,
        title: String,
        description: String,
        delete: Boolean,
        csrfToken: String?,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.myListService.modifyPlaylist(
                listCode, title, description,
                if (delete) "on" else null,
                csrfToken
            )
        },
        permittedSuccessCode = intArrayOf(302)
    ) {
        Log.d("modify_playlist_body", it)
        return@websiteIOFlow WebsiteState.Success(
            ModifiedPlaylistArgs(
                title = title, desc = description, isDeleted = delete,
            )
        )
    }

    //</editor-fold>

    //<editor-fold desc="Comment">

    fun getComments(type: String, code: String) = websiteIOFlow(
        request = { HanimeNetwork.commentService.getComments(type, code) },
        action = Parser::comments
    )

    fun getCommentReply(commentId: String) = websiteIOFlow(
        request = { HanimeNetwork.commentService.getCommentReply(commentId) },
        action = Parser::commentReply
    )

    fun postComment(
        csrfToken: String?,
        currentUserId: String,
        targetUserId: String,
        type: String,
        text: String,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.commentService.postComment(
                csrfToken, currentUserId,
                type, targetUserId, text
            )
        }
    ) {
        Log.d("post_comment_body", it)
        return@websiteIOFlow WebsiteState.Success(Unit)
    }

    fun postCommentReply(
        csrfToken: String?,
        replyCommentId: String,
        text: String,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.commentService.postCommentReply(
                csrfToken, replyCommentId, text
            )
        }
    ) {
        Log.d("post_comment_reply_body", it)
        return@websiteIOFlow WebsiteState.Success(Unit)
    }

    fun likeComment(
        csrfToken: String?,
        commentPlace: CommentPlace,
        foreignId: String?,
        isPositive: Boolean, // 你選擇的是讚還是踩，1是讚，0是踩
        likeUserId: String?,
        commentLikesCount: Int,
        commentLikesSum: Int,
        likeCommentStatus: Boolean, // 你之前有沒有點過讚，1是0否
        unlikeCommentStatus: Boolean, // 你之前有沒有點過踩，1是0否
        commentPosition: Int, comment: VideoComments.VideoComment,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.commentService.likeComment(
                csrfToken, commentPlace.value, foreignId,
                if (isPositive) 1 else 0,
                likeUserId, commentLikesCount, commentLikesSum,
                if (likeCommentStatus) 1 else 0,
                if (unlikeCommentStatus) 1 else 0
            )
        }
    ) {
        Log.d("like_comment_body", it)
        return@websiteIOFlow WebsiteState.Success(
            VideoCommentArgs(
                commentPosition, isPositive, comment
            )
        )
    }

    //</editor-fold>

    //<editor-fold desc="Subscription">

    fun subscribeArtist(
        csrfToken: String?,
        userId: String,
        artistId: String,
        // 这里表示目标状态
        status: Boolean,
    ) = websiteIOFlow(
        request = {
            HanimeNetwork.subscriptionService.subscribeArtist(
                csrfToken, userId, artistId,
                if (status) "" else "1"
            )
        }
    ) {
        Log.d("subscribe_artist_body", it)
        return@websiteIOFlow WebsiteState.Success(status)
    }

    //</editor-fold>

    //<editor-fold desc="Base">

    fun getLatestVersion(forceCheck: Boolean = true) = flow {
        emit(WebsiteState.Loading)
        val versionInfo = HUpdater.checkForUpdate(forceCheck)
        emit(WebsiteState.Success(versionInfo))
    }.catch { e ->
        when (e) {
            is CancellationException -> throw e
            else -> {
                e.printStackTrace()
                emit(WebsiteState.Error(e))
            }
        }
    }.flowOn(Dispatchers.IO)

    fun login(email: String, password: String) = flow {
        emit(WebsiteState.Loading)
        // 首先获取token
        val loginPage = HanimeNetwork.hanimeService.getLoginPage()
        val token = loginPage.body()?.string()?.let(Parser::extractTokenFromLoginPage)
        val req = HanimeNetwork.hanimeService.login(token, email, password)
        if (req.isSuccessful) {
            // 再次获取登录页面，如果失败则返回 cookie
            // 因为登录成功再次访问 login 会 404，这是判断是否登录成功的方法
            val loginPageAgain = HanimeNetwork.hanimeService.getLoginPage()
            if (loginPageAgain.code() == 404) {
                // Cookie 會返回 XSRF-TOKEN 和 hanime1_session，我們只需要後者
                // 错误的，还需要 remember_web 字段！但我没找到！
                Log.d("login_headers", req.headers().toMultimap().toString())
                emit(WebsiteState.Success(req.headers().values("Set-Cookie")))
            } else {
                emit(WebsiteState.Error(IllegalStateException(getString(R.string.account_or_password_wrong))))
            }
        } else {
            // 雙重保險
            emit(WebsiteState.Error(IllegalStateException(getString(R.string.account_or_password_wrong))))
        }
    }.catch { e ->
        emit(WebsiteState.Error(handleException(e)))
    }.flowOn(Dispatchers.IO)

    /**
     * 用于单网页的情况
     *
     * @param permittedSuccessCode 用于处理特殊情况，比如[NetworkRepo.modifyPlaylist]需要302成功
     */
    private fun <T> websiteIOFlow(
        request: suspend () -> Response<ResponseBody>,
        permittedSuccessCode: IntArray? = null,
        action: (String) -> WebsiteState<T>,
    ) = flow {
        val requestResult = request.invoke()
        val resultBody = requestResult.body()?.string()
        val permitted = permittedSuccessCode?.contains(requestResult.code()) == true
        if ((permitted || requestResult.isSuccessful)) {
            checkCloudflareChallenge(resultBody)
            emit(action.invoke(resultBody ?: EMPTY_STRING))
        } else {
            requestResult.throwRequestException()
        }
    }.catch { e ->
        emit(WebsiteState.Error(handleException(e)))
    }.flowOn(Dispatchers.IO)

    /**
     * 用于有page分页的情况
     */
    private fun <T> pageIOFlow(
        request: suspend () -> Response<ResponseBody>,
        action: (String) -> PageLoadingState<T>,
    ) = flow {
        val requestResult = request.invoke()
        val resultBody = requestResult.body()?.string()
        if (requestResult.isSuccessful && resultBody != null) {
            checkCloudflareChallenge(resultBody)
            emit(action.invoke(resultBody))
        } else {
            requestResult.throwRequestException()
        }
    }.catch { e ->
        emit(PageLoadingState.Error(handleException(e)))
    }.flowOn(Dispatchers.IO)

    /**
     * 用于影片界面
     */
    private fun <T> videoIOFlow(
        request: suspend () -> Response<ResponseBody>,
        action: (String) -> VideoLoadingState<T>,
    ) = flow {
        val requestResult = request.invoke()
        val resultBody = requestResult.body()?.string()
        if (requestResult.isSuccessful && resultBody != null) {
            checkCloudflareChallenge(resultBody)
            emit(action.invoke(resultBody))
        } else {
            requestResult.throwRequestException()
        }
    }.catch { e ->
        emit(VideoLoadingState.Error(handleException(e)))
    }.flowOn(Dispatchers.IO)

    private fun Response<ResponseBody>.throwRequestException(): Nothing {
        val body = errorBody()?.string()
        val code = code()
        val isIPBlocked = body?.contains("you have been blocked") == true
        val isCloudflareChallenge =
            headers()["cf-mitigated"]?.equals("challenge", true) == true ||
                    ((code == 403 || code == 503) && body.looksLikeCloudflareChallenge())

        if (isCloudflareChallenge && !isIPBlocked) {
            throwCloudflareVerification()
        }

        when (code) {
            403 -> if (!body.isNullOrBlank()) {
                when {
                    isIPBlocked ->
                        throw IPBlockedException(getString(R.string.do_not_use_japan_ip))

                    else ->
                        throw HanimeNotFoundException(getString(R.string.video_might_not_exist)) // 主要出現在影片界面，當你v數不大時會報403
                }
            } else throw IllegalStateException("$code ${message()}")

            500 -> throw HanimeNotFoundException(getString(R.string.video_might_not_exist)) // 主要出現在影片界面，當你v數很大時會報500

            404 -> if (!isAlreadyLogin) {
                throw IllegalStateException(getString(R.string.not_logged_in_currently))
            } else {
                throw IllegalStateException("$code ${message()}")
            }

            else -> throw IllegalStateException("$code ${message()}")
        }
    }

    private fun checkCloudflareChallenge(body: String?) {
        if (body.looksLikeCloudflareChallenge()) {
            throwCloudflareVerification()
        }
    }

    private fun throwCloudflareVerification(): Nothing {
        CloudflareVerificationManager.onChallengeDetected()
        throw CloudFlareBlockedException(
            getString(CloudFlareBlockedException.localizedMessages.random())
        )
    }

    private fun String?.looksLikeCloudflareChallenge(): Boolean {
        if (this.isNullOrBlank()) return false
        return contains("<title>Just a moment", ignoreCase = true) ||
                contains("Enable JavaScript and cookies to continue", ignoreCase = true) ||
                contains("cf-challenge-running", ignoreCase = true) ||
                contains("id=\"challenge-form\"", ignoreCase = true) ||
                contains("_cf_chl_opt", ignoreCase = true)
    }

    private fun handleException(e: Throwable): Throwable {
        return when (e) {
            is CancellationException -> throw e
            is ParseException -> {
                e.printStackTrace()
                ParseException(getString(R.string.parse_error_msg))
            }

            is SSLHandshakeException -> {
                e.printStackTrace()
                SSLHandshakeException(getString(R.string.network_unstable_msg))
            }

            else -> {
                e.printStackTrace()
                e
            }
        }
    }

    //</editor-fold>

    private fun getString(resId: Int) = applicationContext.getString(resId)
}