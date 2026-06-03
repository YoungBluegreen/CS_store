<template>
  <a-config-provider :locale="zhCN">
    <div class="demo-app">
      <router-view />
      <!-- <div class="map-wrapper">
        <GMap/>
      </div> -->
    </div>
  </a-config-provider>
</template>

<script lang="ts">
import { computed, defineComponent, ref } from 'vue'
import { useMyStore } from './store'
import GMap from '/@/components/GMap.vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'

export default defineComponent({
  name: 'App',
  components: { GMap },

  setup () {
    const store = useMyStore()
    return {
      zhCN
    }
  }
})
</script>
<style lang="scss" scoped>
.demo-app {
  width: 100%;
  height: 100%;

  .map-wrapper {
    height: 100%;
    width: 100%;
  }
}
</style>

<style lang="scss">
#demo-app {
  width: 100%;
  height: 100%
}
</style>
