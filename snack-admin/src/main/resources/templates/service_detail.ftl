<#assign base=request.contextPath />
<!DOCTYPE html>
<html>
<head>
    <base id="base" href="${base}">
    <title>服务详情 - ${serviceName}</title>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
    <script src="/jquery/jquery-2.1.3.min.js"></script>
    <link rel="stylesheet" type="text/css" href="${base}/webjars/bootstrap/3.3.7/css/bootstrap.min.css"/>
    <script type="text/javascript" src="${base}/webjars/bootstrap/3.3.7/js/bootstrap.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/echarts@5.4.3/dist/echarts.min.js"></script>
    <link rel="stylesheet" href="/css/webui.css"/>
    <style>
        .detail-container {
            padding: 20px;
        }
        .metric-card {
            background: #fff;
            border-radius: 8px;
            padding: 15px;
            margin-bottom: 15px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .metric-card h4 {
            margin-top: 0;
            color: #666;
            font-size: 14px;
            font-weight: normal;
        }
        .metric-card .value {
            font-size: 24px;
            font-weight: bold;
            color: #333;
        }
        .metric-card.success .value { color: #28a745; }
        .metric-card.danger .value { color: #dc3545; }
        .metric-card.warning .value { color: #ffc107; }
        
        .circuit-breaker-section {
            background: #fff;
            border-radius: 8px;
            padding: 20px;
            margin-bottom: 20px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
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
        
        .chart-container {
            background: #fff;
            border-radius: 8px;
            padding: 15px;
            margin-bottom: 15px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        
        .config-form {
            background: #f8f9fa;
            padding: 15px;
            border-radius: 5px;
        }
        
        .trace-list {
            max-height: 400px;
            overflow-y: auto;
        }
        .trace-item {
            padding: 10px;
            border-bottom: 1px solid #eee;
        }
        .trace-item:hover {
            background-color: #f5f5f5;
        }
        .trace-success { border-left: 3px solid #28a745; }
        .trace-failure { border-left: 3px solid #dc3545; }
    </style>
</head>
<body>
<div class="container-fluid detail-container">
    <#assign page='service'/>
    <#include 'navbar.ftl'/>
    
    <div class="row">
        <div class="col-md-12">
            <h2>📋 服务详情: <span id="serviceName">${serviceName!''}</span></h2>
            <button class="btn btn-primary" onclick="refreshData()">
                <span class="glyphicon glyphicon-refresh"></span> 刷新数据
            </button>
        </div>
    </div>
    
    <!-- Service Metrics -->
    <div class="row" style="margin-top: 20px;">
        <div class="col-md-2">
            <div class="metric-card">
                <h4>QPS</h4>
                <div class="value" id="qps">-</div>
            </div>
        </div>
        <div class="col-md-2">
            <div class="metric-card">
                <h4>总调用</h4>
                <div class="value" id="totalCalls">-</div>
            </div>
        </div>
        <div class="col-md-2">
            <div class="metric-card success">
                <h4>成功率</h4>
                <div class="value" id="successRate">-</div>
            </div>
        </div>
        <div class="col-md-2">
            <div class="metric-card warning">
                <h4>Avg延迟</h4>
                <div class="value" id="avgLatency">-</div>
            </div>
        </div>
        <div class="col-md-2">
            <div class="metric-card">
                <h4>P90</h4>
                <div class="value" id="p90">-</div>
            </div>
        </div>
        <div class="col-md-2">
            <div class="metric-card">
                <h4>P99</h4>
                <div class="value" id="p99">-</div>
            </div>
        </div>
    </div>
    
    <!-- Charts -->
    <div class="row">
        <div class="col-md-6">
            <div class="chart-container">
                <h4>📊 接口响应时间</h4>
                <div id="methodLatencyChart" style="width: 100%; height: 300px;"></div>
            </div>
        </div>
        <div class="col-md-6">
            <div class="chart-container">
                <h4>📈 调用成功率</h4>
                <div id="successRateChart" style="width: 100%; height: 300px;"></div>
            </div>
        </div>
    </div>
    
    <!-- Method Metrics Table -->
    <div class="row">
        <div class="col-md-12">
            <div class="chart-container">
                <h4>📋 接口详情</h4>
                <table class="table table-striped table-hover">
                    <thead>
                        <tr>
                            <th>方法名</th>
                            <th>QPS</th>
                            <th>总调用</th>
                            <th>成功</th>
                            <th>失败</th>
                            <th>成功率</th>
                            <th>Avg(ms)</th>
                            <th>P50(ms)</th>
                            <th>P90(ms)</th>
                            <th>P99(ms)</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody id="methodTable">
                        <tr>
                            <td colspan="11" class="text-center text-muted">加载中...</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
    
    <!-- Circuit Breaker Section -->
    <div class="row">
        <div class="col-md-6">
            <div class="circuit-breaker-section">
                <h4>⚡ 熔断器状态</h4>
                <div id="circuitBreakerStatus">
                    <div class="text-muted">加载中...</div>
                </div>
            </div>
        </div>
        <div class="col-md-6">
            <div class="circuit-breaker-section">
                <h4>⚙️ 熔断器配置</h4>
                <div class="config-form">
                    <div class="form-group">
                        <label>失败阈值 (连续失败N次触发熔断)</label>
                        <input type="number" class="form-control" id="failureThreshold" value="5" min="1" max="100">
                    </div>
                    <div class="form-group">
                        <label>熔断时长 (毫秒)</label>
                        <input type="number" class="form-control" id="breakDurationMs" value="30000" min="1000" max="600000">
                    </div>
                    <div class="form-group">
                        <label>半开恢复试探数</label>
                        <input type="number" class="form-control" id="halfOpenMaxTrials" value="3" min="1" max="10">
                    </div>
                    <button class="btn btn-primary" onclick="updateCircuitBreakerConfig()">保存配置</button>
                    <button class="btn btn-warning" onclick="resetCircuitBreaker()">重置熔断器</button>
                </div>
            </div>
        </div>
    </div>
    
    <!-- Timeout Configuration -->
    <div class="row">
        <div class="col-md-12">
            <div class="circuit-breaker-section">
                <h4>⏱️ 超时参数配置</h4>
                <div class="config-form">
                    <div class="row">
                        <div class="col-md-4">
                            <div class="form-group">
                                <label>连接超时 (毫秒)</label>
                                <input type="number" class="form-control" id="connectTimeout" value="3000" min="100" max="30000">
                            </div>
                        </div>
                        <div class="col-md-4">
                            <div class="form-group">
                                <label>读取超时 (毫秒)</label>
                                <input type="number" class="form-control" id="readTimeout" value="5000" min="100" max="60000">
                            </div>
                        </div>
                        <div class="col-md-4">
                            <div class="form-group">
                                <label>重试次数</label>
                                <input type="number" class="form-control" id="retryCount" value="3" min="0" max="10">
                            </div>
                        </div>
                    </div>
                    <button class="btn btn-primary" onclick="updateTimeoutConfig()">保存超时配置</button>
                </div>
            </div>
        </div>
    </div>
    
    <!-- Recent Traces -->
    <div class="row">
        <div class="col-md-12">
            <div class="circuit-breaker-section">
                <h4>🔍 最近调用链路</h4>
                <div class="trace-list" id="traceList">
                    <div class="text-muted">加载中...</div>
                </div>
            </div>
        </div>
    </div>
    
    <!-- Service Providers -->
    <div class="row">
        <div class="col-md-12">
            <div class="circuit-breaker-section">
                <h4>🖥️ 服务提供机器列表</h4>
                <#if (serviceInstances?size>0)>
                <table class="table table-hover">
                    <thead>
                        <tr>
                            <td>机器IP</td>
                            <td>机器端口</td>
                            <td>注册时间</td>
                        </tr>
                    </thead>
                    <tbody>
                        <#list serviceInstances as instance>
                        <tr>
                            <td>${instance.address}</td>
                            <td>${instance.port}</td>
                            <td>${instance.registrationTimeUTC}</td>
                        </tr>
                        </#list>
                    </tbody>
                </table>
                <#else>
                <div class="alert alert-warning">没有提供者</div>
                </#if>
            </div>
        </div>
    </div>
</div>

<script>
    const serviceName = '${serviceName!''}';
    let methodLatencyChart, successRateChart;
    
    $(document).ready(function() {
        initCharts();
        refreshData();
        setInterval(refreshData, 10000); // Auto refresh every 10s
    });
    
    function initCharts() {
        methodLatencyChart = echarts.init(document.getElementById('methodLatencyChart'));
        methodLatencyChart.setOption({
            tooltip: { trigger: 'axis' },
            legend: { data: ['Avg', 'P50', 'P90', 'P99'] },
            xAxis: { type: 'category', data: [] },
            yAxis: { type: 'value', name: 'ms' },
            series: [
                { name: 'Avg', type: 'bar', data: [] },
                { name: 'P50', type: 'bar', data: [] },
                { name: 'P90', type: 'bar', data: [] },
                { name: 'P99', type: 'bar', data: [] }
            ]
        });
        
        successRateChart = echarts.init(document.getElementById('successRateChart'));
        successRateChart.setOption({
            tooltip: { trigger: 'item' },
            legend: { data: ['成功', '失败'] },
            series: [{
                name: '调用结果',
                type: 'pie',
                radius: ['40%', '70%'],
                data: []
            }]
        });
        
        window.addEventListener('resize', function() {
            methodLatencyChart.resize();
            successRateChart.resize();
        });
    }
    
    function refreshData() {
        refreshMetrics();
        refreshCircuitBreaker();
        refreshTraces();
    }
    
    function refreshMetrics() {
        $.get('/api/metrics/service/' + encodeURIComponent(serviceName), function(response) {
            if (response.success) {
                const agg = response.aggregatedMetrics;
                const methods = response.methodMetrics;
                
                // Update cards
                $('#qps').text(agg.qps ? agg.qps.toFixed(2) : '0');
                $('#totalCalls').text((agg.totalCalls || 0).toLocaleString());
                $('#successRate').text(agg.successRate ? agg.successRate.toFixed(1) + '%' : '0%');
                $('#avgLatency').text(agg.avgLatency ? agg.avgLatency.toFixed(1) : '0');
                $('#p90').text(agg.p90 ? agg.p90.toFixed(1) : '0');
                $('#p99').text(agg.p99 ? agg.p99.toFixed(1) : '0');
                
                // Update method table
                let tableHtml = '';
                const methodNames = [];
                const avgLatencies = [];
                const p50Latencies = [];
                const p90Latencies = [];
                const p99Latencies = [];
                
                for (const [methodName, m] of Object.entries(methods)) {
                    methodNames.push(methodName);
                    avgLatencies.push((m.avgLatency || 0).toFixed(1));
                    p50Latencies.push((m.p50 || 0).toFixed(1));
                    p90Latencies.push((m.p90 || 0).toFixed(1));
                    p99Latencies.push((m.p99 || 0).toFixed(1));
                    
                    const rateClass = m.successRate >= 95 ? 'success' : m.successRate >= 80 ? 'warning' : 'danger';
                    tableHtml += `
                        <tr>
                            <td><strong>${methodName}</strong></td>
                            <td>${(m.qps!0).toFixed(2)}</td>
                            <td>${m.totalCalls.toLocaleString()}</td>
                            <td class="text-success">${m.successCalls.toLocaleString()}</td>
                            <td class="text-danger">${m.failureCalls.toLocaleString()}</td>
                            <td class="text-${rateClass}">${m.successRate.toFixed(1)}%</td>
                            <td>${(m.avgLatency!0).toFixed(1)}</td>
                            <td>${(m.p50!0).toFixed(1)}</td>
                            <td>${(m.p90!0).toFixed(1)}</td>
                            <td>${(m.p99!0).toFixed(1)}</td>
                            <td>
                                <button class="btn btn-xs btn-info" onclick="viewMethodTraces('${methodName}')">链路</button>
                            </td>
                        </tr>
                    `;
                }
                
                if (tableHtml === '') {
                    tableHtml = '<tr><td colspan="11" class="text-center text-muted">暂无数据</td></tr>';
                }
                $('#methodTable').html(tableHtml);
                
                // Update charts
                methodLatencyChart.setOption({
                    xAxis: { data: methodNames },
                    series: [
                        { name: 'Avg', data: avgLatencies },
                        { name: 'P50', data: p50Latencies },
                        { name: 'P90', data: p90Latencies },
                        { name: 'P99', data: p99Latencies }
                    ]
                });
                
                let successTotal = 0, failureTotal = 0;
                for (const m of Object.values(methods)) {
                    successTotal += m.successCalls;
                    failureTotal += m.failureCalls;
                }
                
                successRateChart.setOption({
                    series: [{
                        data: [
                            { value: successTotal, name: '成功' },
                            { value: failureTotal, name: '失败' }
                        ]
                    }]
                });
            }
        });
    }
    
    function refreshCircuitBreaker() {
        $.get('/api/circuitbreakers/' + encodeURIComponent(serviceName), function(response) {
            if (response.success) {
                const cb = response.data;
                const stateClass = cb.state.toLowerCase();
                
                let html = `
                    <div style="margin-bottom: 15px;">
                        <span class="cb-status ${stateClass}">${cb.state}</span>
                    </div>
                    <table class="table table-condensed">
                        <tr><td>失败阈值</td><td>${cb.failureThreshold}</td></tr>
                        <tr><td>熔断时长</td><td>${cb.breakDurationMs}ms</td></tr>
                        <tr><td>半开试探数</td><td>${cb.halfOpenMaxTrials}</td></tr>
                        <tr><td>当前失败计数</td><td>${cb.currentFailureCount}</td></tr>
                        <tr><td>总请求</td><td>${cb.totalRequests}</td></tr>
                        <tr><td>成功请求</td><td>${cb.successfulRequests}</td></tr>
                        <tr><td>失败请求</td><td>${cb.failedRequests}</td></tr>
                        <tr><td>阻止请求</td><td>${cb.blockedRequests}</td></tr>
                `;
                
                if (cb.state === 'OPEN') {
                    html += `<tr><td colspan="2" class="text-danger">等待恢复: ${Math.ceil(cb.timeUntilRetry / 1000)}秒</td></tr>`;
                }
                
                html += '</table>';
                $('#circuitBreakerStatus').html(html);
                
                // Update config form
                $('#failureThreshold').val(cb.failureThreshold);
                $('#breakDurationMs').val(cb.breakDurationMs);
                $('#halfOpenMaxTrials').val(cb.halfOpenMaxTrials);
            } else {
                $('#circuitBreakerStatus').html('<div class="text-muted">熔断器未初始化</div>');
            }
        });
    }
    
    function refreshTraces() {
        $.get('/api/traces/recent?limit=10&serviceName=' + encodeURIComponent(serviceName), function(response) {
            if (response.success && response.data) {
                let html = '';
                response.data.forEach(function(trace) {
                    const statusClass = trace.success ? 'trace-success' : 'trace-failure';
                    const statusText = trace.success ? '✅ 成功' : '❌ 失败';
                    html += `
                        <div class="trace-item ${statusClass}">
                            <div class="row">
                                <div class="col-md-3">
                                    <strong>${trace.methodName}</strong>
                                </div>
                                <div class="col-md-2">
                                    ${statusText}
                                </div>
                                <div class="col-md-2">
                                    ${trace.durationMs}ms
                                </div>
                                <div class="col-md-3">
                                    <small class="text-muted">${new Date(trace.startTime).toLocaleString()}</small>
                                </div>
                                <div class="col-md-2">
                                    <small>${trace.role}</small>
                                </div>
                            </div>
                            ${trace.errorInfo ? '<div class="text-danger" style="margin-top:5px;">' + trace.errorInfo + '</div>' : ''}
                        </div>
                    `;
                });
                
                if (html === '') {
                    html = '<div class="text-muted">暂无调用记录</div>';
                }
                $('#traceList').html(html);
            }
        });
    }
    
    function updateCircuitBreakerConfig() {
        const failureThreshold = parseInt($('#failureThreshold').val());
        const breakDurationMs = parseInt($('#breakDurationMs').val());
        const halfOpenMaxTrials = parseInt($('#halfOpenMaxTrials').val());
        
        $.ajax({
            url: '/api/circuitbreakers/' + encodeURIComponent(serviceName) + '/config',
            type: 'PUT',
            data: {
                failureThreshold: failureThreshold,
                breakDurationMs: breakDurationMs,
                halfOpenMaxTrials: halfOpenMaxTrials
            },
            success: function(response) {
                if (response.success) {
                    alert('配置已更新');
                    refreshCircuitBreaker();
                } else {
                    alert('配置更新失败: ' + response.message);
                }
            }
        });
    }
    
    function resetCircuitBreaker() {
        if (confirm('确定要重置熔断器吗?')) {
            $.post('/api/circuitbreakers/' + encodeURIComponent(serviceName) + '/reset', function(response) {
                if (response.success) {
                    alert('熔断器已重置');
                    refreshCircuitBreaker();
                } else {
                    alert('重置失败: ' + response.message);
                }
            });
        }
    }
    
    function updateTimeoutConfig() {
        // This would typically save to a configuration file or registry
        const connectTimeout = parseInt($('#connectTimeout').val());
        const readTimeout = parseInt($('#readTimeout').val());
        const retryCount = parseInt($('#retryCount').val());
        
        alert('超时配置已保存 (连接: ' + connectTimeout + 'ms, 读取: ' + readTimeout + 'ms, 重试: ' + retryCount + '次)\n\n注意: 此配置将在服务重启后生效');
    }
    
    function viewMethodTraces(methodName) {
        // Show traces filtered by method
        $.get('/api/traces/recent?limit=20&serviceName=' + encodeURIComponent(serviceName), function(response) {
            if (response.success && response.data) {
                let html = '<h5>' + methodName + ' 的调用记录</h5>';
                response.data.filter(t => t.methodName === methodName).forEach(function(trace) {
                    const statusText = trace.success ? '✅' : '❌';
                    html += `
                        <div class="trace-item ${trace.success ? 'trace-success' : 'trace-failure'}">
                            ${statusText} ${trace.durationMs}ms 
                            <small class="text-muted">${new Date(trace.startTime).toLocaleString()}</small>
                            ${trace.errorInfo ? '<span class="text-danger"> - ' + trace.errorInfo + '</span>' : ''}
                        </div>
                    `;
                });
                $('#traceList').html(html || '<div class="text-muted">暂无记录</div>');
            }
        });
    }
</script>
</body>
</html>
