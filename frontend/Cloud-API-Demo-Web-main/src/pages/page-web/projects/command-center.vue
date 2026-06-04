<template>
  <main
    class="flight-workbench"
    :class="{
      'day-mode': themeMode === 'day',
      'left-rail-collapsed': isLeftRailCollapsed,
      'right-rail-collapsed': isRightRailCollapsed,
    }"
  >
    <div ref="amapContainer" class="map-canvas"></div>
    <div class="map-overlay"></div>

    <header class="workbench-topbar">
      <div class="project-title">
        <strong>低空巡检项目工作台</strong>
        <span>类司空 2 的云端无人机协同作业界面</span>
      </div>
      <nav class="module-tabs">
        <button
          v-for="module in modules"
          :key="module.key"
          :class="{ active: activeModule === module.key, disabled: module.key === 'command' && !canEnterCommandFlight }"
          @click="selectModule(module.key)"
        >
          {{ module.label }}
        </button>
      </nav>
      <label class="global-search">
        <input v-model="keyword" placeholder="搜索设备、航线、标注、媒体..." />
        <button @click="selectedObject = searchResult">搜索</button>
      </label>
      <div class="top-stat">
        <strong>{{ currentTime }}</strong>
        <span>{{ currentDate }}</span>
      </div>
      <button class="theme-toggle" @click="toggleTheme">
        {{ themeMode === 'day' ? '夜间' : '昼间' }}
      </button>
      <button class="primary-action" @click="openRoute('/task/create-plan')">新建任务</button>
    </header>

    <aside class="resource-panel side-rail" :class="{ collapsed: isLeftRailCollapsed }">
      <button
        class="side-collapse-button left"
        type="button"
        @click="toggleLeftRail"
      >
        {{ isLeftRailCollapsed ? '›' : '‹' }}
      </button>

      <section class="project-card rail-block" :class="{ folded: !isBlockExpanded('project') }">
        <div class="rail-block-head">
          <strong>合肥高新区巡检</strong>
          <div class="block-actions">
            <em v-if="!isLeftRailCollapsed">在线</em>
            <button class="block-toggle" type="button" @click="toggleBlock('project')">
              {{ isBlockExpanded('project') ? '▲' : '▼' }}
            </button>
          </div>
        </div>
        <div v-if="!isLeftRailCollapsed && isBlockExpanded('project')" class="rail-block-body project-card-body">
          <span>Workspace · 演示项目</span>
          <small>默认选择第一个机场，进入指令飞行前需保持机场选中。</small>
        </div>
      </section>

      <section class="panel-block rail-block" :class="{ folded: !isBlockExpanded('resources') }">
        <div class="block-head">
          <strong>项目资源</strong>
          <div class="block-actions">
            <button v-if="!isLeftRailCollapsed" @click="openRoute('/workspace')">一张图</button>
            <button class="block-toggle" type="button" @click="toggleBlock('resources')">
              {{ isBlockExpanded('resources') ? '▲' : '▼' }}
            </button>
          </div>
        </div>
        <div v-if="!isLeftRailCollapsed && isBlockExpanded('resources')" class="rail-block-body">
          <div class="resource-switch">
            <button
              v-for="tab in resourceTabs"
              :key="tab.key"
              :class="{ active: activeResourceTab === tab.key }"
              @click="activeResourceTab = tab.key"
            >
              {{ tab.label }}
            </button>
          </div>

          <div v-if="activeResourceTab === 'devices'" class="resource-list">
            <article
              v-for="device in devices"
              :key="device.id"
              :class="{ active: selectedObject.id === device.id, airport: device.kind === '机场设备' }"
              @click="selectObject(device)"
            >
              <span :class="['resource-dot', device.status]"></span>
              <div>
                <strong>{{ device.name }}</strong>
                <small>{{ device.model }} · {{ device.location }}</small>
              </div>
              <em>{{ device.statusText }}</em>
            </article>
          </div>

          <div v-if="activeResourceTab === 'devices'" class="airport-command-entry">
            <div>
              <span>当前机场</span>
              <strong>{{ selectedAirport?.name || '未选择机场' }}</strong>
              <small>{{ commandEntryHint }}</small>
            </div>
            <button :disabled="!canEnterCommandFlight" @click="openCommandFlight">进入指令飞行</button>
          </div>

          <div v-else-if="activeResourceTab === 'routes'" class="resource-list">
            <article
              v-for="route in routes"
              :key="route.id"
              :class="{ active: selectedObject.id === route.id }"
              @click="selectObject(route)"
            >
              <span class="resource-dot route"></span>
              <div>
                <strong>{{ route.name }}</strong>
                <small>{{ route.distance }} · {{ route.points }} 个航点</small>
              </div>
              <em>{{ route.statusText }}</em>
            </article>
          </div>

          <div v-else class="resource-list">
            <article
              v-for="layer in layers"
              :key="layer.id"
              :class="{ active: selectedObject.id === layer.id }"
              @click="selectObject(layer)"
            >
              <span class="resource-dot layer"></span>
              <div>
                <strong>{{ layer.name }}</strong>
                <small>{{ layer.type }} · {{ layer.count }} 个对象</small>
              </div>
              <em>{{ layer.visible ? '显示' : '隐藏' }}</em>
            </article>
          </div>
        </div>
      </section>

      <section class="panel-block compact rail-block" :class="{ folded: !isBlockExpanded('seats') }">
        <div class="block-head">
          <strong>在线席位</strong>
          <div class="block-actions">
            <button v-if="!isLeftRailCollapsed">{{ onlineSeatCount }} 人</button>
            <button class="block-toggle" type="button" @click="toggleBlock('seats')">
              {{ isBlockExpanded('seats') ? '▲' : '▼' }}
            </button>
          </div>
        </div>
        <div v-if="!isLeftRailCollapsed && isBlockExpanded('seats')" class="seat-list rail-block-body">
          <article
            v-for="seat in seatRows"
            :key="seat.id"
            :class="{ active: activeOperatorId === seat.id }"
            @click="selectSeat(seat.id, seat.airportId)"
          >
            <span class="seat-dot" :style="{ background: seat.color, color: seat.color }"></span>
            <div>
              <strong>{{ seat.name }} · {{ seat.role }}</strong>
              <small>{{ seat.airportName }} · {{ seat.statusText }}</small>
            </div>
          </article>
        </div>
      </section>

      <section class="panel-block compact rail-block" :class="{ folded: !isBlockExpanded('alerts') }">
        <div class="block-head">
          <strong>告警与协同</strong>
          <div class="block-actions">
            <button v-if="!isLeftRailCollapsed">全部</button>
            <button class="block-toggle" type="button" @click="toggleBlock('alerts')">
              {{ isBlockExpanded('alerts') ? '▲' : '▼' }}
            </button>
          </div>
        </div>
        <div v-if="!isLeftRailCollapsed && isBlockExpanded('alerts')" class="alert-stream rail-block-body">
          <article v-for="alert in alerts" :key="alert.id">
            <span :class="alert.level"></span>
            <div>
              <strong>{{ alert.title }}</strong>
              <small>{{ alert.source }} · {{ alert.time }}</small>
            </div>
          </article>
        </div>
      </section>
    </aside>

    <section class="map-workspace">
      <div class="map-status-strip">
        <span>设备 4</span>
        <span>在线 3</span>
        <span>任务 3</span>
        <span>标注 18</span>
        <span>媒体 126</span>
      </div>

      <div class="layer-toolbar">
        <button
          v-for="tool in mapTools"
          :key="tool.key"
          :class="{ active: selectedTool === tool.key }"
          @click="selectTool(tool.key)"
        >
          {{ tool.label }}
        </button>
      </div>

      <div class="mission-ribbon">
        <button
          v-for="mode in workModes"
          :key="mode.key"
          :class="{ active: activeWorkMode === mode.key }"
          @click="activeWorkMode = mode.key"
        >
          {{ mode.label }}
        </button>
      </div>
    </section>

    <aside class="inspector-panel side-rail" :class="{ collapsed: isRightRailCollapsed }">
      <button
        class="side-collapse-button right"
        type="button"
        @click="toggleRightRail"
      >
        {{ isRightRailCollapsed ? '‹' : '›' }}
      </button>

      <section class="selected-card rail-block" :class="{ folded: !isBlockExpanded('selected') }">
        <div class="block-head">
          <strong>{{ selectedObject.kind }}</strong>
          <button class="block-toggle" type="button" @click="toggleBlock('selected')">
            {{ isBlockExpanded('selected') ? '▲' : '▼' }}
          </button>
        </div>
        <div v-if="!isRightRailCollapsed && isBlockExpanded('selected')" class="rail-block-body">
          <div class="object-type">{{ selectedObject.kind }}</div>
          <h2>{{ selectedObject.name }}</h2>
          <p>{{ selectedObject.description }}</p>
          <div class="object-meta">
            <div v-for="item in selectedObject.meta" :key="item.label">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </div>
      </section>

      <section class="panel-block rail-block" :class="{ folded: !isBlockExpanded('actions') }">
        <div class="block-head">
          <strong>快捷操作</strong>
          <div class="block-actions">
            <button v-if="!isRightRailCollapsed" @click="openDetailRoute">详情</button>
            <button class="block-toggle" type="button" @click="toggleBlock('actions')">
              {{ isBlockExpanded('actions') ? '▲' : '▼' }}
            </button>
          </div>
        </div>
        <div v-if="!isRightRailCollapsed && isBlockExpanded('actions')" class="action-grid rail-block-body">
          <button @click="openRoute('/devices')">设备列表</button>
          <button @click="openRoute('/wayline')">航线库</button>
          <button @click="openRoute('/task')">计划库</button>
          <button :disabled="!canEnterCommandFlight" @click="openCommandFlight">指令飞行</button>
        </div>
      </section>

      <section class="panel-block rail-block" :class="{ folded: !isBlockExpanded('params') }">
        <div class="block-head">
          <strong>任务参数</strong>
          <div class="block-actions">
            <button v-if="!isRightRailCollapsed" @click="openRoute('/task/create-plan')">编辑</button>
            <button class="block-toggle" type="button" @click="toggleBlock('params')">
              {{ isBlockExpanded('params') ? '▲' : '▼' }}
            </button>
          </div>
        </div>
        <div v-if="!isRightRailCollapsed && isBlockExpanded('params')" class="param-list rail-block-body">
          <div v-for="param in taskParams" :key="param.label">
            <span>{{ param.label }}</span>
            <strong>{{ param.value }}</strong>
          </div>
        </div>
      </section>

      <section class="panel-block media-preview rail-block" :class="{ folded: !isBlockExpanded('media') }">
        <div class="block-head">
          <strong>媒体回传</strong>
          <div class="block-actions">
            <button v-if="!isRightRailCollapsed" @click="openRoute('/media')">查看</button>
            <button class="block-toggle" type="button" @click="toggleBlock('media')">
              {{ isBlockExpanded('media') ? '▲' : '▼' }}
            </button>
          </div>
        </div>
        <div v-if="!isRightRailCollapsed && isBlockExpanded('media')" class="media-grid rail-block-body">
          <div v-for="media in mediaItems" :key="media.id">
            <span>{{ media.type }}</span>
            <strong>{{ media.name }}</strong>
          </div>
        </div>
      </section>
    </aside>

    <footer class="timeline-panel">
      <div class="timeline-head">
        <strong>任务时间线</strong>
        <span>实时任务、航线执行和媒体回传状态</span>
      </div>
      <div class="timeline-items">
        <article v-for="task in tasks" :key="task.id">
          <span :class="task.status"></span>
          <div>
            <strong>{{ task.name }}</strong>
            <small>{{ task.time }} · {{ task.device }}</small>
          </div>
          <progress :value="task.progress" max="100"></progress>
          <em>{{ task.progress }}%</em>
        </article>
      </div>
    </footer>

    <div v-if="mapLoadError" class="map-error">
      <strong>高德地图加载失败</strong>
      <span>{{ mapLoadError }}</span>
    </div>
  </main>
</template>

<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import AMapLoader from '@amap/amap-jsapi-loader'
import { AMapConfig } from '/@/constants'
import {
  commandAirports,
  commandOperators,
  getOperatorForAirport,
  type CommandOperator,
} from './command-flight-context'

type ResourceTab = 'devices' | 'routes' | 'layers'
type ModuleKey = 'project' | 'map' | 'annotation' | 'team' | 'media' | 'command'
type WorkMode = 'overview' | 'planning' | 'live'
type BlockKey = 'project' | 'resources' | 'seats' | 'alerts' | 'selected' | 'actions' | 'params' | 'media'
type ThemeMode = 'night' | 'day'

interface PersistedWorkbenchState {
  activeModule?: ModuleKey
  activeResourceTab?: ResourceTab
  activeWorkMode?: WorkMode
  selectedTool?: string
  themeMode?: ThemeMode
  selectedAirportId?: string
  activeOperatorId?: string
  selectedObjectId?: string
  isLeftRailCollapsed?: boolean
  isRightRailCollapsed?: boolean
  expandedBlocks?: Partial<Record<BlockKey, boolean>>
  mapCenter?: [number, number]
  mapZoom?: number
}

interface WorkbenchObject {
  id: string
  name: string
  kind: string
  description: string
  route: string
  meta: Array<{ label: string, value: string }>
}

interface DeviceResource extends WorkbenchObject {
  model: string
  location: string
  status: string
  statusText: string
}

const WORKBENCH_STATE_KEY = 'uav-command-center-workbench-state'
const defaultMapCenter: [number, number] = [117.283, 31.874]
const defaultMapZoom = 12
const savedMapCenter = ref<[number, number]>(defaultMapCenter)
const savedMapZoom = ref(defaultMapZoom)
const hasSavedMapView = ref(false)

const router = useRouter()
const amapContainer = ref<HTMLDivElement | null>(null)
const amapInstance = shallowRef<any>(null)
const mapInstance = shallowRef<any>(null)
const clockTimer = ref<number | null>(null)
const mapLoadError = ref('')
const currentTime = ref('')
const keyword = ref('')
const persistedState = readPersistedWorkbenchState()
const defaultExpandedBlocks: Record<BlockKey, boolean> = {
  project: true,
  resources: true,
  seats: true,
  alerts: true,
  selected: true,
  actions: true,
  params: true,
  media: true,
}
const activeModule = ref<ModuleKey>(persistedState.activeModule || 'project')
const activeResourceTab = ref<ResourceTab>(persistedState.activeResourceTab || 'devices')
const activeWorkMode = ref<WorkMode>(persistedState.activeWorkMode || 'overview')
const selectedTool = ref(persistedState.selectedTool || '图层')
const themeMode = ref<ThemeMode>(persistedState.themeMode || 'night')
const selectedAirportId = ref(persistedState.selectedAirportId || commandAirports[0]?.id || '')
const activeOperatorId = ref(persistedState.activeOperatorId || commandOperators[0]?.id || '')
const isLeftRailCollapsed = ref(Boolean(persistedState.isLeftRailCollapsed))
const isRightRailCollapsed = ref(Boolean(persistedState.isRightRailCollapsed))
const expandedBlocks = ref<Record<BlockKey, boolean>>({
  ...defaultExpandedBlocks,
  ...(persistedState.expandedBlocks || {}),
})

if (persistedState.mapCenter) savedMapCenter.value = persistedState.mapCenter
if (typeof persistedState.mapZoom === 'number') savedMapZoom.value = persistedState.mapZoom
hasSavedMapView.value = Boolean(persistedState.mapCenter || typeof persistedState.mapZoom === 'number')

const modules = [
  { key: 'project' as const, label: '项目' },
  { key: 'map' as const, label: '地图' },
  { key: 'annotation' as const, label: '标注' },
  { key: 'team' as const, label: '团队' },
  { key: 'media' as const, label: '媒体' },
  { key: 'command' as const, label: '指令飞行' },
]

const resourceTabs = [
  { key: 'devices' as const, label: '设备' },
  { key: 'routes' as const, label: '航线' },
  { key: 'layers' as const, label: '图层' },
]

const workModes = [
  { key: 'overview' as const, label: '态势' },
  { key: 'planning' as const, label: '规划' },
  { key: 'live' as const, label: '直播' },
]

const mapTools = [
  { key: '图层', label: '图层' },
  { key: '标注', label: '标注' },
  { key: '测距', label: '测距' },
  { key: '框选', label: '框选' },
  { key: '回放', label: '回放' },
]

const airportDevices: DeviceResource[] = commandAirports.map(airport => ({
  id: airport.id,
  name: airport.name,
  model: airport.dockModel,
  location: airport.location,
  status: airport.status,
  statusText: airport.statusText,
  kind: '机场设备',
  route: '/devices',
  description: airport.description,
  meta: [
    { label: '设备 SN', value: airport.serialNumber },
    { label: '关联无人机', value: airport.droneName },
    { label: '任务航线', value: airport.routeCode },
    { label: '控制席位', value: getOperatorForAirport(airport.id)?.name || '待分配' },
  ],
}))

const devices: DeviceResource[] = [
  ...airportDevices,
  {
    id: 'drone-01',
    name: '巡检无人机 01',
    model: 'M3TD',
    location: '航线 R-01',
    status: 'flying',
    statusText: '飞行中',
    kind: '无人机',
    route: '/devices',
    description: '正在执行矿区正射巡检航线，媒体文件持续回传。',
    meta: [
      { label: '高度', value: '120m' },
      { label: '速度', value: '8m/s' },
      { label: '电量', value: '72%' },
      { label: 'RTK', value: '固定' },
    ],
  },
  {
    id: 'robot-r1',
    name: '巡检机器狗 R1',
    model: 'Robot Dog',
    location: '地面复核区',
    status: 'online',
    statusText: '在线',
    kind: '空地协同设备',
    route: '/devices',
    description: '承担地面近距离复核任务，可与无人机任务形成空地协同闭环。',
    meta: [
      { label: '电量', value: '82%' },
      { label: '任务', value: '地面复核' },
      { label: '载荷', value: '可见光' },
      { label: '链路', value: '在线' },
    ],
  },
]

const routes = [
  {
    id: 'route-01',
    name: '矿区正射巡检航线',
    distance: '7.0km',
    points: 19,
    statusText: '使用中',
    kind: '航线',
    route: '/wayline',
    description: '覆盖矿区东侧道路和生产区域，适合自动化周期巡检。',
    meta: [
      { label: '航点', value: '19' },
      { label: '预计', value: '26分钟' },
      { label: '高度', value: '120m' },
      { label: '类型', value: '正射巡检' },
    ],
  },
  {
    id: 'route-02',
    name: '倾斜摄影面状航线',
    distance: '2.8km',
    points: 48,
    statusText: '待执行',
    kind: '航线',
    route: '/wayline',
    description: '面向三维建模采集，含多角度拍摄策略。',
    meta: [
      { label: '航点', value: '48' },
      { label: '预计', value: '45分钟' },
      { label: '重叠率', value: '80%' },
      { label: '类型', value: '倾斜摄影' },
    ],
  },
]

const layers = [
  {
    id: 'layer-airspace',
    name: '空域规则',
    type: '禁飞/限飞',
    count: 6,
    visible: true,
    kind: '地图图层',
    route: '/flight-area',
    description: '展示任务区、禁飞区、限飞区以及设备同步状态。',
    meta: [
      { label: '禁飞区', value: '2' },
      { label: '限飞区', value: '3' },
      { label: '任务区', value: '1' },
      { label: '同步', value: '已同步' },
    ],
  },
  {
    id: 'layer-media',
    name: '媒体成果',
    type: '图片/视频',
    count: 126,
    visible: true,
    kind: '媒体图层',
    route: '/media',
    description: '按拍摄点位在地图上展示媒体成果，便于复核。',
    meta: [
      { label: '图片', value: '108' },
      { label: '视频', value: '18' },
      { label: '今日新增', value: '42' },
      { label: 'AI识别', value: '可接入' },
    ],
  },
]

const searchResult: WorkbenchObject = {
  id: 'search',
  name: '搜索结果',
  kind: '检索',
  route: '/dashboard',
  description: '当前为前端演示检索，后续可接入设备、航线、标注和媒体统一搜索。',
  meta: [
    { label: '关键词', value: keyword.value || '未输入' },
    { label: '范围', value: '全项目' },
    { label: '结果', value: '演示' },
    { label: '状态', value: '待接入' },
  ],
}

const alerts = [
  { id: 'a1', title: '机场风速接近阈值', source: '东区机库 A', time: '18:28', level: 'warn' },
  { id: 'a2', title: '空域边界接近提醒', source: '巡检无人机 01', time: '18:24', level: 'danger' },
  { id: 'a3', title: '媒体回传队列繁忙', source: '媒体库', time: '18:18', level: 'info' },
]

const tasks = [
  { id: 't1', name: '东区矿山自动巡检', time: '执行中', device: '东区机库 A', progress: 68, status: 'running' },
  { id: 't2', name: '西区倾斜摄影建模', time: '明日 09:00', device: '西区机库 B', progress: 0, status: 'waiting' },
  { id: 't3', name: '河道应急复核', time: '已完成', device: 'M30T-06', progress: 100, status: 'done' },
]

const taskParams = [
  { label: '返航高度', value: '120m' },
  { label: '失控动作', value: '返航' },
  { label: '媒体策略', value: '优先回传' },
  { label: '协同人员', value: '3 人在线' },
]

const mediaItems = [
  { id: 'm1', type: 'IMG', name: '矿区巡检_001.jpg' },
  { id: 'm2', type: 'AI', name: '裂缝识别_标注.png' },
  { id: 'm3', type: 'VID', name: '热成像复核.mp4' },
]

const selectedObject = ref<WorkbenchObject>(findWorkbenchObjectById(persistedState.selectedObjectId) || devices[0])

const selectedAirport = computed(() => commandAirports.find(airport => airport.id === selectedAirportId.value) || null)

const activeOperator = computed(() => {
  const selectedSeat = commandOperators.find(operator => operator.id === activeOperatorId.value)
  if (selectedSeat) return selectedSeat
  return selectedAirport.value ? getOperatorForAirport(selectedAirport.value.id) || commandOperators[0] : commandOperators[0]
})

const canEnterCommandFlight = computed(() => Boolean(selectedAirport.value))

const commandEntryHint = computed(() => {
  if (!selectedAirport.value) return '请选择一个机场后再进入座舱'
  const seat = getOperatorForAirport(selectedAirport.value.id)
  return seat ? `${seat.name} 正在控制 ${selectedAirport.value.name}` : `${selectedAirport.value.name} 等待席位接管`
})

const onlineSeatCount = computed(() => commandOperators.length)

const seatRows = computed(() => commandOperators.map(operator => {
  const airport = commandAirports.find(item => item.id === operator.airportId)
  return {
    ...operator,
    airportName: airport?.name || '未绑定机场',
    statusText: operator.status === 'busy' ? '操纵中' : operator.status === 'online' ? '在线' : '备勤',
  }
}))

const currentDate = computed(() => {
  const now = new Date()
  const weekday = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][now.getDay()]
  return `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 ${weekday}`
})

function updateClock () {
  currentTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

function readPersistedWorkbenchState (): PersistedWorkbenchState {
  if (typeof window === 'undefined') return {}
  try {
    const raw = window.localStorage.getItem(WORKBENCH_STATE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw) as PersistedWorkbenchState
    if (!parsed || typeof parsed !== 'object') return {}
    if (!Array.isArray(parsed.mapCenter) || parsed.mapCenter.length !== 2) {
      delete parsed.mapCenter
    }
    return parsed
  } catch {
    return {}
  }
}

function persistWorkbenchState () {
  if (typeof window === 'undefined') return
  const state: PersistedWorkbenchState = {
    activeModule: activeModule.value,
    activeResourceTab: activeResourceTab.value,
    activeWorkMode: activeWorkMode.value,
    selectedTool: selectedTool.value,
    themeMode: themeMode.value,
    selectedAirportId: selectedAirportId.value,
    activeOperatorId: activeOperatorId.value,
    selectedObjectId: selectedObject.value.id,
    isLeftRailCollapsed: isLeftRailCollapsed.value,
    isRightRailCollapsed: isRightRailCollapsed.value,
    expandedBlocks: expandedBlocks.value,
    mapCenter: savedMapCenter.value,
    mapZoom: savedMapZoom.value,
  }
  window.localStorage.setItem(WORKBENCH_STATE_KEY, JSON.stringify(state))
}

watch(
  [
    activeModule,
    activeResourceTab,
    activeWorkMode,
    selectedTool,
    themeMode,
    selectedAirportId,
    activeOperatorId,
    isLeftRailCollapsed,
    isRightRailCollapsed,
    expandedBlocks,
    savedMapCenter,
    savedMapZoom,
    () => selectedObject.value.id,
  ],
  persistWorkbenchState,
  { deep: true },
)

function findWorkbenchObjectById (id?: string) {
  if (!id) return null
  return [...devices, ...routes, ...layers].find(resource => resource.id === id) || null
}

function openRoute (path: string) {
  persistWorkbenchState()
  router.push(path)
}

function isBlockExpanded (block: BlockKey) {
  return expandedBlocks.value[block]
}

function toggleBlock (block: BlockKey) {
  expandedBlocks.value[block] = !expandedBlocks.value[block]
  persistWorkbenchState()
}

function toggleLeftRail () {
  isLeftRailCollapsed.value = !isLeftRailCollapsed.value
  persistWorkbenchState()
}

function toggleRightRail () {
  isRightRailCollapsed.value = !isRightRailCollapsed.value
  persistWorkbenchState()
}

function openCommandFlight () {
  if (!selectedAirport.value) {
    activeResourceTab.value = 'devices'
    persistWorkbenchState()
    return
  }
  const operator = activeOperator.value || getOperatorForAirport(selectedAirport.value.id)
  persistWorkbenchState()
  router.push({
    path: '/command-flight',
    query: {
      dockId: selectedAirport.value.id,
      operatorId: operator?.id || '',
    },
  })
}

function selectModule (module: ModuleKey) {
  activeModule.value = module
  if (module === 'command') {
    openCommandFlight()
  }
}

function openDetailRoute () {
  openRoute(selectedObject.value.route)
}

function selectObject (object: WorkbenchObject) {
  selectedObject.value = object
  const airport = commandAirports.find(item => item.id === object.id)
  if (airport) {
    selectedAirportId.value = airport.id
    const seat = getOperatorForAirport(airport.id)
    if (seat) activeOperatorId.value = seat.id
  }
}

function selectSeat (operatorId: CommandOperator['id'], airportId: string) {
  activeOperatorId.value = operatorId
  selectedAirportId.value = airportId
  const airportDevice = devices.find(device => device.id === airportId)
  if (airportDevice) selectedObject.value = airportDevice
}

function selectTool (tool: string) {
  selectedTool.value = tool
  const map = mapInstance.value
  if (!map) return
  if (tool === '回放') map.setZoomAndCenter(13, [117.283, 31.874])
  if (tool === '测距') map.setZoom(14)
  window.setTimeout(syncMapViewState)
}

function applyThemeToMap () {
  const map = mapInstance.value
  if (!map) return
  map.setMapStyle(themeMode.value === 'day' ? 'amap://styles/normal' : 'amap://styles/darkblue')
}

function toggleTheme () {
  themeMode.value = themeMode.value === 'day' ? 'night' : 'day'
  applyThemeToMap()
}

function syncMapViewState () {
  const map = mapInstance.value
  if (!map) return
  const center = map.getCenter?.()
  const lng = Number(center?.lng ?? center?.getLng?.())
  const lat = Number(center?.lat ?? center?.getLat?.())
  const zoom = Number(map.getZoom?.())
  if (Number.isFinite(lng) && Number.isFinite(lat)) {
    savedMapCenter.value = [lng, lat]
    hasSavedMapView.value = true
  }
  if (Number.isFinite(zoom)) {
    savedMapZoom.value = zoom
    hasSavedMapView.value = true
  }
}

function createMarker (AMap: any, item: { id: string, name: string, position: number[], type: string }) {
  const marker = new AMap.Marker({
    position: item.position,
    anchor: 'center',
    content: `<div class="workbench-marker ${item.type}">${item.name}</div>`,
  })
  marker.on('click', () => {
    const match = [...devices, ...routes, ...layers].find(resource => resource.id === item.id)
    if (match) selectObject(match)
  })
  return marker
}

function renderMapOverlays (AMap: any, map: any) {
  const flightPath = [
    [117.244, 31.858],
    [117.265, 31.876],
    [117.292, 31.866],
    [117.326, 31.892],
  ]
  const markers = [
    ...commandAirports.map(airport => ({ id: airport.id, name: airport.name, type: 'dock', position: airport.position })),
    { id: 'drone-01', name: '巡检无人机 01', type: 'drone', position: [117.292, 31.866] },
    { id: 'robot-r1', name: '机器狗 R1', type: 'robot', position: [117.315, 31.875] },
  ].map(item => createMarker(AMap, item))

  const routeLine = new AMap.Polyline({
    path: flightPath,
    strokeColor: '#00d8ff',
    strokeWeight: 5,
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
  if (hasSavedMapView.value) {
    map.setZoomAndCenter(savedMapZoom.value, savedMapCenter.value)
  } else {
    map.setFitView([routeLine, taskArea, ...markers], false, [110, 430, 180, 340])
    window.setTimeout(syncMapViewState)
  }
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
      mapStyle: themeMode.value === 'day' ? 'amap://styles/normal' : 'amap://styles/darkblue',
      layers: [
        new AMap.TileLayer.Satellite(),
        new AMap.TileLayer.RoadNet(),
      ],
    })
    mapInstance.value = map
    renderMapOverlays(AMap, map)
    map.on('moveend', syncMapViewState)
    map.on('zoomend', syncMapViewState)
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
.flight-workbench {
  position: fixed;
  inset: 0;
  z-index: 100;
  overflow: hidden;
  --workbench-text: #d9f4ff;
  --workbench-title: #fff;
  --workbench-muted: #9ddff5;
  --workbench-panel: rgba(3, 24, 48, 0.72);
  --workbench-panel-strong: rgba(2, 22, 44, 0.84);
  --workbench-panel-soft: rgba(0, 20, 38, 0.5);
  --workbench-panel-inner: rgba(0, 20, 38, 0.44);
  --workbench-border: rgba(0, 216, 255, 0.2);
  --workbench-border-strong: rgba(0, 216, 255, 0.34);
  --workbench-button: rgba(4, 42, 76, 0.58);
  --workbench-button-active: rgba(0, 142, 220, 0.58);
  --workbench-card-active: rgba(0, 130, 210, 0.24);
  --workbench-placeholder: rgba(189, 239, 255, 0.48);
  --workbench-soft-shadow: inset 0 0 24px rgba(0, 170, 255, 0.08), 0 14px 38px rgba(0, 18, 38, 0.34);
  --workbench-map-overlay: linear-gradient(90deg, rgba(2, 14, 28, 0.76), rgba(2, 14, 28, 0.08) 24%, rgba(2, 14, 28, 0.08) 76%, rgba(2, 14, 28, 0.76)),
    linear-gradient(180deg, rgba(0, 50, 95, 0.36), transparent 30%, rgba(0, 18, 38, 0.5)),
    repeating-linear-gradient(90deg, rgba(0, 216, 255, 0.035) 0 1px, transparent 1px 72px);
  color: #d9f4ff;
  background: #021428;
}

.flight-workbench.day-mode {
  --workbench-text: #193953;
  --workbench-title: #082238;
  --workbench-muted: #5b7488;
  --workbench-panel: rgba(239, 248, 253, 0.84);
  --workbench-panel-strong: rgba(231, 244, 251, 0.92);
  --workbench-panel-soft: rgba(225, 239, 247, 0.78);
  --workbench-panel-inner: rgba(214, 232, 243, 0.72);
  --workbench-border: rgba(41, 142, 196, 0.22);
  --workbench-border-strong: rgba(18, 126, 185, 0.32);
  --workbench-button: rgba(226, 240, 248, 0.9);
  --workbench-button-active: rgba(0, 132, 210, 0.22);
  --workbench-card-active: rgba(0, 132, 210, 0.16);
  --workbench-placeholder: rgba(76, 107, 128, 0.58);
  --workbench-soft-shadow: inset 0 0 20px rgba(0, 132, 210, 0.05), 0 12px 30px rgba(82, 120, 148, 0.2);
  --workbench-map-overlay: linear-gradient(90deg, rgba(224, 239, 248, 0.58), rgba(224, 239, 248, 0.05) 22%, rgba(224, 239, 248, 0.05) 78%, rgba(224, 239, 248, 0.58)),
    linear-gradient(180deg, rgba(224, 239, 248, 0.36), transparent 36%, rgba(219, 235, 245, 0.42)),
    repeating-linear-gradient(90deg, rgba(0, 125, 190, 0.035) 0 1px, transparent 1px 72px);
  color: var(--workbench-text);
  background: #e6f1f8;
}

.map-canvas,
.map-overlay {
  position: absolute;
  inset: 0;
}

.map-canvas {
  z-index: 0;
  background: #061f3a;
}

.map-overlay {
  z-index: 1;
  pointer-events: none;
  background: var(--workbench-map-overlay);
}

.workbench-topbar,
.resource-panel,
.inspector-panel,
.timeline-panel,
.map-status-strip,
.layer-toolbar,
.mission-ribbon,
.map-error {
  position: absolute;
  z-index: 3;
}

.workbench-topbar {
  top: 0;
  left: 0;
  right: 0;
  height: 64px;
  display: grid;
  grid-template-columns: 300px 400px minmax(260px, 1fr) 150px 78px 104px;
  align-items: center;
  gap: 14px;
  padding: 0 22px;
  border-bottom: 1px solid var(--workbench-border-strong);
  background: var(--workbench-panel-strong);
  box-shadow: 0 14px 38px rgba(0, 20, 44, 0.42);
  backdrop-filter: blur(12px);
}

.project-title {
  padding-left: 16px;
  border-left: 3px solid #00d8ff;
}

.project-title strong,
.project-title span,
.top-stat strong,
.top-stat span {
  display: block;
}

.project-title strong {
  color: var(--workbench-title);
  font-size: 18px;
}

.project-title span,
.top-stat span {
  margin-top: 4px;
  color: var(--workbench-muted);
  font-size: 12px;
}

.module-tabs,
.resource-switch,
.mission-ribbon {
  display: flex;
  gap: 6px;
}

button {
  font: inherit;
}

.module-tabs button,
.resource-switch button,
.mission-ribbon button,
.layer-toolbar button,
.primary-action,
.theme-toggle,
.block-head button,
.block-toggle,
.side-collapse-button,
.action-grid button,
.global-search button {
  border: 1px solid var(--workbench-border);
  border-radius: 4px;
  color: var(--workbench-text);
  background: var(--workbench-button);
  cursor: pointer;
}

.module-tabs button {
  height: 34px;
  padding: 0 14px;
}

.module-tabs button.active,
.resource-switch button.active,
.mission-ribbon button.active,
.layer-toolbar button.active {
  color: #fff;
  border-color: #00d8ff;
  background: var(--workbench-button-active);
  box-shadow: inset 0 -2px 0 #00d8ff, 0 0 14px rgba(0, 216, 255, 0.22);
}

.global-search {
  height: 38px;
  display: grid;
  grid-template-columns: 1fr 56px;
  border: 1px solid var(--workbench-border);
  border-radius: 4px;
  background: var(--workbench-panel-soft);
  box-shadow: inset 0 0 14px rgba(0, 132, 210, 0.05);
}

.global-search input {
  min-width: 0;
  padding: 0 14px;
  border: 0;
  outline: 0;
  color: var(--workbench-text);
  background: transparent;
}

.global-search input::placeholder {
  color: var(--workbench-placeholder);
}

.global-search button {
  border: 0;
  border-left: 1px solid var(--workbench-border);
  border-radius: 0;
}

.top-stat strong {
  color: #00d8ff;
  font-family: Consolas, monospace;
  font-size: 22px;
  font-weight: 500;
}

.primary-action {
  height: 38px;
  color: #fff;
  background: linear-gradient(180deg, rgba(0, 180, 255, 0.86), rgba(0, 94, 166, 0.72));
}

.theme-toggle {
  height: 38px;
}

.resource-panel,
.inspector-panel {
  top: 78px;
  bottom: 116px;
  width: 326px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--workbench-border);
  background: rgba(2, 18, 36, 0.58);
  box-shadow: var(--workbench-soft-shadow);
  backdrop-filter: blur(12px);
  transition: width 0.22s ease, padding 0.22s ease;
  overflow: auto;
}

.resource-panel {
  left: 14px;
}

.inspector-panel {
  right: 14px;
  width: 336px;
}

.flight-workbench.day-mode .resource-panel,
.flight-workbench.day-mode .inspector-panel {
  background: rgba(237, 248, 253, 0.72);
}

.side-rail.collapsed {
  width: 58px;
  padding: 42px 6px 8px;
  overflow: hidden;
}

.side-collapse-button {
  position: absolute;
  top: 8px;
  z-index: 5;
  width: 28px;
  height: 28px;
  padding: 0;
  color: #62e6ff;
  background: rgba(0, 72, 120, 0.62);
  font-size: 20px;
  line-height: 1;
}

.side-collapse-button.left {
  right: 8px;
}

.side-collapse-button.right {
  left: 8px;
}

.project-card,
.panel-block,
.selected-card,
.timeline-panel,
.map-status-strip,
.layer-toolbar,
.mission-ribbon {
  border: 1px solid rgba(0, 216, 255, 0.2);
  border-color: var(--workbench-border);
  background: var(--workbench-panel);
  box-shadow: var(--workbench-soft-shadow);
  backdrop-filter: blur(10px);
}

.project-card,
.panel-block,
.selected-card {
  border-color: rgba(0, 216, 255, 0.14);
  background: rgba(0, 24, 48, 0.3);
  box-shadow: none;
}

.flight-workbench.day-mode .project-card,
.flight-workbench.day-mode .panel-block,
.flight-workbench.day-mode .selected-card {
  background: rgba(236, 247, 253, 0.52);
}

.project-card {
  min-height: 0;
}

.project-card strong,
.selected-card h2,
.block-head strong,
.resource-list strong,
.timeline-head strong {
  color: var(--workbench-title);
}

.project-card span,
.resource-list small,
.alert-stream small,
.selected-card p,
.timeline-head span,
.timeline-items small,
.param-list span,
.media-grid span {
  color: var(--workbench-muted);
}

.project-card em {
  padding: 4px 9px;
  border-radius: 10px;
  color: #7cff9e;
  background: rgba(24, 168, 82, 0.22);
  font-style: normal;
}

.panel-block,
.selected-card {
  padding: 12px;
}

.project-card {
  padding: 12px;
}

.panel-block.compact {
  min-height: 0;
  flex: 1;
}

.rail-block {
  position: relative;
  transition: min-height 0.18s ease, padding 0.18s ease, background 0.18s ease;
}

.rail-block.folded {
  min-height: 48px;
  flex: 0 0 auto;
}

.rail-block-head,
.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.rail-block.folded .block-head,
.rail-block.folded .rail-block-head {
  margin-bottom: 0;
}

.rail-block-head strong,
.block-head strong {
  padding-left: 9px;
  border-left: 3px solid #00d8ff;
}

.block-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.block-head button {
  height: 26px;
  padding: 0 9px;
}

.block-toggle {
  width: 28px;
  height: 26px;
  padding: 0;
  color: #62e6ff;
  background: rgba(0, 52, 92, 0.46);
}

.rail-block-body {
  min-width: 0;
}

.project-card-body {
  display: grid;
  gap: 6px;
  color: var(--workbench-muted);
}

.project-card-body small {
  color: var(--workbench-muted);
  line-height: 1.5;
}

.side-rail.collapsed .rail-block {
  min-height: 92px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 4px;
}

.side-rail.collapsed .block-head,
.side-rail.collapsed .rail-block-head {
  justify-content: center;
  margin: 0;
}

.side-rail.collapsed .block-head strong,
.side-rail.collapsed .rail-block-head strong {
  width: 18px;
  padding: 0;
  border-left: 0;
  color: var(--workbench-title);
  font-size: 13px;
  line-height: 1.2;
  text-align: center;
  writing-mode: vertical-rl;
}

.side-rail.collapsed .block-actions,
.side-rail.collapsed .block-toggle {
  display: none;
}

.resource-switch {
  margin-bottom: 10px;
}

.resource-switch button {
  flex: 1;
  height: 30px;
}

.resource-list,
.alert-stream,
.route-card-list,
.seat-list {
  display: grid;
  gap: 8px;
}

.resource-list article,
.alert-stream article,
.timeline-items article {
  display: grid;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--workbench-border);
  background: var(--workbench-panel-soft);
}

.resource-list article {
  grid-template-columns: 10px 1fr auto;
  min-height: 58px;
  padding: 9px;
  cursor: pointer;
}

.resource-list article.active {
  border-color: #00d8ff;
  background: var(--workbench-card-active);
}

.resource-list article.airport {
  border-color: rgba(0, 216, 255, 0.28);
}

.resource-list strong,
.resource-list small {
  display: block;
}

.resource-list em {
  color: #7cff9e;
  font-size: 12px;
  font-style: normal;
}

.resource-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #00d8ff;
}

.resource-dot.online {
  background: #52ff78;
}

.resource-dot.flying {
  background: #00d8ff;
  box-shadow: 0 0 14px #00d8ff;
}

.resource-dot.idle {
  background: #ffcf5a;
}

.resource-dot.offline {
  background: #778a9a;
}

.resource-dot.route {
  border-radius: 2px;
  background: #ffcc4d;
}

.resource-dot.layer {
  border-radius: 2px;
  background: #b77cff;
}

.airport-command-entry {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  padding: 10px;
  border: 1px solid rgba(0, 216, 255, 0.24);
  background: rgba(0, 118, 186, 0.16);
}

.airport-command-entry span,
.airport-command-entry small {
  display: block;
  color: var(--workbench-muted);
  font-size: 12px;
}

.airport-command-entry strong {
  display: block;
  margin: 3px 0;
  color: var(--workbench-title);
}

.airport-command-entry button {
  height: 34px;
  padding: 0 12px;
  color: #001624;
  background: linear-gradient(135deg, #61efff, #25a8ff);
}

.airport-command-entry button:disabled,
.action-grid button:disabled,
.module-tabs button.disabled {
  cursor: not-allowed;
  opacity: 0.48;
}

.seat-list article {
  display: grid;
  grid-template-columns: 10px 1fr;
  align-items: center;
  gap: 10px;
  min-height: 50px;
  padding: 8px;
  border: 1px solid var(--workbench-border);
  background: var(--workbench-panel-soft);
  cursor: pointer;
}

.seat-list article.active {
  border-color: rgba(98, 230, 255, 0.74);
  background: rgba(0, 130, 210, 0.22);
}

.seat-list strong,
.seat-list small {
  display: block;
}

.seat-list small {
  color: var(--workbench-muted);
}

.seat-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  box-shadow: 0 0 14px currentColor;
}

.alert-stream {
  max-height: 210px;
  overflow: auto;
}

.alert-stream article {
  grid-template-columns: 8px 1fr;
  min-height: 50px;
  padding: 8px;
}

.alert-stream article > span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.alert-stream .warn {
  background: #ffbf47;
}

.alert-stream .danger {
  background: #ff5964;
}

.alert-stream .info {
  background: #00d8ff;
}

.map-status-strip {
  top: 78px;
  left: 352px;
  right: 370px;
  height: 38px;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 0 14px;
}

.left-rail-collapsed .map-status-strip,
.left-rail-collapsed .mission-ribbon,
.left-rail-collapsed .timeline-panel {
  left: 96px;
}

.right-rail-collapsed .map-status-strip,
.right-rail-collapsed .layer-toolbar,
.right-rail-collapsed .timeline-panel {
  right: 96px;
}

.map-status-strip span {
  color: var(--workbench-text);
}

.layer-toolbar {
  right: 370px;
  top: 134px;
  display: grid;
  gap: 8px;
  padding: 8px;
}

.layer-toolbar button {
  width: 58px;
  height: 38px;
}

.mission-ribbon {
  left: 352px;
  top: 134px;
  padding: 8px;
}

.mission-ribbon button {
  height: 34px;
  padding: 0 18px;
}

.selected-card:not(.folded) {
  min-height: 180px;
}

.object-type {
  display: inline-flex;
  padding: 4px 10px;
  border-radius: 12px;
  color: #00d8ff;
  background: rgba(0, 216, 255, 0.12);
}

.selected-card h2 {
  margin: 12px 0 8px;
  font-size: 22px;
}

.selected-card p {
  margin: 0 0 14px;
  line-height: 1.65;
}

.object-meta,
.param-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.object-meta div,
.param-list div,
.media-grid div {
  min-height: 52px;
  display: grid;
  align-content: center;
  gap: 4px;
  padding: 8px;
  border: 1px solid var(--workbench-border);
  background: var(--workbench-panel-inner);
}

.object-meta span,
.object-meta strong,
.param-list span,
.param-list strong {
  display: block;
}

.object-meta strong,
.param-list strong {
  color: var(--workbench-title);
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.action-grid button {
  height: 34px;
}

.media-grid {
  display: grid;
  gap: 8px;
}

.media-grid strong {
  color: var(--workbench-title);
  font-size: 12px;
}

.timeline-panel {
  left: 352px;
  right: 370px;
  bottom: 14px;
  height: 86px;
  display: grid;
  grid-template-columns: 180px 1fr;
  gap: 14px;
  padding: 12px;
}

.timeline-head {
  display: grid;
  align-content: center;
  gap: 4px;
}

.timeline-items {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.timeline-items article {
  grid-template-columns: 8px 1fr 78px 42px;
  padding: 8px;
}

.timeline-items article > span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.timeline-items .running {
  background: #00d8ff;
  box-shadow: 0 0 12px #00d8ff;
}

.timeline-items .waiting {
  background: #ffbf47;
}

.timeline-items .done {
  background: #52ff78;
}

.timeline-items progress {
  width: 78px;
  height: 6px;
  accent-color: #00d8ff;
}

.timeline-items em {
  color: var(--workbench-title);
  font-style: normal;
}

.map-error {
  left: 50%;
  bottom: 122px;
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

:deep(.workbench-marker) {
  height: 28px;
  display: inline-flex;
  align-items: center;
  padding: 0 10px;
  border-radius: 14px;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 0 18px rgba(0, 216, 255, 0.55);
}

:deep(.workbench-marker.dock) {
  background: #0e8bff;
}

:deep(.workbench-marker.drone) {
  background: #00b894;
}

:deep(.workbench-marker.robot) {
  background: #7c5cff;
}

@media (max-width: 1360px) {
  .workbench-topbar {
    grid-template-columns: 250px 300px minmax(200px, 1fr) 130px 74px 94px;
    gap: 10px;
    padding: 0 14px;
  }

  .resource-panel {
    width: 290px;
  }

  .inspector-panel {
    width: 310px;
  }

  .side-rail.collapsed {
    width: 58px !important;
  }

  .left-rail-collapsed .map-status-strip,
  .left-rail-collapsed .mission-ribbon,
  .left-rail-collapsed .timeline-panel {
    left: 96px;
  }

  .right-rail-collapsed .map-status-strip,
  .right-rail-collapsed .layer-toolbar,
  .right-rail-collapsed .timeline-panel {
    right: 96px;
  }

  .map-status-strip,
  .mission-ribbon,
  .timeline-panel {
    left: 318px;
  }

  .map-status-strip,
  .layer-toolbar,
  .timeline-panel {
    right: 334px;
  }
}

@media (max-width: 980px) {
  .workbench-topbar {
    height: 120px;
    grid-template-columns: 1fr 1fr;
  }

  .module-tabs,
  .global-search {
    grid-column: 1 / -1;
  }

  .resource-panel {
    top: 132px;
    bottom: 104px;
    left: 10px;
    width: 260px;
  }

  .inspector-panel {
    top: 132px;
    bottom: 104px;
    right: 10px;
    width: 280px;
  }

  .side-rail.collapsed {
    width: 58px !important;
  }

  .left-rail-collapsed .map-status-strip,
  .left-rail-collapsed .mission-ribbon,
  .left-rail-collapsed .timeline-panel {
    left: 84px;
  }

  .right-rail-collapsed .map-status-strip,
  .right-rail-collapsed .layer-toolbar,
  .right-rail-collapsed .timeline-panel {
    right: 84px;
  }

  .map-status-strip,
  .mission-ribbon,
  .timeline-panel {
    left: 284px;
  }

  .map-status-strip,
  .layer-toolbar,
  .timeline-panel {
    right: 304px;
  }
}
</style>
