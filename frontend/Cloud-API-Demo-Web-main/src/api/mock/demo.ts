import { AxiosRequestConfig, AxiosResponse } from 'axios'
import { EDeviceTypeName, ELocalStorageKey } from '/@/types'
import { DEVICE_MODEL_KEY, DeviceFirmwareStatusEnum } from '/@/types/device'
import { OutOfControlAction, TaskStatus, TaskType } from '/@/types/task'
import { WaylineType } from '/@/types/wayline'

export const DEMO_MODE = true

const now = Date.now()
const workspaceId = 'demo-workspace'
const workspaceName = '低空作业演示工作区'

const pagination = (page = 1, pageSize = 50, total = 0) => ({ page, page_size: pageSize, total })
const ok = <T>(data: T, message = 'success') => ({ code: 0, message, data })
const list = <T>(items: T[], page = 1, pageSize = 50) => ok({ list: items, pagination: pagination(page, pageSize, items.length) })

const demoDrone = {
  device_name: 'M3TD',
  device_sn: '1581F6Q8D2345001',
  nickname: '巡检无人机-01',
  firmware_version: '10.01.32.05',
  firmware_status: DeviceFirmwareStatusEnum.None,
  status: true,
  workspace_name: workspaceName,
  bound_time: '2026-06-01 09:12:00',
  login_time: '2026-06-03 17:21:00',
  domain: EDeviceTypeName.Aircraft,
  type: 91,
  device_model: { device_model_key: DEVICE_MODEL_KEY.M3TD },
}

const demoDrone2 = {
  device_name: 'M30T',
  device_sn: '1581F6Q8D2345002',
  nickname: '应急热成像无人机',
  firmware_version: '08.00.01.21',
  firmware_status: DeviceFirmwareStatusEnum.ToUpgraded,
  status: true,
  workspace_name: workspaceName,
  bound_time: '2026-05-28 14:30:00',
  login_time: '2026-06-03 17:18:00',
  domain: EDeviceTypeName.Aircraft,
  type: 67,
  device_model: { device_model_key: DEVICE_MODEL_KEY.M30T },
}

const demoDock = {
  device_name: 'Dock2',
  device_sn: '7CTDM3D001',
  nickname: '东区机库 A',
  firmware_version: '09.02.04.12',
  firmware_status: DeviceFirmwareStatusEnum.ConsistencyUpgrade,
  status: true,
  workspace_name: workspaceName,
  bound_time: '2026-05-20 10:00:00',
  login_time: '2026-06-03 17:22:00',
  domain: EDeviceTypeName.Dock,
  type: 2,
  device_model: { device_model_key: DEVICE_MODEL_KEY.Dock2 },
  children: demoDrone,
}

const demoDock2 = {
  device_name: 'Dock',
  device_sn: '1ZNDK32002',
  nickname: '西区机库 B',
  firmware_version: '07.01.10.08',
  firmware_status: DeviceFirmwareStatusEnum.None,
  status: false,
  workspace_name: workspaceName,
  bound_time: '2026-05-18 15:42:00',
  login_time: '2026-06-02 19:05:00',
  domain: EDeviceTypeName.Dock,
  type: 1,
  device_model: { device_model_key: DEVICE_MODEL_KEY.Dock },
  children: demoDrone2,
}

const waylines = [
  {
    id: 'wl-area-001',
    name: '矿区正射巡检航线.kmz',
    drone_model_key: DEVICE_MODEL_KEY.M3TD,
    payload_model_keys: [DEVICE_MODEL_KEY.M3TDCamera],
    template_types: [WaylineType.NormalWaypointWayline],
    update_time: now - 1000 * 60 * 18,
    user_name: 'adminPC',
  },
  {
    id: 'wl-oblique-002',
    name: '倾斜摄影面状航线.kmz',
    drone_model_key: DEVICE_MODEL_KEY.M30T,
    payload_model_keys: [DEVICE_MODEL_KEY.M30TCamera],
    template_types: [WaylineType.AccurateReshootingWayline],
    update_time: now - 1000 * 60 * 60 * 5,
    user_name: 'planner',
  },
]

let tasks = [
  {
    job_id: 'job-001',
    job_name: '东区矿山自动巡检',
    task_type: TaskType.Immediate,
    file_id: 'wl-area-001',
    file_name: '矿区正射巡检航线.kmz',
    wayline_type: WaylineType.NormalWaypointWayline,
    dock_sn: demoDock.device_sn,
    dock_name: demoDock.nickname,
    workspace_id: workspaceId,
    username: 'adminPC',
    begin_time: '2026-06-03 17:00:00',
    end_time: '2026-06-03 17:25:00',
    execute_time: '2026-06-03 17:03:00',
    completed_time: '',
    status: TaskStatus.Carrying,
    progress: 68,
    code: 0,
    rth_altitude: 120,
    out_of_control_action: OutOfControlAction.ReturnToHome,
    media_count: 42,
    uploading: true,
    uploaded_count: 26,
  },
  {
    job_id: 'job-002',
    job_name: '西区倾斜摄影建模',
    task_type: TaskType.Timed,
    file_id: 'wl-oblique-002',
    file_name: '倾斜摄影面状航线.kmz',
    wayline_type: WaylineType.AccurateReshootingWayline,
    dock_sn: demoDock2.device_sn,
    dock_name: demoDock2.nickname,
    workspace_id: workspaceId,
    username: 'planner',
    begin_time: '2026-06-04 09:00:00',
    end_time: '2026-06-04 09:45:00',
    execute_time: '',
    completed_time: '',
    status: TaskStatus.Wait,
    progress: 0,
    code: 0,
    rth_altitude: 130,
    out_of_control_action: OutOfControlAction.Hover,
    media_count: 0,
    uploading: false,
    uploaded_count: 0,
  },
  {
    job_id: 'job-003',
    job_name: '河道应急复核',
    task_type: TaskType.Immediate,
    file_id: 'wl-area-001',
    file_name: '矿区正射巡检航线.kmz',
    wayline_type: WaylineType.NormalWaypointWayline,
    dock_sn: demoDock.device_sn,
    dock_name: demoDock.nickname,
    workspace_id: workspaceId,
    username: 'operator',
    begin_time: '2026-06-03 15:10:00',
    end_time: '2026-06-03 15:31:00',
    execute_time: '2026-06-03 15:11:00',
    completed_time: '2026-06-03 15:29:00',
    status: TaskStatus.Success,
    progress: 100,
    code: 0,
    rth_altitude: 110,
    out_of_control_action: OutOfControlAction.Land,
    media_count: 36,
    uploading: false,
    uploaded_count: 36,
  },
]

const mediaFiles = [
  { fingerprint: 'media-001', drone: demoDrone.nickname, payload: 'M3TD Camera', is_original: '是', file_name: '矿区巡检_001.jpg', file_path: '/demo/media/2026-06-03/矿区巡检_001.jpg', create_time: '2026-06-03 17:08:21', file_id: 'media-001' },
  { fingerprint: 'media-002', drone: demoDrone.nickname, payload: 'M3TD Camera', is_original: '否', file_name: 'AI识别标注_裂隙区域.png', file_path: '/demo/media/2026-06-03/AI识别标注_裂隙区域.png', create_time: '2026-06-03 17:12:44', file_id: 'media-002' },
  { fingerprint: 'media-003', drone: demoDrone2.nickname, payload: 'M30T Camera', is_original: '是', file_name: '热成像巡查_河道.mp4', file_path: '/demo/media/2026-06-03/热成像巡查_河道.mp4', create_time: '2026-06-03 15:28:13', file_id: 'media-003' },
]

const users = [
  { user_id: 'u-admin', username: 'adminPC', workspace_id: workspaceId, user_type: 1, mqtt_username: 'adminPC', mqtt_password: 'demo', create_time: '2026-05-20 10:00:00' },
  { user_id: 'u-planner', username: 'planner', workspace_id: workspaceId, user_type: 1, mqtt_username: 'planner', mqtt_password: 'demo', create_time: '2026-05-22 09:00:00' },
  { user_id: 'u-operator', username: 'operator', workspace_id: workspaceId, user_type: 1, mqtt_username: 'operator', mqtt_password: 'demo', create_time: '2026-05-24 14:00:00' },
]

const flightAreas = [
  {
    area_id: 'fa-001',
    name: '禁飞区-变电站',
    type: 1,
    status: true,
    username: 'adminPC',
    create_time: now - 86400000,
    update_time: now,
    content: {
      properties: { color: '#e23c39', clampToGround: true },
      geometry: { type: 'Polygon', coordinates: [[[113.9432, 22.5441], [113.9492, 22.5441], [113.9492, 22.5484], [113.9432, 22.5484], [113.9432, 22.5441]]] },
    },
  },
  {
    area_id: 'fa-002',
    name: '限飞区-学校',
    type: 0,
    status: true,
    username: 'planner',
    create_time: now - 43200000,
    update_time: now,
    content: {
      properties: { color: '#ffbb00', clampToGround: true },
      geometry: { type: 'Circle', coordinates: [113.9368, 22.552], radius: 450 },
    },
  },
]

function getUrl (config: AxiosRequestConfig): string {
  return `${config.url || ''}`
}

function getPage (config: AxiosRequestConfig) {
  const url = getUrl(config)
  return {
    page: Number(url.match(/[?&]page=(\d+)/)?.[1] || 1),
    pageSize: Number(url.match(/[?&]page_size=(\d+)/)?.[1] || 50),
  }
}

function deviceListForUrl (url: string) {
  if (url.includes('domain=3')) return [demoDock, demoDock2]
  if (url.includes('domain=0')) return [demoDrone, demoDrone2]
  return [demoDock, demoDock2, demoDrone, demoDrone2]
}

function responseData (config: AxiosRequestConfig): any {
  const url = getUrl(config)
  const method = (config.method || 'get').toLowerCase()
  const { page, pageSize } = getPage(config)

  if (url.endsWith('/login')) return ok({ access_token: 'demo-token', username: 'adminPC', user_id: 'u-admin', workspace_id: workspaceId })
  if (url.includes('/token/refresh')) return ok({ access_token: 'demo-token' })
  if (url.includes('/workspaces/current')) return ok({ id: workspaceId, workspace_id: workspaceId, name: workspaceName, platform_name: '无人机低空作业指挥平台', desc: '前端演示模式' })
  if (url.includes('/users/current')) return ok(users[0])
  if (url.includes('/users/') && url.includes('/users?')) return list(users, page, pageSize)
  if (url.includes('/devices/') && url.includes('/devices/bound')) return list(deviceListForUrl(url), page, pageSize)
  if (url.includes('/devices/') && url.includes('/devices/hms')) {
    return list([
      { hms_id: 'hms-001', sn: demoDock.device_sn, level: 1, message_zh: '机库风速接近阈值', message_en: 'Wind speed near threshold', create_time: '2026-06-03 16:51:00', update_time: '2026-06-03 16:51:00', domain: 3 },
      { hms_id: 'hms-002', sn: demoDrone.device_sn, level: 0, message_zh: 'RTK 固定良好', message_en: 'RTK fixed', create_time: '2026-06-03 17:02:00', update_time: '2026-06-03 17:02:00', domain: 0 },
    ], page, pageSize)
  }
  if (url.includes('/devices/') && url.includes('/logs-uploaded')) {
    return list([{
      logs_id: 'logs-001',
      happen_time: '2026-06-03 16:30:00',
      user_name: 'adminPC',
      logs_information: '演示日志上传记录',
      create_time: '2026-06-03 16:35:00',
      status: 3,
      device_topo: { hosts: [{ sn: demoDock.device_sn, device_model: { device_model_key: DEVICE_MODEL_KEY.Dock2 }, device_callsign: demoDock.nickname }], parents: [] },
      logs_progress: [{ device_sn: demoDock.device_sn, device_model_domain: '3', progress: 100, result: 0, upload_rate: 1024, status: 3 }],
      device_logs: { files: [] },
    }], page, pageSize)
  }
  if (url.includes('/devices/') && url.includes('/logs')) {
    return ok({ files: [{ device_sn: demoDock.device_sn, module: '3', result: 0, object_key: 'demo/logs/dock.log', file_id: 'log-file-001', list: [{ boot_index: 1, start_time: now - 3600000, end_time: now, size: 1024 * 512 }] }] })
  }
  if (url.includes('/devices/') && /\/devices\/[^/]+$/.test(url)) return ok(url.includes(demoDrone2.device_sn) ? demoDrone2 : demoDrone)
  if (url.includes('/live/capacity')) {
    return ok({
      available: true,
      device_list: [{
        sn: demoDock.device_sn,
        available_video_number: 2,
        coexist_video_number_max: 4,
        camera_list: [
          { camera_index: '81-0-0', camera_name: '广角相机', available_video_number: 1, video_list: [{ video_index: 'normal-0', video_type: 'normal', video_quality: 1 }] },
          { camera_index: '81-0-7', camera_name: '变焦相机', available_video_number: 1, video_list: [{ video_index: 'zoom-0', video_type: 'zoom', video_quality: 1 }] },
        ],
      }],
    })
  }
  if (url.includes('/live/streams')) return ok({ url: 'webrtc://demo/live/stream', stream_id: 'demo-live-stream' })
  if (url.includes('/firmware-release-notes/latest')) return ok([{ device_name: 'Dock2', product_version: '09.02.05.00', release_note: '演示固件：优化任务调度与日志上传。', released_time: '2026-06-01' }])
  if (url.includes('/firmwares')) {
    return list([
      { firmware_id: 'fw-001', device_name: 'Dock2', product_version: '09.02.05.00', firmware_status: true, file_name: 'Dock2_FW.bin', file_size: 248000000, create_time: '2026-06-01 10:00:00' },
      { firmware_id: 'fw-002', device_name: 'M3TD', product_version: '10.01.33.00', firmware_status: false, file_name: 'M3TD_FW.bin', file_size: 188000000, create_time: '2026-06-02 11:20:00' },
    ], page, pageSize)
  }
  if (url.includes('/waylines') && method === 'get') return list(waylines, page, pageSize)
  if (url.includes('/flight-tasks') && method === 'post') {
    const body = typeof config.data === 'string' ? JSON.parse(config.data || '{}') : (config.data || {})
    tasks = [{ ...tasks[1], ...body, job_id: `job-${Date.now()}`, job_name: body.name || '新建演示任务', file_name: waylines.find(item => item.id === body.file_id)?.name || '演示航线.kmz', dock_name: demoDock.nickname, username: 'adminPC', status: TaskStatus.Wait, progress: 0 }, ...tasks]
    return ok({})
  }
  if (url.includes('/jobs') && method === 'get') return list(tasks, page, pageSize)
  if (url.includes('/jobs') && method === 'delete') {
    const params = (config as any).params || {}
    tasks = tasks.filter(item => item.job_id !== params.job_id)
    return ok({})
  }
  if (url.includes('/jobs') && method === 'put') {
    const jobId = url.split('/').pop()
    tasks = tasks.map(item => item.job_id === jobId ? { ...item, status: item.status === TaskStatus.Paused ? TaskStatus.Carrying : TaskStatus.Paused } : item)
    return ok({})
  }
  if (url.includes('/media-highest')) return ok({})
  if (url.includes('/files/') && url.includes('/files')) return list(mediaFiles, page, pageSize)
  if (url.includes('/file/') && url.includes('/url')) return new Blob(['demo media file'], { type: 'text/plain' })
  if (url.includes('/element-groups')) {
    return list([
      { id: 'demo-layer-default', name: '默认图层', type: 1, is_distributed: true, is_check: true, is_select: false, elements: [] },
      { id: 'demo-layer-share', name: '共享标绘', type: 2, is_distributed: true, is_check: true, is_select: false, elements: [] },
    ], page, pageSize)
  }
  if (url.includes('/elements')) return ok({ id: `ele-${Date.now()}` })
  if (url.includes('/flight-areas') && method === 'get') return ok(flightAreas)
  if (url.includes('/flight-area/sync')) return ok({})
  if (url.includes('/flight-area')) return ok({ area_id: `fa-${Date.now()}` })
  if (url.includes('/device-status')) {
    return ok([
      { device_sn: demoDock.device_sn, nickname: demoDock.nickname, device_name: demoDock.device_name, online: true, flight_area_status: { sync_code: 0, sync_status: 2, sync_msg: '已同步' } },
      { device_sn: demoDock2.device_sn, nickname: demoDock2.nickname, device_name: demoDock2.device_name, online: false, flight_area_status: { sync_code: 0, sync_status: 0, sync_msg: '待同步' } },
    ])
  }
  if (url.includes('/drc/connect')) return ok({ address: 'broker.emqx.io:8084/mqtt', username: 'demo', password: 'demo', client_id: 'demo-client', expire_time: now + 600000, enable_tls: false })
  if (url.includes('/drc/enter')) return ok({ sub: ['demo/drc/sub'], pub: ['demo/drc/pub'] })
  if (url.includes('/control/api/v1')) return ok(null)
  if (url.includes('/binding') || url.includes('/unbinding') || method === 'put' || method === 'post' || method === 'delete') return ok({})
  return ok({})
}

export function ensureDemoSession () {
  if (!DEMO_MODE) return
  localStorage.setItem(ELocalStorageKey.Token, 'demo-token')
  localStorage.setItem(ELocalStorageKey.WorkspaceId, workspaceId)
  localStorage.setItem(ELocalStorageKey.Username, 'adminPC')
  localStorage.setItem(ELocalStorageKey.UserId, 'u-admin')
  localStorage.setItem(ELocalStorageKey.Flag, '1')
  localStorage.setItem(ELocalStorageKey.PlatformName, '无人机低空作业指挥平台')
  localStorage.setItem(ELocalStorageKey.WorkspaceName, workspaceName)
  localStorage.setItem(ELocalStorageKey.WorkspaceDesc, '前端演示模式')
}

export async function demoAdapter (config: AxiosRequestConfig): Promise<AxiosResponse> {
  return { data: responseData(config), status: 200, statusText: 'OK', headers: {}, config, request: {} }
}
