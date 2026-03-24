// Dashboard JavaScript

// Initialize Charts
var qpsChart = echarts.init(document.getElementById('qps-chart'));
var latencyChart = echarts.init(document.getElementById('latency-chart'));

var qpsOption = {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', backgroundColor: '#1e293b', borderColor: '#475569' },
    legend: { data: ['QPS'], textStyle: { color: '#94a3b8' } },
    xAxis: { type: 'time', axisLine: { lineStyle: { color: '#334155' } }, axisLabel: { color: '#94a3b8' }, splitLine: { show: false } },
    yAxis: { type: 'value', axisLine: { lineStyle: { color: '#334155' } }, axisLabel: { color: '#94a3b8' }, splitLine: { lineStyle: { color: '#1e293b' } } },
    series: [{ name: 'QPS', type: 'line', smooth: true, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(59, 130, 246, 0.3)' }, { offset: 1, color: 'rgba(59, 130, 246, 0)' }] } }, lineStyle: { color: '#3b82f6', width: 2 }, itemStyle: { color: '#3b82f6' }, data: [] }]
};

var latencyOption = {
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
var qpsHistory = [];
var latencyHistory = { p50: [], p90: [], p99: [] };

// Load data
function loadMetrics() {
    fetch('/api/metrics/all')
    .then(function(resp) { return resp.json(); })
    .then(function(data) {
        if (data.success) {
            document.getElementById('qps-value').textContent = data.globalQps ? data.globalQps.toFixed(1) : '0';
            document.getElementById('success-rate').textContent = data.successRate ? (data.successRate * 100).toFixed(1) + '%' : '100%';
            document.getElementById('total-calls').textContent = formatNumber(data.totalCalls || 0);
            
            var now = new Date();
            qpsHistory.push([now.getTime(), data.globalQps || 0]);
            if (qpsHistory.length > 30) qpsHistory.shift();
            qpsChart.setOption({ series: [{ data: qpsHistory }] });
        }
    })
    .catch(function(e) { console.error('Failed to load metrics:', e); });
}

function loadServices() {
    fetch('/api/services')
    .then(function(resp) { return resp.json(); })
    .then(function(data) {
        var container = document.getElementById('service-list');
        if (data.success && data.data && data.data.length > 0) {
            container.innerHTML = data.data.map(function(s) {
                return '<div class="bg-slate-800/50 rounded-xl p-4 border border-slate-700 hover:border-violet-500/50 transition-colors">' +
                    '<div class="flex items-center justify-between">' +
                    '<div><h4 class="font-semibold text-white">' + s.name + '</h4>' +
                    '<p class="text-xs text-slate-400">' + (s.instances || 0) + ' 实例</p></div>' +
                    '<span class="w-3 h-3 rounded-full bg-green-400 pulse-dot"></span></div></div>';
            }).join('');
        } else {
            container.innerHTML = '<div class="text-center text-slate-500 py-8">暂无注册服务</div>';
        }
    })
    .catch(function() {
        document.getElementById('service-list').innerHTML = '<div class="text-center text-red-400 py-8">加载失败</div>';
    });
}

function loadCircuitBreakers() {
    fetch('/api/circuit-breakers')
    .then(function(resp) { return resp.json(); })
    .then(function(data) {
        var container = document.getElementById('circuit-list');
        if (data.success && data.data && data.data.length > 0) {
            container.innerHTML = data.data.map(function(cb) {
                var stateClass = cb.state === 'CLOSED' ? 'text-green-400' : cb.state === 'OPEN' ? 'text-red-400' : 'text-yellow-400';
                var glowClass = cb.state === 'CLOSED' ? 'glow-green' : cb.state === 'OPEN' ? 'glow-red' : 'glow-yellow';
                return '<div class="bg-slate-800/50 rounded-xl p-4 border border-slate-700 ' + glowClass + '">' +
                    '<div class="flex items-center justify-between mb-2">' +
                    '<span class="font-semibold text-white">' + cb.name + '</span>' +
                    '<span class="px-2 py-1 rounded text-xs font-bold ' + stateClass + ' bg-slate-900/50">' + cb.state + '</span></div>' +
                    '<div class="grid grid-cols-3 gap-2 text-xs text-slate-400">' +
                    '<div>失败: ' + (cb.failedRequests || 0) + '</div>' +
                    '<div>成功: ' + (cb.successfulRequests || 0) + '</div>' +
                    '<div>阻止: ' + (cb.blockedRequests || 0) + '</div></div></div>';
            }).join('');
        } else {
            container.innerHTML = '<div class="text-center text-slate-500 py-8">暂无熔断器数据</div>';
        }
    })
    .catch(function() {
        document.getElementById('circuit-list').innerHTML = '<div class="text-center text-red-400 py-8">加载失败</div>';
    });
}

function loadTraces() {
    fetch('/api/traces/recent?limit=10')
    .then(function(resp) { return resp.json(); })
    .then(function(data) {
        var tbody = document.getElementById('trace-table');
        if (data.success && data.data && data.data.length > 0) {
            tbody.innerHTML = data.data.map(function(t) {
                var traceId = t.traceId ? t.traceId.substring(0, 8) + '...' : 'N/A';
                var statusClass = t.success ? 'bg-green-400/20 text-green-400' : 'bg-red-400/20 text-red-400';
                var statusText = t.success ? '成功' : '失败';
                var timeStr = t.timestamp ? new Date(t.timestamp).toLocaleTimeString() : 'N/A';
                return '<tr class="border-b border-slate-700/50 hover:bg-slate-800/50">' +
                    '<td class="py-3 px-4 font-mono text-xs">' + traceId + '</td>' +
                    '<td class="py-3 px-4">' + (t.serviceName || 'Unknown') + '</td>' +
                    '<td class="py-3 px-4">' + (t.method || 'Unknown') + '</td>' +
                    '<td class="py-3 px-4">' + ((t.latency || 0).toFixed(2)) + ' ms</td>' +
                    '<td class="py-3 px-4"><span class="px-2 py-1 rounded text-xs ' + statusClass + '">' + statusText + '</span></td>' +
                    '<td class="py-3 px-4 text-slate-500">' + timeStr + '</td></tr>';
            }).join('');
        } else {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center py-8 text-slate-500">暂无调用记录</td></tr>';
        }
    })
    .catch(function() {
        document.getElementById('trace-table').innerHTML = '<tr><td colspan="6" class="text-center py-8 text-red-400">加载失败</td></tr>';
    });
}

function formatNumber(num) {
    if (!num) return '0';
    if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
    if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
    return num.toString();
}

function updateTime() {
    var timeEl = document.getElementById('current-time');
    if (timeEl) timeEl.textContent = new Date().toLocaleString();
}

// Initial load
document.addEventListener('DOMContentLoaded', function() {
    loadMetrics();
    loadServices();
    loadCircuitBreakers();
    loadTraces();
    updateTime();

    setInterval(loadMetrics, 3000);
    setInterval(loadServices, 10000);
    setInterval(loadCircuitBreakers, 5000);
    setInterval(loadTraces, 15000);
    setInterval(updateTime, 1000);

    window.addEventListener('resize', function() {
        qpsChart.resize();
        latencyChart.resize();
    });
});
