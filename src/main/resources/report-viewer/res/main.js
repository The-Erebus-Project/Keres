var fast_threshold = 1000;
var slow_threshold = 2500;

var first_percentile_value = 50;
var second_percentile_value = 60;
var third_percentile_value = 75;
var fourth_percentile_value = 85;
var fifth_percentile_value = 95;

var response_times_distribution_chart = null;
var request_distribution_chart = null;
var response_times_chart = null;
var rps_chart = null;
var failures_chart = null;
var users_chart = null;
var failure_entries = new Map();

const chartColors = [
    'rgba(255, 159, 64, 0.8)',      // Soft orange
    'rgba(0, 139, 139, 0.8)',       // Dark cyan
    'rgba(54, 162, 235, 0.8)',      // Sky blue
    'rgba(255, 99, 132, 0.8)',      // Coral pink
    'rgba(153, 102, 255, 0.8)',     // Lavender
    'rgba(255, 205, 86, 0.8)',      // Golden yellow
    'rgba(75, 192, 192, 0.8)',      // Teal
    'rgba(201, 203, 207, 0.8)',     // Light gray
    'rgba(255, 140, 0, 0.8)',       // Dark orange
    'rgba(255, 69, 0, 0.8)',        // Bright red-orange
    'rgba(0, 128, 128, 0.8)',       // Dark teal
    'rgba(30, 144, 255, 0.8)',      // Dodger blue
    'rgba(147, 112, 219, 0.8)',     // Medium purple
    'rgba(220, 20, 60, 0.8)',       // Crimson
  ];

function setThresholdValues(low, high) {
    fast_threshold = low;
    slow_threshold = high;

    document.getElementById("fastThresholdValue").value = fast_threshold;
    document.getElementById("slowThresholdValue").value = slow_threshold;
}

function setPercentileValues(first, second, third, fourth, fifth) {
    first_percentile_value = first;
    second_percentile_value = second;
    third_percentile_value = third;
    fourth_percentile_value = fourth;
    fifth_percentile_value = fifth;

    document.getElementById("first_percentile_header").innerText = first + "%, ms";
    document.getElementById("second_percentile_header").innerText = second + "%, ms";
    document.getElementById("third_percentile_header").innerText = third + "%, ms";
    document.getElementById("fourth_percentile_header").innerText = fourth + "%, ms";
    document.getElementById("fifth_percentile_header").innerText = fifth + "%, ms";

    document.getElementById("firstPercentileValue").value = first;
    document.getElementById("secondPercentileValue").value = second;
    document.getElementById("thirdPercentileValue").value = third;
    document.getElementById("fourthPercentileValue").value = fourth;
    document.getElementById("fifthPercentileValue").value = fifth;
}

function updateThresholdValues() {
    setThresholdValues(document.getElementById("fastThresholdValue").value, document.getElementById("slowThresholdValue").value);
    drawResponseTimesDistributionChart();
    drawResponseTimesSection();
}

function updatePercentiles() {
    setPercentileValues(
        document.getElementById("firstPercentileValue").value,
        document.getElementById("secondPercentileValue").value,
        document.getElementById("thirdPercentileValue").value,
        document.getElementById("fourthPercentileValue").value,
        document.getElementById("fifthPercentileValue").value
    )

    populateResponseTimesTable();
}

function drawResponseTimesSection() {
    if (response_times_chart !== null) {
        response_times_chart.destroy();
    }
    response_times_chart = drawGraph(document.getElementById("response_times_chart"), "responseTimesLog", "Response time, ms", {
        line1: {
            label: {
                content: 'Fast',
                display: true,
                position: 'start'
            },
            type: 'line',
            yMin: fast_threshold,
            yMax: fast_threshold,
            borderColor: 'rgba(49, 222, 78, 1)',
            borderWidth: 2,
        },
        line2: {
            label: {
                content: 'Slow',
                display: true,
                position: 'start'
            },
            type: 'line',
            yMin: slow_threshold,
            yMax: slow_threshold,
            borderColor: 'rgba(237, 237, 50, 1)',
            borderWidth: 2,
        }
    },
    "response_times_chart_legend",
    false);
    populateResponseTimesTable();
}

function drawRPSGraph() {
    rps_chart = drawGraph(document.getElementById("rps_chart"), "requestsPerSecondLog", "Responses per second", {}, "rps_chart_legend");
}

function drawFailuresGraph() {
    failures_chart = drawGraph(document.getElementById("failures_chart"), "failuresLog", "Failures", {}, "failures_chart_legend");
}

function drawResponseTimesDistributionChart() {
    let fast_responses = 0;
    let normal_responses = 0;
    let slow_responses = 0;
    let total_failures = 0;

    for (const [key, value] of Object.entries(requests_log)) {
        if (key.startsWith("(ACTION)")) {
            continue;
        }

        fast_responses += value.filter(res => res[2] <= fast_threshold && res[3] !== true).length;
        normal_responses += value.filter(res => res[2] > fast_threshold && res[2] <= slow_threshold && res[3] !== true).length;
        slow_responses += value.filter(res => res[2] > slow_threshold && res[3] !== true).length;
        total_failures += value.filter(res => res[3] === true).length
    }

    let datasets = [
        {
            label: 'Responses',
            data: [fast_responses, normal_responses, slow_responses, total_failures],
            backgroundColor: [
                'rgba(49, 222, 78, 0.2)',
                'rgba(147, 222, 49, 0.2)',
                'rgba(237, 237, 50, 0.2)',
                'rgba(230, 67, 46, 0.2)'
            ],
            borderColor: [
                'rgba(49, 222, 78, 1)',
                'rgba(147, 222, 49, 1)',
                'rgb(237, 178, 50)',
                'rgba(230, 67, 46, 1)'
            ],
            borderWidth: 1
        }
    ];

    let labels = [
        'Fast (<=' + fast_threshold + 'ms)', 
        'Normal (>' + fast_threshold + 'ms, <=' + slow_threshold + 'ms)',
        'Slow(>' + slow_threshold + 'ms)',
        'Failed'
    ]

    if (response_times_distribution_chart !== null) {
        response_times_distribution_chart.destroy();
    }

    const datalabels = {
        formatter: (value, ctx) => {
            const datapoints = ctx.chart.data.datasets[0].data
            const total = datapoints.reduce((total, datapoint) => total + datapoint, 0)
            const percentage = value / total * 100
            return percentage.toFixed(2) + "%";
        },
        color: 'rgba(44, 36, 40, 0.8)',
    }
    response_times_distribution_chart = drawBarChart(document.getElementById("response_times_distribution_bar_chart"), datasets, labels, datalabels);
}

function drawRequestsDistributionChart() {
    let labels = [];
    let datasets = [
        {
            label: "Passed",
            data: [],
            backgroundColor: "rgba(49, 222, 78, 0.2)",
            borderColor: "rgba(49, 222, 78, 1)",
            borderWidth: 1
        },
        {
            label: "Failed",
            data: [],
            backgroundColor: "rgba(230, 67, 46, 0.2)",
            borderColor: "rgba(230, 67, 46, 1)",
            borderWidth: 1
        }
    ];

    for (const [key, value] of Object.entries(requests_log)) {
        if (key.startsWith("(ACTION)")) {
            continue;
        }
        
        labels.push(key);
        datasets[0].data.push(value.filter(entry => entry[3] === false).length);
        datasets[1].data.push(value.filter(entry => entry[3] === true).length);
    }

    if (request_distribution_chart !== null) {
        request_distribution_chart.destroy();
    }

    const datalabels = {
        formatter: (value, ctx) => {
            // Calculate stack-relative percentage (alternative value)
            const allValues = ctx.chart.data.datasets.map(ds => ds.data[ctx.dataIndex]);
            const stackTotal = allValues.reduce((sum, val) => sum + val, 0);
            const stackPercentage = stackTotal > 0 ? (value / stackTotal * 100) : 0;
            
            // Calculate dataset-relative percentage
            const datasetTotal = ctx.dataset.data.reduce((sum, val) => sum + val, 0);
            const datasetPercentage = datasetTotal > 0 ? (value / datasetTotal * 100) : 0;
            
            // Return both percentages formatted
            return [
                `${stackPercentage.toFixed(2)}%`,
                `${datasetPercentage.toFixed(2)}%`
            ].join('\n');
        },
        color: 'rgba(44, 36, 40, 0.8)',
        font: {
            size: 10 // Smaller font to fit both values
        }
    }

    request_distribution_chart = drawBarChart(document.getElementById("requests_distribution_bar_chart"), datasets, labels, datalabels);
}

function drawGraph(canvas, valuesArray, metricName, annotations, legendContainerElemendId, includeTotals = true) {
    let datasets = [];

    // Create a map for totals with initial zero values for each timestamp
    const totalsMap = new Map(timestamps.map(t => [t, 0]));

    for (const [key, value] of Object.entries(requests_averages_data)) {
        let color = chartColors[datasets.length % chartColors.length];
        let values_data_set = {
            label: key,
            data: [],
            fill: 'origin',
            borderColor: color,
            backgroundColor: color
        };

        // Create a lookup map for this dataset's entries
        const entryMap = new Map(value[valuesArray].map(entry => [entry.timeStamp, entry.logValue]));

        for (const timestamp of timestamps) {
            const logValue = entryMap.get(timestamp) || 0;
            values_data_set.data.push({x: timestamp, y: logValue});
            
            // Accumulate to totals
            totalsMap.set(timestamp, totalsMap.get(timestamp) + logValue);
        }
        
        datasets.push(values_data_set);
    }

    // Add the Totals dataset
    if (includeTotals === true) {
        datasets.unshift({
            label: "Total",
            data: Array.from(totalsMap.entries()).map(([x, y]) => ({x, y})),
            fill: false,
            borderColor: "rgba(39, 157, 245, 0.8)",
            backgroundColor: "rgba(39, 157, 245, 0.8)",
            borderWidth: 2,
            borderDash: [5, 5]
        });
    }

    return createChart(canvas, datasets, metricName, annotations, legendContainerElemendId);
}

function drawUsersGraph() {
    let dataset = {
        label: "Active users",
        data: [],
        fill: 'origin',
        borderColor: chartColors[0],
        backgroundColor: chartColors[0]
    };

    for (timestamp of timestamps) {
        let entry = users_timeline.find((entry) => entry.timeStamp === timestamp);
        if (entry === undefined) {
            dataset.data.push({x: timestamp, y: 0});
        } else {
            dataset.data.push({x: entry.timeStamp, y: entry.logValue});
        }
    }

    let datasets = [];
    datasets.push(dataset);

    users_chart = createChart(document.getElementById("users_chart"), datasets, "Active users, no.", {}, "users_chart_legend");
}

function populateResponseTimesTable() {
    const tableBody = document.getElementById("response_times_table_rows");
    tableBody.innerHTML = "";
    let rows = [];
    
    let total_min_values = [];
    let total_p1_values = [];
    let total_p2_values = [];
    let total_p3_values = [];
    let total_p4_values = [];
    let total_p5_values = [];
    let total_max_values = [];
    let total_avg_values = [];
    
    let total_requests = 0;
    let total_failures = 0;
    let endpoint_count = 0;

    for (const [key, value] of Object.entries(requests_averages_data)) {
        endpoint_count++;
        
        const responseTimes = value.responseTimesLog
            .filter(entry => entry.logValue > 0)
            .map(entry => entry.logValue);
        
        responseTimes.sort((a, b) => a - b);
        
        const count = responseTimes.length;
        const total_response_time = responseTimes.reduce((sum, val) => sum + val, 0);
        
        const getPercentile = (p) => count > 0 ? 
            responseTimes[Math.floor((count / 100) * p)] : 
            0;
            
        const p1 = getPercentile(first_percentile_value);
        const p2 = getPercentile(second_percentile_value);
        const p3 = getPercentile(third_percentile_value);
        const p4 = getPercentile(fourth_percentile_value);
        const p5 = getPercentile(fifth_percentile_value);
        
        const failures = value.failuresLog
            .filter(entry => entry.logValue > 0)
            .map(entry => entry.logValue);
            
        const total_failure_count = failures.reduce((sum, val) => sum + val, 0);
        const request_count = requests_log[key]?.length || 0;
        const failure_percentage = request_count > 0 ? 
            Math.floor((total_failure_count / request_count) * 100) : 
            0;
        
        if (count > 0) {
            total_min_values.push(responseTimes[0]);
            total_p1_values.push(p1);
            total_p2_values.push(p2);
            total_p3_values.push(p3);
            total_p4_values.push(p4);
            total_p5_values.push(p5);
            total_max_values.push(responseTimes[count - 1]);
            total_avg_values.push(Math.floor(total_response_time / count));
        }
        
        total_requests += request_count;
        total_failures += total_failure_count;
        
        rows.push(
            `<tr>
               <td>${key}</td>
               <td>${responseTimes[0] || 0}</td>
               <td>${p1}</td>
               <td>${p2}</td>
               <td>${p3}</td>
               <td>${p4}</td>
               <td>${p5}</td>
               <td>${count > 0 ? responseTimes[count - 1] : 0}</td>
               <td>${count > 0 ? Math.floor(total_response_time / count) : 0}</td>
               <td>${request_count}</td>
               <td>${total_failure_count}</td>
               <td>${failure_percentage}%</td>
            </tr>`
        );
    }

    const calculateAverage = (arr) => arr.length > 0 ? 
        Math.floor(arr.reduce((sum, val) => sum + val, 0) / arr.length) : 
        0;

    if (endpoint_count > 0) {
        rows.push(
            `<tr style="font-weight: bold;">
               <td>Total</td>
               <td>${calculateAverage(total_min_values)}</td>
               <td>${calculateAverage(total_p1_values)}</td>
               <td>${calculateAverage(total_p2_values)}</td>
               <td>${calculateAverage(total_p3_values)}</td>
               <td>${calculateAverage(total_p4_values)}</td>
               <td>${calculateAverage(total_p5_values)}</td>
               <td>${calculateAverage(total_max_values)}</td>
               <td>${calculateAverage(total_avg_values)}</td>
               <td>${total_requests}</td>
               <td>${total_failures}</td>
               <td>${total_requests > 0 ? Math.floor((total_failures / total_requests) * 100) : 0}%</td>
            </tr>`
        );
    }

    tableBody.innerHTML = rows.join("");
}

function populateResponseFailuresTable() {
    document.getElementById("failures_table_rows").innerHTML = "";
    let rows = [];
    let counter = 1;

    for (const [key, value] of Object.entries(failures)) {
        rows.push(
            `<tr>
               <td>${key}</td>
               <td>${value.occurrencesCount}</td>
               <td>${value.responseCode}</td>
               <td>
                   <button class="btn btn-primary" onclick="openErrorContentModal(${counter});">View</button>
               </td>
            </tr>`
        );
        
        failure_entries.set(counter, value.response);
        counter += 1;
    }

    document.getElementById("failures_table_rows").innerHTML = rows.join("");
}

function openErrorContentModal(number) {
    document.getElementById("errorModalContent").innerHTML = failure_entries.get(number);
    $('#errorModal').modal('show');
}

function generateTooltip(context) {
    // Tooltip Element
    let tooltipEl = document.getElementById('chartjs-tooltip');

    // Create element on first render
    if (!tooltipEl) {
        tooltipEl = document.createElement('div');
        tooltipEl.id = 'chartjs-tooltip';
        document.body.appendChild(tooltipEl);
    }

    tooltipEl.classList.add("chart_tooltip");

    // Hide if no tooltip
    const tooltipModel = context.tooltip;
    if (tooltipModel.opacity === 0) {
        tooltipEl.style.opacity = 0;
        return;
    }

    // Set caret Position
    tooltipEl.classList.remove('above', 'below', 'no-transform');
    if (tooltipModel.yAlign) {
        tooltipEl.classList.add(tooltipModel.yAlign);
    } else {
        tooltipEl.classList.add('no-transform');
    }

    function getBody(bodyItem) {
        return bodyItem.lines;
    }

    // Set Text
    if (tooltipModel.body) {
        const titleLines = tooltipModel.title || [];
        const bodyLines = tooltipModel.body.map(getBody);

        let innerHtml = "";

        titleLines.forEach(function(title) {
            innerHtml += '<h7>' + title + '</h7>';
        });
        
        innerHtml += 
            `<table>
               <thead>
                   <tr>
                       <th scope="col">Name</th>
                       <th scope="col">Value</th>
                   </tr>
               </thead>
               <tbody>`;

        bodyLines.forEach(function(body, i) {
            const colors = tooltipModel.labelColors[i];
            let style = 'background:' + colors.backgroundColor;
            style += '; border-color:' + colors.borderColor;
            style += '; border-width: 2px';
            let val = body[0].split(":");
            innerHtml += 
                `       <tr style="${style}">
                           <td>${val[0]}</td>
                           <td>${val[1]}</td>
                       <tr>`
            
        });
        innerHtml += '</tbody></table>';

        tooltipEl.innerHTML = innerHtml;
    }

    const position = context.chart.canvas.getBoundingClientRect();
    const bodyFont = Chart.helpers.toFont(tooltipModel.options.bodyFont);

    // Display, position, and set styles for font
    tooltipEl.style.opacity = 1;
    tooltipEl.style.position = 'absolute';
    tooltipEl.style.left = (((position.left + tooltipModel.caretX) > (window.screen.width / 2)) ? position.left + tooltipModel.caretX - tooltipEl.offsetWidth : position.left + tooltipModel.caretX) + 'px';
    tooltipEl.style.top = (((position.top + window.scrollY + tooltipModel.caretY) > (window.screen.height)) ? position.top + window.scrollY + tooltipModel.caretY - tooltipEl.offsetHeight : position.top + window.scrollY + tooltipModel.caretY) + 'px';
    tooltipEl.style.font = bodyFont.string;
    tooltipEl.style.padding = tooltipModel.padding + 'px ' + tooltipModel.padding + 'px';
    tooltipEl.style.pointerEvents = 'none';
}

function createChart(container, datasets, metricName, annotations, legendContainerElemendId) {
    return new Chart(
        container,
        {
            type: "line",
            data: {
                datasets: datasets
            },
            options: {
                maintainAspectRatio: false,
                responsive: true,
                plugins: {
                    legend: {
                        display: false,
                    },
                    datalabels: {
                        display: false
                    },
                    htmlLegend: {
                        // ID of the container to put the legend in
                        containerID: legendContainerElemendId,
                      },
                    subtitle: {
                        display: true,
                        text: "Alt + Scroll to zoom in/out, Alt + Click + Drag left-right to pan"
                    },
                    zoom: {
                        zoom: {
                            wheel: {
                                enabled: true,
                                modifierKey: "alt"
                            },
                            pinch: {
                                enabled: true
                            },
                            mode: 'x',
                            onZoom({ chart }) {
                                [response_times_chart, rps_chart, failures_chart, users_chart].forEach(chrt => {
                                    chrt.zoomScale(
                                        'x',
                                        { min: Math.trunc(chart.scales.x.min), max: Math.trunc(chart.scales.x.max) },
                                        'none'
                                      );
                                });
                            }
                            },
                            pan: {
                                enabled: true,
                                mode: "x",
                                modifierKey: "alt",
                                onPan({ chart }) {
                                    [response_times_chart, rps_chart, failures_chart, users_chart].forEach(chrt => {
                                        chrt.zoomScale(
                                            'x',
                                            { min: Math.trunc(chart.scales.x.min), max: Math.trunc(chart.scales.x.max) },
                                            'none'
                                          );
                                    });
                                }
                            }
                    },
                    tooltip: {
                        enabled: false,
                        external: generateTooltip
                    },
                    annotation: {
                        annotations: annotations
                    }
                },
                interaction: {
                    mode: 'index',
                    axis: 'x',
                    intersect: false
                },
                scales: {
                    x: {
                        type: 'time',
                        time: {
                            unit: 'second',
                            displayFormats: {
                                second: 'yyyy.MM.DD : HH.mm.ss'
                            },
                            tooltipFormat: 'yyyy.MM.DD : HH.mm.ss'
                        },
                        title: {
                            display: true,
                            text: 'Timestamp'
                        }
                    },
                    y: {
                        type: 'linear',
                        display: true,
                        position: 'left',
                        title: {
                            display: true,
                            text: metricName
                        }
                    }
                }
            },
            plugins: [htmlLegendPlugin],
        }
    );
}

function drawBarChart(canvas, datasets, labels, datalabels) {
    return new Chart(
        canvas,
        {
            type: "bar",
            data: {
                labels: labels,
                datasets: datasets
            },
            options: {
                plugins: {
                    subtitle: {
                        display: true,
                        text: "Alt + Scroll to zoom in/out, Alt + Click + Drag left-right to pan"
                    },
                    zoom: {
                        zoom: {
                            wheel: {
                                enabled: true,
                                modifierKey: "alt"
                            },
                            pinch: {
                                enabled: true
                            },
                            mode: 'x',
                            },
                            pan: {
                                enabled: true,
                                mode: "x",
                                modifierKey: "alt"
                            }
                    },
                    legend: {
                        display: true
                    },
                    datalabels: datalabels
                },
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    x: {
                        stacked: true,
                        ticks:{
                            display: false // Hides only the labels of the x-axis 
                        }
                    },
                    y: {
                        stacked: true
                    }
                }
            }
        }
    );
}

const getOrCreateLegendList = (chart, id) => {
    const legendContainer = document.getElementById(id);
    let listContainer = legendContainer.querySelector('ul');
  
    if (!listContainer) {
      listContainer = document.createElement('ul');
      listContainer.style.maxHeight = "200px";
      listContainer.style.overflow = "auto"
      listContainer.style.display = 'flex';
      listContainer.style.flexDirection = 'row';
      listContainer.style.flexWrap = "wrap";
      listContainer.style.margin = 0;
      listContainer.style.padding = 0;
  
      legendContainer.appendChild(listContainer);
    }
  
    return listContainer;
  };
  
  const htmlLegendPlugin = {
    id: 'htmlLegend',
    afterUpdate(chart, args, options) {
      const ul = getOrCreateLegendList(chart, options.containerID);
  
      // Remove old legend items
      while (ul.firstChild) {
        ul.firstChild.remove();
      }
  
      // Reuse the built-in legendItems generator
      const items = chart.options.plugins.legend.labels.generateLabels(chart);
  
      items.forEach(item => {
        const li = document.createElement('li');
        li.style.alignItems = 'center';
        li.style.cursor = 'pointer';
        li.style.display = 'flex';
        li.style.flexDirection = 'row';
        li.style.marginLeft = '10px';
  
        li.onclick = () => {
          const {type} = chart.config;
          if (type === 'pie' || type === 'doughnut') {
            // Pie and doughnut charts only have a single dataset and visibility is per item
            chart.toggleDataVisibility(item.index);
          } else {
            chart.setDatasetVisibility(item.datasetIndex, !chart.isDatasetVisible(item.datasetIndex));
          }
          chart.update();
        };
  
        // Color box
        const boxSpan = document.createElement('span');
        boxSpan.style.background = item.fillStyle;
        boxSpan.style.borderColor = item.strokeStyle;
        boxSpan.style.borderWidth = item.lineWidth + 'px';
        boxSpan.style.display = 'inline-block';
        boxSpan.style.flexShrink = 0;
        boxSpan.style.height = '20px';
        boxSpan.style.marginRight = '10px';
        boxSpan.style.width = '20px';
  
        // Text
        const textContainer = document.createElement('p');
        textContainer.style.color = item.fontColor;
        textContainer.style.margin = 0;
        textContainer.style.padding = 0;
        textContainer.style.textDecoration = item.hidden ? 'line-through' : '';
  
        const text = document.createTextNode(item.text);
        textContainer.appendChild(text);
  
        li.appendChild(boxSpan);
        li.appendChild(textContainer);
        ul.appendChild(li);
      });
    }
  };