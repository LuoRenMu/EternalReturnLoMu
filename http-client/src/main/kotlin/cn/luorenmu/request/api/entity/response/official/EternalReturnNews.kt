package cn.luorenmu.request.api.entity.response.official

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 *
 * @author LoMu
 * Date 2026/5/23 18:04
 */
@Serializable
data class EternalReturnNews(
    @SerialName("per_page")
    val perPage: Int,
    @SerialName("current_page")
    val currentPage: Int,
    @SerialName("total_page")
    val totalPage: Int,
    @SerialName("article_count")
    val articleCount: Int,
    val articles: List<Article>
){
    @Serializable
    data class Article(
        val id: Int,
        @SerialName("board_id")
        val boardId: Int,
        @SerialName("category_id")
        val categoryId: Int,
        @SerialName("thumbnail_url")
        val thumbnailUrl: String? = null,
        @SerialName("view_count")
        val viewCount: Int,
        @SerialName("is_hidden")
        val isHidden: Int,
        @SerialName("is_pinned")
        val isPinned: Boolean,
        @SerialName("created_at")
        val createdAt: String,
        @SerialName("updated_at")
        val updatedAt: String,
        val i18ns: I18ns,
        val url: String? = null
    )

    @Serializable
    data class I18ns(
        @SerialName("zh_CN")
        val zhCN: LocaleContent? = null,
    )

    @Serializable
    data class LocaleContent(
        val locale: String,
        val title: String,
        val summary: String? = null,
        @SerialName("created_at_for_humans")
        val createdAtForHumans: String? = null,
        @SerialName("is_hidden")
        val isHidden: Boolean = false,
        @SerialName("content_type")
        val contentType: Int? = null,
        @SerialName("content_link")
        val contentLink: String? = null,
        @SerialName("content_link_target")
        val contentLinkTarget: String? = null
    )
}
