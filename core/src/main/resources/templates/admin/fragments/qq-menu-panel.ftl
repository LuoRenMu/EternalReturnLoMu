<section x-show="view === 'qqmenus'" x-cloak x-data="qqMenuPanelAdmin" @qq-menu-open.window="activate()" class="qq-admin">
    <div class="qq-heading">
        <div><h2 class="text-2xl font-black text-lomu-900">QQ 菜单与指令面板</h2><p class="qq-hint">管理 QQ 线上配置。读取和预览不会发布，所有变更都需明确提交。</p></div>
        <button type="button" class="button" @click="activate()" :disabled="statusLoading || busy" x-text="statusLoading ? '检查连接中…' : '检查连接'"></button>
    </div>
    <p class="panel qq-status" role="status" x-text="status"></p>
    <p class="panel qq-feedback" role="status" x-show="feedback" :class="feedbackError && 'qq-error'" x-text="feedback"></p>
    <div class="qq-tabs" role="group" aria-label="QQ 配置类型">
        <button type="button" class="button" :class="tab === 'menu' && 'button-primary'" @click="tab = 'menu'" :aria-pressed="tab === 'menu'">自定义菜单 · C2C</button>
        <button type="button" class="button" :class="tab === 'panels' && 'button-primary'" @click="tab = 'panels'" :aria-pressed="tab === 'panels'">指令面板 · 四种场景</button>
    </div>

    <div x-show="tab === 'menu'" class="qq-menu-grid">
        <article class="panel qq-card">
            <div class="qq-heading"><div><h3 class="font-black">自定义菜单编辑</h3><p class="qq-hint">仅用于机器人 C2C 单聊窗口底部，不是群聊 Markdown 按钮。</p></div><span class="pill" x-text="menuVersion === null ? '尚未读取' : '版本 ' + menuVersion"></span></div>
            <button type="button" class="button" @click="loadMenu()" :disabled="!available || busy || menuLoading" x-text="menuLoading ? '读取中…' : '重新读取线上菜单'"></button>
            <fieldset :disabled="!available || busy || menuLoading || !menuLoaded">
                <p class="qq-hint" x-show="menuLoaded && !menu.items.length">QQ 当前没有自定义菜单。添加菜单项后再发布。</p>
                <template x-for="(item, index) in menu.items" :key="index">
                    <div class="qq-item">
                        <div class="qq-heading"><strong x-text="'菜单 ' + (index + 1)"></strong><div class="qq-actions"><button type="button" class="button" @click="move(menu.items, index, -1)" :disabled="index === 0" aria-label="菜单上移">↑</button><button type="button" class="button" @click="move(menu.items, index, 1)" :disabled="index === menu.items.length - 1" aria-label="菜单下移">↓</button><button type="button" class="button" @click="menu.items.splice(index, 1)">移除</button></div></div>
                        <div class="qq-fields">
                            <label class="field"><span class="field-label">名称</span><input class="control" x-model="item.name" maxlength="128" placeholder="例如：查询玩家"></label>
                            <label class="field"><span class="field-label">动作类型</span><select class="control" x-model="item.type"><option value="send_message">填入消息</option><option value="link">打开链接</option><option value="menu">二级菜单</option><option value="switch">开关</option></select></label>
                        </div>
                        <label class="field" x-show="item.type === 'send_message'"><span class="field-label">填入输入框的消息</span><input class="control" x-model="item.send_message" maxlength="2048" placeholder="/查询玩家 "></label>
                        <label class="field" x-show="item.type === 'link'"><span class="field-label">HTTP／HTTPS 链接</span><input class="control" x-model="item.link" placeholder="https://example.com"></label>
                        <div x-show="item.type === 'switch'" class="qq-fields"><label class="field"><span class="field-label">开关标识 switch_id</span><input class="control" x-model="item.switch.switch_id" maxlength="128"></label><label class="qq-check"><input type="checkbox" x-model="item.switch.default">默认开启</label><p class="qq-hint">仅管理 QQ 菜单开关配置，不会自动改变群宠等业务订阅。</p></div>
                        <div x-show="item.type === 'menu'" class="qq-submenus">
                            <template x-for="(child, childIndex) in item.sub_menu_items" :key="childIndex">
                                <div class="qq-item">
                                    <div class="qq-heading"><strong x-text="'二级菜单 ' + (childIndex + 1)"></strong><div class="qq-actions"><button type="button" class="button" @click="move(item.sub_menu_items, childIndex, -1)" :disabled="childIndex === 0" aria-label="二级菜单上移">↑</button><button type="button" class="button" @click="move(item.sub_menu_items, childIndex, 1)" :disabled="childIndex === item.sub_menu_items.length - 1" aria-label="二级菜单下移">↓</button><button type="button" class="button" @click="item.sub_menu_items.splice(childIndex, 1)">移除</button></div></div>
                                    <div class="qq-fields"><label class="field"><span class="field-label">名称</span><input class="control" x-model="child.name" maxlength="128"></label><label class="field"><span class="field-label">动作</span><select class="control" x-model="child.type"><option value="send_message">填入消息</option><option value="link">打开链接</option></select></label></div>
                                    <label class="field" x-show="child.type === 'send_message'"><span class="field-label">消息内容</span><input class="control" x-model="child.send_message" maxlength="2048" placeholder="/帮助"></label>
                                    <label class="field" x-show="child.type === 'link'"><span class="field-label">链接</span><input class="control" x-model="child.link" placeholder="https://example.com"></label>
                                </div>
                            </template>
                            <button type="button" class="button" @click="item.sub_menu_items.push(newMenuItem(true))" :disabled="item.sub_menu_items.length >= 20">添加二级菜单项</button>
                        </div>
                    </div>
                </template>
                <div class="qq-actions"><button type="button" class="button" @click="menu.items.push(newMenuItem())" :disabled="menu.items.length >= 20">添加一级菜单</button><button type="button" class="button button-primary" @click="saveMenu()" :disabled="!menu.items.length">提交并覆盖 QQ 菜单</button><button type="button" class="button" @click="saveMenu(true)">清空线上菜单</button></div>
            </fieldset>
        </article>
        <aside class="panel qq-card qq-preview">
            <h3 class="font-black">结构预览</h3><p class="qq-hint">仅展示草稿结构，不模拟 QQ 客户端的精确外观。具体数量、文本长度和能力由 QQ 校验。</p>
            <template x-for="(item,index) in menu.items" :key="index"><div class="qq-preview-item"><strong x-text="item.name || '未命名菜单'"></strong><small x-text="({send_message:'填入消息',link:'打开链接',menu:'二级菜单',switch:'开关'})[item.type]"></small><span x-show="item.type === 'send_message'" x-text="item.send_message"></span><span x-show="item.type === 'link'" x-text="item.link"></span><template x-for="(child,childIndex) in item.type === 'menu' ? item.sub_menu_items : []" :key="childIndex"><span x-text="'↳ ' + (child.name || '未命名') + ' · ' + (child.send_message || child.link || '')"></span></template></div></template>
            <details><summary>查看待提交 JSON</summary><pre x-text="JSON.stringify(menuPayload(), null, 2)"></pre></details>
        </aside>
    </div>

    <div x-show="tab === 'panels'" class="qq-panels-grid">
        <aside class="panel qq-card">
            <h3 class="font-black">线上指令面板</h3>
            <label class="field"><span class="field-label">筛选生效场景</span><select class="control" x-model="listScope" @change="loadPanels()" :disabled="!available || busy"><option value="group">群聊 group</option><option value="c2c">单聊 c2c</option><option value="channel">文字子频道 channel</option><option value="dm">频道私信 dm</option></select></label>
            <div class="qq-actions"><button type="button" class="button" @click="loadPanels()" :disabled="!available || busy || panelLoading">刷新列表</button><button type="button" class="button button-primary" @click="newPanel()" :disabled="!available || busy || detailLoading">新建面板草稿</button></div>
            <p class="qq-hint" x-show="panelLoading">正在读取面板…</p><p class="qq-hint" x-show="panelsLoaded && !panels.length">该场景暂无指令面板。</p>
            <template x-for="record in panels" :key="record.panel_id"><button type="button" class="qq-panel-record" :class="form.id === record.panel_id && 'qq-selected'" @click="editPanel(record.panel_id)" :disabled="!available || busy"><strong x-text="record.panel.remark || record.panel_id"></strong><span x-text="record.scope + ' · ' + (record.target_type === 'all' ? '全部对象' : '指定对象')"></span><small x-text="record.panel_id"></small></button></template>
            <button type="button" class="button" x-show="!isEnd" @click="loadPanels(true)" :disabled="!available || busy || panelLoading">加载更多</button>
        </aside>
        <article class="panel qq-card">
            <div class="qq-heading"><div><h3 class="font-black" x-text="form.id ? '编辑指令面板' : '新建指令面板'"></h3><p class="qq-hint" x-text="form.id || '默认只对指定对象生效，填写 OpenID 后才能创建。'"></p></div><span class="pill" x-show="selectedPanel" x-text="'版本 ' + (selectedPanel?.version ?? selectedPanel?.panel.version ?? '—')"></span></div>
            <p class="qq-hint" x-show="detailLoading">正在读取面板详情…</p>
            <fieldset :disabled="!available || busy || detailLoading">
                <div class="qq-fields">
                    <label class="field"><span class="field-label">生效场景</span><select class="control" x-model="form.scope" @change="scopeChanged()" :disabled="!!form.id"><option value="group">群聊 group</option><option value="c2c">单聊 c2c</option><option value="channel">文字子频道 channel</option><option value="dm">频道私信 dm</option></select></label>
                    <label class="field"><span class="field-label">生效范围</span><select class="control" x-model="form.target_type" :disabled="!!form.id || !['group','c2c'].includes(form.scope)"><option value="specific">指定对象</option><option value="all">该场景的全部对象</option></select></label>
                </div>
                <p class="qq-hint" x-show="form.target_type === 'all'">此配置会面向该场景下的全部对象；频道和频道私信仅支持此模式。</p>
                <label class="field" x-show="!form.id && form.target_type === 'specific'"><span class="field-label" x-text="form.scope === 'group' ? '群 OpenID（每行一个，最多 20 个）' : '用户 OpenID（每行一个，最多 20 个）'"></span><textarea class="control" rows="3" x-model="form.targetText" placeholder="32 位 OpenID；不是普通 QQ 群号或 QQ 号"></textarea></label>
                <label class="field"><span class="field-label">开发者备注（不对用户展示）</span><input class="control" x-model="form.remark" maxlength="255" placeholder="例如：Eternal Return 群聊常用查询"></label>
                <template x-for="(item,index) in form.items" :key="index">
                    <div class="qq-item">
                        <div class="qq-heading"><strong x-text="'元素 ' + (index + 1)"></strong><div class="qq-actions"><button type="button" class="button" @click="move(form.items, index, -1)" :disabled="index === 0" aria-label="面板元素上移">↑</button><button type="button" class="button" @click="move(form.items, index, 1)" :disabled="index === form.items.length - 1" aria-label="面板元素下移">↓</button><button type="button" class="button" @click="form.items.splice(index,1)">移除</button></div></div>
                        <div class="qq-fields"><label class="field"><span class="field-label">类型</span><select class="control" x-model="item.type"><option value="command">指令（填入输入框）</option><option value="link">链接</option></select></label><label class="qq-check"><input type="checkbox" x-model="item.only_admin">仅群／频道管理员可点击</label></div>
                        <label class="field"><span class="field-label" x-text="item.type === 'command' ? '填入输入框的指令（QQ 上限 14 字符，约 7 个汉字）' : '显示名称（QQ 上限 14 字符）'"></span><input class="control" x-model="item.name" maxlength="14" placeholder="/查询玩家"></label>
                        <label class="field" x-show="item.type === 'command'"><span class="field-label">从本机已启用命令填入（可选）</span><select class="control" @change="if ($event.target.value) item.name = $event.target.value"><option value="">手动输入／选择命令</option><template x-for="command in commands" :key="command.alias"><option :value="'/' + command.alias" x-text="'/' + command.alias"></option></template></select></label>
                        <label class="field"><span class="field-label">描述（QQ 上限 30 字符，约 15 个汉字）</span><input class="control" x-model="item.desc" maxlength="30"></label>
                        <label class="field" x-show="item.type === 'link'"><span class="field-label">HTTP／HTTPS 链接</span><input class="control" x-model="item.link" placeholder="https://example.com"></label>
                    </div>
                </template>
                <div class="qq-actions"><button type="button" class="button" @click="form.items.push(newPanelItem())" :disabled="form.items.length >= 20">添加元素</button><span class="qq-hint" x-text="form.items.length + ' / 20'"></span><button type="button" class="button button-primary" @click="savePanel()" :disabled="!form.items.length" x-text="form.id ? '提交元素与备注' : '创建 QQ 线上面板'"></button><button type="button" class="button" x-show="form.id" @click="deletePanel()">删除线上面板</button></div>
                <p class="qq-hint">修改已有面板不会改变生效场景或关联对象。指令元素点击后填入输入框，不是直接执行命令。</p>
            </fieldset>
            <div class="qq-targets" x-show="selectedPanel?.target_type === 'specific'">
                <h4 class="font-black">管理面板关联对象</h4><p class="qq-hint">增删对象与修改面板内容分开提交；不会自动取得或修改群主动消息权限。</p>
                <p class="qq-hint" x-text="'QQ 当前返回的关联对象：' + ((selectedPanel?.group_openids || selectedPanel?.user_openids || []).join('、') || '未返回')"></p>
                <fieldset :disabled="!available || busy || detailLoading"><label class="field"><span class="field-label">操作</span><select class="control" x-model="targetOp"><option value="add">增加对象</option><option value="del">移除对象</option></select></label><label class="field"><span class="field-label">群／用户 OpenID（每行一个，每次最多 20 个）</span><textarea class="control" rows="3" x-model="targetText"></textarea></label><button type="button" class="button" @click="updateTargets()">提交关联对象变更</button></fieldset>
            </div>
            <div class="qq-preview">
                <h4 class="font-black">面板内容预览</h4><div class="qq-preview-tiles"><template x-for="(item,index) in form.items" :key="index"><div class="qq-preview-item"><strong x-text="item.name || '未命名'"></strong><span x-text="item.desc || '暂无描述'"></span><small x-text="(item.type === 'link' ? '链接' : '指令') + (item.only_admin ? ' · 仅管理员' : ' · 所有人')"></small></div></template></div>
                <details><summary>查看待提交面板 JSON</summary><pre x-text="JSON.stringify(panelPayload(), null, 2)"></pre></details>
            </div>
        </article>
    </div>
    <dialog x-ref="confirmDialog" class="qq-confirm" aria-labelledby="qq-confirm-title" aria-describedby="qq-confirm-message" @cancel.prevent="answerConfirmation(false)" @click.self="answerConfirmation(false)">
        <div class="qq-card"><h3 id="qq-confirm-title" class="font-black">确认 QQ 配置变更</h3><p id="qq-confirm-message" class="qq-hint" x-text="confirmation.message"></p><div class="qq-actions"><button type="button" class="button" @click="answerConfirmation(false)">取消</button><button type="button" class="button button-primary" @click="answerConfirmation(true)">确认操作</button></div></div>
    </dialog>
</section>
