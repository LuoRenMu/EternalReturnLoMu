<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>游戏活动</title>
    <style>
        *,
        *::before,
        *::after {
            box-sizing: border-box;
        }

        body {
            margin: 0;
            background: #111315;
            color: #ece7df;
            font-family: "Inter", "Segoe UI", "Microsoft YaHei", system-ui, sans-serif;
            line-height: 1.45;
        }

        #activity-page {
            width: 1040px;
            padding: 28px;
            background:
                linear-gradient(180deg, rgba(34, 124, 112, 0.12), rgba(17, 19, 21, 0) 220px),
                #111315;
        }

        .page-header {
            display: flex;
            align-items: flex-end;
            justify-content: space-between;
            gap: 24px;
            margin-bottom: 18px;
            border-bottom: 1px solid rgba(236, 231, 223, 0.12);
            padding-bottom: 18px;
        }

        .title {
            margin: 0;
            font-size: 34px;
            font-weight: 800;
            letter-spacing: 0;
        }

        .summary {
            margin: 6px 0 0;
            color: #a8b0aa;
            font-size: 15px;
        }

        .count {
            min-width: 112px;
            text-align: right;
            color: #ffd166;
            font-size: 28px;
            font-weight: 800;
        }

        .list {
            display: grid;
            gap: 14px;
        }

        .activity-card {
            display: grid;
            grid-template-columns: 260px minmax(0, 1fr);
            gap: 18px;
            min-height: 148px;
            padding: 14px;
            border: 1px solid rgba(236, 231, 223, 0.12);
            border-radius: 8px;
            background: #1a1d1f;
        }

        .thumb {
            position: relative;
            overflow: hidden;
            width: 260px;
            height: 146px;
            border-radius: 6px;
            background: #24282a;
            border: 1px solid rgba(236, 231, 223, 0.10);
        }

        .thumb img {
            width: 100%;
            height: 100%;
            object-fit: cover;
            display: block;
        }

        .thumb-placeholder {
            display: grid;
            place-items: center;
            width: 100%;
            height: 100%;
            color: #77817d;
            font-weight: 700;
            letter-spacing: 0;
        }

        .content {
            min-width: 0;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }

        .row-top {
            display: flex;
            align-items: flex-start;
            justify-content: space-between;
            gap: 16px;
        }

        .activity-title {
            margin: 0;
            color: #fffaf0;
            font-size: 22px;
            line-height: 1.25;
            font-weight: 800;
        }

        .status {
            flex: 0 0 auto;
            padding: 4px 10px;
            border-radius: 6px;
            font-size: 13px;
            font-weight: 700;
            color: #0f1513;
            background: #59d39b;
        }

        .status.expired {
            background: #ffb347;
        }

        .code {
            display: inline-flex;
            align-items: center;
            width: fit-content;
            max-width: 100%;
            min-height: 38px;
            padding: 6px 12px;
            border-radius: 6px;
            background: #0e1011;
            color: #7bdff2;
            border: 1px solid rgba(123, 223, 242, 0.28);
            font-family: "Cascadia Code", "SF Mono", Consolas, monospace;
            font-size: 18px;
            font-weight: 800;
            overflow-wrap: anywhere;
        }

        .details {
            display: grid;
            gap: 5px;
            color: #d3d0c8;
            font-size: 15px;
        }

        .detail-line {
            display: grid;
            grid-template-columns: 52px minmax(0, 1fr);
            gap: 8px;
        }

        .label {
            color: #8d9892;
            font-weight: 700;
        }

        .value {
            min-width: 0;
            overflow-wrap: anywhere;
        }
    </style>
</head>
<body>
<main id="activity-page">
    <header class="page-header">
        <div>
            <h1 class="title">游戏活动</h1>
            <p class="summary">有效期内与刚过期 1 天的永恒轮回官方活动 · ${generatedDate?html}</p>
        </div>
        <div class="count">${items?size} 条</div>
    </header>

    <section class="list">
        <#list items as item>
            <article class="activity-card">
                <div class="thumb">
                    <#if item.thumbnailUrl?? && item.thumbnailUrl?has_content>
                        <img src="${item.thumbnailUrl?html}" alt="${item.title?html}" referrerpolicy="no-referrer">
                    <#else>
                        <div class="thumb-placeholder">NO IMAGE</div>
                    </#if>
                </div>

                <div class="content">
                    <div class="row-top">
                        <h2 class="activity-title">${item.title?html}</h2>
                        <#if item.status == "已过期 1 天">
                            <span class="status expired">${item.status?html}</span>
                        <#else>
                            <span class="status">${item.status?html}</span>
                        </#if>
                    </div>

                    <#if item.code?? && item.code?has_content>
                        <div class="code">${item.code?html}</div>
                    </#if>

                    <div class="details">
                        <#if item.reward?has_content>
                            <div class="detail-line">
                                <span class="label">奖励</span>
                                <span class="value">${item.reward?html}</span>
                            </div>
                        </#if>
                        <#if item.note?has_content>
                            <div class="detail-line">
                                <span class="label">说明</span>
                                <span class="value">${item.note?html}</span>
                            </div>
                        </#if>
                        <div class="detail-line">
                            <span class="label">有效期</span>
                            <span class="value">${item.period?html}</span>
                        </div>
                    </div>
                </div>
            </article>
        </#list>
    </section>
</main>
</body>
</html>
