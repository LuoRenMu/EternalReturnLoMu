package cn.luorenmu.plugins.patchnotes

import cn.luorenmu.ConfigFile
import cn.luorenmu.ai.AIConfig
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.request.RequestManager
import cn.luorenmu.request.api.PakeApi
import cn.luorenmu.request.api.entity.response.official.EternalReturnNews
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import java.net.URI

internal fun officialPatchNotesService(): PatchNotesService {
    val ai = ConfigFile.config.ai
    val client = PatchNotesAiClient(AIConfig(ai.apiKey, ai.model, ai.baseUrl))
    val summarizer = PatchNotesSummarizer(client::complete)
    val selector = PatchImageSelector(client::complete)
    return PatchNotesService(PathUtils.resourcesPathResolve("patch-notes"), articles = {
        if (!client.isEnabled) emptyList() else {
            val chinese = RequestManager.call(PatchPostsApi("zh-CN,zh;q=0.9")).body<EternalReturnNews>().articles
                .filter { it.isHidden == 0 && it.categoryId == 28 }
                .sortedWith(compareByDescending<EternalReturnNews.Article> { it.createdAt }.thenByDescending { it.id }).take(1)
            val english = if (chinese.any { it.i18ns.zhCN?.takeIf { locale -> !locale.isHidden }?.title.isNullOrBlank() })
                RequestManager.call(PatchPostsApi("en-US,en;q=0.9")).body<EternalReturnNews>().articles.associateBy { it.id }
                else emptyMap()
            chinese.mapNotNull { article ->
                val locale = article.i18ns.zhCN?.takeIf { it.title.isNotBlank() && !it.isHidden }
                    ?: english[article.id]?.takeIf { it.isHidden == 0 }?.i18ns?.en?.takeIf { it.title.isNotBlank() && !it.isHidden }
                    ?: return@mapNotNull null
                // Canonical official URL, not a remote URL supplied by a command or an LLM.
                PatchArticle(article.id, locale.title, article.createdAt, article.updatedAt,
                    "https://playeternalreturn.com/posts/news/${article.id}", locale.locale.replace('_', '-'))
            }
        }
    }, content = { article ->
        val uri = URI(article.url)
        require(uri.scheme == "https" && uri.host == "playeternalreturn.com")
        RequestManager.call(PatchHtmlApi(article.url, article.language)).bodyAsText()
    }, summarize = summarizer::summarize, bodyImages = PatchBodyImages(select = selector::select))
}

/** Feature endpoint lives with its plugin rather than adding patch-specific APIs to the host. */
private class PatchPostsApi(language: String) : PakeApi(
    url = "posts/news?page=1&category=patchnote", method = HttpMethod.Get,
    headers = mutableMapOf("Accept-Language" to language),
) {
    override var baseUrl: String = "https://playeternalreturn.com/api/v1/"
}

private class PatchHtmlApi(override var url: String, language: String) : PakeApi(
    url = url, method = HttpMethod.Get,
    headers = mutableMapOf("Accept-Language" to language),
) {
    override var baseUrl: String = ""
}
