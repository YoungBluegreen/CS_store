<template>
  <main
    class="command-center"
    :class="[
      `screen-${screenMode}`,
      {
        'night-mode': isNightMode,
        'left-collapsed': leftCollapsed,
        'right-collapsed': rightCollapsed,
      },
    ]"
  >
    <button class="collapse-toggle left" @click="leftCollapsed = !leftCollapsed">
      {{ leftCollapsed ? '展开功能' : '收起功能' }}
    </button>
    <button class="collapse-toggle right" @click="rightCollapsed = !rightCollapsed">
      {{ rightCollapsed ? '展开态势' : '收起态势' }}
    </button>
    <aside class="module-rail">
      <button
        v-for="item in modules"
        :key="item.key"
        :class="{ active: activeModule === item.key }"
        @click="selectModule(item.key)"
      >
        <component :is="item.icon" />
        <span>{{ item.label }}</span>
      </button>
    </aside>

    <section class="workspace">
      <header class="workspace-head">
        <div>
          <span class="eyebrow">{{ currentModule.kicker }}</span>
          <h1>{{ currentModule.title }}</h1>
          <p>{{ currentModule.desc }}</p>
        </div>
        <div class="head-actions">
          <a-button
            v-for="action in currentModule.actions"
            :key="action.label"
            :type="action.primary ? 'primary' : 'default'"
            :disabled="!action.route"
            @click="openRoute(action.route)"
          >
            <template #icon>
              <component :is="action.icon" />
            </template>
            {{ action.label }}
          </a-button>
        </div>
      </header>

      <div class="metrics-grid">
        <div v-for="metric in currentModule.metrics" :key="metric.label" class="metric-card">
          <component :is="metric.icon" />
          <div>
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
          </div>
          <em :class="metric.tone">{{ metric.note }}</em>
        </div>
      </div>

      <section class="doc-control-bar">
        <div class="control-group">
          <span>桌面大屏</span>
          <button
            v-for="mode in screenModes"
            :key="mode.key"
            :class="{ active: screenMode === mode.key }"
            @click="screenMode = mode.key"
          >
            {{ mode.label }}
          </button>
        </div>
        <div class="control-group">
          <span>底图</span>
          <button :class="{ active: baseLayer === 'vector' }" @click="setBaseLayer('vector')">矢量</button>
          <button :class="{ active: baseLayer === 'satellite' }" @click="setBaseLayer('satellite')">卫星</button>
        </div>
        <div class="control-group">
          <span>主题</span>
          <button :class="{ active: !isNightMode }" @click="setDayNight(false)">日间</button>
          <button :class="{ active: isNightMode }" @click="setDayNight(true)">夜间</button>
        </div>
        <div class="control-group collaborative">
          <span>协同规划</span>
          <button @click="syncCollaborativePlan">同步规划</button>
          <em>{{ onlineUsers.length }} 人在线</em>
        </div>
      </section>

      <section class="module-detail">
        <div class="detail-head">
          <div>
            <strong>{{ currentModule.label }}二级页面</strong>
            <span>按后端实际接口能力填充，按钮跳转到当前项目已有页面。</span>
          </div>
          <a-tag color="blue">{{ currentModule.coverage }}</a-tag>
        </div>

        <div class="capability-grid">
          <article
            v-for="capability in currentModule.capabilities"
            :key="capability.title"
            class="capability-card"
          >
            <div class="capability-icon">
              <component :is="capability.icon" />
            </div>
            <div class="capability-copy">
              <div class="capability-title">
                <strong>{{ capability.title }}</strong>
                <a-tag :color="capability.statusColor">{{ capability.status }}</a-tag>
              </div>
              <p>{{ capability.desc }}</p>
              <div class="api-list">
                <span v-for="api in capability.apis" :key="api">{{ api }}</span>
              </div>
              <div class="card-actions">
                <a-button
                  size="small"
                  type="primary"
                  :disabled="!capability.route"
                  @click="openRoute(capability.route)"
                >
                  {{ capability.action || '进入页面' }}
                </a-button>
                <a-button size="small" @click="selectModule(capability.related || activeModule)">
                  查看关联
                </a-button>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section class="map-stage">
        <div class="map-toolbar">
          <button v-for="layer in currentModule.layers" :key="layer" class="enabled">{{ layer }}</button>
        </div>
        <div class="amap-wrap">
          <div ref="amapContainer" class="amap-container"></div>
          <div class="map-hud top-left">
            <strong>高德底图联动</strong>
            <span>{{ baseLayer === 'vector' ? '矢量图层' : '卫星图层' }} / {{ isNightMode ? '夜间模式' : '日间模式' }}</span>
          </div>
          <div class="map-hud top-right">
            <strong>多人协同</strong>
            <div class="avatar-row">
              <span v-for="user in onlineUsers" :key="user.name" :style="{ background: user.color }">{{ user.short }}</span>
            </div>
            <em>{{ collaborationStatus }}</em>
          </div>
          <div class="fpv-pad">
            <div>
              <strong>模拟 FPV 指令飞行</strong>
              <span>{{ fpvStatus.mode }} · 高度 {{ fpvStatus.altitude }}m · 速度 {{ fpvStatus.speed }}m/s</span>
              <span>空地协同：巡检机器狗 R1 在线 · 电量 82%</span>
            </div>
            <div class="fpv-grid">
              <button @click="sendFpvCommand('上升')">升</button>
              <button @click="sendFpvCommand('前进')">前</button>
              <button @click="sendFpvCommand('下降')">降</button>
              <button @click="sendFpvCommand('左转')">左</button>
              <button class="primary" @click="sendFpvCommand('悬停')">停</button>
              <button @click="sendFpvCommand('右转')">右</button>
              <button @click="sendFpvCommand('机器狗前进')">犬进</button>
              <button @click="sendFpvCommand('机器狗驻停')">犬停</button>
              <button @click="sendFpvCommand('机器狗回传')">回传</button>
            </div>
            <p>{{ commandLog[0] }}</p>
          </div>
          <div v-if="mapLoadError" class="map-error">
            <strong>高德地图加载失败</strong>
            <span>{{ mapLoadError }}</span>
          </div>
        </div>
      </section>
    </section>

    <aside class="inspector">
      <section class="panel">
        <div class="panel-head">
          <span>后端能力盘点</span>
          <a-tag color="green">已接入</a-tag>
        </div>
        <div class="backend-list">
          <div v-for="item in backendCoverage" :key="item.name">
            <strong>{{ item.name }}</strong>
            <span>{{ item.desc }}</span>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-head">
          <span>当前模块链路</span>
          <a-tag>{{ currentModule.label }}</a-tag>
        </div>
        <div class="flow-list">
          <div v-for="(step, index) in currentModule.flow" :key="step">
            <em>{{ index + 1 }}</em>
            <span>{{ step }}</span>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-head">
          <span>执行提示</span>
          <a-tag color="orange">落地建议</a-tag>
        </div>
        <div class="notice-list">
          <p>设备、任务、直播、媒体、空域、固件和成员管理都已经有后端接口，可以直接做成可操作页面。</p>
          <p>AI 识别目前在本地 Python 脚本和结果目录中，建议下一步封装成 REST 服务后再做上传、识别、报告按钮。</p>
        </div>
      </section>
    </aside>
  </main>
</template>

<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AMapLoader from '@amap/amap-jsapi-loader'
import { ERouterName } from '/@/types'
import { AMapConfig } from '/@/constants'
import {
  AlertOutlined,
  ApartmentOutlined,
  BarChartOutlined,
  CloudServerOutlined,
  ControlOutlined,
  DatabaseOutlined,
  EnvironmentOutlined,
  EyeOutlined,
  FileImageOutlined,
  FileSearchOutlined,
  FundProjectionScreenOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  RadarChartOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TeamOutlined,
  UploadOutlined,
  VideoCameraOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'

type ModuleKey = 'dashboard' | 'device' | 'airspace' | 'route' | 'monitor' | 'data' | 'ai' | 'system'

const route = useRoute()
const router = useRouter()
const activeModule = ref<ModuleKey>('dashboard')
const amapContainer = ref<HTMLDivElement | null>(null)
const mapLoadError = ref('')
const amapInstance = shallowRef<any>(null)
const mapInstance = shallowRef<any>(null)
const satelliteLayer = shallowRef<any>(null)
const realtimeTimer = ref<number | null>(null)
const leftCollapsed = ref(false)
const rightCollapsed = ref(false)
const isNightMode = ref(false)
const baseLayer = ref<'vector' | 'satellite'>('vector')
const screenMode = ref<'desktop' | 'wide' | 'control'>('desktop')
const collaborationStatus = ref('规划锁空闲，等待同步')
const fpvStatus = ref({ mode: '姿态控制', altitude: 120, speed: 8 })
const commandLog = ref(['等待指令'])

const screenModes = [
  { key: 'desktop', label: '桌面' },
  { key: 'wide', label: '宽屏' },
  { key: 'control', label: '指挥' },
] as const

const onlineUsers = [
  { name: 'adminPC', short: 'A', color: '#0d6fd6' },
  { name: 'planner', short: 'P', color: '#19a35b' },
  { name: 'operator', short: 'O', color: '#d46b08' },
]

const pathOf = (name: ERouterName) => '/' + name
const taskCreatePath = '/' + ERouterName.TASK + '/' + ERouterName.CREATE_PLAN
const livePath = `/${ERouterName.LIVESTREAM}/${ERouterName.LIVING}`

const modules = [
  { key: 'dashboard', label: '综合态势', icon: RadarChartOutlined },
  { key: 'device', label: '设备管理', icon: ApartmentOutlined },
  { key: 'airspace', label: '空域管理', icon: EnvironmentOutlined },
  { key: 'route', label: '航线任务', icon: RocketOutlined },
  { key: 'monitor', label: '实时监控', icon: VideoCameraOutlined },
  { key: 'data', label: '数据成果', icon: FileImageOutlined },
  { key: 'ai', label: '智能识别', icon: EyeOutlined },
  { key: 'system', label: '系统管理', icon: SettingOutlined },
] as const

const backendCoverage = [
  { name: '管理服务', desc: '登录、用户、工作空间、设备、固件、HMS、日志、直播' },
  { name: '航线服务', desc: 'KMZ 上传、航线列表、任务创建、任务暂停/恢复/删除、媒体优先上传' },
  { name: '地图服务', desc: '图层元素、空域规则、空域同步、设备空域状态' },
  { name: '控制服务', desc: '机场指令、飞向目标点、一键起飞、飞行/负载控制权、DRC' },
  { name: '媒体服务', desc: '媒体文件列表、预览下载地址、任务成果回传' },
]

const commonMetrics = [
  { label: '后端模块', value: '5', note: 'Spring', tone: 'up', icon: DatabaseOutlined },
  { label: '可跳转页面', value: '11', note: '已映射', tone: 'up', icon: FundProjectionScreenOutlined },
  { label: '接口能力', value: '40+', note: '可复用', tone: 'flat', icon: CloudServerOutlined },
]

const moduleData = {
  dashboard: {
    label: '综合态势',
    kicker: '一张图入口',
    title: '综合态势指挥中心',
    desc: '把设备、空域、航线、任务、直播、媒体、AI 和系统管理汇总到一个入口，二级页面承接真实后端能力。',
    coverage: '全局导航',
    layers: ['机场', '无人机', '航线', '轨迹', '空域', '告警', '成果'],
    metrics: [
      ...commonMetrics,
      { label: '核心业务链', value: '8', note: '闭环', tone: 'flat', icon: BarChartOutlined },
    ],
    actions: [
      { label: '进入一张图', route: pathOf(ERouterName.WORKSPACE), icon: RadarChartOutlined, primary: true },
      { label: '新建任务', route: taskCreatePath, icon: PlusOutlined },
    ],
    flow: ['设备接入', '空域校验', '航线上传', '任务调度', '实时监控', '媒体回传', 'AI 分析', '报告输出'],
    capabilities: [
      {
        title: '设备态势汇总',
        desc: '读取设备拓扑、绑定设备和设备状态，在一张图中呈现机场与无人机在线情况。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/manage/api/v1/devices/{workspace_id}/devices', '/map/api/v1/workspaces/{workspace_id}/device-status'],
        route: pathOf(ERouterName.DEVICES),
        related: 'device',
        icon: CloudServerOutlined,
      },
      {
        title: '任务态势汇总',
        desc: '航线任务列表、进度、暂停恢复、删除和媒体优先上传可以直接进入任务页面处理。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/wayline/api/v1/workspaces/{workspace_id}/jobs', '/wayline/api/v1/workspaces/{workspace_id}/flight-tasks'],
        route: pathOf(ERouterName.TASK),
        related: 'route',
        icon: RocketOutlined,
      },
      {
        title: '直播与媒体态势',
        desc: '直播能力、开播停播、媒体列表和下载地址已经有后端接口，可作为作业回传入口。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/manage/api/v1/live/capacity', '/media/api/v1/files/{workspace_id}/files'],
        route: pathOf(ERouterName.MEDIA),
        related: 'data',
        icon: VideoCameraOutlined,
      },
    ],
  },
  device: {
    label: '设备管理',
    kicker: 'Manage API',
    title: '设备接入与运维',
    desc: '承接设备拓扑、绑定解绑、详情、HMS、日志、OTA 和属性设置等后端能力。',
    coverage: '设备闭环',
    layers: ['机场', '无人机', 'HMS', '固件', '日志'],
    metrics: [
      ...commonMetrics,
      { label: '设备接口', value: '12+', note: '可操作', tone: 'up', icon: CloudServerOutlined },
    ],
    actions: [
      { label: '设备列表', route: pathOf(ERouterName.DEVICES), icon: CloudServerOutlined, primary: true },
      { label: '固件管理', route: pathOf(ERouterName.FIRMWARES), icon: UploadOutlined },
    ],
    flow: ['设备绑定', '在线状态', 'HMS 告警', '日志回传', '属性设置', '固件升级'],
    capabilities: [
      {
        title: '设备拓扑与详情',
        desc: '支持获取工作空间设备拓扑、绑定设备分页列表、单设备详情和设备信息更新。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/manage/api/v1/devices/{workspace_id}/devices', '/manage/api/v1/devices/{workspace_id}/devices/{device_sn}'],
        route: pathOf(ERouterName.DEVICES),
        icon: ApartmentOutlined,
      },
      {
        title: 'HMS 与设备日志',
        desc: '支持 HMS 查询/已读、设备日志列表、日志上传、取消上传、删除记录和下载日志文件。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/manage/api/v1/devices/{workspace_id}/devices/hms', '/manage/api/v1/workspaces/{workspace_id}/devices/{device_sn}/logs'],
        route: pathOf(ERouterName.DEVICES),
        icon: AlertOutlined,
      },
      {
        title: '固件与 OTA',
        desc: '支持固件列表、固件上传、发布状态修改、最新升级说明和设备 OTA。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/manage/api/v1/workspaces/{workspace_id}/firmwares', '/manage/api/v1/devices/{workspace_id}/devices/ota'],
        route: pathOf(ERouterName.FIRMWARES),
        icon: UploadOutlined,
      },
    ],
  },
  airspace: {
    label: '空域管理',
    kicker: 'Map API',
    title: '空域规则与图层管理',
    desc: '把地图元素、禁飞区、限飞区、任务区和设备同步做成独立二级页面。',
    coverage: '地图可用',
    layers: ['图层', '元素组', '禁飞区', '限飞区', '同步状态'],
    metrics: [
      ...commonMetrics,
      { label: '空域接口', value: '7', note: '增删改查', tone: 'up', icon: EnvironmentOutlined },
    ],
    actions: [
      { label: '空域页面', route: pathOf(ERouterName.FLIGHT_AREA), icon: SafetyCertificateOutlined, primary: true },
      { label: '图层页面', route: pathOf(ERouterName.LAYER), icon: EnvironmentOutlined },
    ],
    flow: ['图层分组', '绘制元素', '保存空域', '启停规则', '同步到设备', '任务前校验'],
    capabilities: [
      {
        title: '地图图层元素',
        desc: '支持元素组查询、点线面元素新增、更新、删除和批量清空。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/map/api/v1/workspaces/{workspace_id}/element-groups', '/map/api/v1/workspaces/{workspace_id}/elements/{id}'],
        route: pathOf(ERouterName.LAYER),
        icon: EnvironmentOutlined,
      },
      {
        title: '空域规则管理',
        desc: '支持空域列表、保存、删除、启停和状态调整，适合做禁飞区/任务区管理页。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/map/api/v1/workspaces/{workspace_id}/flight-areas', '/map/api/v1/workspaces/{workspace_id}/flight-area/{area_id}'],
        route: pathOf(ERouterName.FLIGHT_AREA),
        icon: SafetyCertificateOutlined,
      },
      {
        title: '空域同步设备',
        desc: '支持把空域规则同步给指定设备，并通过 WebSocket 展示同步进度。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/map/api/v1/workspaces/{workspace_id}/flight-area/sync', 'biz_code: flight_areas_sync_progress'],
        route: pathOf(ERouterName.FLIGHT_AREA),
        icon: CloudServerOutlined,
      },
    ],
  },
  route: {
    label: '航线任务',
    kicker: 'Wayline API',
    title: '航线文件与任务调度',
    desc: '后端已经覆盖 KMZ 航线、任务创建、任务列表、任务控制和媒体优先上传。',
    coverage: '任务核心',
    layers: ['航线', '任务', '机场', '航点', '媒体'],
    metrics: [
      ...commonMetrics,
      { label: '任务接口', value: '8', note: '核心链路', tone: 'up', icon: RocketOutlined },
    ],
    actions: [
      { label: '航线库', route: pathOf(ERouterName.WAYLINE), icon: RocketOutlined, primary: true },
      { label: '新建任务', route: taskCreatePath, icon: PlusOutlined },
      { label: '任务列表', route: pathOf(ERouterName.TASK), icon: BarChartOutlined },
    ],
    flow: ['上传 KMZ', '选择机场', '配置安全策略', '创建任务', '执行进度', '媒体优先回传'],
    capabilities: [
      {
        title: '航线文件库',
        desc: '支持航线分页查询、KMZ 上传、航线下载和删除。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/wayline/api/v1/workspaces/{workspace_id}/waylines', '/wayline/api/v1/workspaces/{workspace_id}/waylines/file/upload'],
        route: pathOf(ERouterName.WAYLINE),
        icon: FileSearchOutlined,
      },
      {
        title: '任务创建',
        desc: '支持选择航线、机场、任务类型、失控动作、返航高度、电量和存储阈值。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/wayline/api/v1/workspaces/{workspace_id}/flight-tasks'],
        route: taskCreatePath,
        icon: PlusOutlined,
      },
      {
        title: '任务控制',
        desc: '支持任务列表、删除、暂停/恢复和任务媒体最高优先级上传。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/wayline/api/v1/workspaces/{workspace_id}/jobs', '/wayline/api/v1/workspaces/{workspace_id}/jobs/{job_id}/media-highest'],
        route: pathOf(ERouterName.TASK),
        icon: ControlOutlined,
      },
    ],
  },
  monitor: {
    label: '实时监控',
    kicker: 'Control + Live',
    title: '直播监控与远程控制',
    desc: '直播、DRC、飞行控制和负载控制都有后端入口，适合做实时监控二级页面。',
    coverage: '强操作',
    layers: ['直播', 'DRC', '飞控', '负载', '遥测'],
    metrics: [
      ...commonMetrics,
      { label: '控制接口', value: '10+', note: '需在线设备', tone: 'warn', icon: ControlOutlined },
    ],
    actions: [
      { label: '直播监控', route: livePath, icon: VideoCameraOutlined, primary: true },
      { label: '一张图控制', route: pathOf(ERouterName.WORKSPACE), icon: RadarChartOutlined },
    ],
    flow: ['选择设备', '开启直播', '获取控制权', 'DRC 连接', '飞向目标点', '负载拍照录像'],
    capabilities: [
      {
        title: '直播能力',
        desc: '支持直播能力获取、开始、停止、清晰度更新和镜头切换。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/manage/api/v1/live/capacity', '/manage/api/v1/live/streams/start', '/manage/api/v1/live/streams/stop'],
        route: livePath,
        icon: VideoCameraOutlined,
      },
      {
        title: 'DRC 远程控制',
        desc: '支持 DRC connect、enter、exit，适合虚拟座舱和远程控制页。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/control/api/v1/workspaces/{workspace_id}/drc/connect', '/control/api/v1/workspaces/{workspace_id}/drc/enter'],
        route: pathOf(ERouterName.WORKSPACE),
        icon: ControlOutlined,
      },
      {
        title: '飞行与负载控制',
        desc: '支持飞行控制权、飞向目标点、一键起飞、负载控制权、拍照、录像、变焦和云台复位。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/control/api/v1/devices/{sn}/jobs/fly-to-point', '/control/api/v1/devices/{sn}/payload/commands'],
        route: pathOf(ERouterName.WORKSPACE),
        icon: RocketOutlined,
      },
    ],
  },
  data: {
    label: '数据成果',
    kicker: 'Media API',
    title: '媒体成果与作业数据',
    desc: '媒体文件列表和下载地址可直接承接任务成果，后续可继续串接 AI 识别和报告。',
    coverage: '成果可查',
    layers: ['图片', '视频', '任务', '下载', '报告'],
    metrics: [
      ...commonMetrics,
      { label: '媒体接口', value: '2+', note: '可扩展', tone: 'flat', icon: FileImageOutlined },
    ],
    actions: [
      { label: '媒体成果', route: pathOf(ERouterName.MEDIA), icon: FileImageOutlined, primary: true },
      { label: '任务媒体', route: pathOf(ERouterName.TASK), icon: BarChartOutlined },
    ],
    flow: ['任务执行', '媒体上传', '文件检索', '预览下载', '送入识别', '报告归档'],
    capabilities: [
      {
        title: '媒体文件列表',
        desc: '支持按工作空间查询媒体文件，适合展示图片、视频和任务来源。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/media/api/v1/files/{workspace_id}/files'],
        route: pathOf(ERouterName.MEDIA),
        icon: FileImageOutlined,
      },
      {
        title: '媒体下载地址',
        desc: '支持获取单个媒体文件 URL，用于预览、下载和后续识别输入。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/media/api/v1/files/{workspace_id}/file/{file_id}/url'],
        route: pathOf(ERouterName.MEDIA),
        icon: DatabaseOutlined,
      },
      {
        title: '任务媒体优先上传',
        desc: '支持将任务媒体置为最高上传优先级，保障关键成果快速回传。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/wayline/api/v1/workspaces/{workspace_id}/jobs/{job_id}/media-highest'],
        route: pathOf(ERouterName.TASK),
        icon: UploadOutlined,
      },
    ],
  },
  ai: {
    label: '智能识别',
    kicker: 'Python AI',
    title: '智能识别接入面板',
    desc: '本地存在 YOLO、SAM、TransFuseNet 和邮件发送脚本，但尚未统一成 Spring REST，需要先封装服务再做生产按钮。',
    coverage: '待标准化',
    layers: ['YOLO', 'SAM', '分割', 'CSV', '邮件'],
    metrics: [
      ...commonMetrics,
      { label: '算法目录', value: '4', note: '本地脚本', tone: 'warn', icon: EyeOutlined },
    ],
    actions: [
      { label: '媒体成果', route: pathOf(ERouterName.MEDIA), icon: FileImageOutlined, primary: true },
      { label: '待接入算法服务', route: '', icon: EyeOutlined },
    ],
    flow: ['媒体选择', '上传识别服务', '模型推理', '结果回传', '人工复核', '报告生成'],
    capabilities: [
      {
        title: 'YOLO 目标识别',
        desc: '项目中有 ultralytics/YOLO 结果目录和上传脚本，适合封装车辆、漂浮物、工程目标识别服务。',
        status: '待封装',
        statusColor: 'orange',
        apis: ['本地脚本: DJ/ultralytics-main4/yolo_watcher.py', '结果目录: yolo_results'],
        route: '',
        icon: EyeOutlined,
      },
      {
        title: 'SAM/TransFuseNet 分割',
        desc: '项目中有 SAM 和 TransFuseNet 推理/上传脚本，可封装成地块、水域、病害等分割能力。',
        status: '待封装',
        statusColor: 'orange',
        apis: ['本地脚本: DJ/SAM', '本地脚本: DJ/TransFuseNet'],
        route: '',
        icon: FundProjectionScreenOutlined,
      },
      {
        title: '邮件报告发送',
        desc: 'email_sender 提供 /send-latest，可作为结果报告发送的轻量服务。',
        status: '可接入',
        statusColor: 'blue',
        apis: ['Flask: GET http://localhost:5000/send-latest'],
        route: '',
        icon: FileSearchOutlined,
      },
    ],
  },
  system: {
    label: '系统管理',
    kicker: 'Manage API',
    title: '成员、项目与平台配置',
    desc: '用户、工作空间、固件、设备等后台管理能力已经存在，可以填充系统管理二级页面。',
    coverage: '基础管理',
    layers: ['成员', '工作空间', '设备', '固件', 'Token'],
    metrics: [
      ...commonMetrics,
      { label: '管理接口', value: '10+', note: '基础完整', tone: 'up', icon: SettingOutlined },
    ],
    actions: [
      { label: '成员管理', route: pathOf(ERouterName.MEMBERS), icon: TeamOutlined, primary: true },
      { label: '固件管理', route: pathOf(ERouterName.FIRMWARES), icon: UploadOutlined },
    ],
    flow: ['登录认证', '工作空间', '成员列表', '用户更新', '固件维护', '设备授权'],
    capabilities: [
      {
        title: '登录与工作空间',
        desc: '支持登录、刷新 Token、当前工作空间和当前用户信息。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/manage/api/v1/login', '/manage/api/v1/token/refresh', '/manage/api/v1/workspaces/current'],
        route: pathOf(ERouterName.DASHBOARD),
        icon: SafetyCertificateOutlined,
      },
      {
        title: '成员管理',
        desc: '支持用户列表、当前用户和用户信息更新，可形成角色/成员页面基础。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/manage/api/v1/users/current', '/manage/api/v1/users/{workspace_id}/users'],
        route: pathOf(ERouterName.MEMBERS),
        icon: TeamOutlined,
      },
      {
        title: '平台维护',
        desc: '固件、设备、直播和日志都属于系统维护面，可以从这里统一进入。',
        status: '已支持',
        statusColor: 'green',
        apis: ['/manage/api/v1/workspaces/{workspace_id}/firmwares', '/manage/api/v1/devices/{workspace_id}/devices'],
        route: pathOf(ERouterName.FIRMWARES),
        icon: SettingOutlined,
      },
    ],
  },
}

const docks = [
  { name: '机场 A01', position: [113.93698, 22.5772] },
  { name: '机场 A02', position: [113.95438, 22.5841] },
  { name: '机场 A03', position: [113.9502, 22.5707] },
]

const drones = [
  { name: 'M4E-01', position: [113.9468, 22.5816] },
  { name: 'M30-06', position: [113.9425, 22.5752] },
]

const routePath = [
  [113.93698, 22.5772],
  [113.9416, 22.5801],
  [113.9468, 22.5816],
  [113.95438, 22.5841],
]

const trackPath = [
  [113.93698, 22.5772],
  [113.9412, 22.5759],
  [113.946, 22.5736],
  [113.9502, 22.5707],
]

const currentModule = computed(() => moduleData[activeModule.value])

function applyMapTheme () {
  const AMap = amapInstance.value
  const map = mapInstance.value
  if (!AMap || !map) {
    return
  }

  map.setMapStyle(isNightMode.value ? 'amap://styles/dark' : 'amap://styles/normal')

  if (baseLayer.value === 'satellite') {
    if (!satelliteLayer.value) {
      satelliteLayer.value = new AMap.TileLayer.Satellite()
    }
    map.add(satelliteLayer.value)
  } else if (satelliteLayer.value) {
    map.remove(satelliteLayer.value)
  }
}

function setBaseLayer (layer: 'vector' | 'satellite') {
  baseLayer.value = layer
  applyMapTheme()
}

function setDayNight (enabled: boolean) {
  isNightMode.value = enabled
  applyMapTheme()
}

function syncCollaborativePlan () {
  collaborationStatus.value = `已同步 ${onlineUsers.length} 人规划视图 · ${new Date().toLocaleTimeString()}`
}

function sendFpvCommand (command: string) {
  if (command === '上升') {
    fpvStatus.value.altitude += 5
  } else if (command === '下降') {
    fpvStatus.value.altitude = Math.max(20, fpvStatus.value.altitude - 5)
  } else if (command === '前进') {
    fpvStatus.value.speed = Math.min(18, fpvStatus.value.speed + 1)
  } else if (command === '悬停' || command.includes('驻停')) {
    fpvStatus.value.speed = 0
  }

  commandLog.value = [`${new Date().toLocaleTimeString()} · ${command} 指令已下发`, ...commandLog.value].slice(0, 5)
}

function createLabelMarker (AMap: any, item: { name: string, position: number[] }, type: 'dock' | 'drone') {
  const marker = new AMap.Marker({
    position: item.position,
    anchor: 'center',
    offset: new AMap.Pixel(0, 0),
    content: `<div class="amap-business-marker ${type}">${type === 'dock' ? '机场' : '无人机'} ${item.name.replace(/^机场\s*/, '')}</div>`,
  })
  marker.setExtData(item)
  return marker
}

function renderBusinessOverlays (AMap: any, map: any) {
  const dockMarkers = docks.map(dock => createLabelMarker(AMap, dock, 'dock'))
  const droneMarkers = drones.map(drone => createLabelMarker(AMap, drone, 'drone'))

  const mainRoute = new AMap.Polyline({
    path: routePath,
    strokeColor: '#00a6c8',
    strokeWeight: 6,
    strokeOpacity: 0.9,
    lineJoin: 'round',
    showDir: true,
  })

  const liveTrack = new AMap.Polyline({
    path: trackPath,
    strokeColor: '#19be6b',
    strokeWeight: 4,
    strokeOpacity: 0.95,
    strokeStyle: 'dashed',
    lineJoin: 'round',
  })

  const forbiddenArea = new AMap.Circle({
    center: [113.9561, 22.5817],
    radius: 520,
    strokeColor: '#e23c39',
    strokeOpacity: 0.9,
    strokeWeight: 2,
    fillColor: '#ff4d4f',
    fillOpacity: 0.16,
  })

  const taskArea = new AMap.Circle({
    center: [113.9361, 22.5735],
    radius: 760,
    strokeColor: '#2d8cf0',
    strokeOpacity: 0.85,
    strokeWeight: 2,
    fillColor: '#2d8cf0',
    fillOpacity: 0.12,
  })

  const forbiddenText = new AMap.Text({
    text: '禁飞区 A-12',
    position: [113.9561, 22.5817],
    anchor: 'center',
    style: {
      padding: '4px 8px',
      border: '1px solid rgba(226, 60, 57, 0.45)',
      color: '#cf1322',
      background: 'rgba(255, 255, 255, 0.9)',
      borderRadius: '4px',
      fontSize: '12px',
    },
  })

  const taskText = new AMap.Text({
    text: '任务区 河道-03',
    position: [113.9361, 22.5735],
    anchor: 'center',
    style: {
      padding: '4px 8px',
      border: '1px solid rgba(45, 140, 240, 0.45)',
      color: '#0d6fd6',
      background: 'rgba(255, 255, 255, 0.9)',
      borderRadius: '4px',
      fontSize: '12px',
    },
  })

  map.add([mainRoute, liveTrack, forbiddenArea, taskArea, forbiddenText, taskText, ...dockMarkers, ...droneMarkers])
  map.setFitView([mainRoute, liveTrack, forbiddenArea, taskArea, ...dockMarkers, ...droneMarkers], false, [40, 40, 40, 40])

  let cursor = 0
  realtimeTimer.value = window.setInterval(() => {
    cursor = (cursor + 1) % routePath.length
    const target = routePath[cursor]
    droneMarkers[0].setPosition(target)
    liveTrack.setPath([...trackPath, target])
  }, 2600)
}

async function initAmap () {
  await nextTick()
  if (!amapContainer.value || mapInstance.value) {
    return
  }

  try {
    const AMap = await AMapLoader.load(AMapConfig)
    amapInstance.value = AMap
    const map = new AMap.Map(amapContainer.value, {
      center: [113.943225499, 22.577673716],
      zoom: 15,
      resizeEnable: true,
      viewMode: '2D',
      mapStyle: 'amap://styles/normal',
    })
    mapInstance.value = map
    map.addControl(new AMap.Scale())
    map.addControl(new AMap.ToolBar({ position: 'RB' }))
    applyMapTheme()
    renderBusinessOverlays(AMap, map)
  } catch (error: any) {
    mapLoadError.value = error?.message || '请检查高德地图 Key、网络或域名白名单配置。'
  }
}

function selectModule (key: ModuleKey | string) {
  if (!(key in moduleData)) {
    return
  }
  activeModule.value = key as ModuleKey
  router.replace({ path: '/' + ERouterName.DASHBOARD, query: { module: key } })
}

function openRoute (path?: string) {
  if (!path) {
    return
  }
  if (/^https?:\/\//.test(path)) {
    window.location.href = path
    return
  }
  router.push(path)
}

watchEffect(() => {
  const module = route.query.module
  if (typeof module === 'string' && module in moduleData) {
    activeModule.value = module as ModuleKey
  } else if (route.name === ERouterName.AI_DETECT) {
    activeModule.value = 'ai'
  } else if (route.name === ERouterName.DASHBOARD) {
    activeModule.value = 'dashboard'
  }
})

onMounted(() => {
  initAmap()
})

onBeforeUnmount(() => {
  if (realtimeTimer.value) {
    window.clearInterval(realtimeTimer.value)
    realtimeTimer.value = null
  }
  if (mapInstance.value) {
    mapInstance.value.destroy()
    mapInstance.value = null
  }
})
</script>

<style lang="scss" scoped>
.command-center {
  position: relative;
  min-height: calc(100vh - 64px);
  display: grid;
  grid-template-columns: 148px minmax(680px, 1fr) 340px;
  gap: 16px;
  padding: 16px;
  color: #17233d;
  background: #eef2f6;
}

.command-center.screen-wide {
  grid-template-columns: 112px minmax(820px, 1fr) 280px;
}

.command-center.screen-control {
  grid-template-columns: 88px minmax(940px, 1fr) 360px;
}

.command-center.left-collapsed {
  grid-template-columns: 0 minmax(720px, 1fr) 340px;
}

.command-center.right-collapsed {
  grid-template-columns: 148px minmax(760px, 1fr) 0;
}

.command-center.left-collapsed.right-collapsed {
  grid-template-columns: 0 minmax(900px, 1fr) 0;
}

.command-center.night-mode {
  color: #d9e7f7;
  background: #101722;
}

.command-center.night-mode .workspace,
.command-center.night-mode .panel,
.command-center.night-mode .module-detail,
.command-center.night-mode .module-rail {
  border-color: #26384c;
  background: #151f2b;
}

.collapse-toggle {
  position: fixed;
  z-index: 20;
  top: 92px;
  height: 30px;
  padding: 0 10px;
  border: 1px solid #b9d7f4;
  border-radius: 6px;
  color: #0d6fd6;
  background: rgba(255, 255, 255, 0.94);
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(20, 35, 52, 0.12);
}

.collapse-toggle.left {
  left: 18px;
}

.collapse-toggle.right {
  right: 18px;
}

.left-collapsed .module-rail,
.right-collapsed .inspector {
  width: 0;
  min-width: 0;
  padding: 0;
  border: 0;
  overflow: hidden;
  opacity: 0;
}

.module-rail,
.workspace,
.panel,
.module-detail {
  border: 1px solid #dce4ec;
  border-radius: 8px;
  background: #fff;
}

.module-rail {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-self: start;
  position: sticky;
  top: 16px;
}

.module-rail button {
  height: 42px;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 10px;
  border: 0;
  border-radius: 6px;
  color: #3f5368;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.module-rail button:hover,
.module-rail button.active {
  color: #0d6fd6;
  background: #eaf4ff;
}

.workspace {
  min-width: 0;
  padding: 16px;
}

.workspace-head,
.detail-head,
.capability-title,
.head-actions,
.panel-head,
.flow-list div {
  display: flex;
  align-items: center;
}

.workspace-head {
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 14px;
}

.eyebrow {
  color: #2d8cf0;
  font-size: 13px;
  font-weight: 650;
}

h1 {
  margin: 4px 0 0;
  color: #17233d;
  font-size: 26px;
  line-height: 1.2;
}

.workspace-head p {
  max-width: 760px;
  margin: 8px 0 0;
  color: #63758a;
  line-height: 1.7;
}

.head-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.metric-card {
  min-height: 78px;
  display: grid;
  grid-template-columns: 34px 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e0e8f0;
  border-radius: 8px;
  background: #f8fbfd;
}

.metric-card .anticon,
.capability-icon {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: #0d6fd6;
  background: #eaf4ff;
  font-size: 18px;
}

.metric-card span,
.detail-head span,
.capability-card p,
.backend-list span,
.notice-list p {
  color: #6b7c8f;
  font-size: 12px;
}

.metric-card strong {
  display: block;
  margin-top: 3px;
  font-size: 22px;
}

.metric-card em {
  font-style: normal;
  font-size: 12px;
}

.metric-card em.up {
  color: #19a35b;
}

.metric-card em.warn {
  color: #d46b08;
}

.metric-card em.flat {
  color: #2d8cf0;
}

.doc-control-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 0 0 14px;
  padding: 10px;
  border: 1px solid #dce8f4;
  border-radius: 8px;
  background: #f8fbfd;
}

.control-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 4px 6px;
  border-radius: 7px;
  background: #fff;
}

.control-group span {
  padding: 0 6px;
  color: #63758a;
  font-size: 12px;
  font-weight: 650;
}

.control-group button,
.robot-card button {
  height: 28px;
  padding: 0 10px;
  border: 1px solid #d6e4f0;
  border-radius: 6px;
  color: #31506e;
  background: #fff;
  cursor: pointer;
}

.control-group button.active,
.control-group button:hover,
.robot-card button:hover {
  color: #0d6fd6;
  border-color: #86bfff;
  background: #eaf4ff;
}

.control-group.collaborative em {
  padding: 0 6px;
  color: #19a35b;
  font-size: 12px;
  font-style: normal;
}

.module-detail {
  padding: 14px;
  margin-bottom: 14px;
}

.detail-head {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.detail-head strong,
.detail-head span {
  display: block;
}

.detail-head strong {
  font-size: 16px;
}

.detail-head span {
  margin-top: 4px;
}

.capability-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.capability-card {
  min-width: 0;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  gap: 11px;
  padding: 13px;
  border: 1px solid #e0e8f0;
  border-radius: 8px;
  background: #fbfdff;
}

.capability-title {
  justify-content: space-between;
  gap: 8px;
}

.capability-title strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.capability-card p {
  min-height: 42px;
  margin: 9px 0 10px;
  line-height: 1.65;
}

.api-list {
  display: grid;
  gap: 6px;
  min-height: 58px;
}

.api-list span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 5px 7px;
  border-radius: 5px;
  color: #31506e;
  background: #eef5fb;
  font-family: Consolas, monospace;
  font-size: 11px;
}

.card-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.map-stage {
  border: 1px solid #dce4ec;
  border-radius: 8px;
  overflow: hidden;
}

.map-toolbar {
  height: 44px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  background: #f8fbfd;
  border-bottom: 1px solid #dce4ec;
  overflow-x: auto;
}

.map-toolbar button {
  height: 28px;
  padding: 0 10px;
  border: 1px solid #86bfff;
  border-radius: 6px;
  color: #0d6fd6;
  background: #eaf4ff;
  white-space: nowrap;
}

.amap-wrap {
  position: relative;
  min-height: 430px;
  overflow: hidden;
  background: #dbe8ef;
}

.amap-container {
  width: 100%;
  height: 520px;
  min-height: 430px;
}

.screen-control .amap-container {
  height: 620px;
}

.screen-wide .amap-container {
  height: 560px;
}

.map-hud,
.fpv-pad {
  position: absolute;
  z-index: 2;
  border: 1px solid rgba(220, 232, 244, 0.9);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 14px 34px rgba(20, 35, 52, 0.18);
  backdrop-filter: blur(8px);
}

.map-hud {
  display: grid;
  gap: 4px;
  min-width: 168px;
  padding: 10px 12px;
}

.map-hud.top-left {
  top: 14px;
  left: 14px;
}

.map-hud.top-right {
  top: 14px;
  right: 14px;
}

.map-hud strong,
.fpv-pad strong,
.robot-card strong {
  color: #17233d;
}

.map-hud span,
.map-hud em,
.fpv-pad span,
.fpv-pad p,
.robot-card span {
  color: #63758a;
  font-size: 12px;
  font-style: normal;
}

.avatar-row {
  display: flex;
  gap: 6px;
}

.avatar-row span {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  font-weight: 700;
}

.fpv-pad {
  left: 14px;
  bottom: 14px;
  width: 320px;
  padding: 12px;
}

.fpv-pad > div:first-child {
  display: grid;
  gap: 4px;
  margin-bottom: 10px;
}

.fpv-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.fpv-grid button {
  height: 34px;
  border: 1px solid #b9d7f4;
  border-radius: 6px;
  color: #0d6fd6;
  background: #eaf4ff;
  cursor: pointer;
}

.fpv-grid button.primary {
  color: #fff;
  border-color: #0d6fd6;
  background: #0d6fd6;
}

.fpv-pad p {
  margin: 10px 0 0;
  padding: 8px;
  border-radius: 6px;
  background: #f3f7fb;
}

.map-error {
  position: absolute;
  left: 16px;
  bottom: 16px;
  z-index: 2;
  display: grid;
  gap: 4px;
  max-width: 360px;
  padding: 12px 14px;
  border-radius: 6px;
  color: #b42318;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 12px 26px rgba(20, 35, 52, 0.18);
}

.map-error span {
  color: #6b7c8f;
  font-size: 12px;
}

:deep(.amap-business-marker) {
  height: 30px;
  display: inline-flex;
  align-items: center;
  padding: 0 10px;
  border-radius: 6px;
  color: #fff;
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
  box-shadow: 0 10px 24px rgba(20, 35, 52, 0.24);
}

:deep(.amap-business-marker.dock) {
  background: #0f766e;
}

:deep(.amap-business-marker.drone) {
  background: #0d6fd6;
}

.inspector {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  align-self: start;
}

.panel {
  padding: 14px;
  height: auto;
  overflow: visible;
}

.panel-head {
  justify-content: space-between;
  margin-bottom: 12px;
  font-weight: 650;
}

.backend-list,
.flow-list,
.notice-list {
  display: grid;
  gap: 10px;
}

.backend-list div {
  padding: 10px;
  border-radius: 6px;
  background: #f8fbfd;
  min-height: 54px;
}

.backend-list strong,
.backend-list span {
  display: block;
}

.backend-list span {
  margin-top: 4px;
  line-height: 1.55;
  white-space: normal;
}

.flow-list div {
  min-height: 30px;
  gap: 10px;
  align-items: flex-start;
}

.flow-list em {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: 50%;
  color: #0d6fd6;
  background: #eaf4ff;
  font-style: normal;
  font-size: 12px;
  font-weight: 650;
}

.flow-list span {
  color: #33475f;
  line-height: 1.55;
}

.notice-list p {
  margin: 0;
  line-height: 1.7;
}

.night-mode h1,
.night-mode .detail-head strong,
.night-mode .metric-card strong,
.night-mode .capability-title strong,
.night-mode .panel-head,
.night-mode .map-hud strong,
.night-mode .fpv-pad strong {
  color: #e7f0fb;
}

.night-mode .metric-card,
.night-mode .capability-card,
.night-mode .backend-list div,
.night-mode .doc-control-bar,
.night-mode .control-group {
  border-color: #26384c;
  background: #1b2836;
}

.night-mode .map-hud,
.night-mode .fpv-pad {
  border-color: rgba(38, 56, 76, 0.92);
  background: rgba(21, 31, 43, 0.9);
}

.night-mode .map-hud span,
.night-mode .map-hud em,
.night-mode .fpv-pad span,
.night-mode .fpv-pad p,
.night-mode .workspace-head p,
.night-mode .control-group span {
  color: #a7b8cb;
}

.night-mode .fpv-pad p {
  background: #101722;
}

@media (max-width: 1360px) {
  .command-center {
    grid-template-columns: 132px minmax(0, 1fr) 320px;
  }

  .inspector {
    grid-column: auto;
    display: flex;
    flex-direction: column;
  }

  .capability-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .command-center {
    grid-template-columns: 1fr;
  }

  .module-rail {
    position: static;
    flex-direction: row;
    overflow-x: auto;
  }

  .module-rail button {
    flex: 0 0 auto;
  }

  .workspace-head,
  .inspector {
    display: flex;
    flex-direction: column;
  }

  .head-actions {
    justify-content: flex-start;
    margin-top: 12px;
  }

  .metrics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
