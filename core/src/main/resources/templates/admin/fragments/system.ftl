<#if error??>
<div class="grid min-h-72 place-items-center p-6 text-center">
    <div><p class="font-black text-[#b83f68]">${error?html}</p><p class="mt-2 text-sm text-[#9b7187]">保存正确的管理令牌后重试。</p></div>
</div>
<#else>
<div class="runtime-clock" data-server-time-millis="${runtime.serverTimeMillis?c}" data-uptime-millis="${runtime.uptimeMillis?c}">
<div class="panel-head"><h3 class="font-black text-lomu-900">运行状态</h3><span class="text-xs text-[#9b7187]">当前 <span data-runtime-server-time>${runtime.serverTime?html}</span></span></div>
<div class="p-5">
    <div class="flex items-start justify-between gap-4 border-b border-lomu-100 pb-4">
        <div><label class="block text-xs font-extrabold text-[#9b7187]">持续运行</label><strong class="mt-1 block text-3xl font-black text-lomu-900" data-runtime-uptime>${runtime.uptime?html}</strong><small class="text-[#9b7187]">启动于 ${runtime.startedAt?html}</small></div>
        <span class="relative rounded-full bg-[#e8f8ef] py-1.5 pl-6 pr-3 text-xs font-black text-[#39845d]"><span class="absolute left-2.5 top-1/2 size-2 -translate-y-1/2 rounded-full bg-[#54bd80] shadow-[0_0_0_4px_rgba(84,189,128,.15)]"></span>运行中</span>
    </div>
    <div class="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
        <div class="rounded-2xl border border-lomu-200/70 bg-lomu-50/70 p-3"><label class="block text-[10px] font-extrabold text-[#9b7187]">操作系统</label><strong class="block truncate text-xs" title="${runtime.operatingSystem?html}">${runtime.operatingSystem?html}</strong></div>
        <div class="rounded-2xl border border-lomu-200/70 bg-lomu-50/70 p-3"><label class="block text-[10px] font-extrabold text-[#9b7187]">处理器</label><strong class="block truncate text-xs">${runtime.processor?html}</strong></div>
        <div class="rounded-2xl border border-lomu-200/70 bg-lomu-50/70 p-3"><label class="block text-[10px] font-extrabold text-[#9b7187]">Java</label><strong class="block truncate text-xs" title="${runtime.javaVendor?html}">${runtime.java?html}</strong></div>
        <div class="rounded-2xl border border-lomu-200/70 bg-lomu-50/70 p-3"><label class="block text-[10px] font-extrabold text-[#9b7187]">JVM 内存</label><strong class="block truncate text-xs">${runtime.jvmMemory?html}</strong></div>
        <div class="rounded-2xl border border-lomu-200/70 bg-lomu-50/70 p-3"><label class="block text-[10px] font-extrabold text-[#9b7187]">物理内存</label><strong class="block truncate text-xs">${runtime.systemMemory?html}</strong></div>
        <div class="rounded-2xl border border-lomu-200/70 bg-lomu-50/70 p-3"><label class="block text-[10px] font-extrabold text-[#9b7187]">运行模式</label><strong class="block truncate text-xs">${runtime.runtimeMode?html}</strong></div>
    </div>
</div>
</div>
</#if>
