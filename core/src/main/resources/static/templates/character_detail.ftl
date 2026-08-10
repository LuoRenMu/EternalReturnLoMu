<#--
  CharacterDetail 角色详情渲染模板（对应 bserAnalysis CharacterDetail.tsx 的核心分析区块）。
  数据源：service.entity.CharacterDetail（作为 FreeMarker 数据模型根对象，字段顶层访问），
  图片为 cdn.dak.gg CDN URL。
-->
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Eternal Return — ${name} 角色详情</title>
    <link rel="stylesheet" href="${httpServer}/static/css/character_detail.css">
</head>
<body>
<div id="content-container">

    <#if analysis?has_content && analysis.weapons?has_content>
        <#assign weapon = analysis.weapons?first>

        <#-- 头部 -->
        <header class="cd-header">
            <img class="cd-avatar" src="${httpServer}${imageUrl}" alt="${name}">
            <div class="cd-header-info">
                <div class="cd-title-row">
                    <h1 class="cd-name">${name}</h1>
                    <#if analysis.characterTier?has_content>
                        <span class="tier-badge tier-${analysis.characterTier?lower_case}">${analysis.characterTier}</span>
                    </#if>
                </div>
                <#if title?has_content>
                    <div class="cd-title">${title}</div>
                </#if>
                <div class="cd-archetypes">
                    <#list archetypes as tag>
                        <#if tag?has_content && tag != "None">
                            <span class="cd-archetype">${tag}</span>
                        </#if>
                    </#list>
                </div>
                <div class="cd-meta">
                    <span>登场率 <b>${analysis.pickRate?string("0.0")}%</b></span>
                    <span>${analysis.matchingModeLabel} · ${analysis.teamModeLabel}</span>
                    <span>${analysis.tierLabel}</span>
                    <#if analysis.patchLabel?has_content><span>版本 ${analysis.patchLabel}</span></#if>
                    <span>更新 ${analysis.updatedLabel}</span>
                </div>
            </div>
        </header>

        <#-- 武器流派 -->
        <section class="section">
            <h3 class="section-title">武器流派</h3>
            <div class="weapon-head">
                <div class="weapon-id">
                    <div class="weapon-icon-box"><img src="${httpServer}${weapon.iconUrl}" alt="${weapon.weapon}"></div>
                    <div>
                        <div class="weapon-name-row">
                            <span class="weapon-name">${weapon.weapon}</span>
                            <#if weapon.tier?has_content>
                                <span class="tier-badge tier-${weapon.tier?lower_case}">${weapon.tier}</span>
                            </#if>
                        </div>
                        <div class="weapon-sub">评分 ${weapon.tierScore?string("0.0")} · 第 ${weapon.rank}/${weapon.rankSize} 热门</div>
                    </div>
                </div>
                <div class="stat-grid">
                    <div class="stat-cell"><div class="stat-cell-label">登场率</div><div class="stat-cell-value">${weapon.pickRate?string("0.0")}%</div></div>
                    <div class="stat-cell"><div class="stat-cell-label">胜率</div><div class="stat-cell-value">${weapon.winRate?string("0.0")}%</div></div>
                    <div class="stat-cell"><div class="stat-cell-label">前三率</div><div class="stat-cell-value">${weapon.top3Rate?string("0.0")}%</div></div>
                    <div class="stat-cell"><div class="stat-cell-label">平均名次</div><div class="stat-cell-value">#${weapon.avgRank?string("0.0")}</div></div>
                    <div class="stat-cell"><div class="stat-cell-label">平均击杀</div><div class="stat-cell-value">${weapon.avgKills?string("0.0")}</div></div>
                    <div class="stat-cell"><div class="stat-cell-label">场次</div><div class="stat-cell-value">${weapon.games}</div></div>
                </div>
            </div>
        </section>

        <#-- 各部位最高选择率装备 -->
        <#if weapon.topEquipments?has_content>
            <section class="section">
                <h3 class="section-title">各部位最高选择率装备</h3>
                <div class="equipment-slots">
                    <#list weapon.topEquipments as eq>
                        <div class="equipment-slot">
                            <div class="equipment-slot-label">${eq.slot}</div>
                            <div class="equipment-icon">
                                <#if eq.bgUrl?has_content><div class="equipment-icon-bg" style="background-image:url('${httpServer}${eq.bgUrl}')"></div></#if>
                                <img class="equipment-icon-img" src="${httpServer}${eq.iconUrl}" alt="${eq.name}">
                            </div>
                            <div class="equipment-name">${eq.name}</div>
                            <div class="equipment-rate">${eq.pickRate?string("0.0")}%</div>
                        </div>
                    </#list>
                </div>
            </section>
        </#if>

        <#-- 技能学习顺序 -->
        <#if weapon.skillBuilds?has_content>
            <section class="section">
                <h3 class="section-title">技能学习顺序</h3>
                <div class="build-list">
                    <#list weapon.skillBuilds as sb>
                        <div class="build-row">
                            <div class="build-head">
                                <span class="build-tag">方案 #${sb_index + 1}</span>
                                <span class="build-rates">登场率 <b>${sb.pickRate?string("0.0")}%</b> · 胜率 <b>${sb.winRate?string("0.0")}%</b></span>
                            </div>
                            <#if sb.order?has_content>
                                <div class="build-sub-label">1-15级加点</div>
                                <div class="icon-chain">
                                    <#list sb.order as slot>
                                        <#assign skill = weapon.skillBySlot[slot]!>
                                        <#if slot_index gt 0><span class="chain-arrow">→</span></#if>
                                        <div class="icon-cell">
                                            <#if skill?has_content>
                                                <img class="icon-cell-img" src="${httpServer}${skill.iconUrl}" alt="${skill.name}">
                                            </#if>
                                            <span class="icon-cell-step">${slot_index + 1}</span>
                                            <span class="icon-cell-slot">${slot}</span>
                                        </div>
                                    </#list>
                                </div>
                            </#if>
                            <#if sb.priority?has_content>
                                <div class="build-sub-label">满级优先级</div>
                                <div class="icon-chain">
                                    <#list sb.priority as slot>
                                        <#assign skill = weapon.skillBySlot[slot]!>
                                        <#if slot_index gt 0><span class="chain-arrow">→</span></#if>
                                        <div class="icon-cell small">
                                            <#if skill?has_content>
                                                <img class="icon-cell-img" src="${httpServer}${skill.iconUrl}" alt="${skill.name}">
                                            </#if>
                                            <span class="icon-cell-slot">${slot}</span>
                                        </div>
                                    </#list>
                                </div>
                            </#if>
                        </div>
                    </#list>
                </div>
            </section>
        </#if>

        <#-- 推荐出装顺序 -->
        <#if weapon.itemBuilds?has_content>
            <section class="section">
                <h3 class="section-title">推荐出装顺序</h3>
                <div class="build-list">
                    <#list weapon.itemBuilds as b>
                        <div class="build-row">
                            <div class="build-head">
                                <span class="build-tag">方案 #${b_index + 1}</span>
                                <span class="build-rates">登场率 <b>${b.pickRate?string("0.0")}%</b> · 胜率 <b>${b.winRate?string("0.0")}%</b></span>
                            </div>
                            <#if b.order?has_content>
                                <div class="build-sub-label">前期出装顺序</div>
                                <div class="icon-chain">
                                    <#list b.order as it>
                                        <#if it_index gt 0><span class="chain-arrow">→</span></#if>
                                        <div class="icon-cell">
                                            <#if it.bgUrl?has_content><div class="icon-cell-bg" style="background-image:url('${httpServer}${it.bgUrl}')"></div></#if>
                                            <img class="icon-cell-img" src="${httpServer}${it.iconUrl}" alt="${it.name}">
                                            <span class="icon-cell-step">${it_index + 1}</span>
                                        </div>
                                    </#list>
                                </div>
                            </#if>
                            <div class="build-sub-label">最终成装</div>
                            <div class="icon-chain">
                                <#list b.items as it>
                                    <div class="icon-cell final">
                                        <#if it.bgUrl?has_content><div class="icon-cell-bg" style="background-image:url('${httpServer}${it.bgUrl}')"></div></#if>
                                        <img class="icon-cell-img" src="${httpServer}${it.iconUrl}" alt="${it.name}">
                                    </div>
                                </#list>
                            </div>
                        </div>
                    </#list>
                </div>
            </section>
        </#if>

        <#-- 战术技能 / 潜能 -->
        <div class="two-col">
            <#if weapon.tacticals?has_content>
                <section class="section">
                    <h3 class="section-title">战术技能</h3>
                    <div class="picks">
                        <#list weapon.tacticals as t>
                            <div class="pick-icon">
                                <div class="pick-icon-img-box"><img src="${httpServer}${t.iconUrl}" alt="${t.name}"></div>
                                <div class="pick-icon-name">${t.name}</div>
                                <div class="pick-icon-rates">
                                    <span class="pick-rate">${t.pickRate?string("0.0")}%</span>
                                    <span class="pick-win">${t.winRate?string("0.0")}%</span>
                                </div>
                            </div>
                        </#list>
                    </div>
                </section>
            </#if>

            <#if weapon.augments?has_content>
                <section class="section">
                    <h3 class="section-title">潜能</h3>
                    <div class="augments">
                        <#list weapon.augments as aug>
                            <div class="augment-block">
                                <div class="pick-icon">
                                    <div class="pick-icon-img-box"><img src="${httpServer}${aug.core.iconUrl}" alt="${aug.core.name}"></div>
                                    <div class="pick-icon-name">${aug.core.name}</div>
                                    <div class="pick-icon-rates">
                                        <span class="pick-rate">${aug.core.pickRate?string("0.0")}%</span>
                                        <span class="pick-win">${aug.core.winRate?string("0.0")}%</span>
                                    </div>
                                </div>
                                <span class="chain-arrow">→</span>
                                <div class="augment-subs">
                                    <#list aug.subs as sub>
                                        <div class="pick-icon">
                                            <div class="pick-icon-img-box"><img src="${httpServer}${sub.iconUrl}" alt="${sub.name}"></div>
                                            <div class="pick-icon-name">${sub.name}</div>
                                        </div>
                                    </#list>
                                </div>
                            </div>
                        </#list>
                    </div>
                </section>
            </#if>
        </div>

        <#-- 灌注选择率 -->
        <#if weapon.infusions?has_content>
            <section class="section">
                <h3 class="section-title">灌注选择率</h3>
                <div class="picks">
                    <#list weapon.infusions as inf>
                        <div class="pick-icon">
                            <div class="pick-icon-img-box"><img src="${httpServer}${inf.iconUrl}" alt="${inf.name}"></div>
                            <div class="pick-icon-name">${inf.name}</div>
                            <div class="pick-icon-rates">
                                <span class="pick-rate">${inf.pickRate?string("0.0")}%</span>
                                <span class="pick-win">${inf.winRate?string("0.0")}%</span>
                            </div>
                        </div>
                    </#list>
                </div>
            </section>
        </#if>

        <#-- 高分玩家 -->
        <#if analysis.topPlayers?has_content>
            <section class="section">
                <h3 class="section-title">高分玩家</h3>
                <div class="top-players">
                    <#list analysis.topPlayers as p>
                        <div class="top-player">
                            <#if p.tierIconUrl?has_content>
                                <img class="top-player-tier" src="${httpServer}${p.tierIconUrl}" alt="${p.tierName}">
                            </#if>
                            <div class="top-player-info">
                                <div class="top-player-name">${p.name}</div>
                                <div class="top-player-mmr">${p.mmr} MMR</div>
                            </div>
                        </div>
                    </#list>
                </div>
            </section>
        </#if>

    <#else>
        <div class="empty">暂无该角色的统计数据</div>
    </#if>

</div>
</body>
</html>
