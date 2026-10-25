# QQ 自定义菜单与指令面板（core 3.0.5）

管理后台新增 **QQ 菜单与面板**。使用现有管理令牌／登录 Cookie，不增加公开管理接口。

## 上游与实现边界

- 对应 [Simbot PR #391](https://github.com/simple-robot/simbot-component-qq-guild/pull/391)：C2C 自定义菜单、指令面板 CRUD 及关联对象管理。
- 2026-10-25 核对时，此功能已合入上游 `dev/main`、API 标注自 4.7.0 提供；已发布的 4.x 稳定版仍为 4.5.0。主程序保留现用版本，不为此引入 5.0 预览版或未发布依赖。
- `core.qq` 持有应用自己的模型与接口；`qgbot` 使用 Simbot 4.5 的 `QQGuildApi` 基类与 `bot.executeData` 接入对应 `/v2/menu`、`/v2/panels` API。继续使用现有 SDK 鉴权、令牌刷新和服务器配置，不另造 HTTP 鉴权客户端。
- 这是主程序功能，不能通过热更新某个业务插件获得；升级主程序需重启。OneBot 或未连接 QQ 时显示不可用，不伪装成本地保存成功。
- QQ 端接口权限、客户端支持和缓存决定真实展示效果；未授权的接口错误（例如 11253）会显示在页面。单凭机器人连接成功不能认定已获接口权限。

## Web 操作

### 自定义菜单

读取线上版本，添加／移除／排序一级及二级菜单，配置填入消息、链接、一级开关，预览结构与待提交 JSON。最多 10 个一级菜单，每个最多 5 个二级菜单；一级名称显示宽度最多 10，二级名称最多 14。

提交会**整体覆盖** QQ C2C 单聊窗口底部菜单，清空会发送显式 `{"menu":null}`；均需二次确认。菜单不是群聊 Markdown 按钮，开关配置也不会自动创建群宠／直播订阅。

### 指令面板

- 按 `group`、`c2c`、`channel`、`dm` 场景分页读取、查看详情、新建、修改元素及备注、删除。
- 以指令／链接形式配置元素、描述、仅管理员可点击选项；支持从已启用的本机命令目录填入指令。
- 指令元素按 QQ 语义填入输入框，不自动执行。
- 支持在 **表单编辑／JSON 编辑** 间切换。JSON 编辑可手动粘贴 QQ 官方请求体：创建填写 `{scope, target_type, panel, ...}`，修改已有面板仅填写 `{panel}`，无需添加管理 API 的 `configuration`／`confirmed` 包装。提供格式化，切换回表单时同步支持的字段；非法 JSON 不覆盖草稿。
- JSON 提交同样校验字段、名称／描述宽度和 OpenID，仍需明确确认；只编辑或格式化不会请求 QQ。新建成功后即使回读失败也保留面板 ID，后续按更新处理，不会重复创建。
- 新建默认选择 **指定对象**，需要明确填写 OpenID。`channel`／`dm` 仅允许 `all`。
- 面板内容与关联对象分开提交；只有 `specific` 面板可增删关联对象。群聊使用 `group_openids`，C2C 使用 `user_openids`，每次 1–20 个。
- OpenID 不是普通 QQ 号或群号。创建后场景和目标类型不能通过“修改元素”偷偷改变。
- 机器人跨场景最多 20 个面板，每个面板最多 20 个元素；备注最多 255 字符。名称／描述显示宽度上限为 14／30，ASCII 字符算 1、中文等非 ASCII 码点算 2（例如 7 个汉字已占满名称，前面再加 `/` 就会超限）。前后端按相同规则校验，编辑时实时显示已用宽度，不自动截断内容。

## 安全与失败语义

读取、启动、切换页面不会自动发布配置。所有修改均要求 `confirmed=true`；缺失 `menu` 不会被当成清空。

修改接口要求 `application/json`，拒绝可由跨站简单表单提交的类型。JSON 请求限制为 64 KiB，拒绝未知字段、非法链接协议、无效场景、错误关联类型及路径注入。QQ 返回的内容仅通过文本绑定显示，令牌和上游原始错误体不返回页面。

QQ 菜单／面板修改不使用 SDK 的 5xx 或连接异常自动重试，以免重复创建；其他 QQ 请求保持原重试规则。网络失败或超时可能存在“已生效但回执未到”的情况，应先重新读取线上状态，不承诺跨平台事务或严格恰好一次。创建已返回的面板 ID 在后续读取失败时仍保留，下一次提交按更新处理。

QQ 的 `30013`／文档中的 `40030013` 表示配置超限，包括文字显示宽度及数量限制，不应直接判定为接口权限缺失。页面显示针对性排查提示，管理 API 返回 400；明确的超限拒绝不附带“可能已生效”提示。仅网络／解析失败等结果未知的修改请求以及修改超时保留该警告，读取失败不显示修改警告。

限制依据：[QQ 修改全局自定义菜单](https://bot.q.qq.com/wiki/develop/api-v2/autogen/api/v2_menu.put.html)、[QQ 创建指令面板](https://bot.q.qq.com/wiki/develop/api-v2/autogen/api/v2_panels.post.html)。

## 管理 API

统一前缀 `/api/admin/qq`，所有接口均需管理鉴权：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/status` | 适配器／连接可用性（不代表接口权限） |
| GET / PUT | `/menu` | 查询／覆盖菜单；PUT 使用 `{menu, confirmed}` |
| GET | `/panels?scope=group&cursor=...&limit=20` | 列表，limit 1–50 |
| POST | `/panels` | `{configuration: {scope, target_type, panel, ...}, confirmed}` |
| GET / PUT / DELETE | `/panels/{id}` | 详情；PUT `{panel, confirmed}`；DELETE `{confirmed}` |
| PUT | `/panels/{id}/target` | `{targets: {op: "add"或"del", group_openids或user_openids}, confirmed}` |

## 验证

`./gradlew :core:test :qgbot:test :onebot:compileKotlin` 检查鉴权、参数校验、CRUD、实际 SDK 请求编码、204 空响应及重试边界。

`node --test core/src/test/js/qq-menu-panel.test.cjs` 检查前端确认、配置转换、分页竞态和创建后读取失败的去重保护。

自动化及本地页面验收使用 fixture，不会向真实 QQ 账号发布、清空或删除菜单。
