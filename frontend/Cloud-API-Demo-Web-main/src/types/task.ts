import { commonColor } from '/@/utils/color'

export enum TaskType {
  Immediate = 0,
  Timed = 1,
  Condition = 2,
}

export const TaskTypeMap = {
  [TaskType.Immediate]: '立即执行',
  [TaskType.Timed]: '定时执行',
  [TaskType.Condition]: '条件执行',
}

export const TaskTypeOptions = [
  { value: TaskType.Immediate, label: TaskTypeMap[TaskType.Immediate] },
  { value: TaskType.Timed, label: TaskTypeMap[TaskType.Timed] },
  { value: TaskType.Condition, label: TaskTypeMap[TaskType.Condition] },
]

export enum OutOfControlAction {
  ReturnToHome = 0,
  Hover = 1,
  Land = 2,
}

export const OutOfControlActionMap = {
  [OutOfControlAction.ReturnToHome]: '返航',
  [OutOfControlAction.Hover]: '悬停',
  [OutOfControlAction.Land]: '降落',
}

export const OutOfControlActionOptions = [
  { value: OutOfControlAction.ReturnToHome, label: OutOfControlActionMap[OutOfControlAction.ReturnToHome] },
  { value: OutOfControlAction.Hover, label: OutOfControlActionMap[OutOfControlAction.Hover] },
  { value: OutOfControlAction.Land, label: OutOfControlActionMap[OutOfControlAction.Land] },
]

export enum TaskStatus {
  Wait = 1,
  Carrying = 2,
  Success = 3,
  CanCel = 4,
  Fail = 5,
  Paused = 6,
}

export const TaskStatusMap = {
  [TaskStatus.Wait]: '待执行',
  [TaskStatus.Carrying]: '执行中',
  [TaskStatus.Success]: '已完成',
  [TaskStatus.CanCel]: '已取消',
  [TaskStatus.Fail]: '执行失败',
  [TaskStatus.Paused]: '已暂停',
}

export const TaskStatusColor = {
  [TaskStatus.Wait]: commonColor.BLUE,
  [TaskStatus.Carrying]: commonColor.BLUE,
  [TaskStatus.Success]: commonColor.NORMAL,
  [TaskStatus.CanCel]: commonColor.FAIL,
  [TaskStatus.Fail]: commonColor.FAIL,
  [TaskStatus.Paused]: commonColor.BLUE,
}

export enum TaskProgressStatus {
  Sent = 'sent',
  inProgress = 'in_progress',
  Paused = 'paused',
  Rejected = 'rejected',
  Canceled = 'canceled',
  Timeout = 'timeout',
  Failed = 'failed',
  OK = 'ok',
}

export interface TaskProgressInfo {
  bid: string,
  output:{
    ext: {
      current_waypoint_index: number,
      media_count: number
    },
    progress:{
      current_step: number,
      percent: number
    },
    status: TaskProgressStatus
  },
  result: number,
}

export const TaskProgressWsStatusMap = {
  [TaskProgressStatus.Sent]: TaskStatus.Carrying,
  [TaskProgressStatus.inProgress]: TaskStatus.Carrying,
  [TaskProgressStatus.Rejected]: TaskStatus.Fail,
  [TaskProgressStatus.OK]: TaskStatus.Success,
  [TaskProgressStatus.Failed]: TaskStatus.Fail,
  [TaskProgressStatus.Canceled]: TaskStatus.CanCel,
  [TaskProgressStatus.Timeout]: TaskStatus.Fail,
  [TaskProgressStatus.Paused]: TaskStatus.Paused,
}

export enum MediaStatus {
  ToUpload = 1,
  Uploading = 2,
  Empty = 3,
  Success = 4,
}

export const MediaStatusMap = {
  [MediaStatus.ToUpload]: '待上传',
  [MediaStatus.Uploading]: '上传中',
  [MediaStatus.Success]: '已上传',
  [MediaStatus.Empty]: '无媒体文件',
}

export const MediaStatusColorMap = {
  [MediaStatus.ToUpload]: commonColor.BLUE,
  [MediaStatus.Uploading]: commonColor.BLUE,
  [MediaStatus.Success]: commonColor.NORMAL,
  [MediaStatus.Empty]: commonColor.WARN,
}

export interface MediaStatusProgressInfo {
  job_id: string,
  media_count: number
  uploaded_count: number,
}

export interface TaskMediaHighestPriorityProgressInfo {
  pre_job_id: string,
  job_id: string,
}
