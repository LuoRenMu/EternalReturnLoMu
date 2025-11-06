package cn.luorenmu.common.util

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.BoundingBox
import com.microsoft.playwright.options.WaitUntilState
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 *
 * @author LoMu
 * Date 2025/10/25 17:40
 */
object BrowserPool {
    private const val POOL = 3
    private val webPageScreenshots = run {
        val item = CopyOnWriteArrayList<WebPageScreenshot>()
        (1..POOL).forEach { i ->
            item.add(WebPageScreenshot(false))
        }
        item
    }

    private val index = AtomicInteger(0)

    fun getBrowser(): WebPageScreenshot {
        val idx = index.getAndUpdate { (it + 1) % webPageScreenshots.size }
        return webPageScreenshots[idx]
    }

    class WebPageScreenshot internal constructor(headless: Boolean = true) {
        private val playwright: Playwright = Playwright.create()
        private val browser: Browser =

            playwright.chromium()
                .launch(BrowserType.LaunchOptions().setHeadless(headless))

        private val context = browser.newContext(
            Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .setExtraHTTPHeaders(
                    mapOf(
                        "Accept-Language" to "zh-CN,zh;q=0.9",
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
                    )
                )
        )

        private val page = context.newPage()

        /**
         * @param url 网页链接
         * @param selector 标签
         * @param waitUntilState 等待规则
         * @param pageConsumer 消费者
         */
        fun customizeSelector(
            url: String,
            selector: String,
            waitUntilState: WaitUntilState = WaitUntilState.DOMCONTENTLOADED,
            pageConsumer: (page: Page, box: BoundingBox) -> Unit,
        ) {
            synchronized(this) {
                page.navigate(url, Page.NavigateOptions().setWaitUntil(waitUntilState).setTimeout(15000.0))
                val locator = page.locator(selector)
                val boundingBox = locator.boundingBox()
                pageConsumer(page, boundingBox)
            }
        }

        fun screenshotSelector(
            url: String,
            output: Path,
            selector: String,
            waitUntilState: WaitUntilState = WaitUntilState.DOMCONTENTLOADED,
            pageConsumer: (page: Page) -> Unit = {},
        ) {
            synchronized(this) {
                page.navigate(url, Page.NavigateOptions().setWaitUntil(waitUntilState).setTimeout(15000.0))
                val locator = page.locator(selector)
                val boundingBox = locator.boundingBox()
                pageConsumer(page)
                page.screenshot(
                    Page.ScreenshotOptions().setPath(output)
                        .setFullPage(true)
                        .setClip(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height)
                )
            }
        }


        fun screenshot(
            url: String,
            output: Path,
            waitUntilState: WaitUntilState = WaitUntilState.DOMCONTENTLOADED,
            pageConsumer: (page: Page) -> Unit = {},
        ) {
            synchronized(this) {
                page.navigate(url, Page.NavigateOptions().setWaitUntil(waitUntilState).setTimeout(15000.0))
                pageConsumer(page)
                page.screenshot(
                    Page.ScreenshotOptions().setPath(output)
                        .setFullPage(true)
                )
            }
        }


    }
}