<template>
  <main class="command-center">
    <div ref="amapContainer" class="map-canvas"></div>
    <div class="map-tint"></div>

    <header class="top-command-bar">
      <div class="brand-block">
        <strong>飞码无人机管理平台</strong>
        <span>Feima Drone Management Platform</span>
      </div>
      <div class="clock-block">
        <strong>{{ currentTime }}</strong>
        <span>{{ currentDate }}</span>
      </div>
      <div class="status-chip">系统状态：正常运行</div>
      <div class="status-chip">在线设备：0</div>
      <label class="search-box">
        <input v-model="keyword" placeholder="搜索无人机、任务或区域..." />
        <button @click="selectedTool = 'search'">搜索</button>
      </label>
      <div class="admin-box">
        <span class="online-dot"></span>
        <strong>管理员</strong>
      </div>
    </header>

    <aside class="left-glass-panel">
      <nav class="panel-tabs">
        <button
          v-for="tab in leftTabs"
          :key="tab.key"
          :class="{ active: activeLeftTab === tab.key }"
          @click="activeLeftTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </nav>

      <section class="panel-section">
        <div class="section-title">
          <span></span>
          <strong>全局态势信息</strong>
        </div>
        <div class="mini-map">
          <div class="mini-road horizontal"></div>
          <div class="mini-road vertical"></div>
          <div class="mini-pin">H</div>
          <em>二维地图信息概要</em>
        </div>
      </section>

      <section class="panel-section">
        <div class="section-title">
          <span></span>
          <strong>气象信息概览</strong>
        </div>
        <select v-model="selectedDevice" class="glass-select">
          <option v-for="device in devices" :key="device">{{ device }}</option>
        </select>
        <div class="weather-grid">
          <div v-for="item in weatherItems" :key="item.label">
            <em>{{ item.icon }}</em>
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
        <p class="weather-note">天气晴朗，适合飞行作业 <small>{{ currentTime }}</small></p>
      </section>

      <section class="panel-section alert-section">
        <div class="section-title">
          <span></span>
          <strong>突发情况预警</strong>
          <small>{{ alerts.length }}</small>
        </div>
        <div class="alert-filter">
          <button class="active">全部</button>
          <button>未读</button>
          <button>已读</button>
        </div>
        <div class="alert-list">
          <article v-for="alert in alerts" :key="alert.id">
            <b></b>
            <div>
              <strong>{{ alert.title }}</strong>
              <span>{{ alert.device }}</span>
            </div>
            <em>{{ alert.time }}</em>
          </article>
        </div>
      </section>
    </aside>

    <aside class="right-glass-panel">
      <nav class="panel-tabs compact">
        <button
          v-for="tab in rightTabs"
          :key="tab.key"
          :class="{ active: activeRightTab === tab.key }"
          @click="activeRightTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </nav>

      <section class="panel-section">
        <div class="section-title">
          <span></span>
          <strong>航线概览</strong>
        </div>
        <div class="route-overview">
          <div v-for="metric in routeMetrics" :key="metric.label">
            <strong>{{ metric.value }}</strong>
            <span>{{ metric.label }}</span>
          </div>
        </div>
      </section>

      <section class="panel-section">
        <div class="section-title">
          <span></span>
          <strong>航线工具</strong>
        </div>
        <div class="tool-actions">
          <button @click="openRoute('/wayline')">导入航线</button>
          <button @click="openRoute('/task')">自航线列表</button>
        </div>
      </section>

      <section class="panel-section route-card-list">
        <article v-for="card in routeCards" :key="card.id" class="route-card">
          <div>
            <strong>{{ card.name }}</strong>
            <span :class="{ active: card.active }">{{ card.active ? '使用中' : '待使用' }}</span>
          </div>
          <p>{{ card.points }}个航点 / {{ card.duration }}分钟 / {{ card.distance }}</p>
          <div class="route-card-actions">
            <button @click="openRoute('/wayline')">下载</button>
            <button @click="openRoute('/task/create-plan')">创建任务</button>
            <button class="danger">删除</button>
          </div>
        </article>
      </section>

      <section class="panel-section planning-panel">
        <div class="section-title">
          <span></span>
          <strong>规划面板</strong>
        </div>
        <button @click="openRoute('/task/create-plan')">+ 新建航点航线</button>
      </section>
    </aside>

    <div class="map-tool-strip">
      <button
        v-for="tool in mapTools"
        :key="tool.key"
        :class="{ active: selectedTool === tool.key }"
        :title="tool.label"
        @click="selectTool(tool.key)"
      >
        {{ tool.icon }}
      </button>
    </div>

    <div class="compass-widget">
      <span>N</span>
      <strong>航向</strong>
    </div>

    <footer class="map-footer">
      <span>比例尺：1803米</span>
      <span>经度：117°17'20"E</span>
      <span>纬度：31°52'26"N</span>
    </footer>

    <div v-if="mapLoadError" class="map-error">
      <strong>高德地图加载失败</strong>
      <span>{{ mapLoadError }}</span>
    </div>
  </main>
</template>

<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import AMapLoader from '@amap/amap-jsapi-loader'
import { AMapConfig } from '/@/constants'

type LeftTab = 'device' | 'airspace' | 'algorithm'
type RightTab = 'route' | 'media'

const router = useRouter()
const amapContainer = ref<HTMLDivElement | null>(null)
const amapInstance = shallowRef<any>(null)
const mapInstance = shallowRef<any>(null)
const mapLoadError = ref('')
const clockTimer = ref<number | null>(null)
const activeLeftTab = ref<LeftTab>('device')
const activeRightTab = ref<RightTab>('route')
const currentTime = ref('')
const keyword = ref('')
const selectedTool = ref('zoom-in')
const selectedDevice = ref('设备 7CTDLCE00AG83Q')

const leftTabs = [
  { key: 'device' as const, label: '设备管理' },
  { key: 'airspace' as const, label: '空域管理' },
  { key: 'algorithm' as const, label: '算法库' },
]

const rightTabs = [
  { key: 'route' as const, label: '航线' },
  { key: 'media' as const, label: '媒体' },
]

const devices = ['设备 7CTDLCE00AG83Q', 'DJI Dock2', 'M30T-06']

const weatherItems = [
  { label: '温度', value: '34.7°C', icon: '☼' },
  { label: '湿度', value: '73.0%', icon: '~' },
  { label: '风速', value: '0.0m/s', icon: '↗' },
  { label: '降雨量', value: '0.0mm', icon: '▦' },
]

const alerts = [
  { id: 'a1', title: '高温预警（≥45°C）', device: 'DJI Dock2', time: '今天 18:28' },
  { id: 'a2', title: '空调外循环出风口温差过大', device: 'DJI Dock2', time: '今天 18:26' },
  { id: 'a3', title: '雨量过大，无法执行飞行任务', device: 'DJI Dock2', time: '今天 18:24' },
  { id: 'a4', title: '未知错误（dock_tip_0x19114816）', device: 'DJI Dock2', time: '今天 18:22' },
  { id: 'a5', title: '空域边界接近告警', device: 'M30T-06', time: '今天 18:20' },
]

const routeMetrics = [
  { label: '总航线数', value: '50' },
  { label: '活跃航线', value: '50' },
  { label: '固定航线', value: '50' },
  { label: '动态航线', value: '0' },
  { label: '总里程', value: '384.6km' },
  { label: '冲突检测', value: '0' },
]

const routeCards = [
  { id: '111', name: '111', points: 0, duration: 19, distance: '7.0km', active: true },
  { id: '093001', name: '093001', points: 0, duration: 48, distance: '2.8km', active: true },
  { id: '0111', name: '0111', points: 0, duration: 32, distance: '5.0km', active: true },
]

const mapTools = [
  { key: 'zoom-in', label: '放大', icon: '+' },
  { key: 'zoom-out', label: '缩小', icon: '-' },
  { key: 'locate', label: '定位', icon: '◎' },
  { key: 'measure', label: '测距', icon: '×' },
  { key: '2d', label: '2D', icon: '2D' },
  { key: 'draw', label: '绘制', icon: '✎' },
  { key: 'layer', label: '图层', icon: '▣' },
  { key: 'play', label: '播放', icon: '▶' },
]

const currentDate = computed(() => {
  const now = new Date()
  const weekday = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][now.getDay()]
  return `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日${weekday}`
})

function updateClock () {
  currentTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

function openRoute (path: string) {
  router.push(path)
}

function selectTool (key: string) {
  selectedTool.value = key
  const map = mapInstance.value
  if (!map) return
  if (key === 'zoom-in') map.zoomIn()
  if (key === 'zoom-out') map.zoomOut()
  if (key === 'locate') map.setCenter([117.283, 31.874])
}

function createMarker (AMap: any, item: { name: string, position: number[], type: 'dock' | 'drone' }) {
  return new AMap.Marker({
    position: item.position,
    anchor: 'center',
    content: `<div class="screen-marker ${item.type}">${item.name}</div>`,
  })
}

function renderMapOverlays (AMap: any, map: any) {
  const routePath = [
    [117.245, 31.858],
    [117.266, 31.875],
    [117.292, 31.866],
    [117.325, 31.892],
  ]
  const markers = [
    { name: '机场A', type: 'dock' as const, position: [117.245, 31.858] },
    { name: '无人机01', type: 'drone' as const, position: [117.292, 31.866] },
    { name: '机器狗R1', type: 'dock' as const, position: [117.315, 31.875] },
  ].map(item => createMarker(AMap, item))

  const routeLine = new AMap.Polyline({
    path: routePath,
    strokeColor: '#00d8ff',
    strokeWeight: 4,
    strokeOpacity: 0.95,
    showDir: true,
    lineJoin: 'round',
  })

  const taskArea = new AMap.Circle({
    center: [117.292, 31.866],
    radius: 1800,
    strokeColor: '#00d8ff',
    strokeWeight: 2,
    strokeOpacity: 0.85,
    fillColor: '#00a6ff',
    fillOpacity: 0.14,
  })

  map.add([routeLine, taskArea, ...markers])
  map.setFitView([routeLine, taskArea, ...markers], false, [80, 420, 80, 360])
}

async function initAmap () {
  await nextTick()
  if (!amapContainer.value || mapInstance.value) return

  try {
    const AMap = await AMapLoader.load(AMapConfig)
    amapInstance.value = AMap
    const map = new AMap.Map(amapContainer.value, {
      center: [117.283, 31.874],
      zoom: 12,
      resizeEnable: true,
      viewMode: '2D',
      mapStyle: 'amap://styles/darkblue',
      layers: [
        new AMap.TileLayer.Satellite(),
        new AMap.TileLayer.RoadNet(),
      ],
    })
    mapInstance.value = map
    renderMapOverlays(AMap, map)
  } catch (error: any) {
    mapLoadError.value = error?.message || '请检查高德地图 Key、网络或域名白名单配置。'
  }
}

onMounted(() => {
  updateClock()
  clockTimer.value = window.setInterval(updateClock, 1000)
  initAmap()
})

onBeforeUnmount(() => {
  if (clockTimer.value) {
    window.clearInterval(clockTimer.value)
    clockTimer.value = null
  }
  if (mapInstance.value) {
    mapInstance.value.destroy()
    mapInstance.value = null
  }
})
</script>

<style lang="scss" scoped>
.command-center {
  position: fixed;
  inset: 0;
  z-index: 100;
  min-height: 100vh;
  overflow: hidden;
  color: #d8f3ff;
  background: #03152a;
}

.map-canvas,
.map-tint {
  position: absolute;
  inset: 0;
}

.map-canvas {
  z-index: 0;
  background: #05213e;
}

.map-tint {
  z-index: 1;
  pointer-events: none;
  background:
    linear-gradient(90deg, rgba(3, 18, 36, 0.78), rgba(3, 18, 36, 0.12) 24%, rgba(3, 18, 36, 0.08) 72%, rgba(3, 18, 36, 0.76)),
    linear-gradient(180deg, rgba(0, 44, 83, 0.42), transparent 28%, rgba(0, 20, 42, 0.36)),
    repeating-linear-gradient(90deg, rgba(0, 216, 255, 0.04) 0 1px, transparent 1px 72px);
}

.top-command-bar,
.left-glass-panel,
.right-glass-panel,
.map-tool-strip,
.compass-widget,
.map-footer,
.map-error {
  position: absolute;
  z-index: 3;
}

.top-command-bar {
  top: 0;
  left: 0;
  right: 0;
  height: 64px;
  display: grid;
  grid-template-columns: 350px 150px 150px 128px minmax(280px, 500px) 120px;
  align-items: center;
  gap: 18px;
  padding: 0 34px;
  border-bottom: 1px solid rgba(0, 216, 255, 0.34);
  background: rgba(3, 24, 48, 0.84);
  box-shadow: 0 12px 38px rgba(0, 28, 58, 0.45);
  backdrop-filter: blur(10px);
}

.brand-block {
  padding-left: 22px;
  border-left: 3px solid rgba(0, 216, 255, 0.85);
}

.brand-block strong,
.brand-block span,
.clock-block strong,
.clock-block span {
  display: block;
}

.brand-block strong {
  color: #fff;
  font-size: 20px;
  line-height: 1.1;
  letter-spacing: 0;
}

.brand-block span,
.clock-block span {
  margin-top: 6px;
  color: #b8eaff;
  font-size: 12px;
}

.clock-block strong {
  color: #00d8ff;
  font-family: Consolas, monospace;
  font-size: 24px;
  font-weight: 500;
}

.status-chip {
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(0, 174, 255, 0.42);
  border-radius: 4px;
  color: #fff;
  background: rgba(4, 42, 78, 0.68);
  font-weight: 650;
}

.search-box {
  min-width: 0;
  height: 38px;
  display: grid;
  grid-template-columns: 1fr 42px;
  border: 1px solid rgba(0, 174, 255, 0.42);
  border-radius: 4px;
  background: rgba(11, 56, 94, 0.58);
}

.search-box input {
  min-width: 0;
  padding: 0 16px;
  border: 0;
  outline: 0;
  color: #d8f3ff;
  background: transparent;
}

.search-box input::placeholder {
  color: rgba(184, 234, 255, 0.52);
}

.search-box button {
  border: 0;
  color: #00d8ff;
  background: rgba(0, 126, 191, 0.4);
  cursor: pointer;
}

.admin-box {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  color: #fff;
}

.online-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #58d000;
  box-shadow: 0 0 12px #58d000;
}

.left-glass-panel,
.right-glass-panel {
  top: 76px;
  bottom: 16px;
  width: 280px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  border: 1px solid rgba(0, 216, 255, 0.24);
  background: rgba(2, 20, 39, 0.66);
  box-shadow: inset 0 0 26px rgba(0, 170, 255, 0.08), 0 18px 50px rgba(0, 20, 44, 0.38);
  backdrop-filter: blur(9px);
}

.left-glass-panel {
  left: 0;
}

.right-glass-panel {
  right: 0;
  width: 300px;
}

.panel-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  height: 42px;
  border-bottom: 1px solid rgba(0, 216, 255, 0.2);
}

.panel-tabs.compact {
  grid-template-columns: repeat(2, 1fr);
}

button {
  font: inherit;
}

.panel-tabs button,
.alert-filter button,
.tool-actions button,
.route-card-actions button,
.planning-panel button {
  border: 0;
  color: #b8eaff;
  background: transparent;
  cursor: pointer;
}

.panel-tabs button.active {
  color: #fff;
  background: linear-gradient(180deg, rgba(0, 150, 220, 0.34), rgba(0, 150, 220, 0.08));
  box-shadow: inset 0 -2px 0 #00d8ff;
}

.panel-section {
  padding: 10px 12px;
  border: 1px solid rgba(0, 216, 255, 0.16);
  background: rgba(5, 31, 58, 0.5);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: #00d8ff;
}

.section-title span {
  width: 3px;
  height: 18px;
  background: #00d8ff;
  box-shadow: 0 0 10px #00d8ff;
}

.section-title small {
  margin-left: auto;
  color: #ff5964;
}

.mini-map {
  position: relative;
  height: 120px;
  overflow: hidden;
  border-radius: 4px;
  border: 1px solid rgba(0, 216, 255, 0.2);
  background:
    linear-gradient(135deg, rgba(0, 216, 255, 0.22), transparent 45%),
    linear-gradient(45deg, #3a6f3b, #d0c887 48%, #6da4ca);
}

.mini-map em {
  position: absolute;
  left: 10px;
  top: 8px;
  color: #00d8ff;
  font-style: normal;
  font-weight: 650;
}

.mini-road {
  position: absolute;
  background: rgba(255, 222, 83, 0.82);
}

.mini-road.horizontal {
  left: 0;
  right: 0;
  top: 56px;
  height: 8px;
  transform: rotate(-8deg);
}

.mini-road.vertical {
  top: 0;
  bottom: 0;
  left: 118px;
  width: 7px;
  transform: rotate(16deg);
}

.mini-pin {
  position: absolute;
  right: 42px;
  top: 34px;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: #148cff;
}

.glass-select {
  width: 100%;
  height: 30px;
  margin-bottom: 10px;
  border: 1px solid rgba(0, 216, 255, 0.24);
  color: #e7f8ff;
  background: rgba(6, 38, 68, 0.86);
}

.weather-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.weather-grid div {
  min-height: 42px;
  padding: 7px;
  border: 1px solid rgba(0, 216, 255, 0.14);
  background: rgba(2, 19, 35, 0.42);
}

.weather-grid em,
.weather-grid span,
.weather-grid strong {
  display: inline-block;
}

.weather-grid em {
  color: #00d8ff;
  font-style: normal;
  margin-right: 6px;
}

.weather-grid span {
  color: #b8eaff;
  font-size: 12px;
}

.weather-grid strong {
  float: right;
  color: #fff;
}

.weather-note {
  margin: 10px 0 0;
  color: #fff;
  line-height: 1.6;
}

.weather-note small {
  float: right;
  color: #b8eaff;
}

.alert-section {
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

.alert-filter {
  display: flex;
  gap: 14px;
  margin-bottom: 8px;
}

.alert-filter button.active {
  color: #ff5964;
}

.alert-list {
  display: grid;
  gap: 8px;
  max-height: 310px;
  overflow: auto;
  padding-right: 4px;
}

.alert-list article {
  display: grid;
  grid-template-columns: 8px 1fr auto;
  gap: 8px;
  align-items: center;
  min-height: 54px;
  padding: 7px;
  border-left: 3px solid #ff5964;
  background: rgba(80, 22, 34, 0.54);
}

.alert-list b {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #ff5964;
}

.alert-list strong,
.alert-list span {
  display: block;
}

.alert-list strong {
  color: #ff737c;
  font-size: 12px;
}

.alert-list span,
.alert-list em {
  color: #b8eaff;
  font-size: 11px;
  font-style: normal;
}

.route-overview {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: rgba(0, 216, 255, 0.14);
}

.route-overview div {
  min-height: 58px;
  display: grid;
  place-items: center;
  background: rgba(5, 31, 58, 0.76);
}

.route-overview strong {
  color: #00d8ff;
  font-size: 22px;
}

.route-overview span,
.route-card p {
  color: #b8eaff;
  font-size: 12px;
}

.tool-actions,
.route-card-actions {
  display: flex;
  gap: 8px;
}

.tool-actions button,
.route-card-actions button,
.planning-panel button {
  height: 28px;
  padding: 0 10px;
  border-radius: 4px;
  color: #e7f8ff;
  background: rgba(0, 139, 208, 0.46);
}

.route-card-list {
  display: grid;
  gap: 10px;
  min-height: 0;
  overflow: auto;
}

.route-card {
  padding: 10px;
  border-radius: 6px;
  border: 1px solid rgba(0, 216, 255, 0.16);
  background: rgba(2, 19, 35, 0.46);
}

.route-card > div:first-child {
  display: flex;
  justify-content: space-between;
  color: #fff;
}

.route-card > div:first-child span {
  padding: 2px 8px;
  border-radius: 4px;
  color: #79ff7b;
  background: rgba(68, 183, 80, 0.2);
  font-size: 12px;
}

.route-card p {
  margin: 8px 0;
}

.route-card-actions button:nth-child(2) {
  background: rgba(73, 206, 82, 0.62);
}

.route-card-actions button.danger {
  background: rgba(255, 89, 100, 0.8);
}

.planning-panel button {
  width: 100%;
}

.map-tool-strip {
  top: 112px;
  right: 322px;
  display: grid;
  gap: 8px;
  padding: 8px;
  border-radius: 8px;
  background: rgba(230, 246, 255, 0.92);
  box-shadow: 0 16px 36px rgba(0, 20, 44, 0.25);
}

.map-tool-strip button {
  width: 52px;
  height: 52px;
  border: 1px solid rgba(0, 139, 208, 0.2);
  border-radius: 5px;
  color: #1686dc;
  background: #f5fbff;
  cursor: pointer;
}

.map-tool-strip button.active {
  color: #fff;
  border-color: #148cff;
  background: #67c1ff;
}

.compass-widget {
  top: 86px;
  left: 338px;
  width: 78px;
  height: 78px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #148cff;
  background: rgba(241, 249, 255, 0.92);
  box-shadow: 0 10px 30px rgba(0, 20, 44, 0.22);
}

.compass-widget span {
  position: absolute;
  top: 7px;
  font-size: 12px;
}

.compass-widget strong {
  font-size: 13px;
}

.map-footer {
  left: 296px;
  right: 296px;
  bottom: 8px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  border-radius: 14px;
  color: #e7f8ff;
  background: rgba(3, 24, 48, 0.8);
}

.map-error {
  left: 50%;
  bottom: 70px;
  transform: translateX(-50%);
  display: grid;
  gap: 4px;
  max-width: 420px;
  padding: 12px 16px;
  border: 1px solid rgba(255, 89, 100, 0.38);
  border-radius: 6px;
  color: #ff737c;
  background: rgba(40, 10, 20, 0.88);
}

:deep(.screen-marker) {
  height: 28px;
  display: inline-flex;
  align-items: center;
  padding: 0 10px;
  border-radius: 15px;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 0 18px rgba(0, 216, 255, 0.6);
}

:deep(.screen-marker.dock) {
  background: #0e8bff;
}

:deep(.screen-marker.drone) {
  background: #00b894;
}

@media (max-width: 1360px) {
  .top-command-bar {
    grid-template-columns: 270px 130px 120px 110px minmax(220px, 1fr) 90px;
    gap: 10px;
    padding: 0 18px;
  }

  .brand-block strong {
    font-size: 17px;
  }

  .left-glass-panel {
    width: 270px;
  }

  .right-glass-panel {
    width: 286px;
  }

  .map-tool-strip {
    right: 304px;
  }

  .map-footer {
    left: 286px;
    right: 286px;
  }
}

@media (max-width: 960px) {
  .top-command-bar {
    height: auto;
    grid-template-columns: 1fr 1fr;
    padding: 12px;
  }

  .brand-block,
  .search-box {
    grid-column: 1 / -1;
  }

  .left-glass-panel,
  .right-glass-panel {
    top: 170px;
    bottom: 14px;
    width: 260px;
    margin: 0;
  }

  .left-glass-panel {
    left: 10px;
  }

  .right-glass-panel {
    right: 10px;
  }

  .map-tool-strip {
    right: 282px;
    top: 190px;
  }

  .map-tool-strip button {
    width: 42px;
    height: 42px;
  }

  .compass-widget {
    display: none;
  }

  .map-footer {
    left: 284px;
    right: 284px;
  }
}
</style>
