import { createApp } from 'vue'
import { ElLoading } from 'element-plus'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import './style.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

/**
 * 组件和样式交给 unplugin 按需引入，但**指令不会被自动注册**。
 * 项目里表格大量用 v-loading，不手动挂的话它只是个没人认识的自定义指令：
 * 不报错、也不生效，加载遮罩永远不出来 —— 这是这种改造最容易漏的一处。
 */
app.directive('loading', ElLoading.directive)

app.use(router).mount('#app')
