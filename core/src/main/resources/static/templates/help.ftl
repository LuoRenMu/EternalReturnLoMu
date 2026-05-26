<!DOCTYPE html>
<html lang="zh-CN">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>机器人指令列表</title>
    <style>
        *,
        *::before,
        *::after {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        :root {
            --bg: #0f1117;
            --card-bg: #1a1d27;
            --border: #2a2d3a;
            --text: #e1e4ed;
            --text-muted: #8b8fa3;
            --accent: #6c8cff;
            --tag-bg: #1e2235;
            --tag-text: #a8b4ff;
            --hover: #252836;
        }

        body {
            font-family: "Inter", "Segoe UI", system-ui, -apple-system, sans-serif;
            background: var(--bg);
            color: var(--text);
            min-height: 100vh;
            line-height: 1.6;
        }

        .header {
            background: linear-gradient(135deg, #1a1d27 0%, #1e2235 50%, #1a1d27 100%);
            border-bottom: 1px solid var(--border);
            padding: 48px 24px;
            text-align: center;
            margin-bottom: 20px;
        }

        .header h1 {
            font-size: 2.4rem;
            font-weight: 700;
            letter-spacing: -0.5px;
            margin-bottom: 8px;
        }

        .header .subtitle {
            color: var(--text-muted);
            font-size: 1rem;
        }

        .header .badge {
            display: inline-block;
            margin-top: 16px;
            padding: 4px 14px;
            border-radius: 20px;
            font-size: 0.85rem;
            background: rgba(108, 140, 255, 0.12);
            color: var(--accent);
            border: 1px solid rgba(108, 140, 255, 0.2);
        }

        #content-container {
            max-width: 960px;
            margin: 0 auto;
            padding: 32px 24px 80px;
        }

        /* ---- section ---- */
        .section {
            margin-bottom: 40px;
        }

        .section-title {
            font-size: 1.25rem;
            font-weight: 700;
            margin-bottom: 4px;
        }

        .section-count {
            font-size: 0.82rem;
            color: var(--text-muted);
            margin-bottom: 16px;
        }

        .section-title .icon {
            margin-right: 6px;
        }

        /* ---- command cards ---- */
        .commands-grid {
            display: grid;
            gap: 12px;
        }

        .cmd-card {
            background: var(--card-bg);
            border: 1px solid var(--border);
            border-radius: 12px;
            padding: 20px 24px;
        }

        .cmd-header {
            display: flex;
            align-items: center;
            gap: 12px;
            margin-bottom: 8px;
            flex-wrap: wrap;
        }

        .cmd-name {
            font-family: "SF Mono", "Cascadia Code", "Fira Code", Consolas, monospace;
            font-size: 1.05rem;
            font-weight: 600;
            color: #fff;
            background: rgba(108, 140, 255, 0.1);
            padding: 2px 10px;
            border-radius: 6px;
        }

        .cmd-desc {
            color: var(--text-muted);
            font-size: 0.9rem;
            margin-bottom: 10px;
        }

        .cmd-details {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
            font-size: 0.82rem;
        }

        .cmd-param {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            background: #151821;
            border-radius: 6px;
            padding: 3px 10px;
            color: #cdd6f4;
        }

        .cmd-param .param-name {
            color: #f5c542;
            font-weight: 600;
        }

        .cmd-param .param-required {
            color: #f7768e;
            font-size: 0.7rem;
        }

        .cmd-param .param-optional {
            color: var(--text-muted);
            font-size: 0.7rem;
        }

        .cmd-example {
            margin-top: 10px;
            font-family: "SF Mono", "Cascadia Code", "Fira Code", Consolas, monospace;
            font-size: 0.82rem;
            color: #9ece6a;
            background: #0f1117;
            padding: 6px 12px;
            border-radius: 6px;
            word-break: break-all;
        }

        .cmd-permission {
            font-size: 0.78rem;
            padding: 2px 8px;
            border-radius: 4px;
        }

        .perm-admin {
            background: rgba(247, 118, 142, 0.12);
            color: #f7768e;
        }

        .perm-mod {
            background: rgba(245, 197, 66, 0.12);
            color: #f5c542;
        }

        .perm-all {
            background: rgba(158, 206, 106, 0.12);
            color: #9ece6a;
        }

        /* ---- nav ---- */
        .nav {
            display: flex;
            gap: 4px;
            flex-wrap: wrap;
            margin-bottom: 32px;
        }

        .nav a {
            padding: 6px 16px;
            border-radius: 20px;
            border: 1px solid var(--border);
            background: transparent;
            color: var(--text-muted);
            text-decoration: none;
            font-size: 0.9rem;
        }

        .nav a:hover {
            background: var(--hover);
            color: var(--text);
        }
    </style>
</head>

<body>



<div id="content-container">
    <div class="header">
        <h1>LoMu-Bot 指令</h1>
        <p class="subtitle">所有可用命令的完整参考</p>
        <span class="badge">v2.4.0 </span>
    </div>
    <!-- ==================== 通用 ==================== -->
    <div class="section" id="general">
        <div class="commands-grid">
            <#list helps as help>
                <div class="cmd-card">
                    <div class="cmd-header">
                        <span class="cmd-name">${help.name}</span>
                        <span class="cmd-permission perm-all">所有人</span>
                    </div>
                    <div class="cmd-desc">${help.description}</div>
                    <div class="cmd-details">
                        <#list help.optionals as optional>
                            <span class="cmd-param">
                                <span class="param-name">${optional.name}</span>
                                <#if optional.required><span class="param-required">必填</span><#else><span class="param-optional">可选</span></#if>
                                <span style="color:var(--text-muted)">${optional.description}</span>
                            </span>
                        </#list>
                    </div>
                    <div class="cmd-example">${help.example}</div>
                </div>
            </#list>
        </div>
    </div>
</div>
</body>

</html>