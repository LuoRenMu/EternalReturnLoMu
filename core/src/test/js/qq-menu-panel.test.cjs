const test = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const path = require("node:path");
const source = fs.readFileSync(path.resolve(__dirname, "../../main/resources/static/admin/qq-menu-panel.js"), "utf8");

function editor(confirm = () => true) {
    let factory;
    const context = { document: { addEventListener: (_, listener) => listener() },
        Alpine: { data: (_, value) => { factory = value; } }, window: { confirm }, URLSearchParams,
        fetch: () => { throw Error("No automatic requests expected"); } };
    vm.runInNewContext(source, context);
    const state = factory();
    if (typeof confirm === "function") state.confirmChange = async () => confirm();
    state.available = true;
    return state;
}

test("oversized QQ text and menus are blocked before confirmation or sending", async () => {
    const state = editor(() => { throw Error("invalid configuration must not ask to publish"); });
    state.request = () => { throw Error("invalid configuration must not reach QQ"); };
    state.newPanel(); state.form.target_type = "all";
    for (const id of ["", "p1"]) {
        state.form.id = id;
        for (const value of [{ name: "查".repeat(8) }, { name: "/" + "查".repeat(7) },
            { name: "help", desc: "查".repeat(16) }, { name: "한".repeat(8) }, { name: "😀".repeat(7) + "a" }]) {
            state.form.items = [{ ...state.newPanelItem(), ...value }];
            await state.savePanel();
            assert.equal(state.feedbackError, true);
            assert.match(state.feedback, /显示宽度/);
        }
    }
    state.menuLoaded = true;
    const item = { ...state.newMenuItem(), name: "帮助", send_message: "/帮助" };
    for (const items of [Array.from({ length: 11 }, () => item), [{ ...item, name: "查".repeat(6) }],
        [{ ...item, type: "menu", sub_menu_items: Array.from({ length: 6 }, () => item) }],
        [{ ...item, type: "menu", sub_menu_items: [{ ...item, name: "查".repeat(8) }] }]]) {
        state.menu.items = items;
        await state.saveMenu();
        assert.equal(state.feedbackError, true);
        assert.match(state.feedback, /显示宽度|10|5/);
    }
});

test("QQ width counts Unicode code points and accepts exact mixed text boundaries", () => {
    const state = editor();
    assert.equal(state.textWidth("/查询한😀"), 9);
    assert.equal(state.textWidth("a".repeat(14)), 14);
    state.newPanel(); state.form.target_type = "all";
    for (const item of [{ name: "查".repeat(7), desc: "查".repeat(15) }, { name: "/" + "查".repeat(6) + "a" },
        { name: "😀".repeat(7) }, { name: "a".repeat(14), desc: "a".repeat(30) }]) {
        state.form.items = [{ ...state.newPanelItem(), ...item }];
        assert.doesNotThrow(() => state.validatePanel());
    }
});

test("initialization never publishes and panel defaults are specific rather than global", () => {
    const state = editor();
    assert.equal(state.menuLoaded, false);
    state.newPanel();
    assert.equal(state.form.target_type, "specific");
    assert.throws(() => state.createPayload(), /OpenID/);
    state.form.targetText = "A".repeat(32);
    assert.equal(state.createPayload().group_openids[0], "A".repeat(32));
    assert.equal(state.createPayload().user_openids, undefined);
    state.form.scope = "dm"; state.scopeChanged();
    assert.equal(state.form.target_type, "all");
    assert.equal(state.createPayload().group_openids, undefined);
});

test("changing action types strips irrelevant fields and clear transmits explicit null", async () => {
    const state = editor();
    state.menuLoaded = true;
    state.menu.items.push({ ...state.newMenuItem(), name: "help", type: "link", link: "https://example.com", send_message: "old" });
    const item = state.menuPayload().items[0];
    assert.equal(item.send_message, undefined);
    assert.equal(item.switch, undefined);
    assert.equal(item.link, "https://example.com");
    let body;
    state.request = async (_, method, data) => { assert.equal(method, "PUT"); body = data; return { version: 2 }; };
    await state.saveMenu(true);
    assert.equal(body.menu, null); assert.equal(body.confirmed, true);
    assert.equal(state.menu.items.length, 0);
});

test("cancelling destructive confirmation never issues a request", async () => {
    const state = editor(() => false);
    state.menuLoaded = true; state.form.id = "p1";
    state.request = () => { throw Error("must not be called"); };
    await state.saveMenu(true); await state.deletePanel();
    assert.equal(state.form.id, "p1");
});

test("a created ID survives readback failure and subsequent submit updates instead of creating twice", async () => {
    const state = editor(); state.newPanel();
    state.form.targetText = "A".repeat(32); state.form.items[0].name = "/帮助";
    const writes = [];
    state.request = async (path, method = "GET") => {
        if (method === "POST") { writes.push(method); return { panel_id: "created-1" }; }
        if (method === "PUT") { writes.push(method); assert.equal(path, "panels/created-1"); return { version: 2 }; }
        throw Error("readback unavailable");
    };
    await state.savePanel();
    assert.equal(state.form.id, "created-1");
    await state.savePanel();
    assert.deepEqual(writes, ["POST", "PUT"]);
});

test("out of order scoped list responses cannot overwrite the latest selection", async () => {
    const state = editor();
    const pending = [];
    state.request = () => new Promise(resolve => pending.push(resolve));
    const first = state.loadPanels();
    state.listScope = "c2c";
    const second = state.loadPanels();
    pending[1]({ records: [{ panel_id: "c2c" }], is_end: true, next_cursor: "" }); await second;
    pending[0]({ records: [{ panel_id: "group" }], is_end: false, next_cursor: "old" }); await first;
    assert.equal(state.panels[0].panel_id, "c2c");
    assert.equal(state.isEnd, true);
});

test("the in-page confirmation resolves once and rejects overlapping changes", async () => {
    const state = editor(null);
    let opens = 0; let closes = 0;
    state.$refs = { confirmDialog: { showModal: () => opens++, close: () => closes++ } };
    const first = state.confirmChange("replace menu");
    assert.equal(await state.confirmChange("second mutation"), false);
    assert.equal(state.confirmation.message, "replace menu");
    state.answerConfirmation(false);
    assert.equal(await first, false);
    assert.equal(opens, 1); assert.equal(closes, 1);
});

test("manual JSON creates the supplied configuration and keeps the returned ID on readback failure", async () => {
    const state = editor(); state.newPanel();
    state.setPanelEditor("json");
    assert.equal(JSON.parse(state.panelJson).scope, "group");
    assert.deepEqual(JSON.parse(state.panelJson).group_openids, []);
    const configuration = { scope: "c2c", target_type: "specific", user_openids: ["B".repeat(32)],
        panel: { items: [{ name: "/帮助", desc: "命令列表", type: "command", only_admin: true }], remark: "手动 JSON" } };
    state.panelJson = JSON.stringify(configuration);
    const calls = [];
    state.request = async (path, method = "GET", data) => {
        if (method === "POST") { calls.push({ path, method, data: JSON.parse(JSON.stringify(data)) }); return { panel_id: "json-created" }; }
        if (method === "PUT") { calls.push({ path, method, data: JSON.parse(JSON.stringify(data)) }); return { version: 2 }; }
        throw Error("readback unavailable");
    };
    await state.savePanel();
    assert.deepEqual(calls[0], { path: "panels", method: "POST", data: { configuration, confirmed: true } });
    assert.equal(state.form.id, "json-created");
    assert.equal(state.form.scope, "c2c");
    assert.equal(state.panelEditor, "json");
    assert.deepEqual(JSON.parse(state.panelJson), { panel: configuration.panel });
    await state.savePanel();
    assert.equal(calls.length, 2);
    assert.equal(calls[1].method, "PUT");
    assert.equal(calls[1].path, "panels/json-created");
    assert.deepEqual(calls[1].data, { panel: configuration.panel, confirmed: true });
});

test("manual JSON rejects malformed unsupported and invalid configurations before confirming or sending", async () => {
    const state = editor(() => { throw Error("invalid JSON must not ask for confirmation"); });
    state.newPanel(); state.setPanelEditor("json");
    state.request = () => { throw Error("invalid JSON must not reach QQ"); };
    const base = { scope: "group", target_type: "all", panel: { items: [{ name: "/帮助" }] } };
    for (const text of ["{", "null", "[]", JSON.stringify({ configuration: base, confirmed: true }),
        JSON.stringify({ ...base, scope: "bad" }), JSON.stringify({ ...base, target_type: "specific" }),
        JSON.stringify({ ...base, scope: "channel", target_type: "specific", group_openids: ["A".repeat(32)] }),
        JSON.stringify({ ...base, group_openids: ["A".repeat(32)] }),
        JSON.stringify({ ...base, target_type: "specific", group_openids: ["12345"] }),
        JSON.stringify({ ...base, target_type: "specific", group_openids: [" " + "A".repeat(32)] }),
        JSON.stringify({ ...base, panel: { items: [{ name: "查".repeat(8) }] } }),
        JSON.stringify({ ...base, panel: { items: [{ name: "help", desc: "查".repeat(16) }] } }),
        JSON.stringify({ ...base, panel: { items: [{ name: 123 }] } }),
        JSON.stringify({ ...base, panel: { items: [{ name: "help", only_admin: "false" }] } }),
        JSON.stringify({ ...base, panel: { items: [{ name: "help", unknown: true }] } }),
        JSON.stringify({ ...base, panel: { items: "invalid" } }),
        JSON.stringify({ ...base, panel: { items: [], remark: false } }),
    ]) {
        state.panelJson = text;
        await state.savePanel();
        assert.equal(state.feedbackError, true);
        assert.equal(state.panelJson, text);
        assert.equal(state.form.id, "");
    }
});

test("switching modes synchronizes supported fields while invalid JSON and cancelled resets preserve drafts", async () => {
    const state = editor(() => false); state.newPanel();
    state.form.items[0].name = "/帮助"; state.form.targetText = "A".repeat(32);
    state.setPanelEditor("json");
    const value = JSON.parse(state.panelJson); value.panel.items[0].name = "/群宠";
    state.panelJson = JSON.stringify(value);
    state.setPanelEditor("form");
    assert.equal(state.form.items[0].name, "/群宠");
    assert.equal(state.form.targetText, "A".repeat(32));
    state.setPanelEditor("json");
    state.panelJson = "{";
    state.setPanelEditor("form");
    assert.equal(state.panelEditor, "json");
    assert.equal(state.form.items[0].name, "/群宠");
    await state.newPanel();
    assert.equal(state.panelJson, "{");
    state.request = () => { throw Error("a cancelled read must not discard JSON"); };
    await state.editPanel("another");
    assert.equal(state.panelJson, "{");
});

test("JSON formatting and a cancelled publication never send a mutation", async () => {
    const state = editor(() => false); state.newPanel(); state.setPanelEditor("json");
    state.panelJson = JSON.stringify({ scope: "group", target_type: "all", panel: { items: [{ name: "/帮助" }] } });
    state.formatPanelJson();
    assert.match(state.panelJson, /\n  "scope"/);
    const text = state.panelJson;
    state.request = () => { throw Error("formatting or cancellation must not publish"); };
    await state.savePanel();
    assert.equal(state.panelJson, text);
    assert.equal(state.form.id, "");
});

test("editing JSON cannot change a panel's scope or target associations", async () => {
    const state = editor(() => { throw Error("immutable changes must not ask to publish"); });
    state.newPanel(); state.form.id = "p1"; state.form.items[0].name = "/帮助";
    state.setPanelEditor("json");
    assert.deepEqual(Object.keys(JSON.parse(state.panelJson)), ["panel"]);
    state.panelJson = JSON.stringify({ scope: "c2c", panel: { items: [{ name: "/帮助" }] } });
    state.request = () => { throw Error("immutable changes must not reach QQ"); };
    await state.savePanel();
    assert.equal(state.feedbackError, true);
    assert.equal(state.form.scope, "group");
    assert.equal(state.form.id, "p1");
});
