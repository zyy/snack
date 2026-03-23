<#assign base=request.contextPath />
<!DOCTYPE html>
<html>
<head>
    <base id="base" href="${base}">
    <title>Snack RPC Admin - 实时监控</title>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
    <meta http-equiv="refresh" content="30">
    <script src="/jquery/jquery-2.1.3.min.js"></script>
    <link rel="stylesheet" type="text/css" href="${base}/webjars/bootstrap/3.3.7/css/bootstrap.min.css"/>
    <script type="text/javascript" src="${base}/webjars/bootstrap/3.3.7/js/bootstrap.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/echarts@5.4.3/dist/echarts.min.js"></script>
    <link rel="stylesheet" href="/css/webui.css"/>
    <style>
        .dashboard-container {
            padding: 20px;
        }
        .stat-card {
            background: #fff;
            border-radius: 8px;
            padding: 20px;
            margin-bottom: 20px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .stat-card h3 {
            margin-top: 0;
            color: #666;
            font-size: 14px;
            font-weight: normal;
        }
        .stat-card .value {
            font-size: 32px;
            font-weight: bold;
            color: #333;
        }
        .stat-card.success .value { color: #28a745; }
        .stat-card.danger .value { color: #dc3545; }
        .stat-card.warning .value { color: #ffc107; }
        .stat-card.info .value { color: #17a2b8; }
        
        .chart-container {
            background: #fff;
            border-radius: 8px;
            padding: 20px;
            margin-bottom: 20px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .chart-title {
            font-size: 16px;
            font-weight: bold;
            margin-bottom: 15px;
            color: #333;
        }
        
        .circuit-breaker-panel {
            background: #fff;
            border-radius: 8px;
            padding: 15px;
            margin-bottom: 15px;
            border-left: 4px solid #28a745;
        }
        .circuit-breaker-panel.open { border-left-color: #dc3545; }
        .circuit-breaker-panel.half-open { border-left-color: #ffc107; }
        
        .cb-status {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: bold;
        }
        .cb-status.closed { background: #d4edda; color: #155724; }
        .cb-status.open { background: #f8d7da; color: #721c24; }
        .cb-status.half-open { background: #fff3cd; color: #856404; }
        
        .service-row:hover {
            background-color: #f5f5f5;
            cursor: pointer;
        }
        
        .refresh-controls {
            margin-bottom: 20px;
        }
        
        .health-badge {
            display: inline-block;
            padding: 8px 16px;
            border-radius: 4px;
            font-size: 14px;
            font-weight: bold;
        }
        .health-badge.up { background: #d4edda; color: #155724; }
        .health-badge.down { background: #f8d7da; color: #721c24; }
        
        .loading {
            text-align: center;
            padding: 40px;
            color: #666;
        }
    </style>
</head>
<body>
<div class="container-fluid dashboard-container">
    <#assign page='dashboard'/>
    <#include 'navbar.ftl'/>
    
    <div class="row">
        <div class="col-md-12">
            <h2>📊 实时监控面板</h2>
            <div class="refresh-controls">
                <button class="btn btn-primary" onclick="refreshDashboard()">
                    <span class="glyphicon glyphicon-refresh"></span> 刷新数据
                </button>
                <label style="margin-left: 20px;">
                    自动刷新:
                    <select id="autoRefresh" class="form-control" style="display:inline;width:auto;" onchange="toggleAutoRefresh()">
                        <option value="0">关闭</option>
                        <option value="5">5秒</option>
                        <option value="10" selected>10秒</option>
                        <option value="30">30秒</option>
                    </select>
                </label>
                <span id="lastUpdate" style="margin-left: 20px; color: #666;"></span>
            </div>
        </div>
    </div>
    
    <!-- Stats Cards -->
    <div class="row">
        <div class="col-md-3">
            <div class="stat-card info">
                <h3>全局 QPS</h3>
                <div class="value" id="globalQps">-</div>
            </div>
        </div>
        <div class="col-md-3">
            <div class="stat-card">
                <h3>总调用次数</h3>
                <div class="value" id="totalCalls">-</div>
            </div>
        </div>
        <div class="col-md-3">
            <div class="stat-card success">
                <h3>成功率</h3>
                <div class="value" id="successRate">-</div>
            </div>
        </div>
        <div class="col-md-3">
            <div class="stat-card warning">
                <h3>平均延迟</h3>
                <div class="value" id="avgLatency">-</div>
            </div>
        </div>
    </div>
    
    <!-- Charts Row -->
    <div class="row">
        <div class="col-md-8">
            <div class="chart-container">
                <div class="chart-title">📈 QPS 趋势 (最近60秒)</div>
                <div id="qpsChart" style="width: 100%; height: 300px;"></div>
            </div>
        </div>
        <div class="col-md-4">
            <div class="chart-container">
                <div class="chart-title">🔄 熔断器状态分布</div>
                <div id="cbStateChart" style="width: 100%; height: 300px;"></div>
            </div>
        </div>
    </div>
    
    <!-- Latency Chart and Service Table -->
    <div class="row">
        <div class="col-md-6">
            <div class="chart-container">
                <div class="chart-title">⏱️ 响应时间分布 (ms)</div>
                <div id="latencyChart" style="width: 100%; height: 300px;"></div>
            </div>
        </div>
        <div class="col-md-6">
            <div class="chart-container">
                <div class="chart-title">🏆 Top 5 服务 (按 QPS)</div>
                <div id="topServicesChart" style="width: 100%; height: 300px;"></div>
            </div>
        </div>
    </div>
    
    <!-- Service Metrics Table -->
    <div class="row">
        <div class="col-md-12">
            <div class="chart-container">
                <div class="chart-title">📋 服务指标详情</div>
                <table class="table table-striped table-hover">
                    <thead>
                        <tr>
                            <th>服务名称</th>
                            <th>QPS</th>
                            <th>总调用</th>
                            <th>成功</th>
                            <th>失败</th>
                            <th>成功率</th>
                            <th>Avg(ms)</th>
                            <th>P50(ms)</th>
                            <th>P90(ms)</th>
                            <th>P99(ms)</th>
                            <th>熔断器状态</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody id="serviceTable">
                        <tr>
                            <td colspan="12" class="loading">加载中...</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
    
    <!-- Circuit Breaker Management -->
    <div class="row">
        <div class="col-md-6">
            <div class="chart-container">
                <div class="chart-title">⚡ 熔断器详情</div>
                <div id="circuitBreakerList">
                    <div class="loading">加载中...</div>
                </div>
            </div>
        </div>
        <div class="col-md-6">
            <div class="chart-container">
                <div class="chart-title">💓 系统健康状态</div>
                <div id="healthStatus">
                    <div class="loading">加载中...</div>
                </div>
            </div>
        </div>
    </div>
</div>

<script>
    // Chart instances
    let qpsChart, latencyChart, topServicesChart, cbStateChart;
    let autoRefreshInterval = null;
    let qpsHistory = [];
    
    // Initialize charts
    $(document).ready(function() {
        initCharts();
        refreshDashboard();
    });
    
    function initCharts() {
        // QPS Chart
        qpsChart = echarts.init(document.getElementById('qpsChart'));
        qpsChart.setOption({
            tooltip: { trigger: 'axis' },
            xAxis: {
                type: 'category',
                data: [],
                axisLabel: { rotate: 45 }
            },
            yAxis: { type: 'value', name: 'QPS' },
            series: [{ name: 'QPS', type: 'line', smooth: true, data: [] }]
        });
        
        // Latency Chart
        latencyChart = echarts.init(document.getElementById('latencyChart'));
        latencyChart.setOption({
            tooltip: { trigger: 'axis' },
            legend: { data: ['P50', 'P90', 'P99'] },
            xAxis: { type: 'category', data: [] },
            yAxis: { type: 'value', name: 'ms' },
            series: [
                { name: 'P50', type: 'bar', data: [] },
                { name: 'P90', type: 'bar', data: [] },
                { name: 'P99', type: 'bar', data: [] }
            ]
        });
        
        // Top Services Chart
        topServicesChart = echarts.init(document.getElementById('topServicesChart'));
        topServicesChart.setOption({
            tooltip: { trigger: 'item' },
            series: [{
                name: 'QPS',
                type: 'pie',
                radius: ['40%', '70%'],
                data: []
            }]
        });
        
        // Circuit Breaker State Chart
        cbStateChart = echarts.init(document.getElementById('cbStateChart'));
        cbStateChart.setOption({
            tooltip: { trigger: 'item' },
            series: [{
                name: '状态',
                type: 'pie',
                radius: '60%',
                data: [
                    { value: 0, name: 'CLOSED' },
                    { value: 0, name: 'OPEN' },
                    { value: 0, name: 'HALF_OPEN' }
                ]
            }]
        });
        
        // Handle window resize
        window.addEventListener('resize', function() {
            qpsChart.resize();
            latencyChart.resize();
            topServicesChart.resize();
            cbStateChart.resize();
        });
    }
    
    function toggleAutoRefresh() {
        const interval = parseInt($('#autoRefresh').val());
        if (autoRefreshInterval) {
            clearInterval(autoRefreshInterval);
            autoRefreshInterval = null;
        }
        if (interval > 0) {
            autoRefreshInterval = setInterval(refreshDashboard, interval * 1000);
        }
    }
    
    function refreshDashboard() {
        refreshStats();
        refreshCircuitBreakers();
        refreshHealth();
    }
    
    function refreshStats() {
        $.get('/api/metrics/all', function(response) {
            if (response.success) {
                const data = response.data;
                
                // Update stats cards
                $('#globalQps').text(response.globalQps ? response.globalQps.toFixed(2) : '0');
                
                let totalCalls = 0, successCalls = 0, failureCalls = 0;
                let totalLatency = 0, count = 0;
                let p50Sum = 0, p90Sum = 0, p99Sum = 0;
                
                // Service table
                let tableHtml = '';
                const services = [];
                
                for (const [serviceName, metrics] of Object.entries(data)) {
                    totalCalls += metrics.totalCalls || 0;
                    successCalls += metrics.successCalls || 0;
                    failureCalls += metrics.failureCalls || 0;
                    totalLatency += metrics.avgLatency || 0;
                    count++;
                    
                    const successRate = metrics.totalCalls > 0 
                        ? (metrics.successCalls / metrics.totalCalls * 100).toFixed(1) 
                        : '0.0';
                    
                    services.push({
                        name: serviceName,
                        qps: metrics.qps || 0,
                        totalCalls: metrics.totalCalls,
                        successCalls: metrics.successCalls,
                        failureCalls: metrics.failureCalls,
                        successRate: successRate,
                        avgLatency: (metrics.avgLatency || 0).toFixed(1),
                        p50: (metrics.p50 || 0).toFixed(1),
                        p90: (metrics.p90 || 0).toFixed(1),
                        p99: (metrics.p99 || 0).toFixed(1)
                    });
                    
                    p50Sum += metrics.p50 || 0;
                    p90Sum += metrics.p90 || 0;
                    p99Sum += metrics.p99 || 0;
                }
                
                // Sort by QPS
                services.sort((a, b) => b.qps - a.qps);
                
                const avgLatency = count > 0 ? (totalLatency / count).toFixed(1) : '0.0';
                const successRate = totalCalls > 0 ? (successCalls / totalCalls * 100).toFixed(1) : '0.0';
                
                $('#totalCalls').text(totalCalls.toLocaleString());
                $('#successRate').text(successRate + '%');
                $('#avgLatency').text(avgLatency);
                
                // Update service table
                if (services.length === 0) {
                    tableHtml = '<tr><td colspan="12" class="text-center text-muted">暂无数据</td></tr>';
                } else {
                    services.forEach(function(svc) {
                        const cbStatus = getCircuitBreakerStatus(svc.name);
                        const rateClass = parseFloat(svc.successRate) >= 95 ? 'success' : 
                                         parseFloat(svc.successRate) >= 80 ? 'warning' : 'danger';
                        tableHtml += `
                            <tr class="service-row">
                                <td><strong>${svc.name}</strong></td>
                                <td>${svc.qps.toFixed(2)}</td>
                                <td>${svc.totalCalls.toLocaleString()}</td>
                                <td class="text-success">${svc.successCalls.toLocaleString()}</td>
                                <td class="text-danger">${svc.failureCalls.toLocaleString()}</td>
                                <td class="text-${rateClass}">${svc.successRate}%</td>
                                <td>${svc.avgLatency}</td>
                                <td>${svc.p50}</td>
                                <td>${svc.p90}</td>
                                <td>${svc.p99}</td>
                                <td>${cbStatus}</td>
                                <td>
                                    <a href="/service/detail?serviceName=${encodeURIComponent(svc.name)}" class="btn btn-xs btn-info">详情</a>
                                    <button class="btn btn-xs btn-warning" onclick="resetCircuitBreaker('${svc.name}')">重置熔断器</button>
                                </td>
                            </tr>
                        `;
                    });
                }
                $('#serviceTable').html(tableHtml);
                
                // Update QPS history
                const now = new Date().toLocaleTimeString();
                qpsHistory.push({ time: now, value: response.globalQps || 0 });
                if (qpsHistory.length > 20) qpsHistory.shift();
                
                qpsChart.setOption({
                    xAxis: { data: qpsHistory.map(h => h.time) },
                    series: [{ data: qpsHistory.map(h => h.value.toFixed(2)) }]
                });
                
                // Update latency chart (show top 5 services)
                const top5 = services.slice(0, 5);
                latencyChart.setOption({
                    xAxis: { data: top5.map(s => s.name) },
                    series: [
                        { name: 'P50', data: top5.map(s => s.p50) },
                        { name: 'P90', data: top5.map(s => s.p90) },
                        { name: 'P99', data: top5.map(s => s.p99) }
                    ]
                });
                
                // Update top services chart
                topServicesChart.setOption({
                    series: [{
                        data: top5.map(s => ({ name: s.name, value: s.qps.toFixed(2) }))
                    }]
                });
                
                $('#lastUpdate').text('最后更新: ' + new Date().toLocaleTimeString());
            }
        });
        
        // Also fetch circuit breaker states for the table
        $.get('/api/circuitbreakers', function(response) {
            if (response.success) {
                updateCircuitBreakerChart(response.stateCounts);
            }
        });
    }
    
    function refreshCircuitBreakers() {
        $.get('/api/circuitbreakers', function(response) {
            if (response.success && response.data) {
                let html = '';
                response.data.forEach(function(cb) {
                    const stateClass = cb.state.toLowerCase();
                    html += `
                        <div class="circuit-breaker-panel ${stateClass}">
                            <h4>
                                <strong>${cb.name}</strong>
                                <span class="cb-status ${stateClass}">${cb.state}</span>
                            </h4>
                            <div class="row">
                                <div class="col-md-3">
                                    <small>失败阈值: ${cb.failureThreshold}</small>
                                </div>
                                <div class="col-md-3">
                                    <small>熔断时长: ${cb.breakDurationMs}ms</small>
                                </div>
                                <div class="col-md-3">
                                    <small>半开试探: ${cb.halfOpenMaxTrials}</small>
                                </div>
                                <div class="col-md-3">
                                    <small>当前失败: ${cb.currentFailureCount}/${cb.failureThreshold}</small>
                                </div>
                            </div>
                            <div class="row" style="margin-top:10px;">
                                <div class="col-md-3">
                                    <small>总请求: ${cb.totalRequests}</small>
                                </div>
                                <div class="col-md-3">
                                    <small>成功: ${cb.successfulRequests}</small>
                                </div>
                                <div class="col-md-3">
                                    <small>失败: ${cb.failedRequests}</small>
                                </div>
                                <div class="col-md-3">
                                    <small>阻止: ${cb.blockedRequests}</small>
                                </div>
                            </div>
                            ${cb.state === 'OPEN' ? '<div class="text-danger" style="margin-top:10px;">等待恢复: ' + Math.ceil(cb.timeUntilRetry / 1000) + '秒</div>' : ''}
                            <div style="margin-top:10px;">
                                <button class="btn btn-xs btn-primary" onclick="resetCircuitBreaker('${cb.name}')">重置</button>
                                <button class="btn btn-xs btn-danger" onclick="forceOpenCircuitBreaker('${cb.name}')">强制开启</button>
                            </div>
                        </div>
                    `;
                });
                if (html === '') {
                    html = '<div class="text-muted">暂无熔断器数据</div>';
                }
                $('#circuitBreakerList').html(html);
            }
        });
    }
    
    function refreshHealth() {
        $.get('/api/health', function(response) {
            if (response.success) {
                const health = response.data;
                let html = `
                    <div class="health-badge ${health.status === 'UP' ? 'up' : 'down'}">
                        ${health.status === 'UP' ? '✅ 系统正常' : '❌ 系统异常'}
                    </div>
                    <table class="table table-condensed" style="margin-top:15px;">
                        <tr>
                            <td>组件</td>
                            <td>状态</td>
                            <td>详情</td>
                        </tr>
                `;
                
                for (const [name, component] of Object.entries(health.components)) {
                    const statusClass = component.status === 'UP' ? 'success' : 'danger';
                    let details = '';
                    if (name === 'traceCollector') {
                        details = `span数量: ${component.recentSpanCount}`;
                    } else if (name === 'circuitBreakerRegistry') {
                        const counts = component.stateCounts;
                        details = `CLOSED: ${counts.CLOSED}, OPEN: ${counts.OPEN}, HALF_OPEN: ${counts.HALF_OPEN}`;
                    } else if (name === 'system') {
                        const used = ((component.usedMemory / component.maxMemory) * 100).toFixed(1);
                        details = `内存: ${used}% (${(component.usedMemory / 1024 / 1024).toFixed(0)}MB)`;
                    }
                    html += `
                        <tr>
                            <td>${name}</td>
                            <td><span class="label label-${statusClass}">${component.status}</span></td>
                            <td><small>${details}</small></td>
                        </tr>
                    `;
                }
                html += '</table>';
                $('#healthStatus').html(html);
            }
        });
    }
    
    function updateCircuitBreakerChart(stateCounts) {
        cbStateChart.setOption({
            series: [{
                data: [
                    { value: stateCounts.CLOSED || 0, name: 'CLOSED' },
                    { value: stateCounts.OPEN || 0, name: 'OPEN' },
                    { value: stateCounts.HALF_OPEN || 0, name: 'HALF_OPEN' }
                ]
            }]
        });
    }
    
    function getCircuitBreakerStatus(serviceName) {
        // This will be populated after refreshCircuitBreakers
        return '<span class="cb-status closed">-</span>';
    }
    
    function resetCircuitBreaker(serviceName) {
        if (confirm('确定要重置 ' + serviceName + ' 的熔断器吗?')) {
            $.post('/api/circuitbreakers/' + encodeURIComponent(serviceName) + '/reset', function(response) {
                if (response.success) {
                    alert('熔断器已重置');
                    refreshCircuitBreakers();
                } else {
                    alert('重置失败: ' + response.message);
                }
            });
        }
    }
    
    function forceOpenCircuitBreaker(serviceName) {
        if (confirm('确定要强制开启 ' + serviceName + ' 的熔断器吗?')) {
            $.ajax({
                url: '/api/circuitbreakers/' + encodeURIComponent(serviceName) + '/config',
                type: 'PUT',
                data: { failureThreshold: 1, breakDurationMs: 60000, halfOpenMaxTrials: 1 },
                success: function(response) {
                    if (response.success) {
                        alert('熔断器已强制开启');
                        refreshCircuitBreakers();
                    } else {
                        alert('操作失败');
                    }
                }
            });
        }
    }
</script>
</body>
</html>
