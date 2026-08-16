<!doctype html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${pageTitle?html} · 访问验证</title>
    <link rel="stylesheet" href="/static/admin/admin.css">
    <style>
        html { background:#fff7fb; }
        body { color:#533447; }
        body::before { content:""; position:fixed; inset:0; z-index:-2; background:url("${backgroundImageUrl?html}") center/cover no-repeat; opacity:.72; }
        body::after { content:""; position:fixed; inset:0; z-index:-1; background:rgb(255 247 251/.28); backdrop-filter:blur(5px); }
        .login-shell { min-height:100vh; display:grid; place-items:center; padding:24px; }
        .login-card { width:min(100%, 430px); padding:32px; background:rgb(255 255 255/.58); border-color:rgb(251 207 226/.82); }
        .login-brand { display:flex; align-items:center; justify-content:space-between; gap:16px; }
        .login-logo { display:grid; width:52px; height:52px; place-items:center; border:2px solid rgb(255 255 255/.88); border-radius:18px; background:linear-gradient(135deg,rgb(238 123 174/.72),rgb(189 120 219/.72)); color:white; font-size:20px; font-weight:900; box-shadow:0 12px 28px rgb(206 91 148/.22); transform:rotate(-3deg); }
        .login-title { margin-top:28px; font-size:28px; line-height:1.2; font-weight:900; color:#63364f; }
        .login-description { margin-top:8px; color:#9b7187; font-size:14px; line-height:1.7; }
        .login-form { display:grid; gap:16px; margin-top:28px; }
        .login-button { width:100%; min-height:46px; }
        .login-error { margin-top:16px; padding:11px 14px; border:1px solid rgb(210 65 65/.22); border-radius:16px; background:rgb(255 239 244/.72); color:#b83f68; font-size:13px; font-weight:700; }
        .login-hint { margin-top:18px; text-align:center; color:#9b7187; font-size:12px; }
    </style>
</head>
<body class="font-['Nunito','Segoe_UI','Microsoft_YaHei',sans-serif]">
<main class="login-shell">
    <section class="panel login-card">
        <div class="login-brand">
            <div class="login-logo">L♡</div>
            <span class="pill">安全访问</span>
        </div>
        <h1 class="login-title">欢迎回来喵！</h1>
        <p class="login-description">请输入服务启动时打印的访问令牌，验证后即可进入 LoMu Control Center。</p>
        <form class="login-form" method="post" action="/admin/login">
            <label class="field">
                <span class="field-label">访问令牌</span>
                <input class="control" type="password" name="token" placeholder="粘贴启动日志中的令牌" autocomplete="one-time-code" autofocus required>
            </label>
            <button class="button button-primary login-button" type="submit">验证并进入</button>
        </form>
        <#if error??>
            <p class="login-error" role="alert">${error?html}</p>
        </#if>
        <p class="login-hint">令牌会在每次服务重启时更新</p>
    </section>
</main>
</body>
</html>
