export interface CommandAirport {
  id: string
  name: string
  dockModel: string
  droneName: string
  droneModel: string
  location: string
  status: 'online' | 'flying' | 'idle' | 'offline'
  statusText: string
  missionName: string
  targetName: string
  routeCode: string
  battery: number
  altitude: number
  heading: number
  wind: number
  position: [number, number]
  description: string
  serialNumber: string
  network: string
}

export interface CommandOperator {
  id: string
  name: string
  role: string
  status: 'online' | 'busy' | 'standby'
  airportId: string
  color: string
}

export const commandAirports: CommandAirport[] = [
  {
    id: 'dock-a',
    name: '东区机库 A',
    dockModel: 'DJI Dock 2',
    droneName: '巡检无人机 01',
    droneModel: 'M3TD',
    location: '合肥高新区',
    status: 'online',
    statusText: '在线',
    missionName: '东区矿山自动巡检',
    targetName: '东区 03 号巡检点',
    routeCode: 'R-01',
    battery: 96,
    altitude: 120,
    heading: 327,
    wind: 3.1,
    position: [117.244, 31.858],
    description: '用于东区日常巡检任务，当前空闲，可进入指令飞行座舱。',
    serialNumber: '7CTDM3D001',
    network: '4G / 良好',
  },
  {
    id: 'dock-b',
    name: '西区机库 B',
    dockModel: 'DJI Dock 2',
    droneName: '倾斜采集无人机 02',
    droneModel: 'M3E',
    location: '西区建模区',
    status: 'flying',
    statusText: '飞行中',
    missionName: '西区倾斜摄影建模',
    targetName: '西区 B-07 建模面',
    routeCode: 'R-02',
    battery: 82,
    altitude: 150,
    heading: 286,
    wind: 4.4,
    position: [117.326, 31.892],
    description: '负责西区倾斜摄影采集，支持远程指令飞行和云台监看。',
    serialNumber: '7CTDM3D002',
    network: '5G / 良好',
  },
  {
    id: 'dock-c',
    name: '河道机库 C',
    dockModel: 'DJI Dock 2',
    droneName: '应急复核无人机 03',
    droneModel: 'M30T',
    location: '河道复核区',
    status: 'online',
    statusText: '在线',
    missionName: '河道应急复核',
    targetName: '河道 C-12 异常点',
    routeCode: 'R-03',
    battery: 91,
    altitude: 110,
    heading: 42,
    wind: 2.5,
    position: [117.265, 31.876],
    description: '面向河道应急复核和热成像巡查，当前可被在线席位接管。',
    serialNumber: '7CTDM3D003',
    network: '专网 / 稳定',
  },
]

export const commandOperators: CommandOperator[] = [
  { id: 'op-zhang', name: '张工', role: '主操控席', status: 'online', airportId: 'dock-a', color: '#5dff90' },
  { id: 'op-li', name: '李工', role: '云台观察席', status: 'busy', airportId: 'dock-b', color: '#62e6ff' },
  { id: 'op-wang', name: '王工', role: '应急备勤席', status: 'standby', airportId: 'dock-c', color: '#ffcf5a' },
]

export function getDefaultAirport () {
  return commandAirports[0]
}

export function getAirportById (id?: string | string[] | null) {
  const airportId = Array.isArray(id) ? id[0] : id
  return commandAirports.find(airport => airport.id === airportId)
}

export function getOperatorById (id?: string | string[] | null) {
  const operatorId = Array.isArray(id) ? id[0] : id
  return commandOperators.find(operator => operator.id === operatorId)
}

export function getOperatorForAirport (airportId: string) {
  return commandOperators.find(operator => operator.airportId === airportId)
}
