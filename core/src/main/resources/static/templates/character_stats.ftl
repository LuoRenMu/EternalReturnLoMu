<!DOCTYPE html>
<html lang="zh-CN">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Eternal Return — Character Stats</title>
    <link rel="stylesheet" href="${httpServer}/static/css/character_stats.css">
</head>

<body>
<div id="content-container">
    <div class="header">
        <h1 class="title">
            <span class="title-icon"></span>
            Character Stats
        </h1>
        <div class="stats-bar">
            <div class="stat-badge">
                <span class="stat-label">统计对局</span>
                <span class="stat-value">${totalGames}</span>
            </div>
            <div class="stat-badge">
                <span class="stat-label">统计玩家</span>
                <span class="stat-value">${totalPlayers}</span>
            </div>
            <div class="stat-badge">
                <span class="stat-label">统计段位</span>
                <span class="stat-value rank-bronze">${tier}</span>
            </div>
        </div>
    </div>

    <div class="table-container">
        <table>
            <thead>
            <tr>
                <th class="col-rank">#</th>
                <th class="col-char">Character</th>
                <th class="col-tier">Tier</th>
                <th class="col-rp">RP</th>
                <th class="col-pick">Pick Rate</th>
                <th class="col-win">Win Rate</th>
                <th class="col-top3">Top3 Rate</th>
                <th class="col-rank">Avg.Rank</th>
                <th class="col-dmg">Avg.DMG</th>
                <th class="col-play">play Count</th>
            </tr>
            </thead>
            <tbody>
                <#list players as player>
                    <tr>
                        <td class="col-rank"><span class="rank-badge rank-1">${player.rank}</span></td>
                        <td class="col-char">
                            <div class="char-cell">
                                <div class="avatar-wrap">
                                    <img class="avatar" src="${httpServer}${player.characterImgUrl}" alt="">
                                    <div class="weapon-icon"><img src="${httpServer}${player.weaponImgUrl}" alt=""></div>
                                </div>
                                <span class="char-name">${player.characterName}</span>
                            </div>
                        </td>
                        <td class="col-tier"><span class="tier-badge tier-s">${player.tier}</span></td>
                        <td class="col-rp">${player.rp}</td>
                        <td class="col-pick">${player.pick}</td>
                        <td class="col-win">
                            <div class="bar-cell"><span class="bar-label">${player.winRate}%</span><span class="bar-fill win" style="--w:${player.relativeWinRate}"></span></div>
                        </td>
                        <td class="col-top3">${player.top3Rate}</td>
                        <td class="col-rank">${player.avgRank}</td>
                        <td class="col-dmg"><span class="dmg-value">${player.avgDmg}</span></td>
                        <td class="col-play">${player.playCount}</td>
                    </tr>
                </#list>
            </tbody>
        </table>
    </div>
</div>
</body>
</html>