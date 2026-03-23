<#assign base=request.contextPath />
<!DOCTYPE html>
<html lang="zh-CN" class="dark">
<head>
    <base id="base" href="${base}">
    <title>Snack RPC Admin - 专业监控面板</title>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <script src="https://cdn.tailwindcss.com"></script>
    <script src="https://cdn.jsdelivr.net/npm/echarts@5.4.3/dist/echarts.min.js"></script>
    <link rel="stylesheet" href="/css/webui.css"/>
    <style>
        body { background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); }
        .glass { background: rgba(30, 41, 59, 0.8); backdrop-filter: blur(10px); }
        .stat-card { transition: all 0.3s ease; }
        .stat-card:hover { transform: translateY(-4px); box-shadow: 0 20px 40px rgba(0,0,0,0.3); }
        .glow-green { box-shadow: 0 0 20px rgba(34, 197, 94, 0.4); }
        .glow-red { box-shadow: 0 0 20px rgba(239, 68, 68, 0.4); }
        .glow-yellow { box-shadow: 0 0 20px rgba(234, 179, 8, 0.4); }
        .glow-blue { box-shadow: 0 0 20px rgba(59, 130, 246, 0.4); }
        .glow-purple { box-shadow: 0 0 20px rgba(168, 85, 247, 0.4); }
        .pulse-dot { animation: pulse 2s infinite; }
        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
        .float { animation: float 3s ease-in-out infinite; }
        @keyframes float { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-5px); } }
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
                        <h1 class="text-xl font-bold bg-gradient-to-r from-violet-400 to-purple-400 bg-clip-text text-transparent">Snack RPC Admin</h1>
                        <p class="text-xs text-slate-400">分布式 RPC 监控平台</p>
                    </div>
                </div>
                <div class="flex items-center gap-4">
                    <a href="${base}/dashboard" class="px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-700 transition-colors">监控面板</a>
                    <a href="${base}/services" class="px-4 py-2 rounded-lg hover:bg-slate-700 transition-colors">服务列表</a>
                    <a href="${base}/circuit-breakers" class="px-4 py-2 rounded-lg hover:bg-slate-700 transition-colors">熔断器</a>
                    <a href="${base}/system" class="px-4 py-2 rounded-lg hover:bg-slate-700 transition-colors">系统</a>
                </div>
            </div>
        </div>
    </nav>

    <div class="max-w-7xl mx-auto px-4 py-8">
        <!-- Header -->
        <div class="mb-8">
            <h2 class="text-3xl font-bold text-white mb-2">📊 实时监控面板</h2>
            <p class="text-slate-400">实时监控 RPC 服务的健康状态、性能指标和调用统计</p>
        </div>

        <!-- Stats Cards -->
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
            <!-- QPS Card -->
            <div class="stat-card glass rounded-2xl p-6 border border-slate-700 glow-blue">
                <div class="flex items-center justify-between mb-4">
                    <div class="w-12 h-12 rounded-xl bg-blue-500/20 flex items-center justify-center">
                        <span class="text-2xl">⚡</span>
                    </div>
                    <span class="text-xs text-blue-400 bg-blue-400/10 px-2 py-1 rounded-full">实时</span>
                </div>
                <h3 class="text-slate-400 text-sm mb-1">当前 QPS</h3>
                <p id="qps-value" class="text-4xl font-bold text-blue-400">0</p>
                <p class="text-xs text-slate-500 mt-1">每秒请求数</p>
            </div>

            <!-- Success Rate Card -->
            <div class="stat-card glass rounded-2xl p-6 border border-slate-700 glow-green">
                <div class="flex items-center justify-between mb-4">
                    <div class="w-12 h-12 rounded-xl bg-green-500/20 flex items-center justify-center">
                        <span class="text-2xl">✅</span>
                    </div>
                    <span id="success-badge" class="text-xs text-green-400 bg-green-400/10 px-2 py-1 rounded-full">正常</span>
                </div>
                <h3 class="text-slate-400 text-sm mb-1">成功率</h3>
                <p id="success-rate" class="text-4xl font-bold text-green-400">100%</p>
                <p class="text-xs text-slate-500 mt-1">调用成功率</p>
            </div>

            <!-- Total Calls Card -->
            <div class="stat-card glass rounded-2xl p-6 border border-slate-700 glow-purple">
                <div class="flex items-center justify-between mb-4">
                    <div class="w-12 h-12 rounded-xl bg-purple-500/20 flex items-center justify-center">
                        <span class="text-2xl">📈</span>
                    </div>
                    <span class="text-xs text-purple-400 bg-purple-400/10 px-2 py-1 rounded-full">总计</span>
                </div>
                <h3 class="text-slate-400 text-sm mb-1">总调用量</h3>
                <p id="total-calls" class="text-4xl font-bold text-purple-400">0</p>
                <p class="text-xs text-slate-500 mt-1">历史累计</p>
            </div>

            <!-- Circuit Breaker Card -->
            <div class="stat-card glass rounded-2xl p-6 border border-slate-700 glow-yellow">
                <div class="flex items-center justify-between mb-4">
                    <div class="w-12 h-12 rounded-xl bg-yellow-500/20 flex items-center justify-center">
                        <span class="text-2xl">🔴</span>
                    </div>
                    <span id="circuit-badge" class="text-xs text-yellow-400 bg-yellow-400/10 px-2 py-1 rounded-full">正常</span>
                </div>
                <h3 class="text-slate-400 text-sm mb-1">熔断器状态</h3>
                <p id="circuit-state" class="text-2xl font-bold text-yellow-400">CLOSED</p>
                <p class="text-xs text-slate-500 mt-1">所有熔断器正常</p>
            </div>
        </div>

        <!-- Charts Row -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
            <!-- QPS Trend Chart -->
            <div class="glass rounded-2xl p-6 border border-slate-700">
                <h3 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                    <span class="w-2 h-2 rounded-full bg-blue-400 pulse-dot"></span>
                    QPS 趋势
                </h3>
                <div id="qps-chart" class="w-full h-64"></div>
            </div>

            <!-- Latency Chart -->
            <div class="glass rounded-2xl p-6 border border-slate-700">
                <h3 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                    <span class="w-2 h-2 rounded-full bg-green-400 pulse-dot"></span>
                    响应时间 (ms)
                </h3>
                <div id="latency-chart" class="w-full h-64"></div>
            </div>
        </div>

        <!-- Services and Circuits Row -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
            <!-- Service List -->
            <div class="glass rounded-2xl p-6 border border-slate-700">
                <div class="flex items-center justify-between mb-4">
                    <h3 class="text-lg font-semibold text-white flex items-center gap-2">
                        <span class="w-2 h-2 rounded-full bg-violet-400 pulse-dot"></span>
                        注册服务
                    </h3>
                    <button onclick="refreshServices()" class="px-3 py-1 text-sm bg-slate-700 hover:bg-slate-600 rounded-lg transition-colors">刷新</button>
                </div>
                <div id="service-list" class="space-y-3 max-h-80 overflow-y-auto">
                    <div class="text-center text-slate-500 py-8">加载中...</div>
                </div>
            </div>

            <!-- Circuit Breakers -->
            <div class="glass rounded-2xl p-6 border border-slate-700">
                <div class="flex items-center justify-between mb-4">
                    <h3 class="text-lg font-semibold text-white flex items-center gap-2">
                        <span class="w-2 h-2 rounded-full bg-yellow-400 pulse-dot"></span>
                        熔断器状态
                    </h3>
                    <a href="${base}/circuit-breakers" class="px-3 py-1 text-sm bg-slate-700 hover:bg-slate-600 rounded-lg transition-colors">管理</a>
                </div>
                <div id="circuit-list" class="space-y-3 max-h-80 overflow-y-auto">
                    <div class="text-center text-slate-500 py-8">加载中...</div>
                </div>
            </div>
        </div>

        <!-- Recent Traces -->
        <div class="glass rounded-2xl p-6 border border-slate-700">
            <div class="flex items-center justify-between mb-4">
                <h3 class="text-lg font-semibold text-white flex items-center gap-2">
                    <span class="w-2 h-2 rounded-full bg-emerald-400 pulse-dot"></span>
                    最近调用链路
                </h3>
                <button onclick="loadTraces()" class="px-3 py-1 text-sm bg-slate-700 hover:bg-slate-600 rounded-lg transition-colors">刷新</button>
            </div>
            <div class="overflow-x-auto">
                <table class="w-full text-sm">
                    <thead>
                        <tr class="text-slate-400 border-b border-slate-700">
                            <th class="text-left py-3 px-4">TraceId</th>
                            <th class="text-left py-3 px-4">服务</th>
                            <th class="text-left py-3 px-4">方法</th>
                            <th class="text-left py-3 px-4">延迟</th>
                            <th class="text-left py-3 px-4">状态</th>
                            <th class="text-left py-3 px-4">时间</th>
                        </tr>
                    </thead>
                    <tbody id="trace-table" class="text-slate-300">
                        <tr class="border-b border-slate-700/50">
                            <td colspan="6" class="text-center py-8 text-slate-500">加载中...</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>
    </div>

    <!-- Footer -->
    <footer class="glass border-t border-slate-700 mt-8 py-6">
        <div class="max-w-7xl mx-auto px-4 text-center text-slate-500 text-sm">
            <p>Snack RPC Admin · 实时监控平台 · <span id="current-time"></span></p>
        </div>
    </footer>

    <script>
        // Initialize Charts
        const qpsChart = echarts.init(document.getElementById('qps-chart'));
        const latencyChart = echarts.init(document.getElementById('latency-chart'));

        const qpsOption = {
            backgroundColor: 'transparent',
            tooltip: { trigger: 'axis', backgroundColor: '#1e293b', borderColor: '#475569' },
            legend: { data: ['QPS'], textStyle: { color: '#94a3b8' } },
            xAxis: { type: 'time', axisLine: { lineStyle: { color: '#334155' } }, axisLabel: { color: '#94a3b8' }, splitLine: { show: false } },
            yAxis: { type: 'value', axisLine: { lineStyle: { color: '#334155' } }, axisLabel: { color: '#94a3b8' }, splitLine: { lineStyle: { color: '#1e293b' } } },
            series: [{ name: 'QPS', type: 'line', smooth: true, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(59, 130, 246, 0.3)' }, { offset: 1, color: 'rgba(59, 130, 246, 0)' }] } }, lineStyle: { color: '#3b82f6', width: 2 }, itemStyle: { color: '#3b82f6' }, data: [] }]
        };

        const latencyOption = {
            backgroundColor: 'transparent',
            tooltip: { trigger: 'axis', backgroundColor: '#1e293b', borderColor: '#475569' },
            legend: { data: ['P50', 'P90', 'P99'], textStyle: { color: '#94a3b8' } },
            xAxis: { type: 'time', axisLine: { lineStyle: { color: '#334155' } }, axisLabel: { color: '#94a3b8' }, splitLine: { show: false } },
            yAxis: { type: 'value', axisLine: { lineStyle: { color: '#334155' } }, axisLabel: { color: '#94a3b8' }, splitLine: { lineStyle: { color: '#1e293b' } } },
            series: [
                { name: 'P50', type: 'line', smooth: true, lineStyle: { color: '#22c55e', width: 2 }, data: [] },
                { name: 'P90', type: 'line', smooth: true, lineStyle: { color: '#f59e0b', width: 2 }, data: [] },
                { name: 'P99', type: 'line', smooth: true, lineStyle: { color: '#ef4444', width: 2 }, data: [] }
            ]
        };

        qpsChart.setOption(qpsOption);
        latencyChart.setOption(latencyOption);

        // Data storage
        let qpsHistory = [];
        let latencyHistory = { p50: [], p90: [], p99: [] };

        // Load data
        async function loadMetrics() {
            try {
                const resp = await fetch('/api/metrics/all');
                const data = await resp.json();
                
                if (data.success) {
                    document.getElementById('qps-value').textContent = data.globalQps.toFixed(1);
                    document.getElementById('success-rate').textContent = (data.successRate * 100).toFixed(1) + '%';
                    document.getElementById('total-calls').textContent = formatNumber(data.totalCalls);
                    
                    // Update QPS history
                    const now = new Date();
                    qpsHistory.push([now.getTime(), data.globalQps]);
                    if (qpsHistory.length > 30) qpsHistory.shift();
                    qpsChart.setOption({ series: [{ data: qpsHistory }] });
                    
                    // Update latency history
                    if (data.avgLatency) {
                        latencyHistory.p50.push([now.getTime(), data.avgLatency]);
                        latencyHistory.p90.push([now.getTime(), data.p90Latency || data.avgLatency * 1.5]);
                        latencyHistory.p99.push([now.getTime(), data.p99Latency || data.avgLatency * 2]);
                        if (latencyHistory.p50.length > 30) {
                            latencyHistory.p50.shift();
                            latencyHistory.p90.shift();
                            latencyHistory.p99.shift();
                        }
                        latencyChart.setOption({ series: [
                            { name: 'P50', data: latencyHistory.p50 },
                            { name: 'P90', data: latencyHistory.p90 },
                            { name: 'P99', data: latencyHistory.p99 }
                        ]});
                    }
                }
            } catch (e) {
                console.error('Failed to load metrics:', e);
            }
        }

        async function loadServices() {
            try {
                const resp = await fetch('/api/services');
                const data = await resp.json();
                const container = document.getElementById('service-list');
                
                if (data.success && data.data && data.data.length > 0) {
                    container.innerHTML = data.data.map(s => `
                        <div class="bg-slate-800/50 rounded-xl p-4 border border-slate-700 hover:border-violet-500/50 transition-colors">
                            <div class="flex items-center justify-between">
                                <div>
                                    <h4 class="font-semibold text-white">${s.name}</h4>
                                    <p class="text-xs text-slate-400">${s.instances!0} 实例</p>
                                </div>
                                <span class="w-3 h-3 rounded-full bg-green-400 pulse-dot"></span>
                            </div>
                        </div>
                    `).join('');
                } else {
                    container.innerHTML = '<div class="text-center text-slate-500 py-8">暂无注册服务</div>';
                }
            } catch (e) {
                document.getElementById('service-list').innerHTML = '<div class="text-center text-red-400 py-8">加载失败</div>';
            }
        }

        async function loadCircuitBreakers() {
            try {
                const resp = await fetch('/api/circuit-breakers');
                const data = await resp.json();
                const container = document.getElementById('circuit-list');
                
                if (data.success && data.data && data.data.length > 0) {
                    container.innerHTML = data.data.map(cb => {
                        const stateClass = cb.state === 'CLOSED' ? 'text-green-400' : cb.state === 'OPEN' ? 'text-red-400' : 'text-yellow-400';
                        const glowClass = cb.state === 'CLOSED' ? 'glow-green' : cb.state === 'OPEN' ? 'glow-red' : 'glow-yellow';
                        return `
                            <div class="bg-slate-800/50 rounded-xl p-4 border border-slate-700 ${glowClass}">
                                <div class="flex items-center justify-between mb-2">
                                    <span class="font-semibold text-white">${cb.name}</span>
                                    <span class="px-2 py-1 rounded text-xs font-bold ${stateClass} bg-slate-900/50">${cb.state}</span>
                                </div>
                                <div class="grid grid-cols-3 gap-2 text-xs text-slate-400">
                                    <div>失败: ${cb.failedRequests!0}</div>
                                    <div>成功: ${cb.successfulRequests!0}</div>
                                    <div>阻止: ${cb.blockedRequests!0}</div>
                                </div>
                            </div>
                        `;
                    }).join('');
                    
                    // Update summary
                    const openCount = data.data.filter(cb => cb.state === 'OPEN').length;
                    const circuitBadge = document.getElementById('circuit-badge');
                    const circuitState = document.getElementById('circuit-state');
                    if (openCount > 0) {
                        circuitBadge.textContent = '有熔断';
                        circuitBadge.className = 'text-xs text-red-400 bg-red-400/10 px-2 py-1 rounded-full';
                        circuitState.textContent = 'OPEN';
                        circuitState.className = 'text-2xl font-bold text-red-400';
                    }
                } else {
                    container.innerHTML = '<div class="text-center text-slate-500 py-8">暂无熔断器数据</div>';
                }
            } catch (e) {
                document.getElementById('circuit-list').innerHTML = '<div class="text-center text-red-400 py-8">加载失败</div>';
            }
        }

        async function loadTraces() {
            try {
                const resp = await fetch('/api/traces/recent?limit=10');
                const data = await resp.json();
                const tbody = document.getElementById('trace-table');
                
                if (data.success && data.data && data.data.length > 0) {
                    tbody.innerHTML = data.data.map(t => `
                        <tr class="border-b border-slate-700/50 hover:bg-slate-800/50">
                            <td class="py-3 px-4 font-mono text-xs">${t.traceId ? t.traceId.substring(0, 8) + '...' : 'N/A'}</td>
                            <td class="py-3 px-4">${t.serviceName!"Unknown"}</td>
                            <td class="py-3 px-4">${t.method!"Unknown"}</td>
                            <td class="py-3 px-4">${(t.latency!0).toFixed(2)} ms</td>
                            <td class="py-3 px-4">
                                <span class="px-2 py-1 rounded text-xs ${t.success ? 'bg-green-400/20 text-green-400' : 'bg-red-400/20 text-red-400'}">
                                    ${t.success ? '成功' : '失败'}
                                </span>
                            </td>
                            <td class="py-3 px-4 text-slate-500">${t.timestamp ? new Date(t.timestamp).toLocaleTimeString() : 'N/A'}</td>
                        </tr>
                    `).join('');
                } else {
                    tbody.innerHTML = '<tr><td colspan="6" class="text-center py-8 text-slate-500">暂无调用记录</td></tr>';
                }
            } catch (e) {
                document.getElementById('trace-table').innerHTML = '<tr><td colspan="6" class="text-center py-8 text-red-400">加载失败</td></tr>';
            }
        }

        function refreshServices() {
            loadServices();
        }

        function formatNumber(num) {
            if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
            if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
            return num.toString();
        }

        function updateTime() {
            document.getElementById('current-time').textContent = new Date().toLocaleString();
        }

        // Initial load
        loadMetrics();
        loadServices();
        loadCircuitBreakers();
        loadTraces();
        updateTime();

        // Auto refresh
        setInterval(loadMetrics, 3000);
        setInterval(loadServices, 10000);
        setInterval(loadCircuitBreakers, 5000);
        setInterval(loadTraces, 15000);
        setInterval(updateTime, 1000);

        // Resize charts on window resize
        window.addEventListener('resize', () => {
            qpsChart.resize();
            latencyChart.resize();
        });
    </script>
</body>
</html>
