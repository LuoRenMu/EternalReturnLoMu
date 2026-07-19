package cn.luorenmu.common.extensions

import com.github.promeg.pinyinhelper.Pinyin
/**
 *
 * @author LoMu
 * Date 2026/6/11 13:16
 */
fun String.toPinYin(): String {
    val sb = StringBuilder()
    for (ch in this) {
        sb.append(Pinyin.toPinyin(ch))
    }
    return sb.toString()
}