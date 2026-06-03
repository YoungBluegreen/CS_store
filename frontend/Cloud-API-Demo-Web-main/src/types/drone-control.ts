import { ControlSource } from './device'
import { ECommanderModeLostAction, ERthMode, LostControlActionInCommandFLight, WaylineLostControlActionInCommandFlight } from '/@/api/drone-control/drone'

export enum ControlSourceChangeType {
  Flight = 1,
  Payload = 2,
}

// 控制权变化消息
export interface ControlSourceChangeInfo {
  sn: string,
  type: ControlSourceChangeType,
  control_source: ControlSource
}

// 飞向目标点结果
export interface FlyToPointMessage {
  sn: string,
  result: number,
  message: string,
}

// 一键起飞结果
export interface TakeoffToPointMessage {
  sn: string,
  result: number,
  message: string,
}

// 设备端退出drc模式
export interface DrcModeExitNotifyMessage {
  sn: string,
  result: number,
  message: string,
}

// 飞行控制模式状态
export interface DrcStatusNotifyMessage {
  sn: string,
  result: number,
  message: string,
}

export const WaylineLostControlActionInCommandFlightOptions = [
  { label: '继续执行', value: WaylineLostControlActionInCommandFlight.CONTINUE },
  { label: '执行失控动作', value: WaylineLostControlActionInCommandFlight.EXEC_LOST_ACTION }
]

export const LostControlActionInCommandFLightOptions = [
  { label: '返航', value: LostControlActionInCommandFLight.RETURN_HOME },
  { label: '悬停', value: LostControlActionInCommandFLight.HOVER },
  { label: '降落', value: LostControlActionInCommandFLight.Land }
]

export const RthModeInCommandFlightOptions = [
  { label: '智能高度', value: ERthMode.SMART },
  { label: '设定高度', value: ERthMode.SETTING }
]

export const CommanderModeLostActionInCommandFlightOptions = [
  { label: '继续执行', value: ECommanderModeLostAction.CONTINUE },
  { label: '执行失控动作', value: ECommanderModeLostAction.EXEC_LOST_ACTION }
]

export const CommanderFlightModeInCommandFlightOptions = [
  { label: '智能高度', value: ERthMode.SMART },
  { label: '设定高度', value: ERthMode.SETTING }
]

// 云台重置模式
export enum GimbalResetMode {
  Recenter = 0,
  Down = 1,
  RecenterGimbalPan = 2,
  PitchDown = 3,
}

export const GimbalResetModeOptions = [
  { label: '云台回中', value: GimbalResetMode.Recenter },
  { label: '云台朝下', value: GimbalResetMode.Down },
  { label: '偏航回中', value: GimbalResetMode.RecenterGimbalPan },
  { label: '俯仰朝下', value: GimbalResetMode.PitchDown }
]
