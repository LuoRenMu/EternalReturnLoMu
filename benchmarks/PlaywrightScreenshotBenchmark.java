import com.microsoft.playwright.*;
import java.nio.file.*;
import java.util.*;

/** Standalone historical-engine benchmark; not part of production dependencies. */
public class PlaywrightScreenshotBenchmark {
    public static void main(String[] args) {
        String cards = "<div class='left'></div><main>" + "<article></article>".repeat(8) + "</main>";
        String html = "<style>body{margin:0;background:#f5f5f5}.page{width:1290px;height:1250px;padding:20px;box-sizing:border-box}" +
            ".head{height:211px;background:#363944;margin-bottom:20px}.body{display:flex;gap:20px}.left{width:370px;height:455px;background:white}" +
            "main{width:860px}article{height:98px;background:white;border-radius:20px;margin-bottom:22px;border:1px solid #e6e6e6}</style>" +
            "<div class='page'><div class='head'></div><div class='body'>" + cards + "</div></div>";
        long create = System.nanoTime();
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1400, 1400));
            System.out.println("[PLAYER-BENCH] playwright-create-launch=" + ms(create) + "ms");
            List<Long> times = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                long start = System.nanoTime();
                page.setContent(html);
                page.locator(".page").screenshot(new Locator.ScreenshotOptions().setPath(Path.of("build/playwright-benchmark-" + i + ".png")));
                long elapsed = ms(start);
                times.add(elapsed);
                System.out.println("[PLAYER-BENCH] playwright-warm-" + i + "=" + elapsed + "ms");
            }
            Collections.sort(times);
            System.out.println("[PLAYER-BENCH] playwright-warm-median=" + times.get(2) + "ms");
            browser.close();
        }
    }
    private static long ms(long start) { return (System.nanoTime() - start) / 1_000_000; }
}
