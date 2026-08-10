<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LoMu Bot 指令测试</title>
    <style>
        *,
        *::before,
        *::after {
            box-sizing: border-box;
        }

        :root {
            --bg: #f4f6f8;
            --surface: #ffffff;
            --surface-2: #eef2f4;
            --line: #d8dee4;
            --text: #182026;
            --muted: #65717c;
            --green: #167a55;
            --green-soft: #dff3eb;
            --red: #a43c3c;
            --shadow: 0 10px 30px rgba(18, 32, 38, 0.08);
        }

        body {
            margin: 0;
            background: var(--bg);
            color: var(--text);
            font-family: "Inter", "Segoe UI", "Microsoft YaHei", system-ui, sans-serif;
            font-size: 14px;
            line-height: 1.45;
        }

        button,
        input {
            font: inherit;
        }

        button {
            min-height: 34px;
            padding: 0 12px;
            border: 1px solid var(--green);
            border-radius: 6px;
            background: var(--green);
            color: #fff;
            cursor: pointer;
        }

        input {
            width: 100%;
            min-height: 34px;
            padding: 6px 9px;
            border: 1px solid var(--line);
            border-radius: 6px;
            background: #fff;
            color: var(--text);
        }

        .page {
            max-width: 1280px;
            margin: 0 auto;
            padding: 24px;
        }

        .topbar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 20px;
            margin-bottom: 18px;
        }

        .title {
            margin: 0;
            font-size: 24px;
            font-weight: 800;
        }

        .status {
            min-height: 24px;
            color: var(--muted);
            text-align: right;
        }

        .status.error {
            color: var(--red);
        }

        .status.ok {
            color: var(--green);
        }

        .section {
            overflow: hidden;
            border: 1px solid var(--line);
            border-radius: 8px;
            background: var(--surface);
            box-shadow: var(--shadow);
        }

        .section-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 16px;
            padding: 14px 16px;
            border-bottom: 1px solid var(--line);
            background: #fbfcfd;
        }

        .section-title {
            margin: 0;
            font-size: 16px;
            font-weight: 800;
        }

        .command-grid {
            display: grid;
            grid-template-columns: minmax(220px, 1fr) 170px 170px 150px auto;
            gap: 10px;
            padding: 16px;
            align-items: end;
        }

        .field label {
            display: block;
            margin-bottom: 5px;
            color: var(--muted);
            font-size: 12px;
            font-weight: 700;
        }

        .result {
            min-height: 120px;
            padding: 16px;
            border-top: 1px solid var(--line);
            background: var(--surface-2);
        }

        .result-empty {
            color: var(--muted);
        }

        .preview-list {
            display: grid;
            gap: 12px;
        }

        .preview-item {
            padding: 12px;
            border: 1px solid var(--line);
            border-radius: 8px;
            background: #fff;
        }

        .preview-item pre {
            margin: 0;
            white-space: pre-wrap;
            word-break: break-word;
            font-family: "Cascadia Code", "SF Mono", Consolas, monospace;
            font-size: 13px;
        }

        .preview-image {
            display: block;
            max-width: 100%;
            max-height: 760px;
            border: 1px solid var(--line);
            border-radius: 6px;
            background: #fff;
        }

        .badge {
            display: inline-flex;
            align-items: center;
            min-height: 24px;
            padding: 0 8px;
            border-radius: 6px;
            background: var(--green-soft);
            color: var(--green);
            font-size: 12px;
            font-weight: 800;
        }

        @media (max-width: 920px) {
            .page {
                padding: 14px;
            }

            .topbar {
                align-items: flex-start;
                flex-direction: column;
            }

            .status {
                text-align: left;
            }

            .command-grid {
                grid-template-columns: 1fr;
            }
        }
    </style>
</head>
<body>
<main class="page">
    <div class="topbar">
        <h1 class="title">LoMu Bot 指令测试</h1>
        <div id="status" class="status"></div>
    </div>

    <section class="section">
        <div class="section-header">
            <h2 class="section-title">指令测试</h2>
            <span class="badge" id="matchBadge">READY</span>
        </div>
        <form id="commandForm" class="command-grid">
            <div class="field">
                <label for="plainText">指令</label>
                <input id="plainText" name="plainText" value="/游戏活动" autocomplete="off">
            </div>
            <div class="field">
                <label for="groupId">群 ID</label>
                <input id="groupId" name="groupId" value="web-admin-group" autocomplete="off">
            </div>
            <div class="field">
                <label for="senderId">用户 ID</label>
                <input id="senderId" name="senderId" value="web-admin-user" autocomplete="off">
            </div>
            <div class="field">
                <label for="senderName">用户</label>
                <input id="senderName" name="senderName" value="WebAdmin" autocomplete="off">
            </div>
            <button type="submit">运行</button>
        </form>
        <div id="commandResult" class="result">
            <span class="result-empty">无结果</span>
        </div>
    </section>
</main>

<script>
    const statusEl = document.getElementById('status');
    const resultEl = document.getElementById('commandResult');
    const badgeEl = document.getElementById('matchBadge');

    function setStatus(message, type) {
        statusEl.textContent = message || '';
        statusEl.className = 'status' + (type ? ' ' + type : '');
    }

    async function requestJson(url, options) {
        const response = await fetch(url, options);
        const payload = await response.json();
        if (!response.ok || !payload.ok) {
            throw new Error(payload.error || ('HTTP ' + response.status));
        }
        return payload.data;
    }

    function escapeHtml(value) {
        return String(value ?? '')
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
    }

    function renderPreview(preview) {
        if (!preview.matched) {
            badgeEl.textContent = 'UNMATCHED';
            resultEl.innerHTML = '<span class="result-empty">未匹配</span>';
            return;
        }

        badgeEl.textContent = 'MATCHED';
        if (!preview.elements.length) {
            resultEl.innerHTML = '<span class="result-empty">空消息</span>';
            return;
        }

        resultEl.innerHTML = '<div class="preview-list">' + preview.elements.map(function (element) {
            if (element.type === 'image' && element.imageUrl) {
                return '<div class="preview-item"><img class="preview-image" src="' + escapeHtml(element.imageUrl) + '" alt=""></div>';
            }
            return '<div class="preview-item"><pre>' + escapeHtml(element.text || element.raw || '') + '</pre></div>';
        }).join('') + '</div>';
    }

    async function runCommand(event) {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        setStatus('运行中');
        resultEl.innerHTML = '<span class="result-empty">运行中</span>';
        const data = await requestJson('/api/admin/test-command', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                plainText: form.get('plainText'),
                groupId: form.get('groupId'),
                senderId: form.get('senderId'),
                senderName: form.get('senderName')
            })
        });
        renderPreview(data);
        setStatus('运行完成', 'ok');
    }

    document.getElementById('commandForm').addEventListener('submit', function (event) {
        runCommand(event).catch(function (error) {
            badgeEl.textContent = 'ERROR';
            resultEl.innerHTML = '<span class="result-empty">' + escapeHtml(error.message) + '</span>';
            setStatus(error.message, 'error');
        });
    });
</script>
</body>
</html>
