import { reactive } from 'vue'

/**
 * 登录态存在 sessionStorage 而不是 localStorage。
 *
 * 原因：localStorage 是**同一个浏览器所有标签页共享**的，
 * 先登 student、再登 admin，token 会被覆盖，A 窗口下一次请求就变成了 admin 的身份。
 * sessionStorage 按标签页隔离：每个窗口各存一份，能同时开 student 和 admin 两个窗口。
 *
 * 代价：关掉标签页登录态就没了，下次要重新登录。
 */
const store = window.sessionStorage

// 兼容旧版本：把遗留在 localStorage 里的登录态搬到当前标签页一次，避免升级后被迫重新登录
;(function migrateFromLocalStorage() {
  const oldToken = window.localStorage.getItem('token')
  if (!oldToken || store.getItem('token')) return

  const keys = ['token', 'username', 'roles', 'permissions', 'role']
  keys.forEach((k) => {
    const v = window.localStorage.getItem(k)
    if (v !== null) store.setItem(k, v)
  })
  keys.forEach((k) => window.localStorage.removeItem(k))
})()

export const session = reactive({
  token: store.getItem('token') || '',
  username: store.getItem('username') || '',
  roles: JSON.parse(store.getItem('roles') || '[]'),
  permissions: JSON.parse(store.getItem('permissions') || '[]'),
  menus: []
})

export function setSession(loginData) {
  session.token = loginData.token || ''
  session.username = loginData.username || ''
  session.roles = loginData.roles || []
  session.permissions = loginData.permissions || []

  store.setItem('token', session.token)
  store.setItem('username', session.username)
  store.setItem('roles', JSON.stringify(session.roles))
  store.setItem('permissions', JSON.stringify(session.permissions))
  // 旧组件还按单个角色读取，保留一个主角色
  store.setItem('role', session.roles[0] || '')
}

export function clearSession() {
  session.token = ''
  session.username = ''
  session.roles = []
  session.permissions = []
  session.menus = []

  const keys = ['token', 'username', 'roles', 'permissions', 'role']
  keys.forEach((k) => store.removeItem(k))
}

/** 给还按散装 key 取值的旧组件用 */
export function readToken() {
  return store.getItem('token') || ''
}

export function readRole() {
  return store.getItem('role') || ''
}
