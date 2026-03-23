<#assign base=request.contextPath />
<!DOCTYPE html>
<html lang="zh-CN" class="dark">
<head>
    <base id="base" href="${base}">
    <title>Snack RPC Admin - 服务列表</title>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        body { background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); }
        .glass { background: rgba(30, 41, 59, 0.8); backdrop-filter: blur(10px); }
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
                        <p class="text-xs text-slate-400">服务列表</p>
                    </div>
                </div>
                <div class="flex items-center gap-4">
                    <a href="${base}/dashboard" class="px-4 py-2 rounded-lg hover:bg-slate-700 transition-colors">监控面板</a>
                    <a href="${base}/services" class="px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-700 transition-colors">服务列表</a>
                    <a href="${base}/circuit-breakers" class="px-4 py-2 rounded-lg hover:bg-slate-700 transition-colors">熔断器</a>
                    <a href="${base}/system" class="px-4 py-2 rounded-lg hover:bg-slate-700 transition-colors">系统</a>
                </div>
            </div>
        </div>
    </nav>

    <div class="max-w-7xl mx-auto px-4 py-8">
        <div class="flex items-center justify-between mb-8">
            <div>
                <h2 class="text-3xl font-bold text-white mb-2">📋 注册服务</h2>
                <p class="text-slate-400">查看和管理所有注册的 RPC 服务</p>
            </div>
            <button onclick="loadServices()" class="px-4 py-2 bg-violet-600 hover:bg-violet-700 rounded-lg transition-colors flex items-center gap-2">
                <span>🔄</span> 刷新
            </button>
        </div>

        <!-- Search -->
        <div class="mb-6">
            <input type="text" id="search-input" placeholder="搜索服务..." 
                   class="w-full bg-slate-800 border border-slate-600 rounded-lg px-4 py-3 text-white placeholder-slate-400 focus:outline-none focus:border-violet-500"
                   oninput="filterServices()">
        </div>

        <!-- Services Grid -->
        <div id="services-grid" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            <div class="text-center text-slate-500 py-12 col-span-3">加载中...</div>
        </div>

        <!-- Service Detail Modal -->
        <div id="detail-modal" class="fixed inset-0 bg-black/50 hidden items-center justify-center z-50 overflow-y-auto">
            <div class="glass rounded-2xl p-6 border border-slate-700 max-w-4xl w-full mx-4 my-8">
                <div class="flex items-center justify-between mb-6">
                    <h3 id="modal-title" class="text-2xl font-bold text-white"></h3>
                    <button onclick="closeModal()" class="text-slate-400 hover:text-white">
                        <span class="text-2xl">✕</span>
                    </button>
                </div>
                <div id="modal-content" class="text-slate-300"></div>
            </div>
        </div>
    </div>

    <script>
        let allServices = [];

        async function loadServices() {
            try {
                const resp = await fetch('/api/services');
                const data = await resp.json();
                allServices = data.data || [];
                renderServices(allServices);
            } catch (e) {
                document.getElementById('services-grid').innerHTML = 
                    '<div class="text-center text-red-400 py-12 col-span-3">加载失败: ' + e.message + '</div>';
            }
        }

        function renderServices(services) {
            const grid = document.getElementById('services-grid');
            
            if (!services || services.length === 0) {
                grid.innerHTML = '<div class="text-center text-slate-500 py-12 col-span-3">暂无注册服务</div>';
                return;
            }

            grid.innerHTML = services.map(s => {
                const successRate = s.successRate || 0;
                const rateClass = successRate >= 99 ? 'text-green-400' : successRate >= 90 ? 'text-yellow-400' : 'text-red-400';
                const totalCalls = s.totalCalls || 0;
                
                return `
                    <div class="glass rounded-2xl p-6 border border-slate-700 hover:border-violet-500/50 transition-all cursor-pointer"
                         onclick="showServiceDetail('${s.name}')">
                        <div class="flex items-center justify-between mb-4">
                            <h4 class="font-bold text-lg text-white truncate">${s.name}</h4>
                            <span class="w-3 h-3 rounded-full bg-green-400 pulse-dot"></span>
                        </div>
                        <div class="space-y-2 text-sm">
                            <div class="flex justify-between">
                                <span class="text-slate-400">实例数</span>
                                <span class="text-white">${s.instances || 0}</span>
                            </div>
                            <div class="flex justify-between">
                                <span class="text-slate-400">总调用</span>
                                <span class="text-white">${formatNumber(totalCalls)}</span>
                            </div>
                            <div class="flex justify-between">
                                <span class="text-slate-400">成功率</span>
                                <span class="${rateClass} font-bold">${(successRate * 100).toFixed(1)}%</span>
                            </div>
                        </div>
                        <div class="mt-4 pt-4 border-t border-slate-700">
                            <div class="flex gap-2">
                                <button onclick="event.stopPropagation(); showServiceDetail('${s.name}')" 
                                        class="flex-1 px-3 py-2 bg-slate-700 hover:bg-slate-600 rounded-lg text-sm transition-colors">
                                    查看详情
                                </button>
                            </div>
                        </div>
                    </div>
                `;
            }).join('');
        }

        function filterServices() {
            const query = document.getElementById('search-input').value.toLowerCase();
            const filtered = allServices.filter(s => 
                s.name.toLowerCase().includes(query)
            );
            renderServices(filtered);
        }

        async function showServiceDetail(name) {
            const modal = document.getElementById('detail-modal');
            const title = document.getElementById('modal-title');
            const content = document.getElementById('modal-content');
            
            title.textContent = name;
            content.innerHTML = '<div class="text-center py-8">加载中...</div>';
            modal.classList.remove('hidden');
            modal.classList.add('flex');

            try {
                const [servicesResp, metricsResp, cbResp] = await Promise.all([
                    fetch('/api/services/' + encodeURIComponent(name)),
                    fetch('/api/metrics/service/' + encodeURIComponent(name)),
                    fetch('/api/circuit-breakers')
                ]);

                const servicesData = await servicesResp.json();
                const metricsData = await metricsResp.json();
                const cbData = await cbResp.json();

                let cbInfo = '';
                if (cbData.success && cbData.data) {
                    const cb = cbData.data.find(c => c.name === name);
                    if (cb) {
                        const stateClass = cb.state === 'CLOSED' ? 'text-green-400' : 
                                          cb.state === 'OPEN' ? 'text-red-400' : 'text-yellow-400';
                        cbInfo = `
                            <div class="bg-slate-800/50 rounded-xl p-4 mt-4">
                                <h4 class="font-semibold text-yellow-400 mb-3">🔴 熔断器状态</h4>
                                <div class="grid grid-cols-3 gap-4 text-center">
                                    <div>
                                        <p class="text-2xl font-bold ${stateClass}">${cb.state}</p>
                                        <p class="text-xs text-slate-400">状态</p>
                                    </div>
                                    <div>
                                        <p class="text-2xl font-bold text-red-400">${cb.failedRequests || 0}</p>
                                        <p class="text-xs text-slate-400">失败数</p>
                                    </div>
                                    <div>
                                        <p class="text-2xl font-bold text-green-400">${cb.successfulRequests || 0}</p>
                                        <p class="text-xs text-slate-400">成功数</p>
                                    </div>
                                </div>
                            </div>
                        `;
                    }
                }

                const metrics = metricsData.data || {};
                const aggregated = metrics.aggregatedMetrics || {};
                const methodMetrics = metrics.methodMetrics || {};

                content.innerHTML = `
                    <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
                        <div class="bg-slate-800/50 rounded-xl p-4 text-center">
                            <p class="text-3xl font-bold text-blue-400">${formatNumber(aggregated.totalCalls || 0)}</p>
                            <p class="text-sm text-slate-400">总调用</p>
                        </div>
                        <div class="bg-slate-800/50 rounded-xl p-4 text-center">
                            <p class="text-3xl font-bold text-green-400">${formatNumber(aggregated.successCalls || 0)}</p>
                            <p class="text-sm text-slate-400">成功</p>
                        </div>
                        <div class="bg-slate-800/50 rounded-xl p-4 text-center">
                            <p class="text-3xl font-bold text-red-400">${formatNumber(aggregated.failureCalls || 0)}</p>
                            <p class="text-sm text-slate-400">失败</p>
                        </div>
                        <div class="bg-slate-800/50 rounded-xl p-4 text-center">
                            <p class="text-3xl font-bold ${(aggregated.successRate || 0) >= 0.99 ? 'text-green-400' : 'text-yellow-400'}">
                                ${((aggregated.successRate || 0) * 100).toFixed(1)}%
                            </p>
                            <p class="text-sm text-slate-400">成功率</p>
                        </div>
                    </div>

                    <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
                        <div class="bg-slate-800/50 rounded-xl p-4 text-center">
                            <p class="text-2xl font-bold text-blue-400">${(aggregated.avgLatency || 0).toFixed(2)}ms</p>
                            <p class="text-sm text-slate-400">平均延迟</p>
                        </div>
                        <div class="bg-slate-800/50 rounded-xl p-4 text-center">
                            <p class="text-2xl font-bold text-green-400">${(aggregated.p50 || 0).toFixed(2)}ms</p>
                            <p class="text-sm text-slate-400">P50</p>
                        </div>
                        <div class="bg-slate-800/50 rounded-xl p-4 text-center">
                            <p class="text-2xl font-bold text-yellow-400">${(aggregated.p90 || 0).toFixed(2)}ms</p>
                            <p class="text-sm text-slate-400">P90</p>
                        </div>
                        <div class="bg-slate-800/50 rounded-xl p-4 text-center">
                            <p class="text-2xl font-bold text-red-400">${(aggregated.p99 || 0).toFixed(2)}ms</p>
                            <p class="text-sm text-slate-400">P99</p>
                        </div>
                    </div>

                    ${cbInfo}

                    <h4 class="font-semibold text-white mt-6 mb-3">方法详情</h4>
                    ${Object.keys(methodMetrics).length > 0 ? `
                        <div class="overflow-x-auto">
                            <table class="w-full text-sm">
                                <thead>
                                    <tr class="text-slate-400 border-b border-slate-700">
                                        <th class="text-left py-2 px-3">方法</th>
                                        <th class="text-right py-2 px-3">调用</th>
                                        <th class="text-right py-2 px-3">成功</th>
                                        <th class="text-right py-2 px-3">失败</th>
                                        <th class="text-right py-2 px-3">成功率</th>
                                        <th class="text-right py-2 px-3">平均延迟</th>
                                    </tr>
                                </thead>
                                <tbody class="text-slate-300">
                                    ${Object.entries(methodMetrics).map(([method, m]) => `
                                        <tr class="border-b border-slate-700/50">
                                            <td class="py-2 px-3 font-mono">${method}</td>
                                            <td class="text-right py-2 px-3">${formatNumber(m.totalCalls)}</td>
                                            <td class="text-right py-2 px-3 text-green-400">${formatNumber(m.successCalls)}</td>
                                            <td class="text-right py-2 px-3 text-red-400">${formatNumber(m.failureCalls)}</td>
                                            <td class="text-right py-2 px-3">${(m.successRate * 100).toFixed(1)}%</td>
                                            <td class="text-right py-2 px-3">${(m.avgLatency).toFixed(2)}ms</td>
                                        </tr>
                                    `).join('')}
                                </tbody>
                            </table>
                        </div>
                    ` : '<div class="text-center text-slate-500 py-4">暂无方法数据</div>'}
                `;
            } catch (e) {
                content.innerHTML = '<div class="text-red-400">加载失败: ' + e.message + '</div>';
            }
        }

        function closeModal() {
            document.getElementById('detail-modal').classList.add('hidden');
            document.getElementById('detail-modal').classList.remove('flex');
        }

        function formatNumber(num) {
            if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
            if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
            return num.toString();
        }

        // Close modal on escape key
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') closeModal();
        });

        // Load services on page load
        loadServices();
        setInterval(loadServices, 30000);
    </script>
</body>
</html>
