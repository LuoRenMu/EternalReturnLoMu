package cn.luorenmu.request.api

import java.nio.file.Path

/**
 *
 * @author LoMu
 * Date 2025/11/2 01:11
 */
interface ResourceApi : Api {
    val path: Path
}