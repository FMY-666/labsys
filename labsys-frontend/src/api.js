import { session } from './stores/session'

/**
 * 统一的请求封装。返回的永远是后端那个 { code, message, data } 对象，
 * 调用方只管判断 code === 200。
 *
 * 这里替掉了每个页面手写三层判断（网络错 / HTTP 错 / 业务错）的重复代码，
 * 但注意 HTTP 非 200 时也包成同样的形状，不让调用方拿到 undefined。
 */
export async function api(path, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(session.token ? { Authorization: 'Bearer ' + session.token } : {}),
    ...(options.headers || {})
  }

  let res
  try {
    res = await fetch(path, { ...options, headers })
  } catch (e) {
    return { code: -1, message: '网络异常，请检查后端是否启动' }
  }

  let data
  try {
    data = await res.json()
  } catch (e) {
    return { code: -1, message: '响应不是合法的 JSON（HTTP ' + res.status + '）' }
  }

  if (!res.ok && !data.code) {
    return { code: res.status, message: data.message || 'HTTP ' + res.status }
  }
  return data
}

export function get(path) {
  return api(path)
}

export function post(path, body) {
  return api(path, { method: 'POST', body: JSON.stringify(body || {}) })
}

export function put(path, body) {
  return api(path, { method: 'PUT', body: JSON.stringify(body || {}) })
}
