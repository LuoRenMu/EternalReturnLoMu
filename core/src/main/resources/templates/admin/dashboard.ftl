<!doctype html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${pageTitle?html}</title>
    <link rel="icon" href="/static/images/favicon.ico" sizes="any">
    <link rel="stylesheet" href="/static/admin/admin.css">
    <script defer src="https://cdn.jsdelivr.net/npm/htmx.org@2.0.8/dist/htmx.min.js"></script>
    <script defer src="/static/admin/admin.js"></script>
    <script defer src="https://cdn.jsdelivr.net/npm/alpinejs@3.15.8/dist/cdn.min.js"></script>
    <style>
        html { background:#fff7fb; }
        body::before { content:""; position:fixed; inset:0; z-index:-2; pointer-events:none; background:url("${backgroundImageUrl?html}") center/cover no-repeat; opacity:.72; }
        body::after { content:""; position:fixed; inset:0; z-index:-1; pointer-events:none; background:rgb(255 247 251/.20); }
        body > div > aside { background:linear-gradient(to bottom,rgb(245 143 186/.84),rgb(236 114 170/.84),rgb(189 132 223/.84)); }
        body > div > div > header { background:rgb(255 255 255/.46); }
        .panel, .stat-card { background:rgb(255 255 255/.52) !important; background-image:none !important; }
        .panel > aside, .bg-lomu-50, .bg-white\/80 { background-color:rgb(255 255 255/.46) !important; background-image:none !important; }
        .control { background:rgb(255 255 255/.58) !important; }
        .button { background:rgb(255 255 255/.48) !important; background-image:none !important; }
        .button-primary { background:linear-gradient(135deg,rgb(238 123 174/.62),rgb(189 120 219/.62)) !important; }
        .pill { background:rgb(255 232 242/.48) !important; background-image:none !important; }
        .nav-button { background-color:rgb(255 255 255/.08); }
        .nav-button:hover, .nav-button-active { background-color:rgb(255 255 255/.18) !important; }
        table thead th { background:rgb(255 247 251/.58) !important; }
        table tbody tr:hover { background:rgb(255 247 251/.30) !important; }
        body > div.fixed form { background:rgb(255 247 251/.68) !important; }
        body > div.fixed[x-show="editOpen"] { background:rgb(91 48 70/.22) !important; }
        body > div.fixed[x-show="toast.visible"] { background:rgb(184 63 104/.64) !important; background-image:none !important; }
        .plugin-upload { display:flex; align-items:flex-end; gap:.75rem; margin-bottom:1rem; padding:1.25rem; }
        .plugin-upload .field { flex:1; min-width:0; }
        .plugin-actions { display:flex; flex-wrap:wrap; gap:.5rem; margin-top:1rem; }
        .plugin-reply { margin-top:1rem; }
        @media (max-width:767px) { .plugin-upload { align-items:stretch; flex-direction:column; } }
    </style>
</head>
<body class="relative isolate min-h-screen bg-transparent font-['Nunito','Segoe_UI','Microsoft_YaHei',sans-serif] text-[#533447]" x-data="adminDashboard" x-init="init()">
<div class="min-h-screen lg:grid lg:grid-cols-[236px_minmax(0,1fr)]">
    <aside class="overflow-x-hidden bg-gradient-to-b from-[#f58fba] via-[#ec72aa] to-[#bd84df] p-4 text-white shadow-[12px_0_40px_rgba(206,91,148,.16)] lg:sticky lg:top-0 lg:h-screen">
        <div class="flex items-center gap-3 border-b border-dashed border-white/35 px-2 pb-5">
            <div class="grid size-11 -rotate-3 place-items-center rounded-2xl border-2 border-white/80 bg-white/20 text-lg font-black shadow-lg">L♡</div>
            <div><strong class="block text-lg">LoMu Control</strong><span class="text-xs text-white/70">EternalReturnLoMu</span></div>
        </div>
        <nav class="mt-5 grid grid-cols-2 gap-2 lg:grid-cols-1">
            <button class="nav-button" :class="view === 'overview' && 'nav-button-active'" @click="showView('overview', '系统概览')"><span class="rounded-lg bg-white/15 px-2 py-1 text-xs">01</span>系统概览</button>
            <button class="nav-button" :class="view === 'database' && 'nav-button-active'" @click="showView('database', '数据库')"><span class="rounded-lg bg-white/15 px-2 py-1 text-xs">02</span>数据库</button>
            <button class="nav-button" :class="view === 'exceptions' && 'nav-button-active'" @click="showView('exceptions', '异常日志')"><span class="rounded-lg bg-white/15 px-2 py-1 text-xs">03</span>异常日志</button>
            <button class="nav-button" :class="view === 'plugins' && 'nav-button-active'" @click="showView('plugins', '命令插件')"><span class="rounded-lg bg-white/15 px-2 py-1 text-xs">04</span>命令插件</button>
            <button class="nav-button" :class="view === 'command' && 'nav-button-active'" @click="showView('command', 'Debug')"><span class="rounded-lg bg-white/15 px-2 py-1 text-xs">05</span>Debug</button>
            <button class="nav-button" :class="view === 'about' && 'nav-button-active'" @click="showView('about', '关于')"><span class="rounded-lg bg-white/15 px-2 py-1 text-xs">06</span>关于</button>
        </nav>
        <div class="absolute bottom-5 hidden text-xs text-white/65 lg:block">Power by LuoRenMu</div>
    </aside>

    <div class="min-w-0">
        <header class="flex flex-col gap-3 border-b border-lomu-200/70 bg-white/80 px-4 py-4 shadow-sm backdrop-blur-xl md:flex-row md:items-center md:justify-between md:px-7">
            <h1 class="text-xl font-black tracking-tight text-lomu-900" x-text="pageTitle"></h1>
            <span class="pill">令牌已验证</span>
        </header>

        <main class="p-4 pb-14 md:p-7">
            <section x-show="view === 'overview'" x-cloak>
                <div class="mb-5 flex items-start justify-between gap-4">
                    <div><h2 class="text-2xl font-black text-lomu-900">系统概览</h2><p class="mt-1 text-sm text-[#9b7187]">集中查看机器人配置和数据服务状态。</p></div>
                    <button class="button" @click="refreshOverview()">刷新</button>
                </div>
                <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                    <article class="stat-card bg-gradient-to-br from-white to-[#ffe9f2]"><span class="stat-dot"></span><label class="text-xs font-extrabold text-[#9b7187]">配置状态</label><strong class="mt-2 block text-3xl font-black" x-text="overview.configState"></strong><small class="font-bold text-lomu-600">当前加载数据库</small></article>
                    <article class="stat-card bg-gradient-to-br from-white to-[#f0eaff]"><span class="stat-dot"></span><label class="text-xs font-extrabold text-[#9b7187]">数据库表</label><strong class="mt-2 block text-3xl font-black" x-text="overview.tableCount"></strong><small class="font-bold text-lomu-600">当前 schema</small></article>
                    <article class="stat-card bg-gradient-to-br from-white to-[#ffe9f6]"><span class="stat-dot"></span><label class="text-xs font-extrabold text-[#9b7187]">数据库记录</label><strong class="mt-2 block text-3xl font-black" x-text="overview.rowCount"></strong><small class="font-bold text-lomu-600">数据记录</small></article>
                    <article class="stat-card bg-gradient-to-br from-white to-[#fff0e5]"><span class="stat-dot"></span><label class="text-xs font-extrabold text-[#9b7187]">服务端口</label><strong class="mt-2 block text-3xl font-black" x-text="overview.serverPort"></strong><small class="font-bold text-lomu-600">运行时端口</small></article>
                </div>
                <div class="mt-4 grid gap-4 xl:grid-cols-[1.2fr_.8fr]">
                    <div id="runtime-panel" class="panel min-h-72" hx-get="/admin/fragments/system" hx-trigger="load, every 15s, refresh-system from:body" hx-swap="innerHTML">
                        <div class="grid min-h-72 place-items-center text-sm text-[#9b7187]">正在读取运行状态</div>
                    </div>
                    <article class="panel">
                        <div class="panel-head"><h3 class="font-black text-lomu-900">组件状态</h3></div>
                        <div class="grid gap-4 p-5">
                            <div class="flex items-center justify-between border-b border-lomu-100 pb-3"><span>数据库</span><span class="pill" x-text="overview.database"></span></div>
                            <div class="flex items-center justify-between border-b border-lomu-100 pb-3"><span>AI 服务</span><span class="pill" x-text="overview.ai"></span></div>
                            <div class="flex items-center justify-between"><span>管理鉴权</span><span class="pill" x-text="overview.auth"></span></div>
                        </div>
                    </article>
                </div>
            </section>

            <section x-show="view === 'database'" x-cloak>
                <div class="mb-5 flex items-start justify-between"><div><h2 class="text-2xl font-black text-lomu-900">数据库</h2><p class="mt-1 text-sm text-[#9b7187]">分页查看所有业务表，点击记录进行修改。</p></div><button class="button" @click="loadTables()">刷新表</button></div>
                <div class="panel min-h-140 md:grid md:grid-cols-[220px_minmax(0,1fr)]">
                    <aside class="flex gap-2 overflow-auto border-b border-lomu-200 bg-gradient-to-b from-lomu-50 to-[#f8f0ff] p-3 md:block md:border-b-0 md:border-r">
                        <template x-for="item in tables" :key="item.name"><button class="mb-1 flex min-w-40 justify-between rounded-2xl px-3 py-2 text-left text-sm hover:bg-white" :class="table?.table === item.name && 'bg-white text-lomu-600 shadow'" @click="selectTable(item.name)"><span x-text="item.name"></span><small x-text="item.rowCount"></small></button></template>
                    </aside>
                    <div class="min-w-0"><div class="panel-head"><h3 class="font-black" x-text="table?.table || '选择数据表'"></h3><span class="pill" x-text="table ? table.total + ' rows' : '0 rows'"></span></div>
                        <div class="max-h-150 overflow-auto"><table class="w-full whitespace-nowrap text-left text-sm" x-show="table"><thead><tr><template x-for="column in table?.columns || []" :key="column.name"><th class="sticky top-0 bg-lomu-50 px-3 py-2 text-xs text-[#a15f80]"><span x-text="column.name"></span><small x-show="column.primaryKey"> (PK)</small></th></template></tr></thead><tbody><template x-for="(row,index) in table?.rows || []" :key="index"><tr class="cursor-pointer border-t border-lomu-100 hover:bg-lomu-50" @click="openEditor(row)"><template x-for="column in table.columns" :key="column.name"><td class="max-w-80 overflow-hidden text-ellipsis px-3 py-2" x-text="displayValue(row[column.name])"></td></template></tr></template></tbody></table><div class="grid min-h-60 place-items-center text-[#9b7187]" x-show="!table">请选择左侧数据表</div></div>
                        <div class="flex items-center justify-end gap-3 border-t border-lomu-100 p-3"><button class="button" :disabled="!table || table.offset === 0" @click="previousPage()">上一页</button><span x-text="pageLabel"></span><button class="button" :disabled="!table || table.offset + table.limit >= table.total" @click="nextPage()">下一页</button></div>
                    </div>
                </div>
            </section>

            <section x-show="view === 'exceptions'" x-cloak>
                <div class="mb-5 flex items-start justify-between gap-4">
                    <div><h2 class="text-2xl font-black text-lomu-900">异常日志</h2><p class="mt-1 text-sm text-[#9b7187]">显示最近 100 条异常，展开记录可查看上下文和完整堆栈追溯。</p></div>
                    <button class="button" @click="loadExceptions()">刷新</button>
                </div>
                <div class="grid gap-4" x-show="exceptions.length">
                    <template x-for="item in exceptions" :key="item.id">
                        <article class="panel">
                            <div class="panel-head">
                                <div class="min-w-0"><div class="flex items-center gap-2"><span class="pill" x-text="item.source"></span><strong class="truncate text-[#b83f68]" x-text="item.exceptionType"></strong></div><p class="mt-2 break-words text-sm" x-text="item.message || '无异常消息'"></p></div>
                                <time class="shrink-0 text-xs text-[#9b7187]" x-text="item.occurredAt.replace('T', ' ')"></time>
                            </div>
                            <details class="p-5">
                                <summary class="cursor-pointer font-bold text-lomu-600">查看详细追溯</summary>
                                <div class="mt-3 rounded-2xl border border-lomu-200 bg-white/80 p-4" x-show="item.context"><strong class="text-xs text-[#9b7187]">执行上下文</strong><pre class="mt-2 whitespace-pre-wrap break-words text-xs" x-text="item.context"></pre></div>
                                <pre class="mt-3 max-h-150 overflow-auto whitespace-pre-wrap break-words rounded-2xl border border-lomu-200 bg-white/80 p-4 font-mono text-xs" x-text="item.stackTrace"></pre>
                            </details>
                        </article>
                    </template>
                </div>
                <div class="panel grid min-h-40 place-items-center text-[#9b7187]" x-show="!exceptions.length">暂无异常记录</div>
            </section>

            <section x-show="view === 'plugins'" x-cloak>
                <div class="mb-5 flex items-start justify-between gap-4">
                    <div><h2 class="text-2xl font-black text-lomu-900">命令插件</h2><p class="mt-1 text-sm text-[#9b7187]">停用会立即从命令注册表移除；替换 plugins 目录中的 JAR 后可单独重载。</p></div>
                    <button class="button" @click="reloadAllPlugins()">扫描并重载</button>
                </div>
                <form class="panel plugin-upload" @submit.prevent="uploadPlugin($event.currentTarget)">
                    <label class="field"><span class="field-label">加载插件 JAR</span><input class="control" type="file" name="plugin" accept=".jar,application/java-archive" required></label>
                    <button class="button button-primary shrink-0">上传并加载</button>
                </form>
                <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                    <template x-for="plugin in plugins" :key="plugin.id">
                        <article class="panel p-5">
                            <div class="flex items-start justify-between gap-3"><div><h3 class="font-black text-lomu-900" x-text="plugin.name"></h3><p class="mt-1 font-mono text-xs text-[#9b7187]" x-text="plugin.id + ' · ' + plugin.version"></p></div><span class="pill" x-text="plugin.enabled ? '已启用' : '已停用'"></span></div>
                            <p class="mt-4 break-words text-sm" x-text="plugin.commands.join('、')"></p>
                            <p class="mt-2 truncate text-xs text-[#9b7187]" :title="plugin.source" x-text="plugin.external ? plugin.source : '内置模块'"></p>
                            <label class="field plugin-reply" x-show="plugin.id !== 'core'"><span class="field-label">停用命令时回复</span><textarea class="control min-h-28" maxlength="1000" x-model="plugin.disabledReply" placeholder="该命令当前已停用"></textarea></label>
                            <div class="plugin-actions"><button class="button" x-show="plugin.id !== 'core'" @click="setPluginEnabled(plugin)" x-text="plugin.enabled ? '停用' : '启用'"></button><button class="button" @click="reloadPlugin(plugin.id)">重载</button><button class="button" x-show="plugin.id !== 'core'" @click="savePluginReply(plugin)">保存回复</button></div>
                        </article>
                    </template>
                </div>
                <div class="panel grid min-h-40 place-items-center text-[#9b7187]" x-show="!plugins.length">暂无已加载插件</div>
            </section>

            <section x-show="view === 'command'" x-cloak>
                <div class="mb-5"><h2 class="text-2xl font-black text-lomu-900">Debug</h2><p class="mt-1 text-sm text-[#9b7187]">自动识别当前已启用的命令，只需选择命令并填写参数。</p></div>
                <article class="panel"><form class="grid items-end gap-3 p-5 md:grid-cols-2" @submit.prevent="runCommand($event.currentTarget)">
                    <label class="field"><span class="field-label">可用命令</span><select class="control" name="command" x-model="selectedCommand" required><option value="" disabled>请选择命令</option><template x-for="command in commands" :key="command.alias"><option :value="command.alias" x-text="'/' + command.alias + (command.parameters ? ' ' + command.parameters : '')"></option></template></select></label>
                    <label class="field"><span class="field-label">参数</span><input class="control" name="arguments" :disabled="!selectedCommandInfo()?.parameters" :placeholder="selectedCommandInfo()?.parameters || '该命令无需参数'"></label>
                    <div class="text-sm text-[#9b7187]"><strong class="block text-lomu-900" x-text="selectedCommandInfo()?.description || '暂无可用命令'"></strong><span x-text="selectedCommandInfo()?.example ? '示例：' + selectedCommandInfo().example : ''"></span></div>
                    <button class="button button-primary" :disabled="!selectedCommand">运行命令</button>
                </form></article>
                <div class="mt-4 min-h-40 rounded-3xl border border-dashed border-lomu-200 bg-white/80 p-5 backdrop-blur" x-show="!commandResult">等待运行命令。</div>
                <div class="mt-4 rounded-3xl border border-dashed border-lomu-200 bg-white/80 p-5 backdrop-blur" x-show="commandResult"><template x-for="(element,index) in commandResult?.elements || []" :key="index"><div><img class="mt-3 max-h-190 max-w-full rounded-3xl border-4 border-white shadow-xl" style="cursor:zoom-in" x-show="element.type === 'image' && element.imageUrl" :src="element.imageUrl" alt="命令结果预览，点击放大" @click="openImagePreview(element.imageUrl)"><pre class="whitespace-pre-wrap break-words" x-show="element.type !== 'image' || !element.imageUrl" x-text="element.text || element.raw || ''"></pre></div></template></div>
            </section>

            <section x-show="view === 'about'" x-cloak>
                <div class="mb-5"><h2 class="text-2xl font-black text-lomu-900">关于 EternalReturnLoMu</h2><p class="mt-1 text-sm text-[#9b7187]">永恒轮回战绩查询与机器人管理项目。</p></div>
                <div class="grid max-w-2xl gap-4">
                    <article class="panel p-5">
                        <div class="flex items-center gap-3"><div class="grid size-11 place-items-center rounded-2xl bg-white/80 text-lg font-black text-lomu-600">L♡</div><div><h3 class="font-black text-lomu-900">项目仓库</h3><p class="text-sm text-[#9b7187]">查看源码、提交反馈或参与开发。</p></div></div>
                        <a class="button button-primary mt-4" href="https://github.com/LuoRenMu/EternalReturnLoMu" target="_blank" rel="noopener noreferrer">GitHub · LuoRenMu/EternalReturnLoMu</a>
                    </article>
                    <article class="panel p-5">
                        <h3 class="font-black text-lomu-900">交流与反馈</h3>
                        <p class="mt-2 text-sm text-[#9b7187]">加入 QQ 群交流使用方法、问题反馈与项目动态。</p>
                        <div class="mt-4 rounded-2xl border border-lomu-200 bg-white/80 p-4"><span class="text-xs font-extrabold text-[#9b7187]">QQ 群</span><strong class="mt-1 block text-2xl font-black text-lomu-600">654087758</strong></div>
                    </article>
                </div>
            </section>
        </main>
    </div>
</div>

<div class="fixed inset-0 z-40 grid place-items-center bg-[#5b3046]/35 p-4 backdrop-blur-md" x-show="editOpen" x-cloak @click.self="editOpen = false">
    <form class="max-h-[90vh] w-full max-w-2xl overflow-auto rounded-3xl border border-lomu-200 bg-lomu-50 shadow-2xl" @submit.prevent="saveRow()"><div class="panel-head"><h3 class="font-black">编辑记录</h3><button type="button" class="button" @click="editOpen = false">关闭</button></div><div class="grid gap-3 p-5"><template x-for="column in table?.columns || []" :key="column.name"><label class="field"><span class="field-label" x-text="column.name + ' · ' + column.type + (column.primaryKey ? ' · 主键' : '')"></span><input class="control" :readonly="column.primaryKey || column.autoIncrement" :value="displayValue(editValues[column.name], '')" @input="editValues[column.name] = $event.target.value"></label></template><div class="flex justify-end gap-2"><button type="button" class="button" @click="editOpen = false">取消</button><button class="button button-primary">保存记录</button></div></div></form>
</div>
<div class="fixed inset-0 z-50 grid place-items-center bg-[#5b3046]/35 p-4 backdrop-blur-md" x-show="previewImageUrl" x-cloak @click.self="previewImageUrl = null" @keydown.escape.window="previewImageUrl = null">
    <button type="button" class="button absolute right-5" style="top:1.25rem" @click="previewImageUrl = null">关闭</button>
    <img class="max-h-[90vh] max-w-full rounded-3xl border-4 border-white shadow-xl" :src="previewImageUrl" alt="放大的命令结果">
</div>
<div class="fixed bottom-5 right-5 z-50 max-w-sm rounded-2xl px-4 py-3 text-sm font-bold text-white shadow-xl" x-show="toast.visible" x-transition x-cloak :class="toast.error ? 'bg-[#b83f68]' : 'bg-gradient-to-br from-lomu-500 to-[#9d72d4]'" x-text="toast.message"></div>
</body>
</html>
