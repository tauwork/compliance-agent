import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import Dashboard from './views/Dashboard.vue'
import Policies from './views/Policies.vue'
import PolicyDoc from './views/PolicyDoc.vue'
import Rules from './views/Rules.vue'
import Precheck from './views/Precheck.vue'
import Inspection from './views/Inspection.vue'
import Risks from './views/Risks.vue'
import Tickets from './views/Tickets.vue'
import Assistant from './views/Assistant.vue'
import Users from './views/Users.vue'
import Roles from './views/Roles.vue'
import { loadSession } from './session'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: Dashboard },
    { path: '/policies', component: Policies },
    { path: '/policies/:id', component: PolicyDoc },
    { path: '/rules', component: Rules },
    { path: '/decompose', redirect: '/rules' },
    { path: '/precheck', component: Precheck },
    { path: '/inspection', component: Inspection },
    { path: '/risks', component: Risks },
    { path: '/tickets', component: Tickets },
    { path: '/assistant', component: Assistant },
    { path: '/system/users', component: Users },
    { path: '/system/roles', component: Roles }
  ]
})

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
loadSession().finally(() => app.mount('#app'))
