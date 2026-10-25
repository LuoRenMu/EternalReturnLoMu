# QQ 主动消息核对（2026-10-22）

现已新增 `group-pet` 插件，通过 `/群宠 玩家名` 使用主动对局推送；实现与恢复策略见 [群宠插件说明](../plugins/group-pet/README.md)。

## 结论与接口

当前官方文档明确支持 QQ 群聊主动消息：`POST /v2/groups/{group_openid}/messages`，不填写被动回复的 `msg_id`、`event_id`。目标为当前 AppID 下取得的群 OpenID，不是 QQ 群号。复用运行中的 Simbot bot 即可沿用现有鉴权与令牌刷新。

本项目 Simbot Core 4.15.0 / QQ SDK 4.5.0 已具备基础发送模型与接口，无需为普通主动文本升级 SDK：

```kotlin
import love.forte.simbot.component.qguild.bot.QGBot
import love.forte.simbot.qguild.api.message.GroupAndC2CSendBody
import love.forte.simbot.qguild.api.message.group.GroupMessageSendApi

suspend fun sendNotice(bot: QGBot, groupOpenId: String, text: String) {
    val body = GroupAndC2CSendBody.create(text, 0)
    bot.executeData(GroupMessageSendApi.create(groupOpenId, body))
}
```

`QQProactiveMessageContractTest` 通过离线代理截获上述真实 SDK 请求，确认路径、正文以及没有被动回复标识。没有向真实群或用户发送测试消息，当前机器人账号的实际发送权限和接收开关尚未实测。

## 当前官方限制

- 群聊：认证机器人每分钟 60 条，未认证每分钟 30 条；每个群每分钟 20 条，每天最多 1000 条。
- 接收方关闭允许主动发送后会失败；接口能力不代表账号和目标群一定允许接收。
- 群聊被动回复为 5 分钟内、每条消息最多回复 5 次；不要用过期消息 ID 充当主动推送。
- 单聊另用 `/v2/users/{user_openid}/messages`。`is_wakeup=true` 是互动召回能力，与普通主动消息不同，不能携带 `msg_id` 或 `event_id`。
- 已安装 QQ SDK 4.5.0 的 `GroupAndC2CSendBody` 没有 `is_wakeup` 属性；此结论只覆盖普通主动发送，召回需要另外适配。

若后续接入新兑换码或公告通知，应保存群 OpenID 与订阅状态，按群/文章 ID 记录成功发送，限制发送速率，明确记录失败原因；不自动给所有历史群发送。现有 `QQGroupInteractionReplySender` 会填写事件 ID，属于按钮被动回复，不适合作为主动发送入口。

## 官方资料

- [消息收发概述、主动发送限制](https://bot.q.qq.com/wiki/develop/api-v2/server-inter/message/overview.html)
- [发送群聊消息及键盘行为](https://bot.q.qq.com/wiki/develop/api-v2/autogen/api/v2_groups_group_openid_messages.post.html)
- [发送单聊消息与互动召回](https://bot.q.qq.com/wiki/develop/api-v2/autogen/api/v2_users_user_openid_messages.post.html)

群聊指令按钮的 `enter` 不能作为自动发送保证，官方标注仅单聊生效。本次角色武器选择使用 `type=1` 的回调按钮，立即确认交互后执行查询，再以外层事件 ID 回复图片。

## 2026-10-23：状态查询适配

当前 SDK 4.5.0 尚未封装 bot_state，上游 dev 已有实现但最新已发布 Preview5 未包含。本项目在 qq-rich-media 中提供 `QQGroupBotStateApi`，使用现有 SDK API 扩展点，不升级 SDK。
`/test` 显示查询结果和消息发送者身份；需要 core/qgbot 3.0.3 与 test 1.6.0 同时更新，详见 [测试插件说明](../plugins/test/README.md)。
接口仅白名单机器人可调用，11253、缺失字段、超时不可当作关闭。SDK 未提供可靠普通成员标识时显示身份无法确认，bot_state 的机器人身份不会被误用于消息发送者。
