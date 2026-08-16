package cn.luorenmu.ai.news.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// ============ 根对象 ============
@Serializable
/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
data class ArticleResponse(
    @SerialName("per_page")
    val perPage: Int,

    @SerialName("current_page")
    val currentPage: Int,

    @SerialName("total_page")
    val totalPage: Int,

    @SerialName("article_count")
    val articleCount: Int,

    val articles: List<Article>,
    val board: Board
)

// ============ 文章 ============
@Serializable
data class Article(
    val id: Int,

    @SerialName("board_id")
    val boardId: Int,

    @SerialName("category_id")
    val categoryId: Int,

    @SerialName("thumbnail_url")
    val thumbnailUrl: String,

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

    val i18ns: I18ns<ArticleI18n>,
    val url: String
)

// ============ 多语言包装 ============
@Serializable
data class I18ns<T>(
    @SerialName("zh_CN")
    val zhCN: T
)

// ============ 文章多语言内容 ============
@Serializable
data class ArticleI18n(
    val locale: String,
    val title: String,
    val summary: String,

    @SerialName("created_at_for_humans")
    val createdAtForHumans: String,

    @SerialName("is_hidden")
    val isHidden: Boolean,

    @SerialName("content_type")
    val contentType: Int,

    @SerialName("content_link")
    val contentLink: String,

    @SerialName("content_link_target")
    val contentLinkTarget: String
)

// ============ 板块信息 ============
@Serializable
data class Board(
    val id: Int,
    val path: String,
    val i18ns: I18ns<BoardI18n>,
    val categories: List<Category>
)

// ============ 板块多语言内容 ============
@Serializable
data class BoardI18n(
    val id: String,      // "zh_CN"
    val name: String
)

// ============ 分类 ============
@Serializable
data class Category(
    val id: Int,

    @SerialName("board_id")
    val boardId: Int,

    val path: String,
    val color: String,
    val i18ns: I18ns<CategoryI18n>
)

// ============ 分类多语言内容 ============
@Serializable
data class CategoryI18n(
    val id: String,      // "zh_CN"
    val name: String
)
