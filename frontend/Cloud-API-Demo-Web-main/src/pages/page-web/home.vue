<template>
  <a-layout class="app-shell width-100 flex-display">
    <a-layout-header class="header">
      <Topbar />
    </a-layout-header>
    <a-layout-content class="shell-content">
      <router-view />
    </a-layout-content>

  </a-layout>
</template>

<script lang="ts" setup>
import Topbar from '/@/components/common/topbar.vue'
import { onMounted, reactive, ref, UnwrapRef, watch } from 'vue'
import { getRoot } from '/@/root'
import { EBizCode, ELocalStorageKey, ERouterName } from '/@/types'
import { useConnectWebSocket } from '/@/hooks/use-connect-websocket'
import EventBus from '/@/event-bus'

interface FormState {
  user: string
  password: string
}

const root = getRoot()

const messageHandler = async (payload: any) => {
  if (!payload) {
    return
  }
  switch (payload.biz_code) {
    case EBizCode.DeviceUpgrade: {
      EventBus.emit('deviceUpgrade', payload)
      break
    }
    case EBizCode.DeviceLogUploadProgress: {
      EventBus.emit('deviceLogUploadProgress', payload)
      break
    }
  }
}

// 监听ws 消息
useConnectWebSocket(messageHandler)

onMounted(() => {
  const token = localStorage.getItem(ELocalStorageKey.Token)
  if (!token) {
    root.$router.push(ERouterName.PROJECT)
  }
})

</script>

<style lang="scss" scoped>
@import '/@/styles/index.scss';

.fontBold {
  font-weight: 500;
  font-size: 18px;
}

.header {
  background: #0b1724;
  color: white;
  height: 64px;
  font-size: 15px;
  padding: 0;
  line-height: normal;
}

.app-shell {
  height: 100vh;
  background: #eef2f6;
}

.shell-content {
  min-height: 0;
  background:
    linear-gradient(180deg, rgba(45, 140, 240, 0.07), rgba(45, 140, 240, 0) 220px),
    #eef2f6;
  overflow: auto;
}
</style>
