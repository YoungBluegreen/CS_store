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
          <span class="state-dot"></span>
          <strong>控制权已接入</strong>
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
        <div class="sky-field">
          <div class="horizon" :style="{ transform: `rotate(${roll}deg) translateY(${pitch}px)` }">
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
          <div class="flight-vector">
            <span></span>
          </div>
          <div class="sim-readout glass-panel">
            <strong>键盘飞行模拟</strong>
            <span>W/S 加减速 · A/D 转向 · ↑↓ 升降 · ←→ 平移 · Space 悬停 · R 返航</span>
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
        <div class="command-grid">
          <button
            v-for="command in commands"
            :key="command.label"
            :class="command.tone"
            @click="issueCommand(command.label)"
          >
            <component :is="command.icon" />
            <span>{{ command.label }}</span>
          </button>
        </div>
        <div class="stick-preview">
          <div class="stick-base">
            <div class="stick-dot" :style="stickStyle"></div>
          </div>
          <div class="stick-labels">
            <span>偏航 {{ Math.round(flightState.yaw * 15) }}°</span>
            <span>油门 {{ Math.round(flightState.throttle * 100) }}%</span>
          </div>
        </div>
        <div class="keyboard-console">
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

const router = useRouter()
const activeMode = ref('指令')
const currentTime = ref('')
const clockTimer = ref<number | null>(null)
const simTimer = ref<number | null>(null)
const roll = ref(-8)
const pitch = ref(10)
const logSeed = ref(4)
const activeKeyLabel = ref('等待键盘输入')
const pressedKeys = reactive<Record<string, boolean>>({})
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
const commandLog = ref([
  { id: 1, time: '19:08:12', text: '控制权切换至云端座舱' },
  { id: 2, time: '19:08:18', text: '加载东区 03 号巡检点' },
  { id: 3, time: '19:08:26', text: '避障、返航、链路状态检查完成' },
])

const telemetry = computed(() => [
  { label: '高度', value: `${Math.round(flightState.altitude)}`, unit: 'm' },
  { label: '速度', value: flightState.speed.toFixed(1), unit: 'm/s' },
  { label: '航向', value: `${Math.round(flightState.heading)}`, unit: 'deg' },
  { label: '电量', value: `${Math.round(flightState.battery)}`, unit: '%' },
  { label: '风速', value: flightState.wind.toFixed(1), unit: 'm/s' },
  { label: 'RTK', value: '固定', unit: '' },
])

const headingLabel = computed(() => `${Math.round(flightState.heading).toString().padStart(3, '0')}°`)

const simWorldStyle = computed(() => ({
  transform: `translate(${(-flightState.x * 0.35).toFixed(1)}px, ${(flightState.y * 0.22).toFixed(1)}px) rotate(${(flightState.yaw * -2).toFixed(1)}deg)`,
}))

const droneStyle = computed(() => ({
  transform: `translate(${flightState.x.toFixed(1)}px, ${flightState.y.toFixed(1)}px) rotate(${roll.value.toFixed(1)}deg)`,
}))

const stickStyle = computed(() => ({
  transform: `translate(${(flightState.yaw * 52).toFixed(1)}px, ${(-flightState.throttle * 52).toFixed(1)}px)`,
}))

const links = [
  { label: '图传链路', value: '18 Mbps', text: '良好', level: 'good' },
  { label: '控制链路', value: '42 ms', text: '稳定', level: 'good' },
  { label: '机场链路', value: '在线', text: '可用', level: 'good' },
]

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

function isPressed (code: string) {
  return Boolean(pressedKeys[code])
}

function isControlKey (code: string) {
  return [
    'KeyW',
    'KeyA',
    'KeyS',
    'KeyD',
    'ArrowUp',
    'ArrowDown',
    'ArrowLeft',
    'ArrowRight',
    'Space',
    'KeyR',
  ].includes(code)
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

function keyActionText (code: string) {
  const actions: Record<string, string> = {
    KeyW: 'W：加速前进',
    KeyS: 'S：减速后退',
    KeyA: 'A：左转偏航',
    KeyD: 'D：右转偏航',
    ArrowUp: '↑：升高',
    ArrowDown: '↓：下降',
    ArrowLeft: '←：左平移',
    ArrowRight: '→：右平移',
  }
  return actions[code] || code
}

function handleKeyDown (event: KeyboardEvent) {
  if (!isControlKey(event.code)) return
  event.preventDefault()
  const wasPressed = pressedKeys[event.code]
  pressedKeys[event.code] = true

  if (event.code === 'Space') {
    activeKeyLabel.value = 'Space：悬停刹停'
    if (!wasPressed) logKeyboardAction('键盘 Space：进入悬停刹停')
  } else if (event.code === 'KeyR') {
    activeKeyLabel.value = 'R：返航复位'
    if (!wasPressed) {
      issueCommand('返航')
      flightState.x = 0
      flightState.y = 0
      flightState.heading = 327
    }
  } else {
    activeKeyLabel.value = keyActionText(event.code)
    if (!wasPressed) {
      logKeyboardAction(`键盘 ${keyActionText(event.code)}`)
    }
  }
}

function handleKeyUp (event: KeyboardEvent) {
  if (!isControlKey(event.code)) return
  event.preventDefault()
  pressedKeys[event.code] = false
  activeKeyLabel.value = '等待键盘输入'
}

function updateSimulation () {
  const throttle = (isPressed('KeyW') ? 1 : 0) - (isPressed('KeyS') ? 1 : 0)
  const yaw = (isPressed('KeyD') ? 1 : 0) - (isPressed('KeyA') ? 1 : 0)
  const strafe = (isPressed('ArrowRight') ? 1 : 0) - (isPressed('ArrowLeft') ? 1 : 0)
  const lift = (isPressed('ArrowUp') ? 1 : 0) - (isPressed('ArrowDown') ? 1 : 0)
  const hover = isPressed('Space')

  flightState.throttle = throttle
  flightState.yaw = yaw
  flightState.speed = clamp(flightState.speed + throttle * 0.22 - (hover ? 0.42 : 0), 0, 18)
  flightState.altitude = clamp(flightState.altitude + lift * 0.85, 20, 300)
  flightState.heading = normalizeHeading(flightState.heading + yaw * 1.9)
  flightState.battery = clamp(flightState.battery - Math.max(flightState.speed, 1) * 0.0009, 0, 100)

  const headingRad = flightState.heading * Math.PI / 180
  flightState.x = clamp(flightState.x + Math.sin(headingRad) * flightState.speed * 0.06 + strafe * 1.2, -95, 95)
  flightState.y = clamp(flightState.y - Math.cos(headingRad) * flightState.speed * 0.06 - lift * 0.25, -72, 72)
  roll.value = yaw * -16 + strafe * 5
  pitch.value = clamp(throttle * -15 + lift * -4, -18, 18)
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

.horizon {
  position: absolute;
  inset: -25%;
  transition: transform 0.45s ease;
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

.sim-world {
  position: absolute;
  inset: 0;
  transition: transform 0.08s linear;
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
.drone-avatar span {
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

.command-grid .anticon {
  font-size: 22px;
}

.command-grid .danger {
  border-color: rgba(255, 94, 109, 0.7);
  background: rgba(165, 31, 46, 0.62);
}

.stick-preview {
  margin-top: 16px;
  padding: 14px;
  border: 1px solid rgba(76, 221, 255, 0.16);
  background: rgba(0, 22, 43, 0.42);
}

.stick-base {
  position: relative;
  width: 150px;
  height: 150px;
  margin: 0 auto;
  border: 1px solid rgba(98, 230, 255, 0.36);
  border-radius: 50%;
  background:
    linear-gradient(transparent 49%, rgba(98, 230, 255, 0.32) 50%, transparent 51%),
    linear-gradient(90deg, transparent 49%, rgba(98, 230, 255, 0.32) 50%, transparent 51%);
}

.stick-dot {
  position: absolute;
  top: 65px;
  left: 65px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #62e6ff;
  box-shadow: 0 0 20px #62e6ff;
  transition: transform 0.08s linear;
}

.stick-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  color: rgba(223, 250, 255, 0.72);
  font-size: 12px;
}

.keyboard-console {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid rgba(76, 221, 255, 0.16);
  background: rgba(0, 22, 43, 0.42);
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
