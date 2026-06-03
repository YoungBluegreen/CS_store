<template>
  <main class="portal">
    <section class="portal-hero">
      <header class="portal-header">
        <div class="brand">
          <img :src="cloudapiLogo" alt="logo" />
          <div>
            <strong>无人机低空作业指挥平台</strong>
            <span>设备、航线、任务、AI 与成果一体化闭环</span>
          </div>
        </div>
        <div class="header-status">
          <span class="pulse"></span>
          低空智能作业中心
        </div>
      </header>

      <div class="hero-content">
        <div class="hero-copy">
          <span class="eyebrow">地图驱动 · 任务闭环 · 智能识别</span>
          <h1>无人机平台</h1>
          <div class="hero-actions">
            <a-button type="primary" size="large" :disabled="loginBtnDisabled" @click="onSubmit">
              进入指挥中心
            </a-button>
            <a-button size="large" ghost @click="fillDefaultAccount">
              使用默认账号
            </a-button>
          </div>
        </div>

      </div>

      <div class="capability-strip">
        <div v-for="item in capabilityCards" :key="item.title" class="capability-card">
          <component :is="item.icon" />
          <div>
            <strong>{{ item.title }}</strong>
            <span>{{ item.desc }}</span>
          </div>
        </div>
      </div>
    </section>

    <aside class="login-panel">
      <div class="panel-inner">
        <div class="panel-heading">
          <span>平台登录</span>
          <h2>欢迎回来</h2>
          <p>登录后进入综合态势首页，完成设备、任务、监控、成果与智能识别的演示闭环。</p>
        </div>

        <a-form :model="formState" layout="vertical" class="login-form">
          <a-form-item label="账号">
            <a-input v-model:value="formState.username" size="large" placeholder="请输入账号">
              <template #prefix>
                <UserOutlined />
              </template>
            </a-input>
          </a-form-item>
          <a-form-item label="密码">
            <a-input-password v-model:value="formState.password" size="large" placeholder="请输入密码" @pressEnter="onSubmit">
              <template #prefix>
                <LockOutlined />
              </template>
            </a-input-password>
          </a-form-item>
          <a-button
            class="login-button"
            type="primary"
            size="large"
            html-type="submit"
            :disabled="loginBtnDisabled"
            @click="onSubmit"
          >
            登录平台
          </a-button>
        </a-form>

        <div class="scenario-list">
          <div v-for="item in scenarioItems" :key="item.title">
            <component :is="item.icon" />
            <span>{{ item.title }}</span>
          </div>
        </div>
      </div>
    </aside>
  </main>
</template>

<script lang="ts" setup>
import cloudapiLogo from '/@/assets/icons/cloudapi.png'
import {
  AimOutlined,
  ApartmentOutlined,
  CloudServerOutlined,
  DashboardOutlined,
  EnvironmentOutlined,
  FileImageOutlined,
  LockOutlined,
  RadarChartOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { reactive, computed, UnwrapRef } from 'vue'
import { login, LoginBody } from '/@/api/manage'
import { getRoot } from '/@/root'
import { ELocalStorageKey, ERouterName, EUserType } from '/@/types'

const root = getRoot()

const formState: UnwrapRef<LoginBody> = reactive({
  username: 'adminPC',
  password: 'adminPC',
  flag: EUserType.Web,
})

const capabilityCards = [
  { title: '设备接入', desc: '机场与无人机状态监测', icon: CloudServerOutlined },
  { title: '飞控调度', desc: '航线任务与远程控制', icon: DashboardOutlined },
  { title: '实时监控', desc: '视频直播与虚拟座舱', icon: VideoCameraOutlined },
  { title: 'AI 识别', desc: '目标检测与语义分割', icon: RadarChartOutlined },
]

const scenarioItems = [
  { title: '自然资源监管', icon: EnvironmentOutlined },
  { title: '智慧城市治理', icon: ApartmentOutlined },
  { title: '低空经济服务', icon: RocketOutlined },
  { title: '飞行空域管控', icon: SafetyCertificateOutlined },
  { title: '媒体数据管理', icon: FileImageOutlined },
  { title: '作业路径优化', icon: AimOutlined },
]

const loginBtnDisabled = computed(() => {
  return !formState.username || !formState.password
})

const fillDefaultAccount = () => {
  formState.username = 'adminPC'
  formState.password = 'adminPC'
}

const onSubmit = async () => {
  const result = await login(formState)
  if (result.code === 0) {
    localStorage.setItem(ELocalStorageKey.Token, result.data.access_token)
    localStorage.setItem(ELocalStorageKey.WorkspaceId, result.data.workspace_id)
    localStorage.setItem(ELocalStorageKey.Username, result.data.username)
    localStorage.setItem(ELocalStorageKey.UserId, result.data.user_id)
    localStorage.setItem(ELocalStorageKey.Flag, EUserType.Web.toString())
    root.$router.push('/' + ERouterName.DASHBOARD)
  } else {
    message.error(result.message)
  }
}
</script>

<style lang="scss" scoped>
@import '/@/styles/index.scss';

.portal {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  color: #f6fbff;
  background:
    radial-gradient(circle at 72% 18%, rgba(25, 190, 107, 0.14), transparent 30%),
    linear-gradient(135deg, #08131f 0%, #101820 52%, #15191f 100%);
  overflow: hidden;
}

.portal-hero {
  position: relative;
  min-width: 0;
  padding: 32px 44px 34px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.portal-hero::after {
  content: '';
  position: absolute;
  left: 44px;
  right: 44px;
  bottom: 138px;
  height: 220px;
  background-image:
    linear-gradient(rgba(92, 173, 255, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(92, 173, 255, 0.08) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: linear-gradient(90deg, transparent, #000 16%, #000 84%, transparent);
  pointer-events: none;
}

.portal-header,
.hero-content,
.capability-strip {
  position: relative;
  z-index: 1;
}

.portal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand img {
  width: 42px;
  height: 42px;
  border-radius: 8px;
}

.brand strong,
.brand span {
  display: block;
}

.brand strong {
  font-size: 18px;
  font-weight: 650;
}

.brand span,
.header-status,
.eyebrow,
  .hero-copy p,
  .capability-card span,
  .panel-heading p,
  .scenario-list span {
  color: rgba(246, 251, 255, 0.66);
}

.header-status {
  height: 36px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  border: 1px solid rgba(92, 173, 255, 0.28);
  border-radius: 4px;
  background: rgba(9, 23, 36, 0.56);
}

.pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #19be6b;
  box-shadow: 0 0 0 6px rgba(25, 190, 107, 0.14);
}

.hero-content {
  display: block;
  align-items: center;
  margin: 70px 0 48px;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border: 1px solid rgba(92, 173, 255, 0.3);
  border-radius: 4px;
  background: rgba(45, 140, 240, 0.1);
}

.hero-copy h1 {
  max-width: 720px;
  margin: 18px 0;
  font-size: 52px;
  line-height: 1.12;
  letter-spacing: 0;
  font-weight: 700;
  color: #fff;
}

.hero-copy p {
  max-width: 680px;
  margin: 0;
  font-size: 17px;
  line-height: 1.9;
}

.hero-actions {
  display: flex;
  gap: 14px;
  margin-top: 34px;
}

.capability-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.capability-card {
  min-height: 82px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(92, 173, 255, 0.16);
  border-radius: 6px;
  background: rgba(8, 19, 31, 0.62);
}

.capability-card .anticon {
  flex: 0 0 auto;
  color: #5cadff;
  font-size: 24px;
}

.capability-card strong,
.capability-card span {
  display: block;
}

.capability-card strong {
  margin-bottom: 4px;
  font-size: 16px;
}

.login-panel {
  display: flex;
  align-items: stretch;
  padding: 28px 28px 28px 0;
}

.panel-inner {
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 42px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  background: rgba(247, 249, 250, 0.96);
  color: #17233d;
  box-shadow: 0 28px 70px rgba(0, 0, 0, 0.34);
}

.panel-heading span {
  color: #2d8cf0;
  font-weight: 600;
}

.panel-heading h2 {
  margin: 10px 0 8px;
  font-size: 30px;
  line-height: 1.2;
  letter-spacing: 0;
}

.panel-heading p {
  margin: 0 0 30px;
  color: rgba(23, 35, 61, 0.62);
  line-height: 1.7;
}

.login-form :deep(.ant-form-item-label > label) {
  color: rgba(23, 35, 61, 0.76);
}

.login-form :deep(.ant-input-affix-wrapper) {
  border-radius: 6px;
}

.login-button {
  width: 100%;
  height: 44px;
  margin-top: 6px;
  border-radius: 6px;
}

.scenario-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 34px;
}

.scenario-list div {
  min-height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border-radius: 6px;
  background: #eef5fc;
}

.scenario-list .anticon {
  color: #2d8cf0;
}

.scenario-list span {
  color: rgba(23, 35, 61, 0.7);
}

@media (max-width: 1180px) {
  .portal {
    grid-template-columns: 1fr;
    overflow-y: auto;
  }

  .login-panel {
    padding: 0 44px 34px;
  }
}

@media (max-width: 900px) {
  .portal-hero {
    padding: 24px;
  }

  .portal-header,
  .hero-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-content {
    grid-template-columns: 1fr;
  }

  .hero-copy h1 {
    font-size: 38px;
  }

  .capability-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .hero-copy h1 {
    font-size: 30px;
  }

  .capability-strip,
  .scenario-list {
    grid-template-columns: 1fr;
  }

  .panel-inner {
    padding: 24px;
  }
}
</style>
