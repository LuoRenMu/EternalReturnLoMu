# Core 心跳与 SMTP 故障告警

心跳属于主程序 `core`，不是业务插件，不自动重启服务。

## 检测范围

- QQ：通过 **当前 Simbot Bot 实际出站的缓存 AccessToken** 请求 `/users/@me`，可发现进程仍运行但 token 已过期的情况。只读检查，不发送 QQ 消息；探针本身不另取新 token 掩盖故障。
- OneBot：检查 Bot 运行状态并调用 `get_status`，检查 `good` / `online`。
- 数据库：通过当前连接池执行 JDBC 4 连接有效性检查（保留超时）。
- 单个探针失败或超时不阻断其他探针；取消应用会结束心跳任务，不发送关闭告警。

这不是端到端消息投递测试：未收到群消息不一定是故障，心跳不以群聊活跃度判断在线。**JVM 退出、机器关机或网络完全断开时，进程内检测不能自行发邮件**，需要独立外部监控；本实现不额外安装运维系统。

## 配置

在私有、已忽略的 `config.json` 中新增 `heartbeat`，修改后重启主程序。旧配置默认关闭，插件热更新不会更新 core。

```json
{
  "heartbeat": {
    "enabled": true,
    "intervalSeconds": 60,
    "startupGraceSeconds": 30,
    "timeoutSeconds": 10,
    "failureThreshold": 2,
    "alertCooldownSeconds": 1800,
    "notificationRetrySeconds": 300,
    "smtp": {
      "provider": "smtp",
      "host": "smtp.qq.com",
      "port": 465,
      "ssl": true,
      "user": "sender@example.com",
      "password": "填写邮箱的 SMTP 授权码，不是登录密码",
      "to": "admin@example.com"
    }
  }
}
```

启动宽限期后每 60 秒扫描，连续失败 2 次才告警。多个异常合并为一封中文邮件，故障持续时最多每 30 分钟提醒一次；恢复清零失败次数，不发正常状态邮件，也不绕过限频。SMTP 失败不算投递成功，5 分钟后重试。配置错误、鉴权失败等邮件异常只记录异常类别，不记录原始错误正文。

465 使用隐式 TLS；若设置 `ssl=false`（如 587），强制 STARTTLS，不允许明文认证。始终校验服务端证书/主机名，连接、读取、写入均有 10 秒超时。SMTP 密码只在私有配置中保存；配置管理接口和调试字符串均脱敏。

## 诊断

`GET /api/admin/health`（需要现有管理令牌）返回 `starting` / `healthy` / `degraded` / `stopped` / `disabled`、各探针连续失败次数、最后检测/告警时间，以及邮件发送失败的异常类别。`GET /api/admin/config` 返回脱敏后的心跳配置。未安装心跳的测试应用返回 `not_installed`。

检查间隔从上次扫描结束计算；SDK 重试和阻塞 JDBC 的驱动超时可能让实际扫描耗时略超过配置的协程超时，不会并行叠加扫描。心跳本身不修改 token、不自动重启机器人，不发送正常状态的测试邮件。

## QQ token 维护（core 3.0.1 所打包的 QGBOT 适配器）

Simbot core 升级至稳定版 4.15.0；QQ 组件仍使用当前稳定版 4.5.0，不切换 5.0 预览版。4.5.0 的定时 token 刷新循环在一次请求/解析异常后可能结束，且再次 `bot.start()` 不会重建已结束但非空的刷新任务。

QGBOT 适配器增加独立、生命周期绑定的 token 租约维护：调用 SDK 的 `GetAppAccessTokenApi`，在租约剩余时间的 85% 时续约，刷新请求最多 30 秒，失败按 5/10/20/40/60 秒退避重试。异常只记录类别，任务不会因一次网络或解析异常停止；有仍有效的租约则继续使用，到期租约不会再被提供。

SDK 的 token 属性没有公开 setter，因此通过公开的 Ktor `HttpSend` 插件仅覆盖 **该 Bot 的 QQ API 主机上已有的 `QQBot` 鉴权头**。不反射修改 SDK 私有字段，不往其他域名、获取 token 的无鉴权接口或其他鉴权类型加入 token。所有图片、按钮等仍由原 Simbot Bot 发送。

维护器独立于邮件心跳的 `enabled` 配置运行；关闭心跳告警不会关闭鉴权续约。服务停止时取消维护任务。若维护持续失败且真实 QQ API 鉴权失败，心跳按既有连续失败阈值发送邮件。
