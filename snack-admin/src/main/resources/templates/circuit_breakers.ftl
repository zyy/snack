<#assign base=request.contextPath />
<!DOCTYPE html>
<html lang="zh-CN" class="dark">
<head>
    <base id="base" href="${base}">
    <title>Snack RPC Admin - 熔断器管理</title>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        body { background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); }
        .glass { background: rgba(30, 41, 59, 0.8); backdrop-filter: blur(10px); }
        .circuit-card { transition: all 0.3s ease; }
        .circuit-card:hover { transform: translateY(-2px); }
        .glow-green { box-shadow: 0 0 20px rgba(34, 197, 94, 0.3); }
        .glow-red { box-shadow: 0 0 20px rgba(239, 68, 68, 0.3); }
        .glow-yellow { box-shadow: 0 0 20px rgba(234, 179, 8, 0.3); }
        .pulse-dot { animation: pulse 2s infinite; }
        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
    </style>
</head>
<body class="text-slate-100 min-h-screen">
    <!-- Navigation -->
    <nav class="glass border-b border-slate-700 sticky top-0 z-50">
        <div class="max-w-7xl mx-auto px-4">
            <div class="flex items-center justify-between h-16">
                <div class="flex items-center gap-3">
                    <div class="w-10 h-10 rounded-lg bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center">
                        <span class="text-xl">⚡</span>
                    </div>
                    <div>
                        <h1 class="text-xl font-bold text-white">Snack RPC Admin</h1>
                        <p class="text-xs text-slate-400">熔断器管理</p>
                    </div>
                </div>
                <div class="flex items-center gap-4">
                    <a href="${base}/dashboard" class="px-4 py-2 rounded-lg hover:bg-slate-700 transition-colors">监控面板</a>
                    <a href="${base}/services" class="px-4 py-2 rounded-lg hover:bg-slate-700 transition-colors">服务列表</a>
                    <a href="${base}/circuit-breakers" class="px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-700 transition-colors">熔断器</a>
                    <a href="${base}/system" class="px-4 py-2 rounded-lg hover:bg-slate-700 transition-colors">系统</a>
                </div>
            </div>
        </div>
    </nav>

    <div class="max-w-7xl mx-auto px-4 py-8">
        <!-- Header -->
        <div class="flex items-center justify-between mb-8">
            <div>
                <h2 class="text-3xl font-bold text-white mb-2">🔴 熔断器管理</h2>
                <p class="text-slate-400">管理和配置 RPC 调用的熔断保护策略</p>
            </div>
            <button onclick="loadCircuitBreakers()" class="px-4 py-2 bg-violet-600 hover:bg-violet-700 rounded-lg transition-colors flex items-center gap-2">
                <span>🔄</span> 刷新
            </button>
        </div>

        <!-- Summary Stats -->
        <div class="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
            <div class="glass rounded-xl p-6 border border-slate-700 text-center">
                <p class="text-4xl font-bold text-slate-300" id="total-count">0</p>
                <p class="text-slate-400 text-sm mt-2">熔断器总数</p>
            </div>
            <div class="glass rounded-xl p-6 border border-slate-700 text-center glow-green">
                <p class="text-4xl font-bold text-green-400" id="closed-count">0</p>
                <p class="text-green-400 text-sm mt-2">正常 (CLOSED)</p>
            </div>
            <div class="glass rounded-xl p-6 border border-slate-700 text-center glow-red">
                <p class="text-4xl font-bold text-red-400" id="open-count">0</p>
                <p class="text-red-400 text-sm mt-2">熔断 (OPEN)</p>
            </div>
            <div class="glass rounded-xl p-6 border border-slate-700 text-center glow-yellow">
                <p class="text-4xl font-bold text-yellow-400" id="half-open-count">0</p>
                <p class="text-yellow-400 text-sm mt-2">半开 (HALF_OPEN)</p>
            </div>
        </div>

        <!-- Circuit Breakers Grid -->
        <div id="circuit-grid" class="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div class="text-center text-slate-500 py-12 col-span-2">加载中...</div>
        </div>

        <!-- Configuration Template -->
        <div class="mt-8 glass rounded-2xl p-6 border border-slate-700">
            <h3 class="text-xl font-bold text-white mb-4">⚙️ 熔断器配置说明</h3>
            <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
                <div class="bg-slate-800/50 rounded-xl p-4">
                    <h4 class="font-semibold text-blue-400 mb-2">失败阈值 (failureThreshold)</h4>
                    <p class="text-slate-400 text-sm">连续失败次数达到此值时，熔断器将打开。默认值: 5</p>
                </div>
                <div class="bg-slate-800/50 rounded-xl p-4">
                    <h4 class="font-semibold text-yellow-400 mb-2">恢复超时 (recoveryTimeout)</h4>
                    <p class="text-slate-400 text-sm">熔断打开后，等待此时间后进入半开状态。默认值: 30000ms</p>
                </div>
                <div class="bg-slate-800/50 rounded-xl p-4">
                    <h4 class="font-semibold text-green-400 mb-2">半开试探数 (halfOpenRequests)</h4>
                    <p class="text-slate-400 text-sm">半开状态下，允许通过的试探请求数。默认值: 3</p>
                </div>
            </div>
        </div>
    </div>

    <!-- Modal for Configuration -->
    <div id="config-modal" class="fixed inset-0 bg-black/50 hidden items-center justify-center z-50">
        <div class="glass rounded-2xl p-6 border border-slate-700 max-w-md w-full mx-4">
            <h3 class="text-xl font-bold text-white mb-4">配置熔断器</h3>
            <div class="space-y-4">
                <div>
                    <label class="block text-sm text-slate-400 mb-1">失败阈值</label>
                    <input type="number" id="config-failure-threshold" class="w-full bg-slate-800 border border-slate-600 rounded-lg px-4 py-2 text-white" value="5">
                </div>
                <div>
                    <label class="block text-sm text-slate-400 mb-1">恢复超时 (ms)</label>
                    <input type="number" id="config-recovery-timeout" class="w-full bg-slate-800 border border-slate-600 rounded-lg px-4 py-2 text-white" value="30000">
                </div>
                <div>
                    <label class="block text-sm text-slate-400 mb-1">半开试探数</label>
                    <input type="number" id="config-half-open-requests" class="w-full bg-slate-800 border border-slate-600 rounded-lg px-4 py-2 text-white" value="3">
                </div>
            </div>
            <div class="flex gap-3 mt-6">
                <button onclick="saveConfig()" class="flex-1 px-4 py-2 bg-violet-600 hover:bg-violet-700 rounded-lg transition-colors">保存</button>
                <button onclick="closeModal()" class="flex-1 px-4 py-2 bg-slate-700 hover:bg-slate-600 rounded-lg transition-colors">取消</button>
            </div>
        </div>
    </div>

    <script>
        let currentCircuitName = null;

        async function loadCircuitBreakers() {
            try {
                const resp = await fetch('/api/circuit-breakers');
                const data = await resp.json();
                const grid = document.getElementById('circuit-grid');

                if (data.success && data.data && data.data.length > 0) {
                    // Update stats
                    document.getElementById('total-count').textContent = data.data.length;
                    document.getElementById('closed-count').textContent = data.data.filter(c => c.state === 'CLOSED').length;
                    document.getElementById('open-count').textContent = data.data.filter(c => c.state === 'OPEN').length;
                    document.getElementById('half-open-count').textContent = data.data.filter(c => c.state === 'HALF_OPEN').length;

                    grid.innerHTML = data.data.map(cb => {
                        const stateConfig = {
                            'CLOSED': { class: 'glow-green border-green-500/30', bg: 'bg-green-500/20', text: 'text-green-400', label: '正常' },
                            'OPEN': { class: 'glow-red border-red-500/30', bg: 'bg-red-500/20', text: 'text-red-400', label: '熔断中' },
                            'HALF_OPEN': { class: 'glow-yellow border-yellow-500/30', bg: 'bg-yellow-500/20', text: 'text-yellow-400', label: '半开' }
                        };
                        const cfg = stateConfig[cb.state] || stateConfig['CLOSED'];

                        return `
                            <div class="circuit-card glass rounded-2xl p-6 border ${cfg.class}">
                                <div class="flex items-center justify-between mb-4">
                                    <div class="flex items-center gap-3">
                                        <div class="w-12 h-12 rounded-full ${cfg.bg} flex items-center justify-center">
                                            <span class="text-2xl ${cfg.text}">${cb.state === 'CLOSED' ? '✅' : cb.state === 'OPEN' ? '🔴' : '⚡'}</span>
                                        </div>
                                        <div>
                                            <h4 class="font-bold text-white">${cb.name}</h4>
                                            <span class="text-xs ${cfg.text}">${cfg.label}</span>
                                        </div>
                                    </div>
                                    <div class="flex gap-2">
                                        <button onclick="resetCircuit('${cb.name}')" class="px-3 py-1 bg-green-600/50 hover:bg-green-600 rounded text-sm transition-colors">重置</button>
                                        <button onclick="openConfigModal('${cb.name}')" class="px-3 py-1 bg-blue-600/50 hover:bg-blue-600 rounded text-sm transition-colors">配置</button>
                                    </div>
                                </div>
                                <div class="grid grid-cols-3 gap-4 text-center">
                                    <div class="bg-slate-800/50 rounded-lg p-3">
                                        <p class="text-2xl font-bold text-green-400">${cb.successfulRequests !0}</p>
                                        <p class="text-xs text-slate-400">成功</p>
                                    </div>
                                    <div class="bg-slate-800/50 rounded-lg p-3">
                                        <p class="text-2xl font-bold text-red-400">${cb.failedRequests !0}</p>
                                        <p class="text-xs text-slate-400">失败</p>
                                    </div>
                                    <div class="bg-slate-800/50 rounded-lg p-3">
                                        <p class="text-2xl font-bold text-yellow-400">${cb.blockedRequests !0}</p>
                                        <p class="text-xs text-slate-400">阻止</p>
                                    </div>
                                </div>
                                <#if cb.state == 'OPEN'>
                                <div class="mt-4 bg-red-500/20 rounded-lg p-3 text-center">
                                    <p class="text-red-400 text-sm">等待恢复: ${(cb.timeUntilRetry !0) / 1000}秒</p>
                                </div>
                                </#if>
                                <div class="mt-4 text-xs text-slate-500">
                                    <p>失败阈值: ${cb.failureThreshold!5} | 恢复超时: ${(cb.recoveryTimeoutMs!30000)}ms | 半开试探: ${cb.halfOpenRequests!3}</p>
                                </div>
                            </div>
                        `;
                    }).join('');
                } else {
                    grid.innerHTML = '<div class="text-center text-slate-500 py-12 col-span-2">暂无熔断器数据</div>';
                }
            } catch (e) {
                document.getElementById('circuit-grid').innerHTML = '<div class="text-center text-red-400 py-12 col-span-2">加载失败: ' + e.message + '</div>';
            }
        }

        async function resetCircuit(name) {
            if (!confirm('确定要重置熔断器 ' + name + ' 吗？')) return;
            try {
                const resp = await fetch('/api/circuit-breaker/' + encodeURIComponent(name) + '/reset', { method: 'POST' });
                const data = await resp.json();
                if (data.success) {
                    alert('熔断器已重置');
                    loadCircuitBreakers();
                } else {
                    alert('重置失败: ' + (data.message || '未知错误'));
                }
            } catch (e) {
                alert('重置失败: ' + e.message);
            }
        }

        function openConfigModal(name) {
            currentCircuitName = name;
            document.getElementById('config-modal').classList.remove('hidden');
            document.getElementById('config-modal').classList.add('flex');
        }

        function closeModal() {
            document.getElementById('config-modal').classList.add('hidden');
            document.getElementById('config-modal').classList.remove('flex');
            currentCircuitName = null;
        }

        async function saveConfig() {
            if (!currentCircuitName) return;
            const config = {
                failureThreshold: parseInt(document.getElementById('config-failure-threshold').value),
                recoveryTimeoutMs: parseInt(document.getElementById('config-recovery-timeout').value),
                halfOpenRequests: parseInt(document.getElementById('config-half-open-requests').value)
            };
            try {
                const resp = await fetch('/api/circuit-breaker/' + encodeURIComponent(currentCircuitName) + '/config', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(config)
                });
                const data = await resp.json();
                if (data.success) {
                    alert('配置已保存');
                    closeModal();
                    loadCircuitBreakers();
                } else {
                    alert('保存失败: ' + (data.message || '未知错误'));
                }
            } catch (e) {
                alert('保存失败: ' + e.message);
            }
        }

        // Initial load
        loadCircuitBreakers();
        setInterval(loadCircuitBreakers, 10000);
    </script>
</body>
</html>
