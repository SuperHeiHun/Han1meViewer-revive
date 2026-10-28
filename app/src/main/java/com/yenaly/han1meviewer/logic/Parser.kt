package com.yenaly.han1meviewer.logic

import android.annotation.SuppressLint
import android.util.Log
import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.HanimeResolution
import com.yenaly.han1meviewer.LOCAL_DATE_FORMAT
import com.yenaly.han1meviewer.Preferences.isAlreadyLogin
import com.yenaly.han1meviewer.logic.exception.ParseException
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.model.HanimePreview
import com.yenaly.han1meviewer.logic.model.HanimeVideo
import com.yenaly.han1meviewer.logic.model.HomePage
import com.yenaly.han1meviewer.logic.model.MyListItems
import com.yenaly.han1meviewer.logic.model.Playlists
import com.yenaly.han1meviewer.logic.model.Subscription
import com.yenaly.han1meviewer.logic.model.VideoComments
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.toVideoCode
import kotlinx.datetime.LocalDate
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2023/07/31 031 16:43
 */
object Parser {

    /**
     * 所需 Regex
     */
    object Regex {
        val videoSource = Regex("""const source = '(.+)'""")

        /**
         * 網站有簡繁兩種語言，且觀看次數和日期之間用的是不換行空格（\u00A0）
         */
        val viewAndUploadTime =
            Regex("""(?:觀看次數|观看次数)[：:]\s*(.+?)次[\s\u00A0]*(\d{4}-\d{2}-\d{2})""")

        /**
         * 從縮圖地址中取出影片編號，例如 `.../thumbnail/408863h.jpg` -> `408863`
         */
        val thumbnailVideoCode = Regex("""thumbnail/(\d+)""")

        /**
         * 新版網站點讚按鈕上的點讚數，例如 `98% (657)` -> `657`
         */
        val likeCount = Regex("""\((\d+)\)""")

        /**
         * 標籤後面的數量，例如 `內射 (1)` -> `內射`
         */
        val tagCountSuffix = Regex("""\s*\(\d+\)$""")
    }

    // 首頁各欄位標題（新版網站改了部分欄位，這裡統一成繁體）
    private const val SECTION_LATEST_RELEASE = "最新上市"
    private const val SECTION_LATEST_UPLOAD = "最新上傳"
    private const val SECTION_LATEST_HANIME = "裏番"
    private const val SECTION_THEY_WATCHED = "他們在看"

    fun extractTokenFromLoginPage(body: String): String {
        val parseBody = Jsoup.parse(body).body()
        return parseBody.selectFirst("input[name=_token]")?.attr("value")
            ?: throw ParseException("Can't find csrf token from login page.")
    }

    fun homePageVer2(body: String): WebsiteState<HomePage> {
        val parseBody = Jsoup.parse(body).body()
        val csrfToken = parseBody.selectFirst("input[name=_token]")?.attr("value") // csrf token

        val userInfo = parseBody.selectFirst("div[id=user-modal-dp-wrapper]")
        val avatarUrl: String? = userInfo?.selectFirst("img")?.absUrl("src")
        val username: String? = userInfo?.getElementById("user-modal-name")?.text()

        // 新版網站的 banner 縮圖只有一張，標題放在 h1 / img[alt] 裡
        val bannerCSS = parseBody.selectFirst("div[id=home-banner-wrapper]")
        val bannerImgWrapper = bannerCSS?.previousElementSibling()
        val bannerImg = bannerImgWrapper?.selectFirst("img")
        // h1 是乾淨的標題，img 的 alt 會帶上社團名稱，優先使用 h1
        val bannerTitle = bannerCSS?.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: bannerImg?.attr("alt")?.takeIf { it.isNotBlank() }
        val bannerPic = bannerImg?.absUrl("src")
            .logIfParseNull(Parser::homePageVer2.name, "bannerPic")
        val bannerDesc = bannerCSS?.selectFirst("h4")?.ownText()
        var bannerVideoCode =
            bannerCSS?.selectFirst("a[class~=play-btn]")?.absUrl("href")?.toVideoCode()
        // 新版網站播放按鈕沒有 href 了，只能從 banner 縮圖的地址裡取影片編號
        if (bannerVideoCode == null) {
            bannerVideoCode = bannerImg?.attr("src")?.let {
                Regex.thumbnailVideoCode.find(it)?.groupValues?.get(1)
            }
        }
        // 舊版網站會把影片編號放在註釋裡
        if (bannerVideoCode == null) {
            bannerCSS?.traverse { node, _ ->
                if (node is Comment) {
                    node.data.toVideoCode()?.let {
                        bannerVideoCode = it
                        return@traverse
                    }
                }
            }
        }
        bannerVideoCode.logIfParseNull(Parser::homePageVer2.name, "bannerVideoCode")

        val banner = if (!bannerTitle.isNullOrBlank() && !bannerPic.isNullOrBlank()) {
            HomePage.Banner(
                title = bannerTitle, description = bannerDesc,
                picUrl = bannerPic, videoCode = bannerVideoCode,
            )
        } else null

        val sections = parseBody.homePageSections()

        return WebsiteState.Success(
            HomePage(
                csrfToken = csrfToken,
                avatarUrl = avatarUrl,
                username = username,
                banner = banner,
                latestHanime = sections.sectionOf(SECTION_LATEST_HANIME),
                latestRelease = sections.sectionOf(SECTION_LATEST_RELEASE),
                latestUpload = sections.sectionOf(SECTION_LATEST_UPLOAD),
                // 新版首頁沒有中文字幕、本日排行、本月排行，
                // 這三個欄位由 [NetworkRepo.getHomePage] 透過搜尋頁補齊
                chineseSubtitle = mutableListOf(),
                hanimeTheyWatched = sections.sectionOf(SECTION_THEY_WATCHED),
                hanimeCurrent = mutableListOf(),
                hotHanimeMonthly = mutableListOf(),
            )
        )
    }

    /**
     * 解析新版首頁的每個橫向欄位
     */
    private fun Element.homePageSections(): Map<String, MutableList<HanimeInfo>> {
        val sections = mutableMapOf<String, MutableList<HanimeInfo>>()
        select("div[id=home-rows-wrapper] > div").forEach { row ->
            val title = row.sectionTitle() ?: return@forEach
            val items = row.select("div.video-item-container")
            val layoutItems = if (items.isNotEmpty()) items else row.select("div[class^=card-mobile-panel]")
            if (layoutItems.isEmpty()) return@forEach
            val list = sections.getOrPut(title.normalizeSectionTitle()) { mutableListOf() }
            if (items.isNotEmpty()) {
                items.forEach { hanimeNormalItemVer3(it)?.let(list::add) }
            } else {
                layoutItems.forEachStep2 { hanimeNormalItemVer2(it)?.let(list::add) }
            }
        }
        return sections
    }

    /**
     * 取得欄位標題，例如「最新上市」
     */
    private fun Element.sectionTitle(): String? {
        // 每個欄位前面緊跟著一個 a.horizontal-row-title
        val titleLink = previousElementSibling()
            ?.takeIf { it.tagName() == "a" && it.hasClass("horizontal-row-title") }
            ?: return null
        return titleLink.selectFirst("h3")?.ownText()?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun String.normalizeSectionTitle(): String = when (this) {
        "最新上传" -> SECTION_LATEST_UPLOAD
        "里番" -> SECTION_LATEST_HANIME
        "他们在看" -> SECTION_THEY_WATCHED
        else -> this
    }

    private fun Map<String, MutableList<HanimeInfo>>.sectionOf(
        title: String,
    ): MutableList<HanimeInfo> = this[title] ?: mutableListOf()

    fun hanimeSearch(body: String): PageLoadingState<MutableList<HanimeInfo>> {
        val parseBody = Jsoup.parse(body).body()
        // 搜尋結果都在 search-rows-wrapper 裡
        val resultContainer = parseBody.selectFirst("div.search-rows-wrapper") ?: parseBody

        // 新版網站：一般搜尋使用橫向卡片
        val normalItems = resultContainer.select("div.video-item-container")
        if (normalItems.isNotEmpty()) {
            val normalList = mutableListOf<HanimeInfo>()
            normalItems.forEach { hanimeNormalItemVer3(it)?.let(normalList::add) }
            if (normalList.isNotEmpty()) {
                Log.d("search_result", "$normalList")
                return PageLoadingState.Success(normalList)
            }
        }

        // 新版網站：按類型（genre）搜尋時使用直式海報卡片
        val simplifiedWrapper = resultContainer.selectFirst("div.home-rows-videos-wrapper")
        if (simplifiedWrapper != null) {
            val simplifiedList = mutableListOf<HanimeInfo>()
            simplifiedWrapper.children().forEach {
                hanimeSimplifiedItem(it)?.let(simplifiedList::add)
            }
            if (simplifiedList.isNotEmpty()) {
                Log.d("search_result", "$simplifiedList")
                return PageLoadingState.Success(simplifiedList)
            }
        }

        // 舊版網站
        val oldItems = resultContainer.select("div[class^=card-mobile-panel]")
        if (oldItems.isNotEmpty()) {
            val oldList = mutableListOf<HanimeInfo>()
            oldItems.forEachStep2 { hanimeNormalItemVer2(it)?.let(oldList::add) }
            if (oldList.isNotEmpty()) {
                Log.d("search_result", "$oldList")
                return PageLoadingState.Success(oldList)
            }
        }

        return PageLoadingState.NoMoreData
    }

    /**
     * 新版網站的橫向影片卡片（`div.video-item-container`），
     * 首頁、搜尋、相關影片、播放清單都使用這種結構。
     */
    private fun hanimeNormalItemVer3(item: Element): HanimeInfo? {
        val videoCode = item.selectFirst("a.video-link")?.absUrl("href")?.toVideoCode()
            ?: item.selectFirst("a[href*='watch?v=']")?.absUrl("href")?.toVideoCode()
            ?: item.attr("data-href").toVideoCode()
        val title = item.selectFirst("div.title")?.text()?.trim()
            ?: item.selectFirst("div[class=card-mobile-title]")?.text()?.trim()
            ?: item.attr("title").trim().takeIf { it.isNotBlank() }
        val coverUrl = item.selectFirst("img.main-thumb")?.absUrl("src")
            ?: item.select("img").getOrNull(1)?.absUrl("src")
            ?: item.selectFirst("img")?.absUrl("src")
        if (videoCode.isNullOrBlank() || title.isNullOrBlank() || coverUrl.isNullOrBlank()) {
            // 大部分情況是廣告，直接忽略
            return null
        }
        val duration = item.selectFirst("div.duration")?.text()?.trim()
        val statItems = item.select("div.stat-item")
        val views = statItems.getOrNull(1)?.text()?.trim()
        val uploader = item.selectFirst("div.subtitle a")?.text()?.trim()
        val uploadTime = item.selectFirst("span.subtitle-time")?.text()?.trim()
            ?.trimStart('•', '·', ' ')
        return HanimeInfo(
            title = title,
            coverUrl = coverUrl,
            videoCode = videoCode,
            duration = duration,
            uploader = uploader,
            views = views,
            uploadTime = uploadTime?.takeIf { it.isNotBlank() },
            genre = null,
            itemType = HanimeInfo.NORMAL,
        )
    }

    // 每一個正常視頻單元（舊版網站）
    // #issue-38: 解析錯誤，原來是加廣告了！所以遇到無法處理的直接返回null。
    private fun hanimeNormalItemVer2(hanimeSearchItem: Element): HanimeInfo? {
        val title =
            hanimeSearchItem.selectFirst("div[class=card-mobile-title]")?.text()
                .logIfParseNull(Parser::hanimeNormalItemVer2.name, "title") // title
        val coverUrl =
            hanimeSearchItem.select("img").getOrNull(1)?.absUrl("src")
                .logIfParseNull(Parser::hanimeNormalItemVer2.name, "coverUrl") // coverUrl
        val videoCode =
            hanimeSearchItem.previousElementSibling()?.absUrl("href")?.toVideoCode()
                .logIfParseNull(Parser::hanimeNormalItemVer2.name, "videoCode") // videoCode
        if (title == null || coverUrl == null || videoCode == null) return null
        val durationAndViews = hanimeSearchItem.select("div[class=card-mobile-duration]")
        val mDuration = durationAndViews.getOrNull(0)?.text() // 改了
        val views = durationAndViews.getOrNull(1)?.text() // 改了
        return HanimeInfo(
            title = title,
            coverUrl = coverUrl,
            videoCode = videoCode,
            duration = mDuration.logIfParseNull(Parser::hanimeNormalItemVer2.name, "duration"),
            uploader = null,
            views = views.logIfParseNull(Parser::hanimeNormalItemVer2.name, "views"),
            uploadTime = null,
            genre = null,
            itemType = HanimeInfo.NORMAL
        )
    }

    // 每一個簡化版視頻單元
    // 新版網站的直式海報卡片（按類型搜尋時使用）
    private fun hanimeSimplifiedItem(hanimeSearchItem: Element): HanimeInfo? {
        val anchor = when {
            hanimeSearchItem.tagName() == "a" -> hanimeSearchItem
            hanimeSearchItem.parent()?.tagName() == "a" -> hanimeSearchItem.parent()
            else -> hanimeSearchItem.selectFirst("a[href*='watch?v=']")
        }
        val videoCode = anchor?.absUrl("href")?.toVideoCode()
            ?: hanimeSearchItem.attr("data-href").toVideoCode()
            .logIfParseNull(Parser::hanimeSimplifiedItem.name, "videoCode")
        val coverUrl = hanimeSearchItem.selectFirst("img")?.absUrl("src")
            .logIfParseNull(Parser::hanimeSimplifiedItem.name, "coverUrl")
        val title = hanimeSearchItem.selectFirst("div[class=home-rows-videos-title]")?.text()?.trim()
            ?: hanimeSearchItem.selectFirst("div[class=grid-title]")?.text()?.trim()
            ?: hanimeSearchItem.selectFirst("div[class$=title]")?.text()?.trim()
            .logIfParseNull(Parser::hanimeSimplifiedItem.name, "title")
        if (videoCode.isNullOrBlank() || coverUrl.isNullOrBlank() || title.isNullOrBlank()) return null
        return HanimeInfo(
            title = title,
            coverUrl = coverUrl,
            videoCode = videoCode,
            itemType = HanimeInfo.SIMPLIFIED
        )
    }

    fun hanimeVideoVer2(body: String): VideoLoadingState<HanimeVideo> {
        val parseBody = Jsoup.parse(body).body()
        val csrfToken = parseBody.selectFirst("input[name=_token]")?.attr("value") // csrf token

        val currentUserId =
            parseBody.selectFirst("input[name=like-user-id]")?.attr("value") // current user id
                ?: parseBody.selectFirst("input[name=playlist-user-id]")?.attr("value")
                ?: parseBody.selectFirst("input[name=comment-user-id]")?.attr("value")

        val title = parseBody.getElementById("shareBtn-title")?.text()?.trim()
            .throwIfParseNull(Parser::hanimeVideoVer2.name, "title")

        // 目前正在觀看的影片編號
        val currentVideoCode =
            parseBody.selectFirst("meta[property=og:url]")?.attr("content")?.toVideoCode()
                ?: parseBody.selectFirst("video[id=player]")?.absUrl("poster")?.let {
                    Regex.thumbnailVideoCode.find(it)?.groupValues?.get(1)
                }

        // 點讚狀態
        val likeWrapper = parseBody.selectFirst("div[id=video-like-form-wrapper]")
        val likeStatus = parseBody.selectFirst("input[name=like-status]")?.attr("value")
        val likesCount = parseBody.selectFirst("input[name=likes-count]")
            ?.attr("value")?.toIntOrNull()
            ?: likeWrapper?.text()?.let {
                Regex.likeCount.find(it)?.groupValues?.get(1)?.toIntOrNull()
            }
        val isFav = likeStatus == "1" ||
                likeWrapper?.selectFirst("button[id=video-like-btn]")?.hasClass("active") == true

        // 觀看次數與上傳時間（新版網站有多個 video-details-wrapper，要找對的那個）
        val uploadTimeWithViews = parseBody.select("div.video-details-wrapper")
            .firstOrNull { Regex.viewAndUploadTime.containsMatchIn(it.text()) }
            ?.text()
        val uploadTimeWithViewsGroups = uploadTimeWithViews?.let {
            Regex.viewAndUploadTime.find(it)?.groups
        }
        val uploadTime = uploadTimeWithViewsGroups?.get(2)?.value?.let { time ->
            LocalDate.parse(time, LOCAL_DATE_FORMAT)
        }
        val views = uploadTimeWithViewsGroups?.get(1)?.value

        val videoCaptionText = parseBody.selectFirst("div[class^=video-caption-text]")
        val chineseTitle = videoCaptionText?.previousElementSibling()?.ownText()?.trim()
        val introduction = videoCaptionText?.ownText()?.trim()

        val tags = parseBody.getElementsByClass("single-video-tag")
        val tagList = mutableListOf<String>()
        tags.forEach { tag ->
            val child = tag.childOrNull(0)
            if (child != null && child.hasAttr("href")) {
                val text = child.text().trim()
                    .replace(Regex.tagCountSuffix, "")
                    .trimStart('#', ' ')
                    .trim()
                if (text.isNotEmpty()) tagList.add(text)
            }
        }

        // 使用者播放清單
        val myListInfo = mutableListOf<HanimeVideo.MyList.MyListInfo>()
        // 新版網站
        parseBody.select("input.playlist-checkbox").forEach { input ->
            val listCode = input.attr("id").takeIf { it.isNotBlank() } ?: return@forEach
            val listTitle = parseBody.selectFirst("label[for='$listCode']")?.text()?.trim()
                ?: input.parent()?.selectFirst("span")?.ownText()?.trim()
            if (!listTitle.isNullOrBlank()) {
                myListInfo += HanimeVideo.MyList.MyListInfo(
                    code = listCode,
                    title = listTitle,
                    isSelected = input.hasAttr("checked"),
                )
            }
        }
        // 舊版網站
        if (myListInfo.isEmpty()) {
            parseBody.select("div[class~=playlist-checkbox-wrapper]").forEach {
                val listTitle = it.selectFirst("span")?.ownText()?.trim()
                    .logIfParseNull(
                        Parser::hanimeVideoVer2.name, "myListTitle", loginNeeded = true
                    )
                val listInput = it.selectFirst("input")
                val listCode = listInput?.attr("id")?.takeIf { code -> code.isNotBlank() }
                    .logIfParseNull(
                        Parser::hanimeVideoVer2.name, "myListCode", loginNeeded = true
                    )
                if (!listTitle.isNullOrBlank() && listCode != null) {
                    myListInfo += HanimeVideo.MyList.MyListInfo(
                        code = listCode, title = listTitle,
                        isSelected = listInput?.hasAttr("checked") == true
                    )
                }
            }
        }
        val saveCheckbox = parseBody.getElementById("playlist-save-checkbox")
        val isWatchLater = when {
            saveCheckbox == null -> false
            saveCheckbox.tagName() == "input" -> saveCheckbox.hasAttr("checked")
            else -> saveCheckbox.selectFirst("input")?.hasAttr("checked") == true
        }
        val myList = HanimeVideo.MyList(isWatchLater = isWatchLater, myListInfo = myListInfo)

        // 系列影片（播放清單）
        val playlistWrapper = parseBody.selectFirst("div.video-playlist-wrapper")
        val playlist = playlistWrapper?.let {
            val playlistVideoList = mutableListOf<HanimeInfo>()
            // 新版網站這個區塊列出的是「社團」的所有影片，h4 裡是「社團 + 作者名」
            val playlistName = it.selectFirst("h4 a")?.text()?.trim()
                ?: it.selectFirst("h4")?.text()?.trim()
            val playlistScroll = it.getElementById("playlist-scroll")
            playlistScroll?.children()?.forEach { parent ->
                playlistItem(parent, currentVideoCode)?.let(playlistVideoList::add)
            }
            if (playlistVideoList.isEmpty()) null
            else HanimeVideo.Playlist(
                playlistName = playlistName, video = playlistVideoList
            )
        }

        // 相關影片
        val relatedAnimeList = mutableListOf<HanimeInfo>()
        val relatedTabContent = parseBody.getElementById("related-tabcontent")
        if (relatedTabContent != null) {
            val container = relatedTabContent.selectFirst("div.desktop-video-grid")
                ?: relatedTabContent
            container.select("div.video-item-container").forEach {
                hanimeNormalItemVer3(it)?.let(relatedAnimeList::add)
            }
        }
        Log.d("related_anime_list", relatedAnimeList.toString())

        val hanimeResolution = HanimeResolution()
        val videoClass = parseBody.selectFirst("video[id=player]")
        val videoCoverUrl = videoClass?.absUrl("poster").orEmpty()
        val videos = videoClass?.children()
        if (!videos.isNullOrEmpty()) {
            videos.forEach { source ->
                val resolution = source.attr("size") + "P"
                val sourceUrl = source.absUrl("src")
                val videoType = source.attr("type")
                hanimeResolution.parseResolution(resolution, sourceUrl, videoType)
            }
        } else {
            val playerDivWrapper = parseBody.selectFirst("div[id=player-div-wrapper]")
            playerDivWrapper?.select("script")?.let { scripts ->
                for (script in scripts) {
                    val data = script.data()
                    if (data.isBlank()) continue
                    val result =
                        Regex.videoSource.find(data)?.groups?.get(1)?.value ?: continue
                    hanimeResolution.parseResolution(null, result)
                    break
                }
            }
        }

        val artistAvatarUrl = parseBody.getElementById("video-user-avatar")?.absUrl("src")
        val artistNameCSS = parseBody.getElementById("video-artist-name")
        val artistGenre = artistNameCSS?.nextElementSibling()?.text()?.trim()
        val artistName = artistNameCSS?.text()?.trim()
        // 新版網站把表單放在 video-subscribe-form-wrapper 裡
        val post = let {
            val userId = parseBody.selectFirst("input[name=subscribe-user-id]")?.attr("value")
            val artistId = parseBody.selectFirst("input[name=subscribe-artist-id]")?.attr("value")
            val isSubscribed = parseBody.selectFirst("input[name=subscribe-status]")?.attr("value")
            if (userId != null && artistId != null && isSubscribed != null) {
                HanimeVideo.Artist.POST(
                    userId = userId,
                    artistId = artistId,
                    isSubscribed = isSubscribed == "1"
                )
            } else null
        }
        val artist = if (artistAvatarUrl != null && artistName != null && artistGenre != null) {
            HanimeVideo.Artist(
                name = artistName,
                avatarUrl = artistAvatarUrl,
                genre = artistGenre,
                post = post,
            )
        } else null

        return VideoLoadingState.Success(
            HanimeVideo(
                title = title, coverUrl = videoCoverUrl,
                chineseTitle = chineseTitle.logIfParseNull(
                    Parser::hanimeVideoVer2.name,
                    "chineseTitle"
                ),
                uploadTime = uploadTime.logIfParseNull(Parser::hanimeVideoVer2.name, "uploadTime"),
                views = views.logIfParseNull(Parser::hanimeVideoVer2.name, "views"),
                introduction = introduction.logIfParseNull(
                    Parser::hanimeVideoVer2.name,
                    "introduction"
                ),
                videoUrls = hanimeResolution.toResolutionLinkMap(),
                tags = tagList,
                myList = myList,
                playlist = playlist,
                relatedHanimes = relatedAnimeList,
                artist = artist.logIfParseNull(Parser::hanimeVideoVer2.name, "artist"),
                favTimes = likesCount,
                isFav = isFav,
                csrfToken = csrfToken,
                currentUserId = currentUserId
            )
        )
    }

    /**
     * 新版網站的系列影片單元（`div.playlist-video-card`）
     */
    private fun playlistItem(parent: Element, currentVideoCode: String?): HanimeInfo? {
        val card = parent.selectFirst("div.playlist-video-card") ?: parent
        val videoCode = card.selectFirst("a[href*='watch?v=']")?.absUrl("href")?.toVideoCode()
            ?: parent.attr("data-href").toVideoCode()
        val title = card.selectFirst(".video-title a")?.text()?.trim()
            ?: card.selectFirst(".video-title")?.text()?.trim()
            ?: parent.attr("title").trim().takeIf { it.isNotBlank() }
        val coverUrl = card.selectFirst("img.main-thumb")?.absUrl("src")
            ?: card.selectFirst("img")?.absUrl("src")
        if (videoCode.isNullOrBlank() || title.isNullOrBlank() || coverUrl.isNullOrBlank()) {
            return null
        }
        val cardDuration = card.select("div.duration")
        val duration = cardDuration.firstOrNull()?.text()?.trim()
        val views = card.select("div.stat-item").getOrNull(1)?.text()?.trim()
        return HanimeInfo(
            title = title, coverUrl = coverUrl, videoCode = videoCode,
            duration = duration, views = views,
            isPlaying = videoCode == currentVideoCode,
            itemType = HanimeInfo.NORMAL
        )
    }

    fun hanimePreview(body: String): WebsiteState<HanimePreview> {
        val parseBody = Jsoup.parse(body).body()

        // latest hanime
        val latestHanimeList = mutableListOf<HanimeInfo>()
        val latestHanimeClass = parseBody.selectFirst("div[class$=owl-theme]")
        latestHanimeClass?.let {
            val latestHanimeItems = latestHanimeClass.select("div[class=home-rows-videos-div]")
            latestHanimeItems.forEach { latestHanimeItem ->
                val coverUrl = latestHanimeItem.selectFirst("img")?.absUrl("src")
                    .throwIfParseNull(Parser::hanimePreview.name, "coverUrl")
                val title = latestHanimeItem.selectFirst("div[class$=title]")?.text()
                    .throwIfParseNull(Parser::hanimePreview.name, "title")
                latestHanimeList.add(
                    HanimeInfo(
                        coverUrl = coverUrl,
                        title = title,
                        videoCode = EMPTY_STRING /* empty string here! */,
                        itemType = HanimeInfo.SIMPLIFIED
                    )
                )
            }
        }

        val contentPaddingClass = parseBody.select("div[class=content-padding] > div")
        val previewInfo = mutableListOf<HanimePreview.PreviewInfo>()
        for (i in 0 until contentPaddingClass.size / 2) {

            val firstPart = contentPaddingClass.getOrNull(i * 2)
            val secondPart = contentPaddingClass.getOrNull(i * 2 + 1)

            val videoCode = firstPart?.id()
            val title = firstPart?.selectFirst("h4")?.text()
            val coverUrl =
                firstPart?.selectFirst("div[class=preview-info-cover] > img")?.absUrl("src")
            val previewInfoContentClass =
                firstPart?.getElementsByClass("preview-info-content-padding")?.firstOrNull()
            val videoTitle = previewInfoContentClass?.selectFirst("h4")?.text()
            val brand = previewInfoContentClass?.selectFirst("h5")?.selectFirst("a")?.text()
            val releaseDate = previewInfoContentClass?.select("h5")?.getOrNull(1)?.ownText()

            val introduction = secondPart?.selectFirst("h5")?.text()
            val tagClass = secondPart?.select("div[class=single-video-tag] > a")
            val tags = mutableListOf<String>()
            tagClass?.forEach { tag: Element? ->
                tag?.let { tags.add(tag.text()) }
            }
            val relatedPicClass = secondPart?.select("img[class=preview-image-modal-trigger]")
            val relatedPics = mutableListOf<String>()
            relatedPicClass?.forEach { relatedPic: Element? ->
                relatedPic?.let { relatedPics.add(relatedPic.absUrl("src")) }
            }

            previewInfo.add(
                HanimePreview.PreviewInfo(
                    title = title,
                    videoTitle = videoTitle,
                    coverUrl = coverUrl,
                    introduction = introduction.logIfParseNull(
                        Parser::hanimePreview.name,
                        "$title introduction"
                    ),
                    brand = brand.logIfParseNull(Parser::hanimePreview.name, "$title brand"),
                    releaseDate = releaseDate.logIfParseNull(
                        Parser::hanimePreview.name,
                        "$title releaseDate"
                    ),
                    videoCode = videoCode.logIfParseNull(
                        Parser::hanimePreview.name,
                        "$title videoCode"
                    ),
                    tags = tags,
                    relatedPicsUrl = relatedPics
                )
            )
        }

        val header = parseBody.selectFirst("div[id=player-div-wrapper]")
        val headerPicUrl = header?.selectFirst("img")?.absUrl("src")
        // 新版網站有多個 hidden-md hidden-lg 容器，要找包含預覽頁連結的那一個
        val previewNav = parseBody.getElementsByClass("hidden-md hidden-lg")
            .firstOrNull { it.selectFirst("a[href*='/previews/']") != null }
        val hasPrevious = previewNav?.selectFirst("div[style*=left]") != null
        val hasNext = previewNav?.selectFirst("div[style*=right]") != null

        return WebsiteState.Success(
            HanimePreview(
                headerPicUrl = headerPicUrl.logIfParseNull(
                    Parser::hanimePreview.name,
                    "headerPicUrl"
                ),
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                latestHanime = latestHanimeList,
                previewInfo = previewInfo
            )
        )
    }

    fun myListItems(body: String): PageLoadingState<MyListItems<HanimeInfo>> {
        val parseBody = Jsoup.parse(body).body()
        val csrfToken = parseBody.selectFirst("input[name=_token]")?.attr("value")
        val desc = parseBody.getElementById("playlist-show-description")?.ownText()

        val myListHanimeList = parseBody.extractVideoItems()
        if (myListHanimeList.isEmpty()) {
            return PageLoadingState.NoMoreData
        }
        return PageLoadingState.Success(
            MyListItems(
                myListHanimeList,
                desc = desc,
                csrfToken = csrfToken
            )
        )
    }

    /**
     * 從頁面中找出所有的影片單元，兼容新舊版網站
     */
    private fun Element.extractVideoItems(): MutableList<HanimeInfo> {
        val container = sequenceOf(
            "div.home-rows-videos-wrapper",
            "div.search-rows-wrapper",
            "div[id=home-rows-wrapper]",
        ).firstNotNullOfOrNull { selectFirst(it) } ?: this

        val result = mutableListOf<HanimeInfo>()
        container.select("div.video-item-container").forEach {
            hanimeNormalItemVer3(it)?.let(result::add)
        }
        if (result.isNotEmpty()) return result
        container.select("div.home-rows-videos-div").forEach {
            hanimeSimplifiedItem(it)?.let(result::add)
        }
        if (result.isNotEmpty()) return result
        container.select("div[class^=card-mobile-panel]").forEachStep2 {
            hanimeNormalItemVer2(it)?.let(result::add)
        }
        return result
    }

    fun subscriptionItems(body: String): PageLoadingState<MyListItems<Subscription>> {
        val parseBody = Jsoup.parse(body).body()
        val csrfToken = parseBody.selectFirst("input[name=_token]")?.attr("value")

        val subscriptionList = mutableListOf<Subscription>()
        val allArtistsClass = parseBody.getElementsByClass("home-rows-videos-wrapper").firstOrNull()
        allArtistsClass?.let { artistClass ->
            if (allArtistsClass.childrenSize() == 0) {
                return PageLoadingState.NoMoreData
            }
            allArtistsClass.children().forEach { artistElement ->
                val title = artistElement.selectFirst("div[class$=search-artist-title]")?.text()
                    ?: artistElement.selectFirst("div[class$=title]")?.text()
                    .logIfParseNull(Parser::subscriptionItems.name, "title", loginNeeded = true)
                val avatarUrl = artistElement.select("img").let {
                    it.getOrNull(1) ?: it.firstOrNull()
                }?.absUrl("src")
                    .logIfParseNull(Parser::subscriptionItems.name, "avatarUrl", loginNeeded = true)
                val artistId =
                    artistElement.selectFirst("input[name=playlist-show-video-id]")
                        ?.attr("value")?.takeIf { id -> id.isNotBlank() }
                        .logIfParseNull(
                            Parser::subscriptionItems.name, "artistId", loginNeeded = true
                        )
                if (!title.isNullOrBlank()) {
                    subscriptionList += Subscription(
                        name = title, avatarUrl = avatarUrl,
                        artistId = artistId
                    )
                }
            }
        }.logIfParseNull(Parser::subscriptionItems.name, "allArtistsClass_CSS")

        if (subscriptionList.isEmpty()) return PageLoadingState.NoMoreData

        return PageLoadingState.Success(
            MyListItems(
                subscriptionList,
                desc = null,
                csrfToken = csrfToken
            )
        )
    }

    fun playlists(body: String): WebsiteState<Playlists> {
        val parseBody = Jsoup.parse(body).body()
        val csrfToken = parseBody.selectFirst("input[name=_token]")?.attr("value")
        var lists = parseBody.select("div[class~=single-user-playlist]")
        if (lists.isEmpty()) {
            // 新版網站：找出所有導向播放清單的區塊
            lists = parseBody.select("a[href*='playlist?list=']").mapNotNull { it.parent() }
                .distinct().let { Elements(it) }
        }
        val playlists = mutableListOf<Playlists.Playlist>()
        lists.forEach {
            val listCode = it.selectFirst("a[href*='list=']")?.absUrl("href")?.let { link ->
                link.substringAfter("list=", EMPTY_STRING).substringBefore('&')
            }?.takeIf { code -> code.isNotBlank() }
                .logIfParseNull(Parser::playlists.name, "listCode")
            val listTitle = it.selectFirst("div[class=card-mobile-title]")?.ownText()?.trim()
                ?: it.selectFirst("div[class=card-mobile-title]")?.text()?.trim()
            val listTotal = it.selectFirst("div[style]")?.text()?.toIntOrNull()
            if (!listCode.isNullOrBlank() && !listTitle.isNullOrBlank()) {
                playlists += Playlists.Playlist(
                    listCode = listCode, title = listTitle,
                    total = listTotal ?: 0
                )
            }
        }
        return WebsiteState.Success(Playlists(playlists = playlists, csrfToken = csrfToken))
    }

    @SuppressLint("BuildListAdds")
    fun comments(body: String): WebsiteState<VideoComments> {
        val jsonObject = JSONObject(body)
        val commentBody = jsonObject.get("comments").toString()
        val parseBody = Jsoup.parse(commentBody).body()
        val csrfToken = parseBody.selectFirst("input[name=_token]")?.attr("value")
        val currentUserId = parseBody.selectFirst("input[name=comment-user-id]")?.attr("value")
        val commentList = mutableListOf<VideoComments.VideoComment>()
        val allCommentsClass = parseBody.getElementById("comment-start")

        // 新版網站的每則留言結構為：
        // <a>頭像</a> + <div class=report-btn-wrapper>內容</div> +
        // <div id=comment-like-form-wrapper>點讚</div> + <br>
        allCommentsClass?.children()?.forEach { child ->
            if (!child.hasClass("report-btn-wrapper")) return@forEach
            val avatarUrl = (child.selectFirst("img")
                ?: child.previousElementSibling()?.selectFirst("img"))?.absUrl("src")
                .throwIfParseNull(Parser::comments.name, "avatarUrl")
            val textClass = child.getElementsByClass("comment-index-text")
            val nameAndDateClass = textClass.firstOrNull()
            val username = nameAndDateClass?.selectFirst("a")?.ownText()?.trim()
                .throwIfParseNull(Parser::comments.name, "username")
            val date = nameAndDateClass?.selectFirst("span")?.ownText()?.trim()
                .throwIfParseNull(Parser::comments.name, "date")
            val content = textClass.getOrNull(1)?.text()
                .throwIfParseNull(Parser::comments.name, "content")
            val likeWrapper = child.nextElementSibling()
                ?.takeIf { it.id() == "comment-like-form-wrapper" }
            val hasMoreReplies = likeWrapper?.selectFirst("div[class^=load-replies-btn]") != null
            val thumbUp = likeWrapper
                ?.select("span[style]")?.getOrNull(1)
                ?.text()?.toIntOrNull()
            val id = likeWrapper?.selectFirst("div[class^=load-replies-btn]")
                ?.attr("data-commentid")?.takeIf { it.isNotBlank() }
                ?: likeWrapper?.selectFirst("div[id^=reply-section-wrapper]")
                    ?.id()?.substringAfterLast("-")
                ?: child.selectFirst("span[data-reportable-id]")
                    ?.attr("data-reportable-id")?.takeIf { it.isNotBlank() }

            val postContainer = likeWrapper ?: child
            val foreignId = postContainer.getElementById("foreign_id")?.attr("value")
            val isPositive = postContainer.getElementById("is_positive")?.attr("value")
            val likeUserId =
                postContainer.selectFirst("input[name=comment-like-user-id]")?.attr("value")
            val commentLikesCount =
                postContainer.selectFirst("input[name=comment-likes-count]")?.attr("value")
            val commentLikesSum =
                postContainer.selectFirst("input[name=comment-likes-sum]")?.attr("value")
            val likeCommentStatus =
                postContainer.selectFirst("input[name=like-comment-status]")?.attr("value")
            val unlikeCommentStatus =
                postContainer.selectFirst("input[name=unlike-comment-status]")?.attr("value")

            val post = VideoComments.VideoComment.POST(
                foreignId.logIfParseNull(Parser::comments.name, "foreignId", loginNeeded = true),
                isPositive == "1",
                likeUserId.logIfParseNull(Parser::comments.name, "likeUserId", loginNeeded = true),
                commentLikesCount?.toIntOrNull().logIfParseNull(
                    Parser::comments.name,
                    "commentLikesCount", loginNeeded = true
                ),
                commentLikesSum?.toIntOrNull().logIfParseNull(
                    Parser::comments.name,
                    "commentLikesSum", loginNeeded = true
                ),
                likeCommentStatus == "1",
                unlikeCommentStatus == "1",
            )
            commentList.add(
                VideoComments.VideoComment(
                    avatar = avatarUrl, username = username, date = date,
                    content = content, hasMoreReplies = hasMoreReplies,
                    thumbUp = thumbUp.logIfParseNull(Parser::comments.name, "thumbUp"),
                    id = id.logIfParseNull(Parser::comments.name, "id"),
                    isChildComment = false, post = post
                )
            )
        }
        Log.d("commentList", commentList.toString())
        return WebsiteState.Success(
            VideoComments(
                commentList,
                currentUserId,
                csrfToken
            )
        )
    }

    fun commentReply(body: String): WebsiteState<VideoComments> {
        val jsonObject = JSONObject(body)
        val replyBody = jsonObject.get("replies").toString()
        val replyList = mutableListOf<VideoComments.VideoComment>()
        val parseBody = Jsoup.parse(replyBody).body()
        val replyStart = parseBody.selectFirst("div[id^=reply-start]")
        replyStart?.children()?.forEach { basicClass ->
            if (!basicClass.hasClass("report-btn-wrapper")) return@forEach
            val postClass = basicClass.nextElementSibling()

            val avatarUrl = basicClass.selectFirst("img")?.absUrl("src")
                .throwIfParseNull(Parser::commentReply.name, "avatarUrl")
            val textClass = basicClass.getElementsByClass("comment-index-text")
            val nameAndDateClass = textClass.firstOrNull()
            val username = nameAndDateClass?.selectFirst("a")?.ownText()?.trim()
                .throwIfParseNull(Parser::commentReply.name, "name")
            val date = nameAndDateClass?.selectFirst("span")?.ownText()?.trim()
                .throwIfParseNull(Parser::commentReply.name, "date")
            val content = textClass.getOrNull(1)?.text()
                .throwIfParseNull(Parser::commentReply.name, "content")
            val thumbUp = postClass
                ?.select("span[style]")?.getOrNull(1)
                ?.text()?.toIntOrNull()

            val foreignId =
                postClass?.getElementById("foreign_id")?.attr("value")
            val isPositive =
                postClass?.getElementById("is_positive")?.attr("value")
            val likeUserId =
                postClass?.selectFirst("input[name=comment-like-user-id]")?.attr("value")
            val commentLikesCount =
                postClass?.selectFirst("input[name=comment-likes-count]")?.attr("value")
            val commentLikesSum =
                postClass?.selectFirst("input[name=comment-likes-sum]")?.attr("value")
            val likeCommentStatus =
                postClass?.selectFirst("input[name=like-comment-status]")?.attr("value")
            val unlikeCommentStatus =
                postClass?.selectFirst("input[name=unlike-comment-status]")?.attr("value")
            val post = VideoComments.VideoComment.POST(
                foreignId.logIfParseNull(
                    Parser::commentReply.name,
                    "foreignId",
                    loginNeeded = true
                ),
                isPositive == "1",
                likeUserId.logIfParseNull(
                    Parser::commentReply.name,
                    "likeUserId",
                    loginNeeded = true
                ),
                commentLikesCount?.toIntOrNull().logIfParseNull(
                    Parser::commentReply.name,
                    "commentLikesCount", loginNeeded = true
                ),
                commentLikesSum?.toIntOrNull().logIfParseNull(
                    Parser::commentReply.name,
                    "commentLikesSum", loginNeeded = true
                ),
                likeCommentStatus == "1",
                unlikeCommentStatus == "1",
            )
            replyList.add(
                VideoComments.VideoComment(
                    avatar = avatarUrl, username = username, date = date,
                    content = content,
                    thumbUp = thumbUp.logIfParseNull(Parser::commentReply.name, "thumbUp"),
                    // 新版網站把回覆的 id 放在檢舉按鈕上
                    id = basicClass.selectFirst("span[data-reportable-id]")
                        ?.attr("data-reportable-id")?.takeIf { it.isNotBlank() },
                    isChildComment = true, post = post
                )
            )
        }

        return WebsiteState.Success(VideoComments(replyList))
    }

    /**
     * 這個網站的網頁結構真的很奇怪，所以我寫了一個 forEachStep2 來處理
     */
    private inline fun Elements.forEachStep2(action: (Element) -> Unit) {
        for (i in 0 until size step 2) {
            action(get(i))
        }
    }

    /**
     * 得到 Element 的 child，如果 index 超出範圍，就返回 null
     */
    private fun Element.childOrNull(index: Int): Element? {
        return try {
            child(index)
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }

    /**
     * 基本都是必需的參數，所以如果是 null，就直接丟出 [ParseException]
     *
     * @param funcName 這個參數是在哪個函數中被使用的
     * @param varName 這個參數的名稱
     * @return 如果 [this] 不是 null，就回傳 [this]
     * @throws ParseException 如果 [this] 是 null，就丟出 [ParseException]
     */
    private fun <T> T?.throwIfParseNull(funcName: String, varName: String): T = this
        ?: throw ParseException(funcName, varName)

    /**
     * 如果 [this] 是 null，就在 logcat 中顯示訊息
     *
     * @param funcName 這個參數是在哪個函數中被使用的
     * @param varName 這個參數的名稱
     * @return 回傳 [this]
     */
    private fun <T> T?.logIfParseNull(
        funcName: String, varName: String, loginNeeded: Boolean = false,
    ): T? = also {
        if (it == null) {
            if (loginNeeded) {
                if (isAlreadyLogin) {
                    Log.d("Parse::$funcName", "[$varName] is null. 而且處於登入狀態，這有點不正常")
                }
            } else {
                Log.d("Parse::$funcName", "[$varName] is null. 這有點不正常")
            }
        }
    }
}
