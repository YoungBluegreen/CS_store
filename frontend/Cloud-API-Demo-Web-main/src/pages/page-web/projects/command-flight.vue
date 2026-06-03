<template>
  <main class="command-flight">
    <section class="cockpit-shell">
      <header class="cockpit-topbar glass-panel">
        <button class="back-button" @click="router.push('/dashboard')">
          <ArrowLeftOutlined />
          <span>返回态势</span>
        </button>
        <div class="flight-title">
          <span>Command Flight</span>
          <strong>指令飞行座舱</strong>
        </div>
        <div class="flight-state">
          <span class="state-dot" :class="{ armed: manualControl }"></span>
          <strong>{{ manualControl ? '人工接管中' : 'FPV 监视中' }}</strong>
          <small>{{ currentTime }}</small>
        </div>
      </header>

      <aside class="left-console glass-panel">
        <div class="panel-head">
          <span>Aircraft</span>
          <strong>巡检无人机 01</strong>
        </div>
        <div class="telemetry-grid">
          <div v-for="item in telemetry" :key="item.label">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.unit }}</small>
          </div>
        </div>
        <div class="signal-stack">
          <div v-for="link in links" :key="link.label">
            <span>{{ link.label }}</span>
            <strong>{{ link.value }}</strong>
            <em :class="link.level">{{ link.text }}</em>
          </div>
        </div>
      </aside>

      <section class="hud-stage">
        <div class="sky-field" :class="{ 'map-expanded': primaryView === 'map' }">
          <div class="fpv-view" :class="{ 'is-mini glass-panel': primaryView === 'map' }">
            <div class="horizon" :style="horizonStyle">
              <div class="sky"></div>
              <div class="ground"></div>
            </div>
            <div class="hud-grid"></div>
            <div class="sim-world" :style="simWorldStyle">
              <div class="runway"></div>
              <div class="terrain-line line-a"></div>
              <div class="terrain-line line-b"></div>
              <div class="waypoint waypoint-a">A</div>
              <div class="waypoint waypoint-b">B</div>
              <div class="target-box">
                <span></span>
                <strong>目标巡检点</strong>
              </div>
            </div>
            <div class="drone-avatar" :style="droneStyle">
              <span></span>
            </div>
            <div class="flight-vector" :style="flightVectorStyle">
              <span></span>
            </div>
            <button
              v-if="primaryView === 'map'"
              type="button"
              class="view-expand-button"
              aria-label="放大第一视角"
              @click="setPrimaryView('fpv')"
            >
              <ArrowsAltOutlined />
            </button>
          </div>
          <div class="map-view glass-panel" :class="{ 'is-main': primaryView === 'map', 'is-mini': primaryView === 'fpv' }">
            <div class="overview-head">
              <strong>鸟瞰图</strong>
              <span>MAP</span>
            </div>
            <div class="overview-body">
              <div class="map-grid"></div>
              <div class="map-route"></div>
              <div class="map-home">H</div>
              <div class="map-target">T</div>
              <div class="map-drone" :style="mapDroneStyle"></div>
              <button
                v-if="primaryView === 'fpv'"
                type="button"
                class="view-expand-button"
                aria-label="放大鸟瞰图"
                @click="setPrimaryView('map')"
              >
                <ArrowsAltOutlined />
              </button>
            </div>
          </div>
          <div class="attitude-orb glass-panel">
            <div class="orb-title">球形水平仪</div>
            <div class="orb-body">
              <div class="orb-ball" :style="attitudeBallStyle">
                <div class="orb-sky"></div>
                <div class="orb-ground"></div>
              </div>
              <div class="orb-cross"></div>
              <div class="orb-aircraft"></div>
            </div>
            <div class="orb-meta">
              <span>ROLL {{ Math.round(roll) }}°</span>
              <span>PITCH {{ Math.round(-pitch) }}°</span>
            </div>
          </div>
          <div class="sim-readout glass-panel">
            <strong>{{ manualControl ? '键盘飞行模拟' : '第一人称视角监视' }}</strong>
            <span>{{ manualControl ? 'W/S 加减速 · A/D 转向 · ↑↓ 升降 · ←→ 平移 · Space 悬停 · R 返航' : '当前仅监看 FPV 画面，云台/相机可用；点击人工接管后开启飞行操纵' }}</span>
          </div>
          <div class="gimbal-sight-preview gimbal-sight-hud glass-panel">
            <div class="gimbal-sight-title">云台姿态准星 · 镜头视角</div>
            <div class="gimbal-sight-base">
              <div class="gimbal-sight-dot" :style="gimbalSightStyle"></div>
            </div>
            <div class="gimbal-sight-labels">
              <span>偏航 {{ Math.round(gimbalState.pan) }}°</span>
              <span>俯仰 {{ Math.round(gimbalState.tilt) }}°</span>
            </div>
          </div>
          <div class="pitch-ladder">
            <span>+20</span>
            <span>+10</span>
            <strong>0</strong>
            <span>-10</span>
            <span>-20</span>
          </div>
          <div class="speed-tape glass-panel">
            <span>SPD</span>
            <strong>{{ flightState.speed.toFixed(1) }}</strong>
            <small>m/s</small>
          </div>
          <div class="altitude-tape glass-panel">
            <span>ALT</span>
            <strong>{{ Math.round(flightState.altitude) }}</strong>
            <small>m</small>
          </div>
          <div class="heading-scale glass-panel">
            <span>W</span>
            <span>300</span>
            <strong>{{ headingLabel }}</strong>
            <span>030</span>
            <span>E</span>
          </div>
        </div>
      </section>

      <aside class="right-console glass-panel">
        <div class="panel-head">
          <span>Control</span>
          <strong>指令面板</strong>
        </div>
        <div class="takeover-panel" :class="{ active: manualControl }">
          <button class="takeover-button" :class="{ active: manualControl }" @click="toggleManualControl">
            <strong>{{ manualControl ? '退出人工接管' : '人工接管' }}</strong>
            <span>{{ manualControl ? '释放键盘飞行控制权' : '开启键盘飞行控制权' }}</span>
          </button>
          <p>{{ takeoverHint }}</p>
        </div>
        <div class="mode-switch">
          <button
            v-for="mode in flightModes"
            :key="mode"
            :class="{ active: activeMode === mode }"
            @click="activeMode = mode"
          >
            {{ mode }}
          </button>
        </div>
        <div class="control-mode-panel">
          <div class="keyboard-title">
            <strong>飞控模式</strong>
            <span>{{ controlMode === 'GPS' ? '定位保持' : '手动姿态' }}</span>
          </div>
          <div class="control-mode-switch">
            <button :class="{ active: controlMode === 'GPS' }" @click="setControlMode('GPS')">
              <strong>GPS</strong>
              <span>稳定位移</span>
            </button>
            <button :class="{ active: controlMode === 'ATTI' }" @click="setControlMode('ATTI')">
              <strong>姿态</strong>
              <span>风漂惯性</span>
            </button>
          </div>
          <p>{{ controlModeHint }}</p>
        </div>
        <div class="command-grid" :class="{ locked: !manualControl }">
          <button
            v-for="command in commands"
            :key="command.label"
            :class="command.tone"
            :disabled="!manualControl"
            @click="issueCommand(command.label)"
          >
            <component :is="command.icon" />
            <span>{{ command.label }}</span>
          </button>
        </div>
        <div class="gimbal-console">
          <div class="keyboard-title">
            <strong>云台 / 相机</strong>
            <span>{{ cameraState.recording ? 'REC' : cameraState.mode }}</span>
          </div>
          <div class="camera-stats">
            <div>
              <span>俯仰</span>
              <strong>{{ Math.round(gimbalState.tilt) }}°</strong>
            </div>
            <div>
              <span>偏航</span>
              <strong>{{ Math.round(gimbalState.pan) }}°</strong>
            </div>
            <div>
              <span>变焦</span>
              <strong>{{ cameraState.zoom.toFixed(1) }}x</strong>
            </div>
            <div>
              <span>曝光</span>
              <strong>{{ cameraState.ev > 0 ? '+' : '' }}{{ cameraState.ev.toFixed(1) }}</strong>
            </div>
          </div>
        </div>
        <div class="keyboard-console" :class="{ locked: !manualControl }">
          <div class="keyboard-title">
            <strong>键盘操纵</strong>
            <span>{{ activeKeyLabel }}</span>
          </div>
          <div class="key-row wasd">
            <span></span>
            <kbd :class="{ active: isPressed('KeyW') }">W</kbd>
            <span></span>
            <kbd :class="{ active: isPressed('ArrowUp') }">↑</kbd>
          </div>
          <div class="key-row">
            <kbd :class="{ active: isPressed('KeyA') }">A</kbd>
            <kbd :class="{ active: isPressed('KeyS') }">S</kbd>
            <kbd :class="{ active: isPressed('KeyD') }">D</kbd>
            <kbd :class="{ active: isPressed('ArrowLeft') }">←</kbd>
            <kbd :class="{ active: isPressed('ArrowDown') }">↓</kbd>
            <kbd :class="{ active: isPressed('ArrowRight') }">→</kbd>
          </div>
          <div class="key-row wide">
            <kbd :class="{ active: isPressed('Space') }">Space 悬停</kbd>
            <kbd :class="{ active: isPressed('KeyR') }">R 返航</kbd>
          </div>
          <div class="remote-map-list">
            <section v-for="group in keyboardMapGroups" :key="group.title">
              <strong>{{ group.title }}</strong>
              <p v-for="item in group.items" :key="item.key">
                <kbd :class="{ active: isPressed(item.code) }">{{ item.key }}</kbd>
                <span>{{ item.action }}</span>
              </p>
            </section>
          </div>
        </div>
      </aside>

      <footer class="bottom-console glass-panel">
        <div class="mission-summary">
          <span>当前目标</span>
          <strong>前往：东区 03 号巡检点</strong>
          <small>预计 02:18 抵达，避障开启，返航高度 120m</small>
        </div>
        <div class="command-log">
          <article v-for="log in commandLog" :key="log.id">
            <span>{{ log.time }}</span>
            <strong>{{ log.text }}</strong>
          </article>
        </div>
      </footer>
    </section>
  </main>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeftOutlined,
  ArrowsAltOutlined,
  AimOutlined,
  PauseCircleOutlined,
  RocketOutlined,
  RotateLeftOutlined,
  SendOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'

interface FlightCommand {
  label: string
  tone: 'primary' | 'normal' | 'danger'
  icon: unknown
}

interface FlightState {
  speed: number
  altitude: number
  heading: number
  battery: number
  wind: number
  x: number
  y: number
  throttle: number
  yaw: number
}

interface GimbalState {
  tilt: number
  pan: number
}

interface CameraState {
  mode: 'PHOTO' | 'VIDEO'
  recording: boolean
  zoom: number
  ev: number
}

interface KeyboardMapItem {
  key: string
  code: string
  action: string
}

interface KeyboardMapGroup {
  title: string
  items: KeyboardMapItem[]
}

type ControlMode = 'GPS' | 'ATTI'
type PrimaryView = 'fpv' | 'map'

const router = useRouter()
const activeMode = ref('指令')
const controlMode = ref<ControlMode>('GPS')
const manualControl = ref(false)
const primaryView = ref<PrimaryView>('fpv')
const currentTime = ref('')
const clockTimer = ref<number | null>(null)
const simTimer = ref<number | null>(null)
const roll = ref(-8)
const pitch = ref(10)
const logSeed = ref(4)
const activeKeyLabel = ref('监视模式：飞行锁定')
const pressedKeys = reactive<Record<string, boolean>>({})
const driftState = reactive({
  x: 0,
  y: 0,
})
const flightState = reactive<FlightState>({
  speed: 8.2,
  altitude: 120,
  heading: 327,
  battery: 72,
  wind: 3.1,
  x: 0,
  y: 0,
  throttle: 0,
  yaw: 0,
})
const initialHeading = flightState.heading
const gimbalState = reactive<GimbalState>({
  tilt: -12,
  pan: 0,
})
const cameraState = reactive<CameraState>({
  mode: 'VIDEO',
  recording: false,
  zoom: 1.0,
  ev: 0,
})
const safetyState = reactive({
  obstacleAvoidance: true,
  auxLight: false,
  mapView: false,
  mode: 'N',
})
const commandLog = ref([
  { id: 1, time: '19:08:12', text: 'FPV 监视链路已建立' },
  { id: 2, time: '19:08:18', text: '加载东区 03 号巡检点' },
  { id: 3, time: '19:08:26', text: '避障、返航、链路状态检查完成' },
])

const telemetry = computed(() => [
  { label: '高度', value: `${Math.round(flightState.altitude)}`, unit: 'm' },
  { label: '速度', value: flightState.speed.toFixed(1), unit: 'm/s' },
  { label: '航向', value: `${Math.round(flightState.heading)}`, unit: 'deg' },
  { label: '电量', value: `${Math.round(flightState.battery)}`, unit: '%' },
  { label: '接管', value: manualControl.value ? 'MAN' : 'FPV', unit: manualControl.value ? '人工' : '监视' },
  { label: '漂移', value: driftMagnitude.value.toFixed(1), unit: 'm' },
])

const driftMagnitude = computed(() => Math.hypot(driftState.x, driftState.y))

const takeoverHint = computed(() => (
  manualControl.value
    ? '人工飞行已接管，键盘飞行、返航、起降、悬停指令生效。'
    : '监视模式下保持 FPV 观察，飞行操纵锁定，仅保留云台、相机和模式切换。'
))

const controlModeHint = computed(() => (
  controlMode.value === 'GPS'
    ? 'GPS 模式：定位保持开启，松杆后自动刹停，抗风漂移。'
    : '姿态模式：GPS 定位保持关闭，松杆后保留惯性并受风向漂移。'
))

const headingLabel = computed(() => `${Math.round(flightState.heading).toString().padStart(3, '0')}°`)

const headingDelta = computed(() => normalizeHeadingDelta(flightState.heading - initialHeading))

const cameraViewOffset = computed(() => ({
  x: gimbalState.pan * -4.2,
  y: gimbalState.tilt * -3.1,
}))

const horizonStyle = computed(() => ({
  transform: `rotate(${roll.value.toFixed(1)}deg) translateY(${(pitch.value + cameraViewOffset.value.y * 0.64).toFixed(1)}px)`,
}))

const simWorldStyle = computed(() => ({
  transform: `translate(${(-flightState.x * 0.35 + cameraViewOffset.value.x).toFixed(1)}px, ${(flightState.y * 0.22 + cameraViewOffset.value.y).toFixed(1)}px) rotate(${(-headingDelta.value).toFixed(1)}deg)`,
}))

const droneStyle = computed(() => ({
  transform: `translate(${flightState.x.toFixed(1)}px, ${flightState.y.toFixed(1)}px) rotate(${headingDelta.value.toFixed(1)}deg)`,
}))

const flightVectorStyle = computed(() => ({
  transform: `translate(-50%, -50%) rotate(${headingDelta.value.toFixed(1)}deg)`,
}))

const gimbalSightStyle = computed(() => ({
  transform: `translate(${(gimbalState.pan * 0.72).toFixed(1)}px, ${(gimbalState.tilt * 0.5).toFixed(1)}px)`,
}))

const attitudeBallStyle = computed(() => ({
  transform: `rotate(${roll.value.toFixed(1)}deg) translateY(${pitch.value.toFixed(1)}px)`,
}))

const mapDroneStyle = computed(() => ({
  transform: `translate(${(flightState.x * 0.54).toFixed(1)}px, ${(flightState.y * 0.42).toFixed(1)}px) rotate(${flightState.heading.toFixed(1)}deg)`,
}))

const keyboardMapGroups: KeyboardMapGroup[] = [
  {
    title: '飞行摇杆',
    items: [
      { key: 'W', code: 'KeyW', action: '右杆前推：前进 / 加速' },
      { key: 'S', code: 'KeyS', action: '右杆后拉：后退 / 减速' },
      { key: 'A', code: 'KeyA', action: '左杆左打：左偏航' },
      { key: 'D', code: 'KeyD', action: '左杆右打：右偏航' },
      { key: '↑', code: 'ArrowUp', action: '左杆上推：上升' },
      { key: '↓', code: 'ArrowDown', action: '左杆下拉：下降' },
      { key: '←', code: 'ArrowLeft', action: '右杆左推：左平移' },
      { key: '→', code: 'ArrowRight', action: '右杆右推：右平移' },
    ],
  },
  {
    title: '云台拨轮',
    items: [
      { key: 'I', code: 'KeyI', action: '云台上仰' },
      { key: 'K', code: 'KeyK', action: '云台下俯' },
      { key: 'J', code: 'KeyJ', action: '云台左转' },
      { key: 'L', code: 'KeyL', action: '云台右转' },
      { key: 'N', code: 'KeyN', action: '云台回中' },
    ],
  },
  {
    title: '拍摄控制',
    items: [
      { key: 'C', code: 'KeyC', action: '拍照 / 快门' },
      { key: 'V', code: 'KeyV', action: '开始 / 停止录像' },
      { key: 'B', code: 'KeyB', action: '照片 / 视频模式切换' },
      { key: 'Z', code: 'KeyZ', action: '变焦缩小' },
      { key: 'X', code: 'KeyX', action: '变焦放大' },
      { key: 'Q', code: 'KeyQ', action: '曝光降低' },
      { key: 'E', code: 'KeyE', action: '曝光提高' },
    ],
  },
  {
    title: '安全与功能键',
    items: [
      { key: 'Space', code: 'Space', action: '急停悬停 / Pause' },
      { key: 'R', code: 'KeyR', action: '返航 RTH' },
      { key: 'T', code: 'KeyT', action: '起飞 / 降落' },
      { key: 'Esc', code: 'Escape', action: '紧急停止' },
      { key: '1', code: 'Digit1', action: '普通挡 N' },
      { key: '2', code: 'Digit2', action: '运动挡 S' },
      { key: '3', code: 'Digit3', action: '平稳挡 C' },
      { key: '4', code: 'Digit4', action: 'GPS 模式' },
      { key: '5', code: 'Digit5', action: '姿态模式' },
      { key: 'M', code: 'KeyM', action: '地图 / 相机视图切换' },
      { key: 'P', code: 'KeyP', action: '避障开关' },
      { key: 'G', code: 'KeyG', action: '补光灯开关' },
      { key: 'F', code: 'KeyF', action: 'Fn 自定义功能' },
      { key: 'H', code: 'KeyH', action: '刷新返航点' },
    ],
  },
]

const manualFlightCodes = new Set([
  'KeyW',
  'KeyS',
  'KeyA',
  'KeyD',
  'ArrowUp',
  'ArrowDown',
  'ArrowLeft',
  'ArrowRight',
  'Space',
  'KeyR',
  'KeyT',
  'Escape',
  'Digit1',
  'Digit2',
  'Digit3',
])

const links = computed(() => [
  { label: '图传链路', value: '18 Mbps', text: '良好', level: 'good' },
  { label: '控制链路', value: manualControl.value ? '42 ms' : '待命', text: manualControl.value ? '接管' : '监视', level: 'good' },
  {
    label: controlMode.value === 'GPS' ? 'GNSS / RTK' : 'GNSS / RTK',
    value: controlMode.value === 'GPS' ? '固定' : '姿态',
    text: controlMode.value === 'GPS' ? '定位保持' : '降级',
    level: controlMode.value === 'GPS' ? 'good' : 'warn',
  },
])

const flightModes = ['指令', '航点', '环绕', '跟随']

const commands: FlightCommand[] = [
  { label: '起飞', tone: 'primary', icon: RocketOutlined },
  { label: '飞向目标', tone: 'primary', icon: SendOutlined },
  { label: '悬停', tone: 'normal', icon: PauseCircleOutlined },
  { label: '精准对准', tone: 'normal', icon: AimOutlined },
  { label: '返航', tone: 'normal', icon: RotateLeftOutlined },
  { label: '紧急停止', tone: 'danger', icon: WarningOutlined },
]

function updateTime () {
  currentTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

function clamp (value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

function normalizeHeading (value: number) {
  return (value + 360) % 360
}

function normalizeHeadingDelta (value: number) {
  return ((value + 540) % 360) - 180
}

function isPressed (code: string) {
  return Boolean(pressedKeys[code])
}

function isControlKey (code: string) {
  return keyboardMapGroups.some(group => group.items.some(item => item.code === code))
}

function logKeyboardAction (text: string) {
  logSeed.value += 1
  commandLog.value.unshift({
    id: logSeed.value,
    time: currentTime.value || '--:--:--',
    text,
  })
  commandLog.value = commandLog.value.slice(0, 4)
}

function toggleManualControl () {
  manualControl.value = !manualControl.value
  if (!manualControl.value) {
    pressedKeys.KeyW = false
    pressedKeys.KeyS = false
    pressedKeys.KeyA = false
    pressedKeys.KeyD = false
    pressedKeys.ArrowUp = false
    pressedKeys.ArrowDown = false
    pressedKeys.ArrowLeft = false
    pressedKeys.ArrowRight = false
    pressedKeys.Space = false
    flightState.throttle = 0
    flightState.yaw = 0
    activeKeyLabel.value = '监视模式：飞行锁定'
  } else {
    activeKeyLabel.value = '人工接管：等待键盘输入'
  }
  logKeyboardAction(manualControl.value ? '人工接管已开启：键盘飞行控制生效' : '已退出人工接管：恢复 FPV 监视')
}

function setControlMode (mode: ControlMode) {
  if (controlMode.value === mode) return
  controlMode.value = mode
  if (mode === 'GPS') {
    driftState.x *= 0.35
    driftState.y *= 0.35
    flightState.speed = clamp(flightState.speed * 0.72, 0, 18)
  }
  logKeyboardAction(mode === 'GPS' ? '飞控模式切换：GPS 定位保持' : '飞控模式切换：姿态模式，GPS 保持关闭')
}

function setPrimaryView (view: PrimaryView) {
  if (primaryView.value === view) return
  primaryView.value = view
  safetyState.mapView = view === 'map'
}

function keyActionText (code: string) {
  const item = keyboardMapGroups.flatMap(group => group.items).find(entry => entry.code === code)
  return item ? `${item.key}：${item.action}` : code
}

function applyMomentaryControl (code: string) {
  if (!manualControl.value && manualFlightCodes.has(code)) {
    activeKeyLabel.value = '请先点击人工接管'
    logKeyboardAction('飞行操纵已锁定：请先人工接管')
    return
  }
  if (code === 'KeyR') {
    issueCommand('返航')
    flightState.x = 0
    flightState.y = 0
    flightState.heading = 327
  }
  if (code === 'KeyN') {
    gimbalState.tilt = 0
    gimbalState.pan = 0
  }
  if (code === 'KeyA') {
    flightState.heading = normalizeHeading(flightState.heading - 5)
  }
  if (code === 'KeyD') {
    flightState.heading = normalizeHeading(flightState.heading + 5)
  }
  if (code === 'ArrowLeft') {
    flightState.x = clamp(flightState.x - 6, -95, 95)
  }
  if (code === 'ArrowRight') {
    flightState.x = clamp(flightState.x + 6, -95, 95)
  }
  if (code === 'KeyI') {
    gimbalState.tilt = clamp(gimbalState.tilt - 4, -90, 30)
  }
  if (code === 'KeyK') {
    gimbalState.tilt = clamp(gimbalState.tilt + 4, -90, 30)
  }
  if (code === 'KeyJ') {
    gimbalState.pan = clamp(gimbalState.pan - 4, -60, 60)
  }
  if (code === 'KeyL') {
    gimbalState.pan = clamp(gimbalState.pan + 4, -60, 60)
  }
  if (code === 'KeyC') {
    logKeyboardAction(`相机快门：已拍摄 ${cameraState.zoom.toFixed(1)}x 照片`)
  }
  if (code === 'KeyV') {
    cameraState.recording = !cameraState.recording
    logKeyboardAction(cameraState.recording ? '录像开始' : '录像停止')
  }
  if (code === 'KeyB') {
    cameraState.mode = cameraState.mode === 'PHOTO' ? 'VIDEO' : 'PHOTO'
    logKeyboardAction(`相机模式切换为 ${cameraState.mode}`)
  }
  if (code === 'KeyZ') {
    cameraState.zoom = clamp(cameraState.zoom - 0.2, 1, 7)
  }
  if (code === 'KeyX') {
    cameraState.zoom = clamp(cameraState.zoom + 0.2, 1, 7)
  }
  if (code === 'KeyQ') {
    cameraState.ev = clamp(cameraState.ev - 0.3, -3, 3)
  }
  if (code === 'KeyE') {
    cameraState.ev = clamp(cameraState.ev + 0.3, -3, 3)
  }
  if (code === 'KeyT') {
    const landing = flightState.altitude > 30
    issueCommand(landing ? '降落' : '起飞')
    flightState.altitude = landing ? 20 : 45
  }
  if (code === 'Escape') {
    flightState.speed = 0
    flightState.throttle = 0
    logKeyboardAction('紧急停止：速度归零，保持悬停')
  }
  if (code === 'Digit1' || code === 'Digit2' || code === 'Digit3') {
    safetyState.mode = code === 'Digit1' ? 'N' : code === 'Digit2' ? 'S' : 'C'
    activeMode.value = code === 'Digit2' ? '运动' : code === 'Digit3' ? '平稳' : '指令'
    logKeyboardAction(`飞行挡位切换：${safetyState.mode}`)
  }
  if (code === 'Digit4') {
    setControlMode('GPS')
  }
  if (code === 'Digit5') {
    setControlMode('ATTI')
  }
  if (code === 'KeyM') {
    safetyState.mapView = !safetyState.mapView
    logKeyboardAction(safetyState.mapView ? '切换到地图鸟瞰主视角' : '切换到相机座舱主视角')
  }
  if (code === 'KeyP') {
    safetyState.obstacleAvoidance = !safetyState.obstacleAvoidance
    logKeyboardAction(safetyState.obstacleAvoidance ? '避障已开启' : '避障已关闭')
  }
  if (code === 'KeyG') {
    safetyState.auxLight = !safetyState.auxLight
    logKeyboardAction(safetyState.auxLight ? '补光灯已开启' : '补光灯已关闭')
  }
  if (code === 'KeyF') {
    logKeyboardAction('Fn 自定义键：标记当前巡检画面')
  }
  if (code === 'KeyH') {
    logKeyboardAction('返航点已刷新为当前机场位置')
  }
}

function handleKeyDown (event: KeyboardEvent) {
  if (!isControlKey(event.code)) return
  event.preventDefault()
  if (!manualControl.value && manualFlightCodes.has(event.code)) {
    activeKeyLabel.value = '监视模式：飞行锁定'
    if (!pressedKeys[event.code]) logKeyboardAction('监视模式下飞行键无效，请点击人工接管')
    return
  }
  const wasPressed = pressedKeys[event.code]
  pressedKeys[event.code] = true

  if (event.code === 'Space') {
    activeKeyLabel.value = 'Space：悬停刹停'
    if (!wasPressed) logKeyboardAction('键盘 Space：进入悬停刹停')
  } else {
    activeKeyLabel.value = keyActionText(event.code)
    if (!wasPressed) {
      logKeyboardAction(`键盘 ${keyActionText(event.code)}`)
      applyMomentaryControl(event.code)
    }
  }
}

function handleKeyUp (event: KeyboardEvent) {
  if (!isControlKey(event.code)) return
  event.preventDefault()
  pressedKeys[event.code] = false
  activeKeyLabel.value = manualControl.value ? '人工接管：等待键盘输入' : '监视模式：飞行锁定'
}

function updateSimulation () {
  const throttle = manualControl.value ? (isPressed('KeyW') ? 1 : 0) - (isPressed('KeyS') ? 1 : 0) : 0
  const yaw = manualControl.value ? (isPressed('KeyD') ? 1 : 0) - (isPressed('KeyA') ? 1 : 0) : 0
  const strafe = manualControl.value ? (isPressed('ArrowRight') ? 1 : 0) - (isPressed('ArrowLeft') ? 1 : 0) : 0
  const lift = manualControl.value ? (isPressed('ArrowUp') ? 1 : 0) - (isPressed('ArrowDown') ? 1 : 0) : 0
  const hover = manualControl.value && isPressed('Space')
  const gimbalTilt = (isPressed('KeyK') ? 1 : 0) - (isPressed('KeyI') ? 1 : 0)
  const gimbalPan = (isPressed('KeyL') ? 1 : 0) - (isPressed('KeyJ') ? 1 : 0)
  const isGpsMode = controlMode.value === 'GPS'

  flightState.throttle = throttle
  flightState.yaw = yaw
  const speedLimit = safetyState.mode === 'S' ? 24 : safetyState.mode === 'C' ? 10 : 18
  const passiveBrake = isGpsMode && throttle === 0 ? 0.1 : 0.025
  const hoverBrake = hover ? (isGpsMode ? 0.62 : 0.28) : 0
  flightState.speed = clamp(flightState.speed + throttle * 0.22 - passiveBrake - hoverBrake, 0, speedLimit)
  flightState.altitude = clamp(flightState.altitude + lift * 0.85, 20, 300)
  flightState.heading = normalizeHeading(flightState.heading + yaw * (safetyState.mode === 'C' ? 1.1 : 1.9) * (isGpsMode ? 0.9 : 1.16))
  flightState.battery = clamp(flightState.battery - Math.max(flightState.speed, 1) * 0.0009, 0, 100)
  gimbalState.tilt = clamp(gimbalState.tilt + gimbalTilt * 1.4, -90, 30)
  gimbalState.pan = clamp(gimbalState.pan + gimbalPan * 1.6, -90, 90)

  const headingRad = flightState.heading * Math.PI / 180
  if (isGpsMode) {
    driftState.x *= 0.82
    driftState.y *= 0.82
  } else {
    driftState.x = clamp(driftState.x + flightState.wind * 0.018 + strafe * 0.18 - yaw * 0.04, -28, 28)
    driftState.y = clamp(driftState.y + flightState.wind * 0.01 + throttle * 0.08, -22, 22)
  }
  const strafeGain = isGpsMode ? 1.2 : 1.42
  flightState.x = clamp(flightState.x + Math.sin(headingRad) * flightState.speed * 0.06 + strafe * strafeGain + driftState.x * 0.035, -95, 95)
  flightState.y = clamp(flightState.y - Math.cos(headingRad) * flightState.speed * 0.06 - lift * 0.25 + driftState.y * 0.03, -72, 72)
  roll.value = yaw * -16 + strafe * (isGpsMode ? 5 : 9) + (isGpsMode ? 0 : driftState.x * 0.14)
  pitch.value = clamp(throttle * -15 + lift * -4 + (isGpsMode ? 0 : driftState.y * 0.12), -18, 18)
}

function issueCommand (label: string) {
  logSeed.value += 1
  commandLog.value.unshift({
    id: logSeed.value,
    time: currentTime.value || '--:--:--',
    text: `已下发「${label}」指令，等待设备确认`,
  })
  commandLog.value = commandLog.value.slice(0, 4)
  roll.value = label.includes('返航') ? 4 : label.includes('目标') ? -12 : -8
  pitch.value = label.includes('起飞') ? -6 : label.includes('悬停') ? 8 : 10
  if (label.includes('起飞')) {
    flightState.altitude = clamp(flightState.altitude + 12, 20, 300)
    flightState.speed = clamp(flightState.speed + 1.4, 0, 18)
  }
  if (label.includes('悬停')) {
    flightState.speed = 0
  }
  if (label.includes('返航')) {
    flightState.x = 0
    flightState.y = 0
    flightState.heading = 327
  }
}

onMounted(() => {
  updateTime()
  clockTimer.value = window.setInterval(updateTime, 1000)
  simTimer.value = window.setInterval(updateSimulation, 80)
  window.addEventListener('keydown', handleKeyDown)
  window.addEventListener('keyup', handleKeyUp)
})

onBeforeUnmount(() => {
  if (clockTimer.value) {
    window.clearInterval(clockTimer.value)
  }
  if (simTimer.value) {
    window.clearInterval(simTimer.value)
  }
  window.removeEventListener('keydown', handleKeyDown)
  window.removeEventListener('keyup', handleKeyUp)
})
</script>

<style lang="scss" scoped>
.command-flight {
  min-height: calc(100vh - 64px);
  color: #e9fbff;
  background:
    radial-gradient(circle at 50% 20%, rgba(0, 206, 255, 0.2), transparent 26%),
    linear-gradient(135deg, #05182b 0%, #082442 44%, #061220 100%);
  overflow: hidden;
}

.cockpit-shell {
  position: relative;
  min-height: calc(100vh - 64px);
  padding: 78px 360px 122px;
}

.glass-panel {
  border: 1px solid rgba(76, 221, 255, 0.24);
  background: linear-gradient(180deg, rgba(9, 43, 74, 0.72), rgba(5, 26, 48, 0.58));
  box-shadow: inset 0 0 26px rgba(0, 210, 255, 0.08), 0 18px 42px rgba(0, 8, 20, 0.34);
  backdrop-filter: blur(16px);
}

.cockpit-topbar,
.left-console,
.right-console,
.bottom-console {
  position: absolute;
  z-index: 3;
}

.cockpit-topbar {
  top: 14px;
  left: 18px;
  right: 18px;
  height: 50px;
  display: grid;
  grid-template-columns: 150px 1fr 210px;
  align-items: center;
  gap: 16px;
  padding: 0 14px;
}

button {
  font: inherit;
}

.back-button,
.command-grid button,
.mode-switch button {
  border: 1px solid rgba(76, 221, 255, 0.25);
  color: #dffaff;
  background: rgba(15, 70, 112, 0.52);
  cursor: pointer;
}

.back-button {
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.flight-title {
  text-align: center;
}

.flight-title span,
.panel-head span,
.mission-summary span,
.telemetry-grid span,
.signal-stack span {
  color: rgba(187, 236, 255, 0.66);
  font-size: 12px;
  text-transform: uppercase;
}

.flight-title strong {
  display: block;
  margin-top: 2px;
  color: #fff;
  font-size: 18px;
}

.flight-state {
  display: grid;
  grid-template-columns: 10px 1fr;
  align-items: center;
  column-gap: 8px;
}

.flight-state small {
  grid-column: 2;
  color: #62e6ff;
  font-family: Consolas, monospace;
}

.state-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #5dff90;
  box-shadow: 0 0 16px #5dff90;
}

.state-dot.armed {
  background: #ffcf5a;
  box-shadow: 0 0 16px rgba(255, 207, 90, 0.95);
}

.left-console,
.right-console {
  top: 78px;
  bottom: 122px;
  width: 318px;
  padding: 16px;
}

.left-console {
  left: 18px;
}

.right-console {
  right: 18px;
}

.panel-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 14px;
}

.panel-head strong {
  color: #fff;
  font-size: 18px;
}

.telemetry-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.telemetry-grid div,
.signal-stack div {
  min-height: 72px;
  padding: 12px;
  border: 1px solid rgba(76, 221, 255, 0.16);
  background: rgba(0, 22, 43, 0.42);
}

.telemetry-grid strong {
  display: inline-block;
  margin-top: 8px;
  color: #fff;
  font-family: Consolas, monospace;
  font-size: 28px;
}

.telemetry-grid small {
  margin-left: 5px;
  color: #62e6ff;
}

.signal-stack {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.signal-stack div {
  min-height: 58px;
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
}

.signal-stack strong {
  color: #fff;
}

.signal-stack em {
  grid-row: span 2;
  padding: 4px 8px;
  border-radius: 4px;
  font-style: normal;
}

.signal-stack .good {
  color: #79ffa2;
  background: rgba(37, 196, 110, 0.18);
}

.signal-stack .warn {
  color: #ffd66b;
  background: rgba(255, 171, 64, 0.18);
}

.hud-stage {
  position: relative;
  z-index: 1;
  height: calc(100vh - 264px);
  min-height: 520px;
}

.sky-field {
  position: relative;
  height: 100%;
  overflow: hidden;
  border: 1px solid rgba(76, 221, 255, 0.18);
  background: #07192e;
  box-shadow: inset 0 0 80px rgba(0, 210, 255, 0.12);
}

.fpv-view,
.map-view {
  position: absolute;
  overflow: hidden;
  transition: inset 0.22s ease, width 0.22s ease, height 0.22s ease, padding 0.22s ease, opacity 0.18s ease;
}

.fpv-view {
  inset: 0;
  z-index: 1;
  background: #07192e;
}

.fpv-view.is-mini {
  inset: 14px 14px auto auto;
  z-index: 8;
  width: 176px;
  height: 166px;
  padding: 10px;
  border-color: rgba(76, 221, 255, 0.28);
  background: rgba(4, 24, 45, 0.82);
  box-shadow: 0 16px 34px rgba(0, 8, 20, 0.38), inset 0 0 22px rgba(98, 230, 255, 0.08);
}

.fpv-view.is-mini .horizon {
  inset: -52%;
}

.fpv-view.is-mini .hud-grid {
  display: none;
}

.fpv-view.is-mini .sim-world {
  inset: -30%;
  opacity: 0.82;
}

.fpv-view.is-mini .waypoint,
.fpv-view.is-mini .target-box,
.fpv-view.is-mini .drone-avatar,
.fpv-view.is-mini .flight-vector {
  display: none;
}

.horizon {
  position: absolute;
  inset: -34%;
  transition: transform 0.16s ease-out;
  will-change: transform;
}

.sky,
.ground {
  height: 50%;
}

.sky {
  background: linear-gradient(180deg, #0d7eaa, #123b70);
}

.ground {
  background: linear-gradient(180deg, #7a5a35, #1b3a2f);
}

.hud-grid {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(rgba(96, 229, 255, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(96, 229, 255, 0.12) 1px, transparent 1px);
  background-size: 72px 72px;
  mask-image: radial-gradient(circle at center, #000 0 55%, transparent 78%);
}

.attitude-orb {
  position: absolute;
  top: 14px;
  z-index: 5;
}

.attitude-orb {
  left: 18px;
  width: 158px;
  padding: 0;
  border-color: transparent;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
}

.orb-title,
.overview-head span {
  color: rgba(223, 250, 255, 0.46);
  font-size: 12px;
}

.orb-body {
  position: relative;
  width: 112px;
  height: 112px;
  margin: 8px auto;
  overflow: hidden;
  border: 2px solid rgba(223, 250, 255, 0.68);
  border-radius: 50%;
  background: #07192e;
  box-shadow: inset 0 0 20px rgba(0, 0, 0, 0.54), 0 0 18px rgba(114, 242, 255, 0.24);
}

.orb-ball {
  position: absolute;
  inset: -34px;
  transition: transform 0.08s linear;
}

.orb-sky,
.orb-ground {
  height: 50%;
}

.orb-sky {
  background: linear-gradient(180deg, #17a0d6, #15497c);
}

.orb-ground {
  background: linear-gradient(180deg, #8a6436, #4a321f);
}

.orb-cross::before,
.orb-cross::after {
  content: "";
  position: absolute;
  left: 50%;
  top: 50%;
  background: rgba(255, 255, 255, 0.82);
  transform: translate(-50%, -50%);
}

.orb-cross::before {
  width: 86px;
  height: 1px;
}

.orb-cross::after {
  width: 1px;
  height: 86px;
}

.orb-aircraft {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 58px;
  height: 20px;
  transform: translate(-50%, -50%);
}

.orb-aircraft::before,
.orb-aircraft::after {
  content: "";
  position: absolute;
  top: 8px;
  width: 24px;
  height: 3px;
  background: #fff;
}

.orb-aircraft::before {
  left: 0;
}

.orb-aircraft::after {
  right: 0;
}

.orb-meta {
  display: flex;
  justify-content: space-between;
  color: rgba(98, 230, 255, 0.72);
  font-family: Consolas, monospace;
  font-size: 11px;
}

.map-view {
  top: 14px;
  right: 14px;
  z-index: 8;
  width: 176px;
  padding: 10px;
}

.map-view.is-main {
  inset: 0;
  z-index: 1;
  width: auto;
  padding: 0;
  border-color: transparent;
  background:
    radial-gradient(circle at 58% 42%, rgba(98, 230, 255, 0.18), transparent 36%),
    linear-gradient(135deg, rgba(6, 43, 70, 0.96), rgba(5, 24, 43, 0.96));
  box-shadow: inset 0 0 96px rgba(0, 210, 255, 0.12);
  backdrop-filter: none;
}

.overview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.overview-head strong {
  color: #fff;
}

.map-view.is-main .overview-head {
  position: absolute;
  top: 14px;
  right: 18px;
  z-index: 2;
  width: 176px;
  padding: 8px 10px;
  margin-bottom: 0;
  border: 1px solid rgba(98, 230, 255, 0.18);
  background: rgba(4, 24, 45, 0.46);
}

.overview-body {
  position: relative;
  height: 124px;
  overflow: hidden;
  border: 1px solid rgba(98, 230, 255, 0.2);
  background:
    radial-gradient(circle at 52% 46%, rgba(0, 216, 255, 0.16), transparent 36%),
    rgba(3, 26, 48, 0.66);
}

.map-view.is-main .overview-body {
  position: absolute;
  inset: 0;
  height: auto;
  border: 0;
  background:
    radial-gradient(circle at 55% 48%, rgba(114, 242, 255, 0.18), transparent 26%),
    linear-gradient(180deg, rgba(6, 68, 96, 0.6), rgba(4, 25, 43, 0.82));
}

.map-grid {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(rgba(98, 230, 255, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(98, 230, 255, 0.12) 1px, transparent 1px);
  background-size: 24px 24px;
}

.map-view.is-main .map-grid {
  background:
    linear-gradient(rgba(98, 230, 255, 0.15) 1px, transparent 1px),
    linear-gradient(90deg, rgba(98, 230, 255, 0.15) 1px, transparent 1px);
  background-size: 72px 72px;
}

.map-route {
  position: absolute;
  left: 22px;
  right: 28px;
  top: 78px;
  height: 2px;
  background: #9dff7a;
  transform: rotate(-28deg);
  transform-origin: left;
  box-shadow: 0 0 12px rgba(157, 255, 122, 0.6);
}

.map-view.is-main .map-route {
  left: 12%;
  right: 14%;
  top: 58%;
  height: 3px;
  transform: rotate(-22deg);
  box-shadow: 0 0 18px rgba(157, 255, 122, 0.72);
}

.map-home,
.map-target,
.map-drone {
  position: absolute;
  display: grid;
  place-items: center;
}

.map-home,
.map-target {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  color: #061220;
  font-size: 11px;
  font-weight: 800;
}

.map-home {
  left: 24px;
  bottom: 22px;
  background: #72f2ff;
}

.map-view.is-main .map-home {
  left: 14%;
  bottom: 28%;
  width: 34px;
  height: 34px;
  font-size: 14px;
}

.map-target {
  right: 24px;
  top: 24px;
  background: #9dff7a;
}

.map-view.is-main .map-target {
  right: 17%;
  top: 30%;
  width: 34px;
  height: 34px;
  font-size: 14px;
}

.map-drone {
  left: calc(50% - 8px);
  top: calc(50% - 8px);
  width: 0;
  height: 0;
  border-right: 8px solid transparent;
  border-bottom: 16px solid #fff;
  border-left: 8px solid transparent;
  filter: drop-shadow(0 0 10px rgba(114, 242, 255, 0.82));
  transition: transform 0.08s linear;
}

.map-view.is-main .map-drone {
  border-right-width: 12px;
  border-bottom-width: 24px;
  border-left-width: 12px;
  filter: drop-shadow(0 0 16px rgba(255, 255, 255, 0.9));
}

.view-expand-button {
  position: absolute;
  left: 8px;
  bottom: 8px;
  z-index: 6;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 1px solid rgba(98, 230, 255, 0.42);
  border-radius: 4px;
  color: #dffaff;
  background: rgba(2, 22, 40, 0.72);
  box-shadow: 0 0 14px rgba(98, 230, 255, 0.16);
  cursor: pointer;
}

.view-expand-button:hover {
  color: #061220;
  background: #72f2ff;
}

.sim-world {
  position: absolute;
  inset: -12%;
  transform-origin: center;
  transition: transform 0.16s ease-out;
  will-change: transform;
}

.runway {
  position: absolute;
  left: 50%;
  bottom: -80px;
  width: 160px;
  height: 420px;
  transform: translateX(-50%) perspective(380px) rotateX(62deg);
  transform-origin: bottom;
  border: 1px solid rgba(114, 242, 255, 0.28);
  background:
    linear-gradient(90deg, transparent 48%, rgba(255, 255, 255, 0.72) 49%, rgba(255, 255, 255, 0.72) 51%, transparent 52%),
    repeating-linear-gradient(180deg, rgba(114, 242, 255, 0.22) 0 18px, transparent 18px 44px),
    linear-gradient(180deg, rgba(34, 88, 96, 0.1), rgba(15, 42, 54, 0.7));
  opacity: 0.78;
}

.terrain-line {
  position: absolute;
  width: 260px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(114, 242, 255, 0.5), transparent);
}

.line-a {
  left: 10%;
  bottom: 30%;
  transform: rotate(-18deg);
}

.line-b {
  right: 8%;
  bottom: 40%;
  transform: rotate(24deg);
}

.waypoint,
.target-box {
  position: absolute;
  display: grid;
  place-items: center;
  color: #07192e;
  font-weight: 800;
}

.waypoint {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #72f2ff;
  box-shadow: 0 0 22px rgba(114, 242, 255, 0.72);
}

.waypoint-a {
  top: 22%;
  left: 22%;
}

.waypoint-b {
  right: 23%;
  bottom: 28%;
}

.target-box {
  top: 25%;
  right: 17%;
  width: 108px;
  height: 78px;
  border: 2px solid #9dff7a;
  color: #dffaff;
  background: rgba(65, 255, 133, 0.08);
  box-shadow: 0 0 22px rgba(93, 255, 144, 0.28);
}

.target-box span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #9dff7a;
  box-shadow: 0 0 16px #9dff7a;
}

.target-box strong {
  font-size: 12px;
}

.drone-avatar {
  position: absolute;
  left: calc(50% - 22px);
  top: calc(50% - 22px);
  z-index: 3;
  width: 44px;
  height: 44px;
  transition: transform 0.08s linear;
}

.drone-avatar::before,
.drone-avatar::after,
.drone-avatar span,
.drone-avatar span::before {
  content: "";
  position: absolute;
  background: #e9fbff;
  box-shadow: 0 0 16px rgba(114, 242, 255, 0.82);
}

.drone-avatar::before {
  left: 20px;
  top: 2px;
  width: 4px;
  height: 40px;
}

.drone-avatar::after {
  left: 2px;
  top: 20px;
  width: 40px;
  height: 4px;
}

.drone-avatar span {
  left: 14px;
  top: 14px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #72f2ff;
}

.drone-avatar span::before {
  left: 50%;
  top: -18px;
  width: 0;
  height: 0;
  border-right: 6px solid transparent;
  border-bottom: 12px solid #9dff7a;
  border-left: 6px solid transparent;
  background: transparent;
  box-shadow: none;
  filter: drop-shadow(0 0 8px rgba(157, 255, 122, 0.85));
  transform: translateX(-50%);
}

.flight-vector {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 112px;
  height: 112px;
  transform: translate(-50%, -50%);
  border: 2px solid #72f2ff;
  border-radius: 50%;
  box-shadow: 0 0 22px rgba(114, 242, 255, 0.45);
  transition: transform 0.08s linear;
}

.flight-vector::before,
.flight-vector::after,
.flight-vector span {
  content: "";
  position: absolute;
  top: 50%;
  width: 86px;
  height: 2px;
  background: #72f2ff;
}

.flight-vector::before {
  right: 100%;
}

.flight-vector::after {
  left: 100%;
}

.flight-vector span {
  left: 50%;
  width: 2px;
  height: 54px;
  transform: translate(-50%, -100%);
}

.sim-readout {
  position: absolute;
  left: 50%;
  top: 18px;
  z-index: 4;
  display: grid;
  gap: 4px;
  width: min(520px, calc(100% - 36px));
  padding: 10px 14px;
  transform: translateX(-50%);
  text-align: center;
}

.sim-readout strong {
  color: #fff;
  font-size: 15px;
}

.sim-readout span {
  color: rgba(223, 250, 255, 0.72);
  font-size: 12px;
}

.flight-vector,
.pitch-ladder,
.speed-tape,
.altitude-tape,
.heading-scale {
  z-index: 4;
}

.speed-tape,
.altitude-tape,
.heading-scale {
  backdrop-filter: blur(10px);
}

.speed-tape strong,
.altitude-tape strong {
  min-width: 66px;
  text-align: center;
}

.pitch-ladder {
  position: absolute;
  top: 50%;
  left: 50%;
  display: grid;
  gap: 22px;
  transform: translate(-50%, -50%);
  color: rgba(223, 250, 255, 0.78);
  text-align: center;
}

.sky-field.map-expanded .pitch-ladder {
  display: none;
}

.pitch-ladder span,
.pitch-ladder strong {
  width: 170px;
  border-top: 1px solid rgba(114, 242, 255, 0.65);
}

.speed-tape,
.altitude-tape {
  position: absolute;
  top: 50%;
  width: 96px;
  height: 190px;
  display: grid;
  place-items: center;
  transform: translateY(-50%);
}

.speed-tape {
  left: 34px;
}

.altitude-tape {
  right: 34px;
}

.speed-tape strong,
.altitude-tape strong {
  color: #fff;
  font-family: Consolas, monospace;
  font-size: 34px;
}

.speed-tape span,
.speed-tape small,
.altitude-tape span,
.altitude-tape small {
  color: #62e6ff;
}

.heading-scale {
  position: absolute;
  left: 50%;
  bottom: 28px;
  width: min(520px, 72%);
  height: 42px;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  align-items: center;
  transform: translateX(-50%);
  color: #dffaff;
  text-align: center;
}

.heading-scale strong {
  color: #fff;
  font-size: 20px;
}

.mode-switch {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.mode-switch button {
  height: 34px;
}

.takeover-panel {
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid rgba(76, 221, 255, 0.18);
  background: rgba(0, 22, 43, 0.46);
}

.takeover-panel.active {
  border-color: rgba(255, 207, 90, 0.55);
  background: rgba(44, 34, 10, 0.48);
  box-shadow: inset 0 0 22px rgba(255, 207, 90, 0.1), 0 0 18px rgba(255, 207, 90, 0.12);
}

.takeover-button {
  width: 100%;
  min-height: 62px;
  display: grid;
  align-content: center;
  gap: 4px;
  border-color: rgba(114, 242, 255, 0.58);
  background: linear-gradient(180deg, rgba(0, 174, 255, 0.56), rgba(0, 78, 138, 0.58));
  color: #fff;
  box-shadow: 0 0 18px rgba(0, 216, 255, 0.2);
}

.takeover-button.active {
  border-color: rgba(255, 207, 90, 0.86);
  background: linear-gradient(180deg, rgba(255, 184, 76, 0.82), rgba(156, 87, 22, 0.72));
  box-shadow: 0 0 18px rgba(255, 184, 76, 0.26);
}

.takeover-button strong {
  font-size: 18px;
}

.takeover-button span,
.takeover-panel p {
  margin: 0;
  color: rgba(223, 250, 255, 0.72);
  font-size: 12px;
}

.takeover-panel p {
  margin-top: 10px;
  line-height: 1.5;
}

.control-mode-panel {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid rgba(76, 221, 255, 0.16);
  background: rgba(0, 22, 43, 0.42);
}

.control-mode-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.control-mode-switch button {
  min-height: 58px;
  display: grid;
  align-content: center;
  gap: 4px;
  border: 1px solid rgba(76, 221, 255, 0.18);
  background: rgba(3, 26, 48, 0.55);
  color: rgba(223, 250, 255, 0.72);
}

.control-mode-switch button.active {
  border-color: rgba(114, 242, 255, 0.76);
  background: linear-gradient(180deg, rgba(0, 174, 255, 0.48), rgba(0, 66, 122, 0.64));
  box-shadow: inset 0 0 18px rgba(98, 230, 255, 0.16), 0 0 16px rgba(98, 230, 255, 0.16);
}

.control-mode-switch strong {
  color: #fff;
  font-family: Consolas, monospace;
  font-size: 18px;
}

.control-mode-switch span,
.control-mode-panel p {
  margin: 0;
  color: rgba(223, 250, 255, 0.68);
  font-size: 12px;
}

.control-mode-panel p {
  margin-top: 10px;
  line-height: 1.5;
}

.mode-switch button.active,
.command-grid .primary {
  border-color: rgba(94, 236, 255, 0.7);
  background: linear-gradient(180deg, rgba(0, 174, 255, 0.78), rgba(0, 98, 176, 0.66));
  box-shadow: 0 0 18px rgba(0, 216, 255, 0.22);
}

.command-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.command-grid button {
  min-height: 72px;
  display: grid;
  place-items: center;
  gap: 6px;
}

.command-grid.locked button {
  cursor: not-allowed;
  opacity: 0.46;
  filter: grayscale(0.35);
}

.keyboard-console.locked {
  border-color: rgba(120, 166, 190, 0.16);
  background: rgba(0, 14, 28, 0.5);
}

.keyboard-console.locked .key-row kbd {
  opacity: 0.46;
}

.command-grid .anticon {
  font-size: 22px;
}

.command-grid .danger {
  border-color: rgba(255, 94, 109, 0.7);
  background: rgba(165, 31, 46, 0.62);
}

.gimbal-sight-preview {
  margin-top: 16px;
  padding: 14px;
  border: 1px solid rgba(76, 221, 255, 0.16);
  background: rgba(0, 22, 43, 0.42);
}

.gimbal-sight-hud {
  position: absolute;
  left: 18px;
  bottom: 18px;
  z-index: 7;
  width: 158px;
  padding: 0;
  margin-top: 0;
  border-color: transparent;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
}

.gimbal-sight-title {
  margin-bottom: 8px;
  color: rgba(223, 250, 255, 0.52);
  font-size: 12px;
  letter-spacing: 0;
}

.gimbal-sight-base {
  position: relative;
  width: 112px;
  height: 112px;
  margin: 0 auto;
  border: 1px solid rgba(98, 230, 255, 0.36);
  border-radius: 50%;
  background:
    radial-gradient(circle at 50% 50%, rgba(98, 230, 255, 0.18), transparent 18%),
    linear-gradient(transparent 49%, rgba(98, 230, 255, 0.32) 50%, transparent 51%),
    linear-gradient(90deg, transparent 49%, rgba(98, 230, 255, 0.32) 50%, transparent 51%);
}

.gimbal-sight-dot {
  position: absolute;
  top: 46px;
  left: 46px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #62e6ff;
  box-shadow: 0 0 20px #62e6ff;
  transition: transform 0.08s linear;
}

.gimbal-sight-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
  color: rgba(223, 250, 255, 0.58);
  font-size: 11px;
}

.gimbal-sight-hud .gimbal-sight-title,
.gimbal-sight-hud .gimbal-sight-labels {
  color: rgba(223, 250, 255, 0.62);
}

.gimbal-sight-hud .gimbal-sight-base {
  width: 112px;
  height: 112px;
}

.gimbal-sight-hud .gimbal-sight-dot {
  top: 46px;
  left: 46px;
  box-shadow: 0 0 22px #62e6ff, 0 0 36px rgba(98, 230, 255, 0.36);
}

.keyboard-console {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid rgba(76, 221, 255, 0.16);
  background: rgba(0, 22, 43, 0.42);
}

.gimbal-console {
  margin-top: 10px;
  padding: 12px;
  border: 1px solid rgba(76, 221, 255, 0.16);
  background: rgba(0, 22, 43, 0.42);
}

.camera-stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin-top: 0;
}

.camera-stats div {
  min-height: 48px;
  display: grid;
  align-content: center;
  gap: 4px;
  padding: 8px;
  border: 1px solid rgba(76, 221, 255, 0.16);
  background: rgba(7, 33, 60, 0.5);
}

.camera-stats span {
  color: rgba(223, 250, 255, 0.62);
  font-size: 12px;
}

.camera-stats strong {
  color: #fff;
  font-family: Consolas, monospace;
}

.keyboard-title {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.keyboard-title strong {
  color: #fff;
}

.keyboard-title span {
  color: #62e6ff;
  font-size: 12px;
}

.key-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 6px;
  margin-top: 6px;
}

.key-row.wasd {
  grid-template-columns: repeat(4, 1fr);
}

.key-row.wide {
  grid-template-columns: 1.5fr 1fr;
}

.remote-map-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.remote-map-list section {
  display: grid;
  gap: 6px;
  padding-top: 8px;
  border-top: 1px solid rgba(98, 230, 255, 0.14);
}

.remote-map-list section > strong {
  color: #fff;
  font-size: 13px;
}

.remote-map-list p {
  display: grid;
  grid-template-columns: 52px 1fr;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: rgba(223, 250, 255, 0.7);
  font-size: 12px;
}

.remote-map-list kbd {
  min-height: 26px;
}

kbd {
  min-height: 34px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(98, 230, 255, 0.28);
  border-radius: 4px;
  color: rgba(223, 250, 255, 0.76);
  background: rgba(15, 70, 112, 0.42);
  font-family: Consolas, monospace;
  font-size: 13px;
}

kbd.active {
  color: #061220;
  background: #72f2ff;
  box-shadow: 0 0 18px rgba(114, 242, 255, 0.72);
}

.bottom-console {
  left: 18px;
  right: 18px;
  bottom: 14px;
  height: 92px;
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 16px;
  padding: 12px 16px;
}

.mission-summary {
  display: grid;
  align-content: center;
  gap: 4px;
}

.mission-summary strong {
  color: #fff;
}

.mission-summary small {
  color: rgba(223, 250, 255, 0.7);
}

.command-log {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.command-log article {
  min-width: 0;
  padding: 10px;
  border: 1px solid rgba(76, 221, 255, 0.16);
  background: rgba(0, 22, 43, 0.42);
}

.command-log span {
  color: #62e6ff;
  font-family: Consolas, monospace;
  font-size: 12px;
}

.command-log strong {
  display: block;
  margin-top: 6px;
  overflow: hidden;
  color: #fff;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1280px) {
  .cockpit-shell {
    padding-right: 320px;
    padding-left: 320px;
  }

  .left-console,
  .right-console {
    width: 286px;
  }
}

@media (max-width: 980px) {
  .command-flight {
    overflow: auto;
  }

  .cockpit-shell {
    min-height: 0;
    display: grid;
    gap: 12px;
    padding: 14px;
  }

  .cockpit-topbar,
  .left-console,
  .right-console,
  .bottom-console {
    position: static;
    width: auto;
  }

  .cockpit-topbar {
    order: 1;
    height: auto;
    grid-template-columns: 1fr;
    gap: 10px;
    padding: 12px;
  }

  .flight-title {
    text-align: left;
  }

  .left-console,
  .right-console {
    padding: 14px;
  }

  .left-console {
    order: 3;
  }

  .right-console {
    order: 4;
  }

  .hud-stage {
    order: 2;
    height: 430px;
    min-height: 430px;
  }

  .speed-tape,
  .altitude-tape {
    width: 72px;
    height: 150px;
  }

  .speed-tape {
    left: 12px;
  }

  .altitude-tape {
    right: 12px;
  }

  .bottom-console {
    order: 5;
    height: auto;
    grid-template-columns: 1fr;
  }

  .command-log {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 520px) {
  .attitude-orb {
    left: 8px;
    width: 116px;
    padding: 8px;
  }

  .gimbal-sight-hud {
    left: 8px;
    width: 116px;
    padding: 8px;
  }

  .map-view.is-mini,
  .fpv-view.is-mini {
    right: 8px;
    width: 124px;
    padding: 8px;
  }

  .fpv-view.is-mini {
    height: 118px;
  }

  .orb-body {
    width: 76px;
    height: 76px;
    margin: 6px auto;
  }

  .orb-meta {
    display: flex;
    font-size: 10px;
  }

  .gimbal-sight-base,
  .gimbal-sight-hud .gimbal-sight-base {
    width: 76px;
    height: 76px;
  }

  .gimbal-sight-dot,
  .gimbal-sight-hud .gimbal-sight-dot {
    top: 28px;
    left: 28px;
  }

  .gimbal-sight-title,
  .gimbal-sight-labels {
    font-size: 10px;
  }

  .overview-body {
    height: 84px;
  }

  .sim-readout {
    top: 112px;
  }

  .telemetry-grid,
  .command-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .telemetry-grid div,
  .command-grid button {
    min-height: 92px;
  }

  .heading-scale {
    width: calc(100% - 24px);
  }
}
</style>
