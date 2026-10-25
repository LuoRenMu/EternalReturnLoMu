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
