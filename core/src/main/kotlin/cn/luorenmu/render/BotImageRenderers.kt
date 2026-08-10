package cn.luorenmu.render

/** Runtime-selected image renderer. Installed by the executable adapter module. */
object BotImageRenderers {
    @Volatile
    private var renderer: BotImageRenderer? = null

    fun install(renderer: BotImageRenderer) {
        this.renderer = renderer
    }

    fun get(): BotImageRenderer = checkNotNull(renderer) {
        "BotImageRenderer has not been installed by the executable module"
    }
}
