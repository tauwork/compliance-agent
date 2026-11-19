<template>
  <el-container class="shell">
    <el-aside width="240px" class="side">
      <div class="logo">
        <div class="mark">合</div>
        <div>
          <div class="t1">政策合规智能体</div>
          <div class="t2">三资监管</div>
        </div>
      </div>
      <div class="search">
        <svg viewBox="0 0 24 24" class="search-ico" aria-hidden="true">
          <circle cx="11" cy="11" r="6" /><path d="M16 16l4 4" />
        </svg>
        <input v-model="keyword" placeholder="搜索菜单" aria-label="搜索菜单" />
      </div>
      <el-menu :key="menuKey" :default-active="active" :default-openeds="opened" router
               background-color="#10192b" text-color="#c5d0e0" active-text-color="#fff"
               class="menu" @open="onOpen" @close="onClose">
        <el-sub-menu v-for="group in visible" :key="group.key" :index="group.key">
          <template #title>
            <span class="group">{{ group.label }}</span>
          </template>
          <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
            <svg viewBox="0 0 24 24" class="ico" aria-hidden="true"><path :d="item.icon" /></svg>
            <span>{{ item.name }}</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
      <div v-if="keyword.trim() && !visible.length" class="empty">没有匹配的菜单</div>
      <div class="ft">演示系统均为模拟数据</div>
    </el-aside>
    <el-container>
      <el-header class="top">
        <div class="crumb">
          <span class="crumb-k">政策合规智能体</span>
          <span class="sep">/</span>
          <span class="crumb-n">{{ activeName }}</span>
        </div>
        <div class="rt">
          <el-dropdown trigger="click" @command="switchUser">
            <span class="who"><i>{{ initial }}</i>{{ session.user?.displayName || '选择用户' }}</span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="u in switchable" :key="u.id" :command="u.id">
                  {{ u.displayName }} · {{ u.roleName }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { session, setCurrentUser } from './session'

const groups = [
  { key: 'cockpit', label: '工作台', items: [
    { path: '/', name: '合规驾驶舱', icon: 'M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6H10v6H5a1 1 0 0 1-1-1z' }
  ]},
  { key: 'policy', label: '政策管理', items: [
    { path: '/policies', name: '政策法规库', icon: 'M6 4h9l3 3v13a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1zm8 0v4h4M8 12h8M8 16h6' },
    { path: '/rules', name: 'AI规则库', icon: 'M5 7h14M5 12h14M5 17h9' }
  ]},
  { key: 'ops', label: '合规运行', items: [
    { path: '/precheck', name: '前置预审', icon: 'M8 4h8v4a4 4 0 0 1-8 0zM6 20h12l-1-6H7z' },
    { path: '/inspection', name: '全量巡检', icon: 'M12 4v2M12 18v2M4 12H6M18 12h2M6.5 6.5l1.4 1.4M16.1 16.1l1.4 1.4M17.5 6.5l-1.4 1.4M7.9 16.1l-1.4 1.4M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z' },
    { path: '/risks', name: '风险预警', icon: 'M12 4 3 19h18zM12 10v4M12 16.5v.5' },
    { path: '/tickets', name: '整改闭环', icon: 'M8 7h11M8 12h11M8 17h11M5 7h.01M5 12h.01M5 17h.01' }
  ]},
  { key: 'ai', label: '智能助手', items: [
    { path: '/assistant', name: '对话助手', icon: 'M5 6h14v9H8l-3 3z' }
  ]},
  { key: 'system', label: '系统管理', items: [
    { path: '/system/users', name: '用户管理', icon: 'M8 9a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM2 20c0-3 2.7-5 6-5s6 2 6 5M16 11a3 3 0 1 0 0-6M16 13c2.8 0 5 1.6 5 4' },
    { path: '/system/roles', name: '角色权限', icon: 'M12 3 5 6v6c0 4 3 6.5 7 8 4-1.5 7-4 7-8V6z' }
  ]}
]

const names = Object.fromEntries(groups.flatMap(g => g.items.map(i => [i.path, i.name])))
const route = useRoute()
const router = useRouter()
const keyword = ref('')
const opened = ref(groups.map(g => g.key))
const menuKey = ref(0)
let openedBeforeSearch = null

const active = computed(() => route.path.startsWith('/policies') ? '/policies' : route.path)
const activeName = computed(() => names[active.value] || '')
const switchable = computed(() => session.users.filter(u => u.enabled))
const initial = computed(() => (session.user?.displayName || '用').slice(0, 1))
const permitted = computed(() => groups
  .map(g => ({ ...g, items: g.items.filter(i => session.menus.includes(i.path)) }))
  .filter(g => g.items.length))
const visible = computed(() => {
  const q = keyword.value.trim()
  if (!q) return permitted.value
  return permitted.value.map(g => {
    const items = g.label.includes(q) ? g.items : g.items.filter(i => i.name.includes(q))
    return { ...g, items }
  }).filter(g => g.items.length)
})

function menuOf(path) {
  return path.startsWith('/policies') ? '/policies' : path
}
function ensureMenu() {
  if (!session.ready || !session.menus.length) return
  if (!session.menus.includes(menuOf(route.path))) router.replace(session.menus[0])
}
watch(() => route.path, ensureMenu)
watch(() => session.menus.join('|'), () => {
  ensureMenu()
  menuKey.value += 1
})

async function switchUser(id) {
  if (id === session.user?.id) return
  await setCurrentUser(id)
}

watch(keyword, (val, prev) => {
  const now = val.trim()
  const before = (prev || '').trim()
  if (now && !before) openedBeforeSearch = [...opened.value]
  opened.value = now ? visible.value.map(g => g.key) : (openedBeforeSearch || groups.map(g => g.key))
  menuKey.value += 1
})

function onOpen(key) {
  if (!opened.value.includes(key)) opened.value = [...opened.value, key]
}
function onClose(key) {
  opened.value = opened.value.filter(k => k !== key)
}
</script>

<style>
:root {
  --el-color-primary: #1e4e8c;
  --el-color-primary-light-3: #5278a8;
  --el-color-primary-light-5: #8eabc8;
  --el-color-primary-light-7: #c5d5e6;
  --el-color-primary-light-8: #dce6f1;
  --el-color-primary-light-9: #f1f5fa;
  --el-color-primary-dark-2: #183e70;
  --el-border-radius-base: 8px;
  --el-font-family: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  --ink: #142033;
  --muted: #6d7d92;
  --line: #e6ebf2;
  --paper: #f4f6fa;
}
html, body, #app { margin: 0; height: 100%; font-family: var(--el-font-family); color: var(--ink); }
.shell { height: 100vh; }
.side { background: #10192b; display: flex; flex-direction: column; color: #c5d0e0; height: 100vh; overflow: hidden; }
.logo { display: flex; align-items: center; gap: 12px; padding: 20px 18px 14px; }
.mark { width: 36px; height: 36px; border-radius: 10px; background: linear-gradient(160deg, #2f6fbe, #173e72);
  color: #fff; font-weight: 700; display: flex; align-items: center; justify-content: center; letter-spacing: 0; flex: none; }
.logo .t1 { font-size: 15px; font-weight: 650; color: #fff; letter-spacing: .4px; }
.logo .t2 { font-size: 11px; color: #8ea0bb; margin-top: 2px; letter-spacing: .12em; }
.search { position: relative; margin: 0 14px 8px; }
.search input { width: 100%; box-sizing: border-box; height: 34px; border: 1px solid rgba(255,255,255,.08);
  background: rgba(255,255,255,.05); color: #e8eef8; border-radius: 8px; padding: 0 12px 0 32px; outline: none; font-size: 13px; }
.search input::placeholder { color: #7e90aa; }
.search input:focus { border-color: rgba(120, 166, 224, .7); background: rgba(255,255,255,.08); }
.search-ico { position: absolute; left: 10px; top: 9px; width: 15px; height: 15px; stroke: #8ea0bb; fill: none; stroke-width: 1.8; }
.menu { flex: 1; min-height: 0; border-right: none; overflow-y: auto; padding-bottom: 8px; }
.menu .el-sub-menu__title { height: 36px; line-height: 36px; color: #8ea0bb !important; font-size: 12px; letter-spacing: .08em; }
.menu .el-sub-menu__title:hover { background: rgba(255,255,255,.04) !important; color: #d5deec !important; }
.menu .el-sub-menu__icon-arrow { color: #8ea0bb; }
.menu .el-menu-item { height: 40px; line-height: 40px; margin: 1px 10px; width: calc(100% - 20px); border-radius: 8px; font-size: 13.5px; }
.menu .el-menu-item:hover { background: rgba(255,255,255,.05) !important; }
.menu .el-menu-item.is-active { background: rgba(47, 111, 190, .28) !important; color: #fff !important; box-shadow: inset 2px 0 0 #7eb0ea; }
.menu .el-sub-menu .el-menu { background: transparent !important; }
.group { font-weight: 600; }
.ico { width: 16px; height: 16px; margin-right: 8px; stroke: currentColor; fill: none; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; vertical-align: -3px; }
.empty { padding: 12px 18px; font-size: 12px; color: #7e90aa; }
.ft { padding: 14px 18px; font-size: 11px; color: #667892; border-top: 1px solid rgba(255,255,255,.06); }
.top { height: 56px; background: #fff; border-bottom: 1px solid var(--line); display: flex; align-items: center; justify-content: space-between; padding: 0 22px; }
.crumb { display: flex; align-items: center; gap: 8px; font-size: 13px; min-width: 0; white-space: nowrap; }
.crumb-k { color: var(--muted); }
.sep { color: #c5ceda; }
.crumb-n { color: var(--ink); font-weight: 600; }
.who { display: inline-flex; align-items: center; gap: 8px; color: #3e4e63; font-size: 13px; flex: none; white-space: nowrap; cursor: pointer; }
.who i { width: 26px; height: 26px; border-radius: 50%; background: #e8eef6; color: #1e4e8c; font-style: normal; font-size: 12px; font-weight: 700; display: inline-flex; align-items: center; justify-content: center; }
.main { background: var(--paper); padding: 20px 22px; }
.pt { font-size: 20px; font-weight: 650; letter-spacing: .2px; color: var(--ink); margin: 0 0 6px; }
.pdesc { color: var(--muted); font-size: 13px; line-height: 1.6; margin: 0 0 16px; }
.el-card { border: 1px solid var(--line); border-radius: 12px; box-shadow: none; }
.el-card__header { font-weight: 600; color: var(--ink); }
</style>
