package cn.luorenmu.onebot

import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
object AdminServerPort {
    fun resolve(
        args: Array<String>,
        configuredPort: Int,
        isAvailable: (Int) -> Boolean = ::isAvailable,
    ): Int {
        val startPort = parse(args, configuredPort)
        for (candidate in startPort..65535) {
            if (isAvailable(candidate)) return candidate
            if (candidate < 65535) println("端口 $candidate 已被占用，尝试 ${candidate + 1}")
        }
        error("从端口 $startPort 开始没有可用端口")
    }

    fun parse(args: Array<String>, configuredPort: Int): Int {
        val raw = when {
            args.isEmpty() -> null
            args.first().startsWith("--port=") -> args.first().substringAfter('=')
            args.first() == "--port" -> args.getOrNull(1)
                ?: error("--port 后需要提供端口号")
            else -> args.first()
        }
        val port = raw?.toIntOrNull() ?: if (raw == null) configuredPort else error("无效端口: $raw")
        require(port in 1..65535) { "端口必须在 1 到 65535 之间" }
        return port
    }

    private fun isAvailable(port: Int): Boolean = runCatching {
        ServerSocket().use { socket ->
            socket.reuseAddress = false
            socket.bind(InetSocketAddress("0.0.0.0", port))
        }
    }.isSuccess
}
