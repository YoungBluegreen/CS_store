<template>
  <section class="planner-shell">
    <header class="planner-head">
      <div>
        <span>8080 航线规划器</span>
        <strong>航线绘制与 KMZ 生成</strong>
      </div>
      <div class="planner-actions">
        <a-button size="small" @click="reloadPlanner">刷新</a-button>
        <a-button size="small" type="primary" @click="openPlanner">独立打开</a-button>
      </div>
    </header>

    <div class="planner-frame-wrap">
      <iframe
        :key="frameKey"
        class="planner-frame"
        :src="plannerUrl"
        title="8080 航线规划器"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const plannerUrl = import.meta.env.VITE_WAYPOINT_PLANNER_URL || 'http://127.0.0.1:8081/'
const frameKey = ref(0)

function reloadPlanner () {
  frameKey.value += 1
}

function openPlanner () {
  window.open(plannerUrl, '_blank', 'noopener,noreferrer')
}
</script>

<style lang="scss" scoped>
.planner-shell {
  height: calc(100vh - 92px);
  min-height: 560px;
  display: flex;
  flex-direction: column;
  background: #181818;
  border: 1px solid #303030;
  border-radius: 6px;
  overflow: hidden;
}

.planner-head {
  min-height: 56px;
  padding: 10px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #fff;
  background: #232323;
  border-bottom: 1px solid #303030;

  div:first-child {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  span {
    color: rgba(255, 255, 255, 0.45);
    font-size: 12px;
  }

  strong {
    color: rgba(255, 255, 255, 0.88);
    font-size: 16px;
    font-weight: 600;
  }
}

.planner-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.planner-frame-wrap {
  flex: 1;
  min-height: 0;
  background: #101010;
}

.planner-frame {
  width: 100%;
  height: 100%;
  border: 0;
  display: block;
  background: #101010;
}
</style>
