<template>
  <main class="member-page">
    <section class="page-heading">
      <div>
        <span class="eyebrow">组织与权限</span>
        <h1>成员管理</h1>
        <p>统一维护平台账号、用户角色与 MQTT 接入凭证，保障无人机作业数据链路安全可控。</p>
      </div>
      <div class="heading-actions">
        <a-button @click="refreshCurrent">
          <ReloadOutlined />
          刷新
        </a-button>
      </div>
    </section>

    <section class="metric-grid">
      <div v-for="item in metrics" :key="item.label" class="metric-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.desc }}</small>
      </div>
    </section>

    <section class="table-panel">
      <div class="table-toolbar">
        <div>
          <h2>成员列表</h2>
          <p>共 {{ paginationProp.total }} 个账号，当前显示 {{ data.member.length }} 条记录</p>
        </div>
        <a-input-search
          v-model:value="keyword"
          class="search"
          placeholder="搜索账号、工作空间或 MQTT 用户名"
          allow-clear
        />
      </div>

      <a-table
        :columns="columns"
        :data-source="filteredMembers"
        :pagination="paginationProp"
        :loading="loading"
        row-key="user_id"
        :row-selection="rowSelection"
        :rowClassName="(record, index) => ((index % 2) === 0 ? 'table-striped' : null)"
        :scroll="{ x: '100%', y: 520 }"
        @change="refreshData"
      >
        <template #username="{ text, record }">
          <div class="account-cell">
            <span>{{ getAvatarText(text) }}</span>
            <div>
              <strong>{{ text }}</strong>
              <small>{{ record.user_id }}</small>
            </div>
          </div>
        </template>

        <template #user_type="{ text }">
          <a-tag :color="text === 'Web' ? 'blue' : 'green'">
            {{ formatUserType(text) }}
          </a-tag>
        </template>

        <template v-for="col in ['mqtt_username', 'mqtt_password']" #[col]="{ text, record }" :key="col">
          <a-input
            v-if="editableData[record.user_id]"
            v-model:value="editableData[record.user_id][col]"
            class="inline-input"
          />
          <code v-else class="credential">{{ text || '-' }}</code>
        </template>

        <template #action="{ record }">
          <a-space class="row-actions">
            <template v-if="editableData[record.user_id]">
              <a-tooltip title="确认修改">
                <a-button type="link" size="small" @click="save(record)">
                  <CheckOutlined />
                </a-button>
              </a-tooltip>
              <a-tooltip title="取消修改">
                <a-button type="link" danger size="small" @click="() => delete editableData[record.user_id]">
                  <CloseOutlined />
                </a-button>
              </a-tooltip>
            </template>
            <a-tooltip v-else title="编辑凭证">
              <a-button type="link" size="small" @click="edit(record)">
                <EditOutlined />
              </a-button>
            </a-tooltip>
          </a-space>
        </template>
      </a-table>
    </section>
  </main>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref, UnwrapRef } from 'vue'
import { message } from 'ant-design-vue'
import { TableState } from 'ant-design-vue/lib/table/interface'
import { IPage } from '/@/api/http/type'
import { getAllUsersInfo, updateUserInfo } from '/@/api/manage'
import { ELocalStorageKey } from '/@/types'
import { CheckOutlined, CloseOutlined, EditOutlined, ReloadOutlined } from '@ant-design/icons-vue'

export interface Member {
  user_id: string
  username: string
  user_type: string
  workspace_name: string
  create_time: string
  mqtt_username: string
  mqtt_password: string
}

interface MemberData {
  member: Member[]
}

const columns = [
  { title: '账号', dataIndex: 'username', width: 210, sorter: (a: Member, b: Member) => a.username.localeCompare(b.username), className: 'titleStyle', slots: { customRender: 'username' } },
  { title: '用户类型', dataIndex: 'user_type', width: 120, className: 'titleStyle', slots: { customRender: 'user_type' } },
  { title: '工作空间', dataIndex: 'workspace_name', width: 190, className: 'titleStyle' },
  { title: 'MQTT 用户名', dataIndex: 'mqtt_username', width: 170, className: 'titleStyle', slots: { customRender: 'mqtt_username' } },
  { title: 'MQTT 密码', dataIndex: 'mqtt_password', width: 170, className: 'titleStyle', slots: { customRender: 'mqtt_password' } },
  { title: '加入时间', dataIndex: 'create_time', width: 180, sorter: (a: Member, b: Member) => a.create_time.localeCompare(b.create_time), className: 'titleStyle' },
  { title: '操作', dataIndex: 'action', width: 110, align: 'center', className: 'titleStyle', slots: { customRender: 'action' } },
]

const loading = ref(false)
const keyword = ref('')
const data = reactive<MemberData>({
  member: []
})

const editableData: UnwrapRef<Record<string, Member>> = reactive({})

const paginationProp = reactive({
  pageSizeOptions: ['20', '50', '100'],
  showQuickJumper: true,
  showSizeChanger: true,
  pageSize: 50,
  current: 1,
  total: 0
})

const filteredMembers = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) {
    return data.member
  }
  return data.member.filter(item => {
    return [item.username, item.workspace_name, item.mqtt_username, item.user_type]
      .some(field => (field || '').toLowerCase().includes(value))
  })
})

const metrics = computed(() => {
  const webCount = data.member.filter(item => item.user_type === 'Web').length
  const pilotCount = data.member.filter(item => item.user_type === 'Pilot').length
  const workspaceCount = new Set(data.member.map(item => item.workspace_name).filter(Boolean)).size
  return [
    { label: '平台账号', value: paginationProp.total || data.member.length, desc: '已接入的管理账号总量' },
    { label: '管理端用户', value: webCount, desc: '负责调度、监控与数据管理' },
    { label: '飞手端用户', value: pilotCount, desc: '面向 Pilot 侧移动作业' },
    { label: '工作空间', value: workspaceCount, desc: '当前账号覆盖的业务空间' },
  ]
})

const rowSelection = {
  onChange: (selectedRowKeys: (string | number)[], selectedRows: []) => {
    console.log(`selectedRowKeys: ${selectedRowKeys}`, 'selectedRows: ', selectedRows)
  },
  onSelect: (record: any, selected: boolean, selectedRows: []) => {
    console.log(record, selected, selectedRows)
  },
  onSelectAll: (selected: boolean, selectedRows: [], changeRows: []) => {
    console.log(selected, selectedRows, changeRows)
  },
}

type Pagination = TableState['pagination']

const body: IPage = {
  page: 1,
  total: 0,
  page_size: 50
}
const workspaceId: string = localStorage.getItem(ELocalStorageKey.WorkspaceId)!

onMounted(() => {
  getAllUsers(workspaceId, body)
})

function refreshData (page: Pagination) {
  body.page = page?.current!
  body.page_size = page?.pageSize!
  getAllUsers(workspaceId, body)
}

function refreshCurrent () {
  getAllUsers(workspaceId, body)
}

function getAllUsers (workspaceId: string, page: IPage) {
  loading.value = true
  getAllUsersInfo(workspaceId, page).then(res => {
    const userList: Member[] = res.data.list
    data.member = userList
    paginationProp.total = res.data.pagination.total
    paginationProp.current = res.data.pagination.page
  }).finally(() => {
    loading.value = false
  })
}

function edit (record: Member) {
  editableData[record.user_id] = { ...record }
}

function save (record: Member) {
  const nextRecord = editableData[record.user_id]
  delete editableData[record.user_id]
  updateUserInfo(workspaceId, record.user_id, nextRecord).then(res => {
    if (res.code !== 0) {
      message.error(res.message)
      return
    }
    Object.assign(record, nextRecord)
    message.success('成员信息已更新')
  })
}

function formatUserType (type: string) {
  const map: Record<string, string> = {
    Web: '管理端',
    Pilot: '飞手端'
  }
  return map[type] || type
}

function getAvatarText (value: string) {
  return (value || 'U').slice(0, 1).toUpperCase()
}
</script>

<style lang="scss" scoped>
.member-page {
  min-height: 100%;
  padding: 28px;
  color: #17233d;
}

.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  margin-bottom: 18px;
}

.eyebrow {
  color: #2d8cf0;
  font-weight: 600;
}

.page-heading h1 {
  margin: 6px 0 8px;
  font-size: 28px;
  line-height: 1.25;
  font-weight: 650;
  letter-spacing: 0;
}

.page-heading p {
  max-width: 760px;
  margin: 0;
  color: #687385;
  line-height: 1.7;
}

.heading-actions {
  display: flex;
  gap: 10px;
  flex: 0 0 auto;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.metric-card {
  min-height: 112px;
  padding: 18px;
  border: 1px solid #e3e8ef;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(15, 35, 58, 0.05);
}

.metric-card span,
.metric-card small {
  display: block;
  color: #687385;
}

.metric-card strong {
  display: block;
  margin: 8px 0 6px;
  font-size: 30px;
  line-height: 1.1;
  color: #17233d;
  font-weight: 650;
}

.table-panel {
  padding: 20px;
  border: 1px solid #e3e8ef;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(15, 35, 58, 0.06);
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  margin-bottom: 18px;
}

.table-toolbar h2 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 650;
}

.table-toolbar p {
  margin: 0;
  color: #7b8794;
}

.search {
  width: 340px;
  flex: 0 0 auto;
}

.account-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.account-cell > span {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: 8px;
  color: #fff;
  font-weight: 650;
  background: linear-gradient(135deg, #2d8cf0, #19be6b);
}

.account-cell strong,
.account-cell small {
  display: block;
}

.account-cell small {
  max-width: 145px;
  overflow: hidden;
  color: #8a96a6;
  text-overflow: ellipsis;
}

.credential {
  display: inline-flex;
  max-width: 150px;
  padding: 2px 8px;
  overflow: hidden;
  border-radius: 5px;
  color: #455466;
  background: #f4f7fb;
  text-overflow: ellipsis;
  vertical-align: middle;
}

.inline-input {
  margin: -5px 0;
}

.row-actions :deep(.ant-btn) {
  padding: 0 4px;
}

:deep(.ant-table) {
  border-top: 1px solid #edf0f5;
  border-bottom: 1px solid #edf0f5;
}

:deep(.ant-table-thead > tr > th) {
  color: #526173;
  font-weight: 650;
  background: #f8fafc !important;
  border-bottom: 1px solid #edf0f5;
}

:deep(.ant-table-tbody > tr > td) {
  border-bottom: 1px solid #f0f3f7;
}

:deep(.table-striped td) {
  background: #fbfcfe;
}

:deep(.ant-table-tbody > tr:hover > td) {
  background: #f3f8ff !important;
}

@media (max-width: 980px) {
  .member-page {
    padding: 18px;
  }

  .page-heading,
  .table-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .search {
    width: 100%;
  }
}

@media (max-width: 560px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
