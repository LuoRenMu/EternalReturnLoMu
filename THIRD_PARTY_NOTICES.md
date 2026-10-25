# 第三方声明

EternalReturnLoMu 自有代码采用 GNU AGPL-3.0-or-later。该许可不替代下列第三方资源及依赖的原始许可证；其版权、许可和声明继续有效。

## 随仓库分发的资源

| 资源 | 版本／来源 | 许可证 | 仓库中的完整许可 |
| --- | --- | --- | --- |
| Alpine.js | 3.15.8，[上游](https://github.com/alpinejs/alpine/tree/v3.15.8) | MIT | [LICENSE.alpine.md](core/src/main/resources/static/admin/vendor/LICENSE.alpine.md) |
| htmx | 2.0.8，[上游](https://github.com/bigskysoftware/htmx/tree/v2.0.8) | 0BSD | [LICENSE.htmx.txt](core/src/main/resources/static/admin/vendor/LICENSE.htmx.txt) |
| Noto 字体，包括 Noto Sans KR | [Noto 字体项目](https://github.com/notofonts/noto-cjk) | SIL Open Font License 1.1 | [OFL.txt](nutdraw/src/main/resources/fonts/OFL.txt) |

二进制分发包另外在 `licenses/` 中附带上述许可副本；前端资源旁的许可和字体资源中的 `OFL.txt` 也保留在应用 JAR 内。

## 运行依赖

项目使用 Kotlin、Ktor、Skiko／Skia、Koin、Ktorm、Simbot、数据库驱动及其他 Gradle 依赖。第三方模块保留各自许可，包括 Simbot 的 LGPL 许可；它们不因本项目采用 AGPL 而被重新许可。依赖的版本和来源由 Gradle 配置声明，JAR 中原有的第三方许可文件仍予保留。

永恒轮回、QQ、B站及 DAK.GG 的名称、商标、游戏图片和上游数据分别属于其权利人。本项目的代码许可证不授予这些内容的商标或额外再分发权利。
