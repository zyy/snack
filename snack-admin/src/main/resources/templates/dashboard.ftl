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
    </style>
</head>
<body class="text-slate-100 min-h-screen">
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
        <div class="mb-8">
            <h2 class="text-3xl font-bold text-white mb-2">📊 实时监控面板</h2>
            <p class="text-slate-400">实时监控 RPC 服务的健康状态，性能指标和调用统计</p>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
            <div class="stat-card glass rounded-2xl p-6 border border-slate-700 glow-blue">
                <div class="flex items-center justify-between mb-4">
                    <div class="w-12 h-12 rounded-xl bg-blue-500/20 flex items-center justify-center"><span class="text-2xl">⚡</span></div>
                </div>
                <h3 class="text-slate-400 text-sm mb-1">当前 QPS</h3>
                <p id="qps-value" class="text-4xl font-bold text-blue-400">0</p>
            </div>
            <div class="stat-card glass rounded-2xl p-6 border border-slate-700 glow-green">
                <div class="flex items-center justify-between mb-4">
                    <div class="w-12 h-12 rounded-xl bg-green-500/20 flex items-center justify-center"><span class="text-2xl">✅</span></div>
                </div>
                <h3 class="text-slate-400 text-sm mb-1">成功率</h3>
                <p id="success-rate" class="text-4xl font-bold text-green-400">100%</p>
            </div>
            <div class="stat-card glass rounded-2xl p-6 border border-slate-700 glow-purple">
                <div class="flex items-center justify-between mb-4">
                    <div class="w-12 h-12 rounded-xl bg-purple-500/20 flex items-center justify-center"><span class="text-2xl">📈</span></div>
                </div>
                <h3 class="text-slate-400 text-sm mb-1">总调用量</h3>
                <p id="total-calls" class="text-4xl font-bold text-purple-400">0</p>
            </div>
            <div class="stat-card glass rounded-2xl p-6 border border-slate-700 glow-yellow">
                <div class="flex items-center justify-between mb-4">
                    <div class="w-12 h-12 rounded-xl bg-yellow-500/20 flex items-center justify-center"><span class="text-2xl">🔴</span></div>
                </div>
                <h3 class="text-slate-400 text-sm mb-1">熔断器状态</h3>
                <p id="circuit-state" class="text-2xl font-bold text-yellow-400">CLOSED</p>
            </div>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
            <div class="glass rounded-2xl p-6 border border-slate-700">
                <h3 class="text-lg font-semibold text-white mb-4">QPS 趋势</h3>
                <div id="qps-chart" class="w-full h-64"></div>
            </div>
            <div class="glass rounded-2xl p-6 border border-slate-700">
                <h3 class="text-lg font-semibold text-white mb-4">响应时间 (ms)</h3>
                <div id="latency-chart" class="w-full h-64"></div>
            </div>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
            <div class="glass rounded-2xl p-6 border border-slate-700">
                <div class="flex items-center justify-between mb-4">
                    <h3 class="text-lg font-semibold text-white">注册服务</h3>
                    <button onclick="refreshServices()" class="px-3 py-1 text-sm bg-slate-700 hover:bg-slate-600 rounded-lg">刷新</button>
                </div>
                <div id="service-list" class="space-y-3 max-h-80 overflow-y-auto"></div>
            </div>
            <div class="glass rounded-2xl p-6 border border-slate-700">
                <div class="flex items-center justify-between mb-4">
                    <h3 class="text-lg font-semibold text-white">熔断器状态</h3>
                    <a href="${base}/circuit-breakers" class="px-3 py-1 text-sm bg-slate-700 hover:bg-slate-600 rounded-lg">管理</a>
                </div>
                <div id="circuit-list" class="space-y-3 max-h-80 overflow-y-auto"></div>
            </div>
        </div>

        <div class="glass rounded-2xl p-6 border border-slate-700">
            <div class="flex items-center justify-between mb-4">
                <h3 class="text-lg font-semibold text-white">最近调用链路</h3>
                <button onclick="loadTraces()" class="px-3 py-1 text-sm bg-slate-700 hover:bg-slate-600 rounded-lg">刷新</button>
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
                        <tr class="border-b border-slate-700/50"><td colspan="6" class="text-center py-8 text-slate-500">加载中...</td></tr>
                    </tbody>
                </table>
            </div>
        </div>
    </div>

    <footer class="glass border-t border-slate-700 mt-8 py-6">
        <div class="max-w-7xl mx-auto px-4 text-center text-slate-500 text-sm">
            <p>Snack RPC Admin · 实时监控平台 · <span id="current-time"></span></p>
        </div>
    </footer>

    <script src="/js/dashboard.js"></script>
</body>
</html>
