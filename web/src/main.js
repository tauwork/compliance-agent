import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import Dashboard from './views/Dashboard.vue'
import Policies from './views/Policies.vue'
import Decompose from './views/Decompose.vue'
import Precheck from './views/Precheck.vue'
import Risks from './views/Risks.vue'
import Tickets from './views/Tickets.vue'
import Assistant from './views/Assistant.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: Dashboard },
    { path: '/policies', component: Policies },
    { path: '/decompose', component: Decompose },
    { path: '/precheck', component: Precheck },
    { path: '/risks', component: Risks },
    { path: '/tickets', component: Tickets },
    { path: '/assistant', component: Assistant }
  ]
})

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
