<#assign base=request.contextPath />
<!DOCTYPE html>
<html lang="zh-CN" class="dark">
<head>
    <base id="base" href="${base}">
    <title>Snack RPC Admin - 系统概览</title>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <script src="https://cdn.tailwindcss.com"></script>
    <script src="https://cdn.jsdelivr.net/npm/echarts@5.4.3/dist/echarts.min.js"></script>
    <style>
        body { background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); }
        .glass { background: rgba(30, 41, 59, 0.8); backdrop-filter: blur(10px); }
        .pulse-dot { animation: pulse 2s infinite; }
        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
        .glow-green { box-shadow: 0 0 20px rgba(34, 197, 94, 0.3); }
        .glow-blue { box-shadow: 0 0 20px rgba(59, 130, 246, 0.3); }
        .glow-purple { box-shadow: 0 0 20px rgba(168, 85, 247, 0.3); }
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
                        <p class="text-xs text-slate-400">系统概览</p>
                    </div>
                </div>
                <div class="flex items-center gap-4">
                    <a href="${base}/dashboard" class="px-4 py-2 rounded-lg hover:bg-slate-700 transition-colors">监控面板</a>
                    <a href="${base}/services" class="px-4 py-2 rounded-lg hover:bg-slate-700 transition-colors">服务列表</a>
                    <a href="${base}/circuit-breakers" class="px-4 py-2 rounded-lg hover:bg-slate-700 transition-colors">熔断器</a>
                    <a href="${base}/system" class="px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-700 transition-colors">系统</a>
                </div>
            </div>
        </div>
    </nav>

    <div class="max-w-7xl mx-auto px-4 py-8">
        <div class="flex items-center justify-between mb-8">
            <div>
                <h2 class="text-3xl font-bold text-white mb-2">💻 系统概览</h2>
                <p class="text-slate-400">查看系统健康状态和资源使用情况</p>
            </div>
            <button onclick="loadSystemInfo()" class="px-4 py-2 bg-violet-600 hover:bg-violet-700 rounded-lg transition-colors flex items-center gap-2">
                <span>🔄</span> 刷新
            </button>
        </div>

        <!-- System Health Cards -->
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
            <div class="glass rounded-2xl p-6 border border-slate-700 glow-green text-center">
                <div class="w-16 h-16 rounded-full bg-green-500/20 flex items-center justify-center mx-auto mb-4">
                    <span class="text-3xl">✅</span>
                </div>
                <h3 id="overall-status" class="text-2xl font-bold text-green-400 mb-2">UP</h3>
                <p class="text-slate-400 text-sm">系统状态</p>
            </div>

            <div class="glass rounded-2xl p-6 border border-slate-700 glow-blue text-center">
                <div class="w-16 h-16 rounded-full bg-blue-500/20 flex items-center justify-center mx-auto mb-4">
                    <span class="text-3xl">💾</span>
                </div>
                <h3 id="memory-usage" class="text-2xl font-bold text-blue-400 mb-2">0%</h3>
                <p class="text-slate-400 text-sm">内存使用</p>
            </div>

            <div class="glass rounded-2xl p-6 border border-slate-700 text-center">
                <div class="w-16 h-16 rounded-full bg-purple-500/20 flex items-center justify-center mx-auto mb-4">
                    <span class="text-3xl">🧵</span>
                </div>
                <h3 id="cpu-cores" class="text-2xl font-bold text-purple-400 mb-2">0</h3>
                <p class="text-slate-400 text-sm">CPU 核心数</p>
            </div>

            <div class="glass rounded-2xl p-6 border border-slate-700 text-center">
                <div class="w-16 h-16 rounded-full bg-yellow-500/20 flex items-center justify-center mx-auto mb-4">
                    <span class="text-3xl">📊</span>
                </div>
                <h3 id="uptime" class="text-2xl font-bold text-yellow-400 mb-2">0s</h3>
                <p class="text-slate-400 text-sm">运行时长</p>
            </div>
        </div>

        <!-- Charts Row -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
            <!-- Memory Chart -->
            <div class="glass rounded-2xl p-6 border border-slate-700">
                <h3 class="text-lg font-semibold text-white mb-4">💾 JVM 内存使用</h3>
                <div id="memory-chart" class="w-full h-64"></div>
            </div>

            <!-- Component Status -->
            <div class="glass rounded-2xl p-6 border border-slate-700">
                <h3 class="text-lg font-semibold text-white mb-4">🔧 组件状态</h3>
                <div id="component-status" class="space-y-4">
                    <div class="text-center text-slate-500 py-8">加载中...</div>
                </div>
            </div>
        </div>

        <!-- System Details -->
        <div class="glass rounded-2xl p-6 border border-slate-700">
            <h3 class="text-lg font-semibold text-white mb-4">📋 详细信息</h3>
            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                <div>
                    <h4 class="text-slate-400 text-sm mb-2">Java 版本</h4>
                    <p id="java-version" class="text-white font-mono text-sm">-</p>
                </div>
                <div>
                    <h4 class="text-slate-400 text-sm mb-2">JVM 版本</h4>
                    <p id="jvm-version" class="text-white font-mono text-sm">-</p>
                </div>
                <div>
                    <h4 class="text-slate-400 text-sm mb-2">操作系统</h4>
                    <p id="os-name" class="text-white font-mono text-sm">-</p>
                </div>
                <div>
                    <h4 class="text-slate-400 text-sm mb-2">启动时间</h4>
                    <p id="start-time" class="text-white font-mono text-sm">-</p>
                </div>
                <div>
                    <h4 class="text-slate-400 text-sm mb-2">最大堆内存</h4>
                    <p id="max-heap" class="text-white font-mono text-sm">-</p>
                </div>
                <div>
                    <h4 class="text-slate-400 text-sm mb-2">非堆内存</h4>
                    <p id="non-heap" class="text-white font-mono text-sm">-</p>
                </div>
            </div>
        </div>
    </div>

    <script>
        const memoryChart = echarts.init(document.getElementById('memory-chart'));
        let startTime = Date.now();

        const memoryOption = {
            backgroundColor: 'transparent',
            tooltip: {
                trigger: 'item',
                formatter: function(params) {
                    return params.name + ': ' + formatBytes(params.value) + ' (' + params.percent + '%)';
                },
                backgroundColor: '#1e293b',
                borderColor: '#475569'
            },
            series: [
                {
                    name: 'Memory',
                    type: 'pie',
                    radius: ['40%', '70%'],
                    avoidLabelOverlap: false,
                    itemStyle: {
                        borderRadius: 10,
                        borderColor: '#1e293b',
                        borderWidth: 2
                    },
                    label: {
                        show: true,
                        formatter: '{b}: {d}%',
                        color: '#e2e8f0'
                    },
                    data: [
                        { value: 0, name: '已使用', itemStyle: { color: '#ef4444' } },
                        { value: 1, name: '可用', itemStyle: { color: '#22c55e' } }
                    ]
                }
            ]
        };
        memoryChart.setOption(memoryOption);

        async function loadSystemInfo() {
            try {
                const resp = await fetch('/api/health');
                const data = await resp.json();

                if (data.success && data.data) {
                    const health = data.data;
                    const components = health.components || {};
                    const system = components.system || {};

                    // Update stats
                    document.getElementById('overall-status').textContent = health.status || 'UP';
                    const statusEl = document.getElementById('overall-status');
                    if (health.status === 'UP') {
                        statusEl.className = 'text-2xl font-bold text-green-400 mb-2';
                    } else {
                        statusEl.className = 'text-2xl font-bold text-red-400 mb-2';
                    }

                    // Memory
                    const totalMem = system.totalMemory || 0;
                    const usedMem = system.usedMemory || 0;
                    const freeMem = system.freeMemory || 0;
                    const maxMem = system.maxMemory || 0;
                    const memPercent = maxMem > 0 ? ((usedMem / maxMem) * 100).toFixed(1) : 0;
                    document.getElementById('memory-usage').textContent = memPercent + '%';

                    memoryChart.setOption({
                        series: [{
                            data: [
                                { value: usedMem, name: '已使用', itemStyle: { color: '#ef4444' } },
                                { value: Math.max(0, maxMem - usedMem), name: '可用', itemStyle: { color: '#22c55e' } }
                            ]
                        }]
                    });

                    // CPU
                    document.getElementById('cpu-cores').textContent = system.availableProcessors || 'N/A';

                    // Uptime
                    const elapsed = Math.floor((Date.now() - startTime) / 1000);
                    document.getElementById('uptime').textContent = formatUptime(elapsed);

                    // Component status
                    renderComponentStatus(components);

                    // System details
                    document.getElementById('java-version').textContent = System.getProperty('java.version') || 'N/A';
                    document.getElementById('jvm-version').textContent = System.getProperty('java.vm.version') || 'N/A';
                    document.getElementById('os-name').textContent = System.getProperty('os.name') + ' ' + System.getProperty('os.version') || 'N/A';
                    document.getElementById('max-heap').textContent = formatBytes(maxMem);
                    document.getElementById('start-time').textContent = new Date(startTime).toLocaleString();
                }
            } catch (e) {
                console.error('Failed to load system info:', e);
                document.getElementById('component-status').innerHTML = 
                    '<div class="text-red-400">加载失败: ' + e.message + '</div>';
            }
        }

        function renderComponentStatus(components) {
            const container = document.getElementById('component-status');
            const html = Object.entries(components).map(([name, info]) => {
                const statusClass = info.status === 'UP' ? 'text-green-400' : 
                                   info.status === 'DOWN' ? 'text-red-400' : 'text-yellow-400';
                const statusIcon = info.status === 'UP' ? '✅' : 
                                  info.status === 'DOWN' ? '❌' : '⚠️';
                
                let details = '';
                if (info.circuitBreakerCount !== undefined) {
                    details = `<p class="text-xs text-slate-500">熔断器: ${info.circuitBreakerCount}</p>`;
                }
                if (info.recentSpanCount !== undefined) {
                    details = `<p class="text-xs text-slate-500">追踪数: ${info.recentSpanCount}</p>`;
                }

                return `
                    <div class="flex items-center justify-between bg-slate-800/50 rounded-lg p-4">
                        <div class="flex items-center gap-3">
                            <span class="${statusClass} text-xl">${statusIcon}</span>
                            <div>
                                <p class="text-white font-medium">${name}</p>
                                ${details}
                            </div>
                        </div>
                        <span class="${statusClass} font-bold">${info.status || 'UNKNOWN'}</span>
                    </div>
                `;
            }).join('');

            container.innerHTML = html || '<div class="text-slate-500">暂无组件数据</div>';
        }

        function formatBytes(bytes) {
            if (bytes === 0) return '0 B';
            const k = 1024;
            const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
        }

        function formatUptime(seconds) {
            if (seconds < 60) return seconds + 's';
            if (seconds < 3600) return Math.floor(seconds / 60) + 'm ' + (seconds % 60) + 's';
            if (seconds < 86400) return Math.floor(seconds / 3600) + 'h ' + Math.floor((seconds % 3600) / 60) + 'm';
            return Math.floor(seconds / 86400) + 'd ' + Math.floor((seconds % 86400) / 3600) + 'h';
        }

        // Initial load
        loadSystemInfo();

        // Auto refresh
        setInterval(loadSystemInfo, 10000);
        setInterval(() => {
            const elapsed = Math.floor((Date.now() - startTime) / 1000);
            document.getElementById('uptime').textContent = formatUptime(elapsed);
        }, 1000);

        // Resize charts
        window.addEventListener('resize', () => {
            memoryChart.resize();
        });
    </script>
</body>
</html>
