/* QQ owns the published configuration. Loading this editor never publishes a local default. */
document.addEventListener("alpine:init", () => {
    Alpine.data("qqMenuPanelAdmin", () => ({
        available: false, status: "尚未连接", statusLoading: false, busy: false,
        feedback: "", feedbackError: false, tab: "menu", commands: [],
        menu: { items: [] }, menuVersion: null, menuLoaded: false, menuLoading: false, savedMenu: "",
        panels: [], listScope: "group", nextCursor: "", isEnd: true, panelsLoaded: false, panelLoading: false,
        listRequest: 0, detailRequest: 0, detailLoading: false,
        form: { id: "", scope: "group", target_type: "specific", targetText: "", remark: "", items: [] },
        selectedPanel: null, targetOp: "add", targetText: "", savedForm: "",
        panelEditor: "form", panelJson: "", savedPanelJson: "",
        confirmation: { message: "", resolve: null },

        async confirmChange(message) {
            if (this.confirmation.resolve) return false;
            this.confirmation.message = message;
            return new Promise(resolve => {
                this.confirmation.resolve = resolve;
                this.$refs.confirmDialog.showModal();
            });
        },
        answerConfirmation(answer) {
            const resolve = this.confirmation.resolve;
            this.confirmation.resolve = null;
            this.$refs.confirmDialog.close();
            if (resolve) resolve(answer);
        },

        async request(path, method = "GET", data) {
            const options = { method, credentials: "same-origin" };
            if (data !== undefined) {
                options.headers = { "Content-Type": "application/json" };
                options.body = JSON.stringify(data);
            }
            const response = await fetch("/api/admin/qq/" + path, options);
            let payload;
            try { payload = await response.json(); } catch { throw new Error("服务器返回无效响应，请重新登录或重试。"); }
            if (!response.ok || !payload.ok) throw new Error(payload.error || `HTTP ${response.status}`);
            return payload.data;
        },
        inform(message, error = false) { this.feedback = message; this.feedbackError = error; },
        async activate() {
            if (this.statusLoading) return;
            this.statusLoading = true;
            try {
                const status = await this.request("status");
                this.available = status.available;
                this.status = status.message;
                if (this.available) {
                    if (!this.savedForm) this.newPanel();
                    await Promise.all([
                        this.menuLoaded ? Promise.resolve() : this.loadMenu(true),
                        this.panelsLoaded ? Promise.resolve() : this.loadPanels(),
                        this.loadCommands(),
                    ]);
                }
            } catch (error) { this.available = false; this.status = error.message; this.inform(error.message, true); }
            finally { this.statusLoading = false; }
        },
        async loadCommands() {
            if (this.commands.length) return;
            try {
                const response = await fetch("/api/admin/commands", { credentials: "same-origin" });
                const data = await response.json();
                if (response.ok && data.ok) this.commands = data.data;
            } catch { /* The optional catalog must not prevent manually editing QQ commands. */ }
        },
        newMenuItem(sub = false) {
            const item = { name: "", type: "send_message", send_message: "", link: "" };
            if (!sub) { item.switch = { switch_id: "", default: false }; item.sub_menu_items = []; }
            return item;
        },
        textWidth(value) { return Array.from(value || "").reduce((width, char) => width + (char.codePointAt(0) <= 0x7f ? 1 : 2), 0); },
        validateTextWidth(value, limit, label, required = true) {
            if (required && !(value || "").trim()) throw new Error(label + "不能为空。");
            const width = this.textWidth(value);
            if (width > limit) throw new Error(`${label}显示宽度为 ${width}/${limit}，超出 QQ 限制；ASCII 算 1，中文等非 ASCII 字符算 2。`);
        },
        validateMenu() {
            if (this.menu.items.length > 10) throw new Error("QQ 最多支持 10 个一级菜单。");
            this.menu.items.forEach((item, index) => {
                this.validateTextWidth(item.name, 10, `第 ${index + 1} 个一级菜单名称`);
                if (item.type === "menu") {
                    const children = item.sub_menu_items || [];
                    if (!children.length || children.length > 5) throw new Error("二级菜单必须包含 1–5 个菜单项。");
                    children.forEach((child, childIndex) => this.validateTextWidth(child.name, 14, `第 ${index + 1} 个菜单的第 ${childIndex + 1} 个二级菜单名称`));
                }
            });
        },
        jsonObject(value, keys, label) {
            if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error(label + "必须为 JSON 对象。");
            const unknown = Object.keys(value).find(key => !keys.includes(key));
            if (unknown) throw new Error(`${label}包含不支持的字段：${unknown}。`);
        },
        validatePanel(panel = this.panelPayload()) {
            this.jsonObject(panel, ["items", "remark", "version"], "panel");
            if (!Array.isArray(panel.items) || !panel.items.length || panel.items.length > 20) throw new Error("指令面板必须包含 1–20 个元素。");
            if (panel.remark != null && (typeof panel.remark !== "string" || panel.remark.length > 255)) throw new Error("面板备注必须为最多 255 个字符的字符串。");
            if (panel.version != null && (!Number.isInteger(panel.version) || panel.version < -2147483648 || panel.version > 2147483647)) throw new Error("面板版本必须为 32 位整数。");
            panel.items.forEach((item, index) => {
                this.jsonObject(item, ["name", "desc", "type", "only_admin", "link"], `面板第 ${index + 1} 项`);
                if (typeof item.name !== "string" || (item.desc != null && typeof item.desc !== "string")) throw new Error(`面板第 ${index + 1} 项名称／描述必须为字符串。`);
                if (item.type !== undefined && !["command", "link"].includes(item.type)) throw new Error("面板元素类型必须为 command 或 link。");
                if (item.only_admin !== undefined && typeof item.only_admin !== "boolean") throw new Error("only_admin 必须为布尔值。");
                if (item.link != null && typeof item.link !== "string") throw new Error("link 必须为字符串。");
                this.validateTextWidth(item.name, 14, `面板第 ${index + 1} 项名称`);
                this.validateTextWidth(item.desc, 30, `面板第 ${index + 1} 项描述`, false);
            });
        },
        menuAction(item, sub = false) {
            const value = { name: item.name, type: item.type };
            if (item.type === "send_message") value.send_message = item.send_message || "";
            if (item.type === "link") value.link = item.link || "";
            if (!sub && item.type === "switch") value.switch = item.switch || { switch_id: "", default: false };
            if (!sub && item.type === "menu") value.sub_menu_items = (item.sub_menu_items || []).map(child => this.menuAction(child, true));
            return value;
        },
        menuPayload() { return { items: this.menu.items.map(item => this.menuAction(item)) }; },
        menuDirty() { return this.menuLoaded && JSON.stringify(this.menuPayload()) !== this.savedMenu; },
        normalizeMenu(menu) {
            return { items: (menu?.items || []).map(item => ({ ...this.newMenuItem(), ...item,
                switch: item.switch || { switch_id: "", default: false },
                sub_menu_items: (item.sub_menu_items || []).map(child => ({ ...this.newMenuItem(true), ...child })),
            })) };
        },
        move(items, index, step) {
            const target = index + step;
            if (target < 0 || target >= items.length) return;
            const [item] = items.splice(index, 1); items.splice(target, 0, item);
        },
        async loadMenu(initial = false) {
            if (this.menuLoading || this.busy) return;
            if (!initial && this.menuDirty() && !await this.confirmChange("重新读取会丢弃未提交的菜单修改，继续吗？")) return;
            this.menuLoading = true;
            try {
                const snapshot = await this.request("menu");
                this.menu = this.normalizeMenu(snapshot.menu);
                this.menuVersion = snapshot.version;
                this.menuLoaded = true;
                this.savedMenu = JSON.stringify(this.menuPayload());
            } catch (error) { this.inform(error.message, true); }
            finally { this.menuLoading = false; }
        },
        async saveMenu(clear = false) {
            if (this.busy || !this.menuLoaded) return;
            try { if (!clear) this.validateMenu(); }
            catch (error) { this.inform(error.message, true); return; }
            if (!await this.confirmChange(clear ? "确认清空 QQ 线上全部 C2C 自定义菜单？此操作不能直接撤销。" : "确认用当前配置整体覆盖 QQ 线上 C2C 自定义菜单？")) return;
            this.busy = true;
            try {
                const result = await this.request("menu", "PUT", { menu: clear ? null : this.menuPayload(), confirmed: true });
                if (clear) this.menu = { items: [] };
                this.menuVersion = result.version;
                this.savedMenu = JSON.stringify(this.menuPayload());
                this.inform(`菜单已提交至 QQ，版本 ${result.version}。客户端展示可能有缓存延迟。`);
            } catch (error) { this.inform(error.message, true); }
            finally { this.busy = false; }
        },
        newPanelItem() { return { name: "", desc: "", type: "command", only_admin: false, link: "" }; },
        panelPayload() {
            return { remark: this.form.remark || "", items: this.form.items.map(item => {
                const value = { name: item.name, desc: item.desc || "", type: item.type, only_admin: !!item.only_admin };
                if (item.type === "link") value.link = item.link || "";
                return value;
            }) };
        },
        scopeChanged() {
            if (!["group", "c2c"].includes(this.form.scope)) this.form.target_type = "all";
        },
        validateIds(ids) {
            if (!Array.isArray(ids) || !ids.length || ids.length > 20 || new Set(ids).size !== ids.length || ids.some(id => !/^[a-fA-F0-9]{32}$/.test(id))) {
                throw new Error("请填写 1–20 个不重复的 32 位 OpenID，可用换行、逗号或空格分隔。");
            }
            return ids;
        },
        ids(text) { return this.validateIds(text.split(/[\s,，;；]+/u).filter(Boolean)); },
        createPayload() {
            const value = { scope: this.form.scope, target_type: this.form.target_type, panel: this.panelPayload() };
            if (value.target_type === "specific") {
                value[value.scope === "group" ? "group_openids" : "user_openids"] = this.ids(this.form.targetText);
            }
            return value;
        },
        panelEditorPayload() {
            const value = { panel: this.panelPayload() };
            if (!this.form.id) {
                value.scope = this.form.scope;
                value.target_type = this.form.target_type;
                if (value.target_type === "specific") value[value.scope === "group" ? "group_openids" : "user_openids"] = this.form.targetText.split(/[\s,，;；]+/u).filter(Boolean);
            }
            return value;
        },
        parsePanelJson() {
            let value;
            try { value = JSON.parse(this.panelJson); }
            catch { throw new Error("JSON 语法不正确，请检查引号、逗号和括号。"); }
            this.jsonObject(value, this.form.id ? ["panel"] : ["scope", "target_type", "panel", "user_openids", "group_openids"], "请求 JSON");
            this.validatePanel(value.panel);
            if (!this.form.id) {
                if (!["group", "c2c", "channel", "dm"].includes(value.scope)) throw new Error("scope 必须为 group、c2c、channel 或 dm。");
                if (!["all", "specific"].includes(value.target_type)) throw new Error("target_type 必须为 all 或 specific。");
                for (const key of ["user_openids", "group_openids"]) {
                    if (value[key] != null && (!Array.isArray(value[key]) || value[key].some(id => typeof id !== "string"))) throw new Error(key + " 必须为字符串数组。");
                }
                if (value.target_type === "specific") {
                    if (!["group", "c2c"].includes(value.scope)) throw new Error("频道和频道私信只支持全部生效。");
                    const key = value.scope === "group" ? "group_openids" : "user_openids";
                    const other = value.scope === "group" ? "user_openids" : "group_openids";
                    if (value[other]?.length) throw new Error("群聊只能传群 OpenID，单聊只能传用户 OpenID。");
                    this.validateIds(value[key]);
                } else if (value.user_openids?.length || value.group_openids?.length) throw new Error("全部生效时不能指定 OpenID。");
            }
            return value;
        },
        applyPanelInput(value) {
            if (value.scope !== undefined) {
                this.form.scope = value.scope; this.form.target_type = value.target_type;
                this.form.targetText = (value[value.scope === "group" ? "group_openids" : "user_openids"] || []).join("\n");
            }
            this.form.remark = value.panel.remark || "";
            this.form.items = value.panel.items.map(item => ({ ...this.newPanelItem(), ...item }));
        },
        setPanelEditor(mode) {
            if (this.busy || this.detailLoading || mode === this.panelEditor) return;
            try {
                if (mode === "json") {
                    this.panelJson = JSON.stringify(this.panelEditorPayload(), null, 2);
                    this.savedPanelJson = this.panelJson;
                } else if (mode === "form") this.applyPanelInput(this.parsePanelJson());
                else return;
                this.panelEditor = mode;
            } catch (error) { this.inform(error.message, true); }
        },
        formatPanelJson() {
            if (this.busy || this.detailLoading) return;
            try { this.panelJson = JSON.stringify(JSON.parse(this.panelJson), null, 2); this.inform("JSON 已格式化，尚未提交。"); }
            catch { this.inform("JSON 语法不正确，请检查引号、逗号和括号。", true); }
        },
        resetPanelEditor() { this.panelEditor = "form"; this.panelJson = ""; this.savedPanelJson = ""; },
        formDirty() { return this.savedForm && (JSON.stringify(this.form) !== this.savedForm || (this.panelEditor === "json" && this.panelJson !== this.savedPanelJson)); },
        async newPanel() {
            if (this.busy || this.detailLoading || (this.formDirty() && !await this.confirmChange("丢弃当前未提交的面板修改？"))) return;
            this.detailRequest++;
            this.selectedPanel = null;
            this.form = { id: "", scope: this.listScope, target_type: ["group", "c2c"].includes(this.listScope) ? "specific" : "all",
                targetText: "", remark: "", items: [this.newPanelItem()] };
            this.targetText = "";
            this.resetPanelEditor();
            this.savedForm = JSON.stringify(this.form);
        },
        async loadPanels(more = false) {
            if (this.busy || (more && (this.panelLoading || this.isEnd || !this.nextCursor))) return;
            const request = ++this.listRequest;
            const scope = this.listScope;
            this.panelLoading = true;
            if (!more) { this.panels = []; this.nextCursor = ""; this.isEnd = true; this.panelsLoaded = false; }
            try {
                const query = new URLSearchParams({ scope, limit: "20" });
                if (more) query.set("cursor", this.nextCursor);
                const page = await this.request("panels?" + query);
                if (request !== this.listRequest || scope !== this.listScope) return;
                this.panels = Array.from(new Map((more ? [...this.panels, ...page.records] : page.records).map(p => [p.panel_id, p])).values());
                this.nextCursor = page.next_cursor;
                this.isEnd = page.is_end || !page.next_cursor;
                this.panelsLoaded = true;
            } catch (error) { if (request === this.listRequest) this.inform(error.message, true); }
            finally { if (request === this.listRequest) this.panelLoading = false; }
        },
        async editPanel(id, discard = false) {
            if (this.busy || (!discard && this.formDirty() && !await this.confirmChange("丢弃当前未提交的面板修改？"))) return;
            const request = ++this.detailRequest;
            this.detailLoading = true;
            try {
                const record = await this.request("panels/" + encodeURIComponent(id));
                if (request !== this.detailRequest) return;
                this.selectedPanel = record;
                this.form = { id: record.panel_id, scope: record.scope, target_type: record.target_type,
                    targetText: "", remark: record.panel.remark || "",
                    items: record.panel.items.map(item => ({ ...this.newPanelItem(), ...item })),
                };
                this.targetText = "";
                this.resetPanelEditor();
                this.savedForm = JSON.stringify(this.form);
            } catch (error) { if (request === this.detailRequest) this.inform(error.message, true); }
            finally { if (request === this.detailRequest) this.detailLoading = false; }
        },
        async savePanel() {
            if (this.busy || this.detailLoading) return;
            let configuration, panel, manual;
            try {
                if (this.panelEditor === "json") {
                    manual = this.parsePanelJson(); panel = manual.panel;
                    if (!this.form.id) configuration = manual;
                } else {
                    this.validatePanel(); panel = this.panelPayload();
                    if (!this.form.id) configuration = this.createPayload();
                }
            }
            catch (error) { this.inform(error.message, true); return; }
            const audience = configuration?.target_type === "all" ? "该场景下的全部对象" : "指定对象";
            if (!await this.confirmChange(this.form.id ? `确认整体覆盖面板 ${this.form.id} 的元素及备注？关联对象不会被改变。` : `确认创建对${audience}生效的 ${configuration.scope} 面板？`)) return;
            this.busy = true;
            let id = this.form.id;
            try {
                if (id) await this.request("panels/" + encodeURIComponent(id), "PUT", { panel, confirmed: true });
                else {
                    const created = await this.request("panels", "POST", { configuration, confirmed: true });
                    id = created.panel_id;
                    // Retain the returned ID even if the subsequent refresh fails. Never create twice blindly.
                    this.form.id = id;
                }
                if (manual) {
                    this.applyPanelInput(manual);
                    // A successful creation is now an update draft, even when the follow-up read fails.
                    this.panelJson = JSON.stringify({ panel }, null, 2);
                    this.savedPanelJson = this.panelJson;
                }
                this.savedForm = JSON.stringify(this.form);
                this.inform(`面板已提交至 QQ：${id}`);
            } catch (error) { this.inform(error.message, true); return; }
            finally { this.busy = false; }
            await Promise.all([this.editPanel(id, true), this.loadPanels()]);
        },
        async updateTargets() {
            if (this.busy || !this.selectedPanel || this.selectedPanel.target_type !== "specific") return;
            let ids;
            try { ids = this.ids(this.targetText); } catch (error) { this.inform(error.message, true); return; }
            if (!await this.confirmChange(`确认${this.targetOp === "add" ? "增加" : "移除"}这 ${ids.length} 个面板关联对象？`)) return;
            const id = this.selectedPanel.panel_id;
            const targets = { op: this.targetOp };
            targets[this.selectedPanel.scope === "group" ? "group_openids" : "user_openids"] = ids;
            this.busy = true;
            try {
                await this.request("panels/" + encodeURIComponent(id) + "/target", "PUT", { targets, confirmed: true });
                this.targetText = "";
                this.inform("关联对象已提交至 QQ。");
            } catch (error) { this.inform(error.message, true); return; }
            finally { this.busy = false; }
            // Do not discard an unsaved content draft just to refresh its association metadata.
            try {
                const record = await this.request("panels/" + encodeURIComponent(id));
                if (this.form.id === id) this.selectedPanel = record;
            }
            catch (error) { this.inform(error.message, true); }
        },
        async deletePanel() {
            if (this.busy || !this.form.id || !await this.confirmChange(`确认删除 QQ 线上面板 ${this.form.id}？此操作不能直接撤销。`)) return;
            this.busy = true;
            try {
                await this.request("panels/" + encodeURIComponent(this.form.id), "DELETE", { confirmed: true });
                this.savedForm = "";
                this.form.id = "";
                this.selectedPanel = null;
                this.inform("面板已从 QQ 删除。");
            } catch (error) { this.inform(error.message, true); return; }
            finally { this.busy = false; }
            this.newPanel(); await this.loadPanels();
        },
    }));
});
