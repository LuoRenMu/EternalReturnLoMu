# QGBOT 测试插件 1.9.0

仅注册 `/test`，保留主动消息状态／发送者身份诊断，以及 5 秒后在原群发送一次主动消息的测试。

直播订阅已拆到独立的 [bili-live 插件](../bili-live/README.md)，本插件不再访问直播订阅数据库或启动直播轮询。
旧 `/test 直播 ...` 只返回新命令提示，不触发延迟消息；请改用 `/直播通知 订阅 房间号`。

升级时先热更新本插件，停止旧直播 worker，再安装 bili-live 1.0.0，避免新旧轮询同时运行。主程序 3.0.3 无需更换。

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
产物：`qgbot/build/libs/qgbot-3.0.4.jar`、`plugins/test/build/libs/test-1.0.0.jar`。

官方与上游：
- [机器人群内状态](https://bot.q.qq.com/wiki/develop/api-v2/autogen/api/v2_groups_group_openid_bot_state.get.html)
- [上游 dev API 实现](https://github.com/simple-robot/simpler-robot/blob/1e1bafbdcae40b9579caa10f810792277c5365dc/simbot-component-qq/simbot-component-qq-api/src/commonMain/kotlin/love/forte/simbot/qguild/api/group/GetGroupBotStateApi.kt)

本地回归采用 SDK 请求代理和测试数据，不发送真实 QQ 消息。当前机器人是否获平台白名单需实际部署后由 `/test` 确认。
