# QGBOT 测试插件 1.7.0

仅注册 QQ 官方机器人（QGBOT）群聊命令 `/test`。

## B站直播监听

- `/test 直播 直播间号`：手动订阅，也支持 `https://live.bilibili.com/直播间号`，自动解析短号。
- `/test 直播 列表`：查看本群监听的房间及最近一次状态。
- `/test 直播 取消 直播间号`：取消本群监听；建议使用回复中的完整房间号，直接删除无需访问 B站。
- 无参数 `/test` 保留原主动消息状态、发送者身份和延迟 5 秒发送测试；直播子命令不会触发该测试。

每群最多 3 个房间，测试模块全局最多 50 个不同房间。首次订阅只记录基线，不补发正在进行的直播；之后发现新直播场次时，主动向订阅群发送标题、房间号及直播间链接。状态 `2`（轮播）不视为开播。

后台每轮结束后等待 60 秒再扫描，同一房间被多个群订阅时每轮只查询一次，房间请求之间至少间隔 1 秒；错误按 2、4、8、16、32、60 分钟退避，不将查询错误视为下播。发送间隔至少 6.1 秒。群主需要在机器人页面开启主动发送消息，实际能否发送由 QQ 接口判定。

订阅和已观察场次存入主程序数据库的 `test_bili_live` 表，配置 PostgreSQL 时复用 PostgreSQL，SQLite 后端则复用 SQLite。重载/重启保留监听和去重记录；禁用插件取消轮询。发送前持久化本场记录，避免重载或 QQ 超时导致重复发送；代价是发送失败或保存后崩溃时该场不会自动补发。当前不实现补发队列或下播提醒。

接口使用 B站 `https://api.live.bilibili.com/room/v1/Room/get_info?room_id=...`，无需提供账号 Cookie；直播状态和场次来自 `live_status/live_time`。参考当前 [yt-dlp 的 B站直播提取实现](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/bilibili.py)。接口可能受风控限制，失败时不创建订阅，轮询时保留旧状态并退避。

本次只需热更新 test 插件（主程序已有 3.0.3 时）。构建：`:plugins:test:test :plugins:test:jar`；产物 `plugins/test/build/libs/test-1.0.0.jar`，逻辑版本 1.7.0。

## 当前行为

1. 使用运行中 Simbot bot 调用 `GET /v2/groups/{group_openid}/bot_state`，查询超时上限 8 秒。
2. 立即回复主动消息状态与当前发送者身份；随后保留原来的延迟 5 秒主动消息测试。
3. 主动发送不携带 `msg_id`、`event_id`，不因本地权限状态或查询失败跳过测试；不自动重发。
4. 禁用／重载插件会取消尚未发送的测试任务。不再注册旧 Markdown 按钮回调监听器。

## 状态与权限

- `allow_proactive_msg=true/false` 分别显示“已开启／未开启”。字段缺失显示“无法确认”。
- 官方错误码 `11253` 显示“机器人未获接口白名单权限”，不是“未开启”。查询异常／超时同样不推断为关闭。
- `bot_state.member_role` 是机器人自己的身份，不能拿它当发送者身份。
- 发送者身份由 QQ 群消息事件的 `QGGroupAuthor.memberRole` 经主程序传入。`OWNER/ADMIN` 分别显示群主／管理员。
- SDK 4.5 将缺失的 `member_role` 默认为 `MEMBER`，因此该值不能可靠证明普通成员；此时保守显示“无法确认（普通成员或 QQ 未提供可靠身份）”。管理后台调试命令没有 QQ 身份上下文，同样显示无法确认。
- 不接入实际权限控制；本功能只显示诊断结果，不根据未知身份赋予管理权限。

## 主动消息诊断依赖

保持 Simbot Core 4.15.0 / QQ 组件 4.5.0，不切换上游 5.x 预览版。
接口适配位于 `qq-rich-media` 的 `QQGroupBotStateApi`，参考上游 dev 的 API 定义，使用 SDK 的 `GetQQGuildApi` 与 `bot.executeData`，复用鉴权及令牌刷新。

主动消息诊断需要主程序 core/qgbot 3.0.3 或更高：主程序负责将可信的发送者身份带入命令上下文。MessageSender 原有五参数构造函数保持兼容，其他旧插件无需同步升级。

构建：`:qq-rich-media:test :plugins:test:test :qgbot:test :qgbot:jar :plugins:test:jar`。
产物：`qgbot/build/libs/qgbot-3.0.3.jar`、`plugins/test/build/libs/test-1.0.0.jar`。

官方与上游：
- [机器人群内状态](https://bot.q.qq.com/wiki/develop/api-v2/autogen/api/v2_groups_group_openid_bot_state.get.html)
- [上游 dev API 实现](https://github.com/simple-robot/simpler-robot/blob/1e1bafbdcae40b9579caa10f810792277c5365dc/simbot-component-qq/simbot-component-qq-api/src/commonMain/kotlin/love/forte/simbot/qguild/api/group/GetGroupBotStateApi.kt)

本地回归采用 SDK 请求代理和测试数据，不发送真实 QQ 消息。当前机器人是否获平台白名单需实际部署后由 `/test` 确认。
