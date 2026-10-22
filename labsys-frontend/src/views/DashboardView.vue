<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
// 按需引入：只拿用到的三种图表 + 四个组件，全量 import 会让这个页面单独打 1MB
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { ElMessage } from 'element-plus'
import { get } from '../api'

echarts.use([
  PieChart, BarChart, LineChart,
  TitleComponent, TooltipComponent, LegendComponent, GridComponent,
  CanvasRenderer
])

const DEVICE_STATUS_LABEL = {
  AVAILABLE: '在库',
  BORROWED: '借出',
  REPAIRING: '维修中',
  SCRAPPED: '已报废'
}

const summary = ref({
  deviceTotal: 0, reservationTotal: 0, ticketTotal: 0, ticketPending: 0, ticketOverdue: 0
})

const elStatus = ref(null)
const elBorrow = ref(null)
const elFault = ref(null)
const elTrend = ref(null)

let charts = []

function mount(el, option) {
  if (!el) return
  const c = echarts.init(el)
  c.setOption(option)
  charts.push(c)
}

function onResize() {
  charts.forEach((c) => c.resize())
}

async function load() {
  const res = await get('/api/stats/overview')
  if (res.code !== 200) {
    ElMessage.error(res.message || '加载看板失败')
    return
  }
  const d = res.data
  summary.value = d.summary

  // 设备状态：饼图。数据为空时给个占位，否则图上什么都看不到
  const statusData = (d.deviceStatus || []).map((x) => ({
    name: DEVICE_STATUS_LABEL[x.name] || x.name,
    value: Number(x.value)
  }))
  mount(elStatus.value, {
    title: { text: '设备状态分布', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['38%', '62%'],
      center: ['50%', '48%'],
      data: statusData.length ? statusData : [{ name: '暂无数据', value: 1 }],
      label: { formatter: '{b} {c}' }
    }]
  })

  // 借用排行 / 故障 TOP：横向柱状图，名字长的设备名不会被挤成一行
  const barOption = (title, list, color) => ({
    title: { text: title, left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 24, top: 46, bottom: 8, containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: {
      type: 'category',
      data: (list || []).map((x) => x.name).reverse(),
      axisLabel: { width: 90, overflow: 'truncate' }
    },
    series: [{
      type: 'bar',
      barMaxWidth: 18,
      itemStyle: { color, borderRadius: [0, 4, 4, 0] },
      data: (list || []).map((x) => Number(x.value)).reverse()
    }]
  })

  mount(elBorrow.value, barOption('借用排行（前 5）', d.borrowRanking, '#2f6feb'))
  mount(elFault.value, barOption('故障 TOP（前 5）', d.faultRanking, '#c1352b'))

  // 工单趋势：折线图。后端只返回有工单的日期，这里不补 0，先看真实形状
  const trend = d.ticketTrend || []
  mount(elTrend.value, {
    title: { text: '近 7 天工单趋势', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 24, top: 46, bottom: 8, containLabel: true },
    xAxis: { type: 'category', data: trend.map((x) => x.name), boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'line',
      smooth: true,
      symbolSize: 7,
      areaStyle: { opacity: 0.15 },
      itemStyle: { color: '#0f8a5f' },
      data: trend.map((x) => Number(x.value))
    }]
  })

  window.addEventListener('resize', onResize)
}

onMounted(load)

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
  charts = []
})
</script>

<template>
  <div>
    <h2 class="page-title">数据看板</h2>

    <div class="cards">
      <div class="card">
        <div class="k">设备总数</div>
        <div class="v">{{ summary.deviceTotal }}</div>
      </div>
      <div class="card">
        <div class="k">预约总数</div>
        <div class="v">{{ summary.reservationTotal }}</div>
      </div>
      <div class="card">
        <div class="k">待受理工单</div>
        <div class="v warn">{{ summary.ticketPending }}</div>
      </div>
      <div class="card">
        <div class="k">超时工单</div>
        <div class="v danger">{{ summary.ticketOverdue }}</div>
      </div>
    </div>

    <div class="grid">
      <div class="chart" ref="elStatus"></div>
      <div class="chart" ref="elBorrow"></div>
      <div class="chart" ref="elFault"></div>
      <div class="chart" ref="elTrend"></div>
    </div>
  </div>
</template>

<style scoped>
.page-title { margin: 0 0 16px; font-size: 20px; }

.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin-bottom: 18px; }
.card { background: #fff; border: 1px solid #e3e7ec; border-radius: 11px; padding: 14px 16px; }
.card .k { font-size: 12px; color: #8a939f; font-weight: 700; }
.card .v { font-size: 24px; font-weight: 700; color: #2f6feb; margin-top: 4px; }
.card .v.warn { color: #a35c00; }
.card .v.danger { color: #c1352b; }

.grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 14px; }
.chart { background: #fff; border: 1px solid #e3e7ec; border-radius: 11px; height: 280px; }
</style>
