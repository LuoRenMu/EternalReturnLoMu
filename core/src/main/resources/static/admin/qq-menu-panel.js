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
        ids(text) {
            const ids = text.split(/[\s,，;；]+/u).filter(Boolean);
            if (!ids.length || ids.length > 20 || new Set(ids).size !== ids.length || ids.some(id => !/^[a-fA-F0-9]{32}$/.test(id))) {
                throw new Error("请填写 1–20 个不重复的 32 位 OpenID，可用换行、逗号或空格分隔。");
            }
            return ids;
        },
        createPayload() {
            const value = { scope: this.form.scope, target_type: this.form.target_type, panel: this.panelPayload() };
            if (value.target_type === "specific") {
                value[value.scope === "group" ? "group_openids" : "user_openids"] = this.ids(this.form.targetText);
            }
            return value;
        },
        formDirty() { return this.savedForm && JSON.stringify(this.form) !== this.savedForm; },
        async newPanel() {
            if (this.busy || this.detailLoading || (this.formDirty() && !await this.confirmChange("丢弃当前未提交的面板修改？"))) return;
            this.detailRequest++;
            this.selectedPanel = null;
            this.form = { id: "", scope: this.listScope, target_type: ["group", "c2c"].includes(this.listScope) ? "specific" : "all",
                targetText: "", remark: "", items: [this.newPanelItem()] };
            this.targetText = "";
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
                this.savedForm = JSON.stringify(this.form);
            } catch (error) { if (request === this.detailRequest) this.inform(error.message, true); }
            finally { if (request === this.detailRequest) this.detailLoading = false; }
        },
        async savePanel() {
            if (this.busy || this.detailLoading) return;
            let configuration;
            try { if (!this.form.id) configuration = this.createPayload(); }
            catch (error) { this.inform(error.message, true); return; }
            const audience = this.form.target_type === "all" ? "该场景下的全部对象" : "指定对象";
            if (!await this.confirmChange(this.form.id ? `确认整体覆盖面板 ${this.form.id} 的元素及备注？关联对象不会被改变。` : `确认创建对${audience}生效的 ${this.form.scope} 面板？`)) return;
            this.busy = true;
            let id = this.form.id;
            try {
                if (id) await this.request("panels/" + encodeURIComponent(id), "PUT", { panel: this.panelPayload(), confirmed: true });
                else {
                    const created = await this.request("panels", "POST", { configuration, confirmed: true });
                    id = created.panel_id;
                    // Retain the returned ID even if the subsequent refresh fails. Never create twice blindly.
                    this.form.id = id;
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
