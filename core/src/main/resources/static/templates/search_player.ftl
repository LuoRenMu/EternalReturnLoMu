<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Document</title>
    <link rel="stylesheet" href="${ httpServer}/static/css/search_player.css">
    <script src="${httpServer}/static/js/chart.js"></script>
</head>
<body>
<div id="content-container">
    <div id="header">
        <div id="banner_user_info"
             style="background-size: contain; background-position: center; background-image: url('${httpServer}/static/images/bg-landing-search-v11.jpg')">
            <div class="profile-image-wrapper">
                <#if profileImageUrl??>
                    <img src="${httpServer}${profileImageUrl}" alt=""/>
                <#else>
                    <img src="" alt=""/>
                </#if>
            </div>
            <div id="top">
                <div class="level">Lv.${level}</div>
                <div class="nickname">${nickName}</div>
                <p>如对该UI有任何建议或问题,欢迎加入654087758群聊反馈 ξ( ✿＞◡❛)</p>
            </div>
        </div>
        <div id="describe">
            <div id="logo">Design inspired by DakGG •
                Powered by LuoRenMu
            </div>
        </div>
    </div>
    <div id="body">
        <div id="left">
            <div id="rank">
                <h4>${mode}(${season})</h4>
                <div id="score">
                    <div id="rp_img">
                        <img src="${httpServer}${data.tierImageUrl}" alt="">
                    </div>
                    <div id="rp_box">
                        <div id="rp">
                            ${data.rp}
                        </div>
                        <div id="rp_name">
                            ${data.rpName}
                        </div>
                    </div>
                </div>
                <div id="record">
                    <div>
                        <div class="record_box">
                            <h4>平均TK</h4>
                            <h4>${data.avgTk}</h4>
                        </div>
                        <div class="record_box">
                            <h4>TOP 1</h4>
                            <h4>${data.top1}</h4>
                        </div>
                        <div class="record_box">
                            <h4>游戏场次</h4>
                            <h4>${data.play}</h4>
                        </div>

                        <div class="record_box">
                            <h4>平均击杀</h4>
                            <h4>${data.avgKill}</h4>
                        </div>
                        <div class="record_box">
                            <h4>TOP 2</h4>
                            <h4>${data.top2}</h4>
                        </div>
                        <div class="record_box">
                            <h4>平均伤害</h4>
                            <h4>${data.avgDmg}</h4>
                        </div>
                        <div class="record_box">
                            <h4>平均助攻</h4>
                            <h4>${data.avgAssists}</h4>
                        </div>
                        <div class="record_box">
                            <h4>TOP 3</h4>
                            <h4>${data.top3}</h4>
                        </div>
                        <div class="record_box">
                            <h4>平均排名</h4>
                            <h4>${data.avgRank}</h4>
                        </div>
                    </div>
                </div>

                <#if mmrStats?? && mode == "排位">
                    <div id="rank_stats">
                        <canvas id="rank_canvas"></canvas>
                    </div>
                    <script>
                        const ctx = document.getElementById('rank_canvas');
                        const labels = ${mmrStats.mmrDateJson};
                        const data = {
                            labels: labels,
                            datasets: [{
                                data: ${mmrStats.mmrJson},
                                fill: false,
                                borderColor: 'rgb(202, 164, 40)',
                                backgroundColor: ['rgb(202, 164, 40)'],
                                pointRadius: 4,
                                tension: 0.1,
                            }]
                        };

                        const config = {
                            type: 'line', // 表类型
                            data: data,
                            options: {
                                plugins: {
                                    legend: {
                                        display: false
                                    }
                                },
                                scales: {
                                    x: {
                                        grid: {
                                            display: false
                                        }
                                    },
                                    y: {
                                        grid: {
                                            display: false
                                        },
                                        ticks: {
                                            stepSize: 100
                                        }
                                    }
                                }
                            }
                        };
                        const myChart = new Chart(ctx, config);
                    </script>
                </#if>


            </div>

            <#if characterUseStats?has_content>
                <section>
                    <table id="rank_character_stats">
                        <thead>
                        <tr>
                            <th class="character">角色</th>
                            <th class="get-rp">RP</th>
                            <th class="avg-rank">平均排名</th>
                            <th class="avg-dmg">平均伤害</th>
                        </tr>
                        </thead>
                        <tbody>
                        <#list characterUseStats as character>
                            <tr>
                                <td class="character">
                                    <div class="image-wrapper"><img
                                                src="${httpServer}${character.imgUrl}"
                                                alt=""></div>
                                    <div class="info">${character.characterName}
                                        <div class="plays">${character.characterPlay} 游戏(${character.winRate})</div>
                                    </div>
                                </td>
                                <td class="get-rp">
                                    <#if character.getRP gte 0>
                                        <svg xmlns="http://www.w3.org/2000/svg" width="8" height="5" viewBox="0 0 8 5"
                                             fill="none"
                                             style="transform: none;">
                                            <path d="M6.75 4.75C7.17188 4.75 7.38281 4.25781 7.07812 3.95312L4.07812 0.953125C3.89062 0.765625 3.58594 0.765625 3.39844 0.953125L0.398438 3.95312C0.09375 4.25781 0.304688 4.75 0.726562 4.75H6.75Z"
                                                  fill="#FF4655"></path>
                                        </svg>
                                        ${character.getRP}
                                    <#else>
                                        <svg xmlns="http://www.w3.org/2000/svg" width="8" height="5" viewBox="0 0 8 5"
                                             fill="none" style="transform: rotate(180deg);">
                                            <path
                                                    d="M6.75 4.75C7.17188 4.75 7.38281 4.25781 7.07812 3.95312L4.07812 0.953125C3.89062 0.765625 3.58594 0.765625 3.39844 0.953125L0.398438 3.95312C0.09375 4.25781 0.304688 4.75 0.726562 4.75H6.75Z"
                                                    fill="#5393ca"></path>
                                        </svg>
                                        ${character.getRP}
                                    </#if>
                                </td>
                                <td class="avg-rank">${character.avgRank}</td>
                                <td class="avg-dmg">${character.avgDmg}</td>
                            </tr>
                        </#list>
                        </tbody>
                    </table>
                </section>
            </#if>
            <#if recentPlayers?has_content>
                <section>
                    <table id="recent_play">
                        <thead>
                        <tr>
                            <th class="character">一起游戏的玩家 (最近组排队友)</th>
                            <th class="win-rate">胜率</th>
                            <th class="avg-rank">平均排名</th>
                        </tr>
                        </thead>
                        <tbody>
                        <#list recentPlayers as recentPlayer>
                            <tr>
                                <td class="character">
                                    <div class="image-wrapper"><img
                                                src="${httpServer}${recentPlayer.imageWrapperUrl}"
                                                alt=""></div>
                                    <div class="info">${recentPlayer.nickname}
                                        <div class="plays">${recentPlayer.plays} 游戏</div>
                                    </div>
                                </td>
                                <td class="win-rate">
                                    ${recentPlayer.winRate}
                                </td>
                                <td class="avg-rank">
                                    ${recentPlayer.avgRank}
                                </td>
                            </tr>
                        </#list>

                        </tbody>
                    </table>
                </section>
            </#if>
        </div>
        <div id="right">
            <#if summary?has_content>
                <div id="recent_play_summary">
                    <div id="recent_play_title">Recent ${summary.count} Match Summary(排位)</div>
                    <div id="recent_play_stats">
                        <div class="summary_item">
                            <div class="summary_label">最近对局获胜数</div>
                            <div class="summary_value">${summary.wins}</div>
                        </div>
                        <div class="summary_item">
                            <div class="summary_label">最近对局平均排名</div>
                            <div class="summary_value">${summary.avgRank}</div>
                        </div>
                        <div class="summary_item">
                            <div class="summary_label">最近对局平均团队击杀</div>
                            <div class="summary_value">${summary.avgTk}</div>
                        </div>
                    </div>
                    <div id="recent_play_rank">
                        <#list summary.ranks as rank>
                            <span style="<#if rank == 1>background-color: #11B288;
                            <#elseif rank == 2>background-color:#207AC7 ;
                            <#elseif rank == 3>background-color:#207AC7 ;
                            <#elseif rank == 99>background-color:#475482;
                            <#else>background-color: #D6D6D6; color: #808080;
                            </#if>">
                                <#if rank == 99>
                                    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
                                    <path d="M5.79221 9.71567C5.98067 10.025 6.22298 10.3063 6.54606 10.5032L6.81529 10.6719L6.43836 11.5719C6.19606 12.1344 5.65759 12.5 5.06529 12.5H3.04606C2.66913 12.5 2.3999 12.2188 2.3999 11.825C2.3999 11.4594 2.66913 11.15 3.04606 11.15H5.06529C5.14606 11.15 5.22683 11.1219 5.25375 11.0375L5.79221 9.71567ZM9.72298 3.50005C8.99606 3.50005 8.43067 2.90942 8.43067 2.15005C8.43067 1.4188 8.99606 0.800049 9.72298 0.800049C10.423 0.800049 11.0153 1.4188 11.0153 2.15005C11.0153 2.90942 10.423 3.50005 9.72298 3.50005ZM12.9537 7.57817C13.3037 7.57817 13.5999 7.88755 13.5999 8.25317C13.5999 8.6188 13.3037 8.92817 12.9268 8.92817H11.6345C10.9614 8.92817 10.3961 8.47817 10.2076 7.80317L9.83067 6.50942C9.77683 6.39692 9.72298 6.28442 9.64221 6.17192L8.51144 9.12505L9.91144 9.99692C10.3691 10.3063 10.6384 10.8125 10.6384 11.3469C10.6384 11.4875 10.6114 11.6563 10.5845 11.7969L9.69606 14.7219C9.61529 15.0313 9.34606 15.2 9.07683 15.2C8.59221 15.2 8.43067 14.75 8.43067 14.525C8.43067 14.4688 8.43067 14.4125 8.45759 14.3563L9.34606 11.4313C9.34606 11.4032 9.34606 11.375 9.34606 11.3469C9.34606 11.2907 9.31913 11.2063 9.23836 11.15L6.97683 9.7438C6.51913 9.43442 6.2499 8.92817 6.2499 8.3938C6.2499 8.19692 6.30375 8.00005 6.38452 7.80317L7.32683 5.32817L6.92298 5.2438C6.84221 5.2438 6.76144 5.21567 6.68067 5.21567C6.43836 5.21567 6.22298 5.30005 6.03452 5.4688L4.71529 6.50942C4.60759 6.5938 4.47298 6.65005 4.33836 6.65005C3.93452 6.65005 3.69221 6.31255 3.69221 5.97505C3.69221 5.77817 3.77298 5.5813 3.93452 5.44067L5.22683 4.40005C5.65759 4.06255 6.16913 3.86567 6.68067 3.86567C6.84221 3.86567 7.03067 3.8938 7.21913 3.92192L9.31913 4.42817C10.1537 4.62505 10.7999 5.27192 11.0691 6.11567L11.4191 7.40942C11.4461 7.52192 11.5537 7.57817 11.6345 7.57817H12.9537Z" fill="white"></path>
                                    </svg>
                                <#else>${rank}
                                </#if>
                        </span>
                        </#list>
                    </div>
                </div>
            </#if>
            <#list matches as match>
                <div class="war_record">
                    <div class="game_id">对局ID
                        ${match.serverName}-${match.gameId}
                        (${match.version})
                    </div>
                    <#if match.rank == 99>
                        <div class="war_record_left_escape"></div>
                        <div class="war_record_right_escape"></div>
                    <#elseif match.rank gte 3 || (match.type == "钴协议" && match.rank == 2) >
                        <div class="war_record_left_top3"></div>
                        <div class="war_record_right_top3"></div>
                    <#else>
                        <div class="war_record_left_top${match.rank}"></div>
                        <div class="war_record_right_top${match.rank}"></div>
                    </#if>
                    <div class="war_record1">
                        <div class="war_rank">
                            <#if match.rank == 99>
                                <div style="color:#475482">逃离</div>
                            <#elseif match.type == "钴协议" && match.rank == 1>
                                <div>胜利</div>
                            <#elseif match.type == "钴协议" && match.rank == 2>
                                <div>失败</div>
                            <#elseif match.rank == 1>
                                <div style="color: #11B288">#${match.rank}</div>
                            <#elseif match.rank == 2>
                                <div style="color: #207AC7">#${match.rank}</div>
                            <#else>
                                <div>#${match.rank}</div>
                            </#if>
                            <div>${match.type}</div>
                            <div>${match.dateHour}</div>
                            <div>${match.dateMonth}</div>
                        </div>
                        <div class="war_record_character_info">
                            <div class="hero_avatar">
                                <img src="${httpServer}${match.characterAvatarUrl}"
                                     alt="">
                            </div>
                            <div class="character_name">${match.characterName}</div>
                        </div>
                        <div class="skill">
                            <div class="weapon">
                                <img src="${httpServer}${match.weaponUrl}"
                                     alt="">
                            </div>
                            <div class="trait">
                                <img src="${httpServer}${match.traitSkillUrl}"
                                     alt="">
                            </div>
                            <div class="trait">
                                <img src="${httpServer}${match.tacticalSkillUrl}"
                                     alt="">
                            </div>

                            <div class="trait">
                                <img src="${httpServer}${match.traitSkillGroupUrl}"
                                     alt="">
                            </div>
                        </div>

                        <div class="play_data">
                            <div class="play_stat">
                                <div class="play_data_title">
                                    ${match.tk} <span>/</span> ${match.kill} <span>/</span> ${match.assist}
                                </div>
                                <div class="play_data_label">
                                    TK <span>/</span> K <span>/</span> A
                                </div>
                            </div>
                            <div class="damage">
                                <div class="play_data_title">${match.dmg}</div>
                                <div class="play_data_label">DMG</div>
                            </div>
                            <#if match.type == "排位">
                                <div class="rp">
                                    <div class="play_data_title">${match.rp}
                                        <#if match.rpChange gte 0>
                                            <svg xmlns="http://www.w3.org/2000/svg" width="8" height="5"
                                                 viewBox="0 0 8 5" fill="none"
                                                 style="transform: none;">
                                                <path d="M6.75 4.75C7.17188 4.75 7.38281 4.25781 7.07812 3.95312L4.07812 0.953125C3.89062 0.765625 3.58594 0.765625 3.39844 0.953125L0.398438 3.95312C0.09375 4.25781 0.304688 4.75 0.726562 4.75H6.75Z"
                                                      fill="#FF4655"></path>
                                            </svg>
                                            <span style="color: #FF4655">${match.rpChange}</span>
                                        <#else>
                                            <svg xmlns="http://www.w3.org/2000/svg" width="8" height="5"
                                                 viewBox="0 0 8 5"
                                                 fill="none" style="transform: rotate(180deg);">
                                                <path
                                                        d="M6.75 4.75C7.17188 4.75 7.38281 4.25781 7.07812 3.95312L4.07812 0.953125C3.89062 0.765625 3.58594 0.765625 3.39844 0.953125L0.398438 3.95312C0.09375 4.25781 0.304688 4.75 0.726562 4.75H6.75Z"
                                                        fill="#5393ca"></path>
                                            </svg>
                                            <span style="color: #5393CA">${match.rpChange}</span>
                                        </#if>
                                    </div>
                                    <div class="play_data_label">RP</div>
                                </div>
                            <#else>
                                <div class="rp">
                                    <div class="play_data_title">${match.kda}</div>
                                    <div class="play_data_label">KDA</div>
                                </div>
                            </#if>
                            <#if match.type != "钴协议">
                                <div class="route">
                                    <div class="play_data_title">${match.routeId}</div>
                                    <div class="play_data_label">路径ID
                                    </div>
                                </div>
                            </#if>
                        </div>

                        <ul class="item_box">
                            <#list match.equips as equip>
                                <li class="item">
                                    <#if equip.itemBgUrl != "" >
                                        <img class="item_bg" src="${httpServer}${equip.itemBgUrl}"
                                             alt="">
                                    </#if>
                                    <#if equip.itemUrl != "" >
                                        <img class="item_img"
                                             src="${httpServer}${equip.itemUrl}" alt="">
                                    </#if>
                                </li>
                            </#list>
                        </ul>
                    </div>
                    <#if match.teamMates??>
                        <#list match.teamMates as teamMate>
                            <div class="war_record2">
                                <div class="teammate_name">
                                    <div class="play_name">${teamMate.nickName}</div>
                                    <div class="teammate_rp">
                                        <div class="teammate_rp_img"><img
                                                    src="${httpServer}${teamMate.rpImageUrl}" alt=""></div>
                                        <span>${teamMate.rp} RP</span>
                                    </div>
                                </div>
                                <div class="hero_avatar">
                                    <img src="${httpServer}${teamMate.avatarUrl}"
                                         alt="">
                                </div>
                                <div class="skill">
                                    <div class="weapon">
                                        <img src="${httpServer}${teamMate.weaponUrl}"
                                             alt="">
                                    </div>
                                    <div class="trait">
                                        <img src="${httpServer}${teamMate.traitSkillUrl}"
                                             alt="">
                                    </div>
                                    <div class="trait">
                                        <img src="${httpServer}${teamMate.skillUrl}"
                                             alt="">
                                    </div>

                                    <div class="trait">
                                        <img src="${httpServer}${teamMate.traitSkillGroupUrl}"
                                             alt="">
                                    </div>
                                </div>
                                <div class="play_data">
                                    <div class="play_stat">
                                        <div class="play_data_title">
                                            ${teamMate.tk} <span>/</span> ${teamMate.kill}
                                            <span>/</span> ${teamMate.assist}
                                        </div>
                                        <div class="play_data_label">
                                            TK <span>/</span> K <span>/</span> A
                                        </div>
                                    </div>
                                    <div class="damage">
                                        <div class="play_data_title">${teamMate.dmg}</div>
                                        <div class="play_data_label">DMG</div>
                                    </div>
                                </div>

                                <ul class="item_box">
                                    <#list teamMate.equips as teamMateEquip>
                                        <li class="item">
                                            <img class="item_bg" src="${httpServer}${teamMateEquip.itemBgUrl}"
                                                 alt="">
                                            <img class="item_img"
                                                 src="${httpServer}${teamMateEquip.itemUrl}" alt="">
                                        </li>
                                    </#list>
                                </ul>
                            </div>
                        </#list>
                    </#if>
                </div>
            </#list>
        </div>
    </div>
</div>
</body>
</html>