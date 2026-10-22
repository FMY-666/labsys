import { createRouter, createWebHistory } from 'vue-router'
import { session, clearSession } from '../stores/session'

const Placeholder = () => import('../views/PlaceholderView.vue')

// 后端只决定“注册哪些路径”，前端负责“这个路径对应哪个组件”
const pageMap = {
  '/devices': () => import('../views/DevicesView.vue'),
  '/my-reservations': () => import('../views/MyReservationsView.vue'),
  '/reservations': () => import('../views/ReservationsView.vue'),
  '/reservation-archive': () => import('../views/ReservationArchiveView.vue'),
  '/users': () => import('../views/UsersView.vue'),
  '/roles': () => import('../views/RolesView.vue'),
  '/logs': () => import('../views/LogsView.vue'),
  '/tickets': () => import('../views/TicketsView.vue'),
  '/my-tickets': () => import('../views/MyTicketsView.vue'),
  '/dashboard': () => import('../views/DashboardView.vue')
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
    {
      path: '/',
      name: 'main',
      component: () => import('../layouts/MainLayout.vue'),
      redirect: '/devices',
      children: [
        { path: ':pathMatch(.*)*', name: 'notFound', component: () => import('../views/NotFoundView.vue') }
      ]
    }
  ]
})

// 菜单树是嵌套的，压平成路径列表
export function collectPaths(menus) {
  const paths = []
  const walk = (nodes) => {
    nodes.forEach((node) => {
      if (node.path) paths.push(node.path)
      if (node.children && node.children.length) walk(node.children)
    })
  }
  walk(menus || [])
  return paths
}

const registered = []
let booted = false

export function registerPaths(paths) {
  paths.forEach((p) => {
    if (router.hasRoute(p)) return
    // 注册成 main 的子路由，页面才会显示在顶栏下方的内容区里
    router.addRoute('main', { path: p.replace(/^\//, ''), name: p, component: pageMap[p] || Placeholder })
    registered.push(p)
  })
}

// 退出登录时要把动态注册的路由摘掉，否则下一个账号会继承上一个账号的权限
export function resetRoutes() {
  registered.forEach((name) => {
    if (router.hasRoute(name)) router.removeRoute(name)
  })
  registered.length = 0
  booted = false
}

// 拿到菜单树后注册路由；刷新页面时也靠它把路由表重建出来
export async function bootstrapRoutes() {
  const res = await fetch('/api/menus/tree', {
    headers: { Authorization: 'Bearer ' + session.token }
  })
  const data = await res.json()

  if (data.code !== 200) return false

  session.menus = data.data
  session.permissions = collectPaths(data.data)
  // 跟着 session 一起存 sessionStorage，保证每个标签页的权限互不干扰
  sessionStorage.setItem('permissions', JSON.stringify(session.permissions))
  registerPaths(session.permissions)
  booted = true
  return true
}

router.beforeEach(async (to) => {
  if (to.name === 'login') {
    return session.token ? { path: '/' } : true
  }

  if (!session.token) {
    return { name: 'login' }
  }

  // 刚刷新或刚登录：路由表还是空的，先把菜单拉回来再按路径重新导航一次
  if (!booted) {
    try {
      const ok = await bootstrapRoutes()
      if (!ok) {
        clearSession()
        return { name: 'login' }
      }
    } catch (e) {
      clearSession()
      return { name: 'login' }
    }
    return { path: to.fullPath, replace: true }
  }

  return true
})

export default router
