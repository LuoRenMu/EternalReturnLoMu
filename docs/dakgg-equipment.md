# DAK.GG 装备背景与查询

## 背景来源（2026-09-23 实测）

页面：https://dak.gg/er/items

页面加载的 JavaScript 中有按装备 `grade` 分支的辅助函数，返回
`https://cdn.dak.gg/er/images/item/ico-itemgradebg-0N.svg`。
并非从 PNG 图标取色，也不依赖角色、胜率或排名。
原始 SVG 尺寸是 51 × 32，圆角 4，内部为纵向线性渐变：

| grade | N | 中文 | 渐变起色 → 终色 |
|---|---|---|---|
| Common / 默认 | 1 | 一般 | #67696C → #A8ADB4 |
| Uncommon | 2 | 高级 | #466A49 → #69B76F |
| Rare | 3 | 稀有 | #344A6C → #6397E7 |
| Epic | 4 | 英雄 | #654987 → #AB80E0 |
| Legend | 5 | 传说 | #997531 → #DBAF5D |
| Mythic | 6 | 超凡 | #39201F → #903232 |

装备资料取自 `https://er.dakgg.io/api/v1/data/items?hl=zh-cn`：
`grade` 决定背景，`imageUrl` 是前景装备图标，`tooltip` 是中文属性说明，
`makeMaterials` 是直接合成材料 ID。新装备卡片直接复用官方 SVG，避免近似配色。
角色详情原本已有背景下载机制；本次复用统一品质映射，并修正未知品质错误映射到 00 的问题。

## 命令

- `/查询装备 军刀`：支持完整中文名、拼音、ID，支持部分匹配；多个结果提示用户选择，不随意取第一件。
  仅查询 Weapon / Armor，展示图标、品质、中文属性说明、直接合成材料，以及近 7 天三排灭钻及以上的高使用率角色。
  角色使用率按该角色/武器对局数计算，胜率按选择此装备的对局数计算；仅展示使用率不低于 1% 的角色，同一角色仅展示使用率最高的武器配置。
  统计来自 `https://er.dakgg.io/api/v1/item-stats?dt=7`，请求失败时保留装备资料并显示暂无统计。
- `/查询角色装备 杰琪 排位 灭钻`：支持角色中文名、拼音和 ID，模式与段位规则同“查询角色”。
  展示登场最多武器的成装方案、各部位装备选择率及胜率；支持钴协议。
  数据复用角色分析接口，不将页面“全局装备近七天统计”混同于角色统计。

`/查询装备` 注册在独立的 equipment 插件；`/查询角色装备` 注册在 character 插件。
两个命令均支持 QG_BOT / ONE_BOT。
默认离线测试：`:plugins:equipment:test` 和 `:plugins:character:test`。
设置 `REAL_EQUIPMENT_PREVIEW=true` 可运行实时 API 和图片渲染验证；
输出位于 `plugins/character/build/previews/`。
