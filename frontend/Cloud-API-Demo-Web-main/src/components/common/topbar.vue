<template>
  <div class="topbar">
    <router-link class="brand" :to="'/' + ERouterName.DASHBOARD">
      <div class="brand-mark">
        <RadarChartOutlined />
      </div>
      <div class="brand-copy">
        <strong>无人机低空作业指挥平台</strong>
        <span>{{ workspaceName || '地图驱动的云端作业管理系统' }}</span>
      </div>
    </router-link>

    <div class="account">
      <div class="system-state">
        <span></span>
        运行正常
      </div>
      <a-dropdown>
        <button class="account-button">
          <UserOutlined />
          <span>{{ username }}</span>
          <DownOutlined />
        </button>
        <template #overlay>
          <a-menu>
            <a-menu-item @click="logout">
              <LogoutOutlined />
              <span>退出登录</span>
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { getRoot } from '/@/root'
import { getPlatformInfo } from '/@/api/manage'
import { ELocalStorageKey, ERouterName } from '/@/types'
import {
  DownOutlined,
  LogoutOutlined,
  RadarChartOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'

const root = getRoot()

const username = ref(localStorage.getItem(ELocalStorageKey.Username) || '管理员')
const workspaceName = ref('')

onMounted(() => {
  getPlatformInfo().then(res => {
    workspaceName.value = res.data?.workspace_name || ''
  }).catch(() => {
    workspaceName.value = ''
  })
})

const logout = () => {
  localStorage.clear()
  root.$router.push(ERouterName.PROJECT)
}
</script>

<style lang="scss" scoped>
@import '/@/styles/index.scss';

.topbar {
  height: 64px;
  width: 100%;
  display: grid;
  grid-template-columns: minmax(300px, 1fr) minmax(250px, 320px);
  align-items: center;
  gap: 18px;
  padding: 0 24px;
  background: #101820;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: #fff;
}

.brand:hover {
  color: #fff;
}

.brand-mark {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: 8px;
  color: #c8f7ff;
  background: linear-gradient(135deg, #14515a, #19694a);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.14);
}

.brand-mark .anticon {
  font-size: 22px;
}

.brand-copy {
  min-width: 0;
}

.brand-copy strong,
.brand-copy span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-copy strong {
  font-size: 16px;
  font-weight: 650;
  line-height: 22px;
}

.brand-copy span {
  margin-top: 2px;
  color: rgba(255, 255, 255, 0.58);
  font-size: 12px;
}

.account {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.system-state {
  height: 30px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 10px;
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.78);
  background: rgba(255, 255, 255, 0.06);
  font-size: 13px;
  white-space: nowrap;
}

.system-state span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #19be6b;
  box-shadow: 0 0 0 4px rgba(25, 190, 107, 0.14);
}

.account-button {
  height: 38px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border: 0;
  border-radius: 6px;
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
  cursor: pointer;
  white-space: nowrap;
}

.account-button:hover {
  background: rgba(255, 255, 255, 0.14);
}

@media (max-width: 1180px) {
  .topbar {
    grid-template-columns: 1fr;
    height: auto;
    gap: 12px;
    padding: 14px 18px;
  }

  .account {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
