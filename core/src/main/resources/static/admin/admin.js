let runtimeClockTimer = null;

function formatRuntimeDuration(milliseconds) {
    const seconds = Math.max(0, Math.floor(milliseconds / 1000));
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const remainder = seconds % 60;
    return [
        days > 0 ? `${days}天` : null,
        days > 0 || hours > 0 ? `${hours}时` : null,
        days > 0 || hours > 0 || minutes > 0 ? `${minutes}分` : null,
        `${remainder}秒`,
    ].filter(Boolean).join(" ");
}

function formatRuntimeDate(milliseconds) {
    return new Date(milliseconds).toLocaleString("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
    }).replaceAll("/", "-");
}

function startRuntimeClock(root = document) {
    const clock = root.matches?.(".runtime-clock") ? root : root.querySelector?.(".runtime-clock");
    if (!clock) return;
    clearInterval(runtimeClockTimer);
    const fetchedAt = Date.now();
    const serverTime = Number(clock.dataset.serverTimeMillis);
    const uptime = Number(clock.dataset.uptimeMillis);
    const render = () => {
        const elapsed = Date.now() - fetchedAt;
        const serverTimeElement = clock.querySelector("[data-runtime-server-time]");
        const uptimeElement = clock.querySelector("[data-runtime-uptime]");
        if (serverTimeElement) serverTimeElement.textContent = formatRuntimeDate(serverTime + elapsed);
        if (uptimeElement) uptimeElement.textContent = formatRuntimeDuration(uptime + elapsed);
    };
    render();
    runtimeClockTimer = setInterval(render, 1000);
}

document.addEventListener("htmx:afterSwap", (event) => startRuntimeClock(event.detail.target));

document.addEventListener("alpine:init", () => {
    Alpine.data("adminDashboard", () => ({
        view: "overview",
        pageTitle: "系统概览",
        overview: {
            configState: "—",
            tableCount: "—",
            rowCount: "—",
            serverPort: "—",
            database: "未连接",
            ai: "未配置",
            auth: "已保护",
        },
        tables: [],
        exceptions: [],
        plugins: [],
        commands: [],
        selectedCommand: "",
        table: null,
        offset: 0,
        limit: 50,
        editOpen: false,
        editRow: null,
        editValues: {},
        commandResult: null,
        previewImageUrl: null,
        toast: { visible: false, message: "", error: false, timer: null },

        async init() {
            await this.refreshOverview();
        },

        async api(url, options = {}) {
            const response = await fetch(url, options);
            let payload;
            try {
                payload = await response.json();
            } catch {
                throw new Error("服务器返回了无效响应");
            }
            if (!response.ok || !payload.ok) {
                throw new Error(payload.error || `HTTP ${response.status}`);
            }
            return payload.data;
        },

        notify(message, error = false) {
            clearTimeout(this.toast.timer);
            this.toast = { visible: true, message, error, timer: null };
            this.toast.timer = setTimeout(() => {
                this.toast.visible = false;
            }, 3200);
        },

        showView(view, title) {
            this.view = view;
            this.pageTitle = title;
            if (view === "database") this.loadTables();
            if (view === "exceptions") this.loadExceptions();
            if (view === "plugins") this.loadPlugins();
            if (view === "command") this.loadCommands();
        },

        async loadCommands() {
            try {
                this.commands = await this.api("/api/admin/commands");
                if (!this.commands.some((command) => command.alias === this.selectedCommand)) {
                    this.selectedCommand = this.commands[0]?.alias || "";
                }
            } catch (error) {
                this.commands = [];
                this.selectedCommand = "";
                this.notify(error.message, true);
            }
        },

        selectedCommandInfo() {
            return this.commands.find((command) => command.alias === this.selectedCommand) || null;
        },

        openImagePreview(url) {
            if (url) this.previewImageUrl = url;
        },

        async refreshOverview() {
            try {
                const config = await this.api("/api/admin/config");
                const sqlite = config.databaseBackend === "SQLite";
                Object.assign(this.overview, {
                    configState: config.databaseBackend || "未加载",
                    serverPort: config.runtimePort,
                    database: sqlite ? "SQLite" : "PostgreSQL",
                    ai: config.ai.apiKey ? "已配置" : "未配置",
                    auth: "已保护",
                });
                try {
                    const tables = await this.api("/api/admin/database/tables");
                    this.overview.tableCount = tables.length;
                    this.overview.rowCount = tables.reduce((total, table) => total + table.rowCount, 0);
                } catch {
                    this.overview.tableCount = "—";
                    this.overview.rowCount = "—";
                }
                if (window.htmx) htmx.trigger(document.body, "refresh-system");
            } catch (error) {
                this.notify(error.message, true);
            }
        },

        async loadTables() {
            try {
                this.tables = await this.api("/api/admin/database/tables");
                if (this.table && this.tables.some((item) => item.name === this.table.table)) {
                    await this.selectTable(this.table.table, this.offset);
                }
            } catch (error) {
                this.tables = [];
                this.notify(error.message, true);
            }
        },

        async loadExceptions() {
            try {
                this.exceptions = await this.api("/api/admin/exceptions?limit=100");
            } catch (error) {
                this.exceptions = [];
                this.notify(error.message, true);
            }
        },

        async loadPlugins() {
            try {
                this.plugins = await this.api("/api/admin/plugins");
            } catch (error) {
                this.plugins = [];
                this.notify(error.message, true);
            }
        },

        async setPluginEnabled(plugin) {
            const action = plugin.enabled ? "disable" : "enable";
            try {
                await this.api(`/api/admin/plugins/${encodeURIComponent(plugin.id)}/${action}`, { method: "POST" });
                await this.loadPlugins();
                this.notify(plugin.enabled ? "插件已停用" : "插件已启用");
            } catch (error) {
                this.notify(error.message, true);
            }
        },

        async savePluginReply(plugin) {
            try {
                await this.api(`/api/admin/plugin-disabled-replies/${encodeURIComponent(plugin.id)}`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ reply: plugin.disabledReply || "" }),
                });
                this.notify("停用回复已保存");
            } catch (error) {
                this.notify(error.message, true);
            }
        },

        async uploadPlugin(form) {
            const data = new FormData(form);
            try {
                await this.api("/api/admin/plugins/upload", { method: "POST", body: data });
                form.reset();
                await this.loadPlugins();
                this.notify("插件已上传并加载");
            } catch (error) {
                this.notify(error.message, true);
            }
        },

        async reloadPlugin(id) {
            try {
                await this.api(`/api/admin/plugins/${encodeURIComponent(id)}/reload`, { method: "POST" });
                await this.loadPlugins();
                this.notify("插件已重载");
            } catch (error) {
                this.notify(error.message, true);
            }
        },

        async reloadAllPlugins() {
            try {
                this.plugins = await this.api("/api/admin/plugins/reload", { method: "POST" });
                this.notify("插件目录已重新扫描");
            } catch (error) {
                this.notify(error.message, true);
            }
        },

        async selectTable(name, offset = 0) {
            try {
                this.offset = offset;
                this.table = await this.api(`/api/admin/database/tables/${encodeURIComponent(name)}?limit=${this.limit}&offset=${offset}`);
            } catch (error) {
                this.notify(error.message, true);
            }
        },

        get pageLabel() {
            if (!this.table) return "—";
            const page = Math.floor(this.table.offset / this.table.limit) + 1;
            const pages = Math.max(1, Math.ceil(this.table.total / this.table.limit));
            return `${page} / ${pages}`;
        },

        previousPage() {
            if (this.table) this.selectTable(this.table.table, Math.max(0, this.offset - this.limit));
        },

        nextPage() {
            if (this.table) this.selectTable(this.table.table, this.offset + this.limit);
        },

        displayValue(value, nullText = "NULL") {
            return value === null || value === undefined ? nullText : String(value);
        },

        openEditor(row) {
            this.editRow = row;
            this.editValues = { ...row };
            this.editOpen = true;
        },

        async saveRow() {
            if (!this.table || !this.editRow) return;
            const keys = {};
            const values = {};
            for (const column of this.table.columns) {
                if (column.primaryKey) {
                    keys[column.name] = this.editRow[column.name];
                } else if (!column.autoIncrement) {
                    const value = this.editValues[column.name];
                    values[column.name] = value === "" && column.nullable ? null : value;
                }
            }
            try {
                await this.api(`/api/admin/database/tables/${encodeURIComponent(this.table.table)}/row`, {
                    method: "PUT",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ keys, values }),
                });
                this.editOpen = false;
                this.notify("记录已更新");
                await this.selectTable(this.table.table, this.offset);
            } catch (error) {
                this.notify(error.message, true);
            }
        },

        async runCommand(form) {
            this.commandResult = { matched: true, elements: [{ type: "text", text: "运行中…" }] };
            try {
                const fields = Object.fromEntries(new FormData(form));
                const command = String(fields.command || "").trim();
                const argumentsText = String(fields.arguments || "").trim();
                const plainText = `/${command}${argumentsText ? ` ${argumentsText}` : ""}`;
                const result = await this.api("/api/admin/test-command", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ plainText }),
                });
                this.commandResult = result.matched
                    ? result
                    : { matched: false, elements: [{ type: "text", text: "命令未匹配" }] };
            } catch (error) {
                this.commandResult = { matched: false, elements: [{ type: "text", text: error.message }] };
                this.notify(error.message, true);
            }
        },
    }));
});
