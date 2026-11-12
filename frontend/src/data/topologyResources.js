// Public topology resources are loaded from /api/topology/resources.
// This module only keeps compatibility helpers for legacy projects and labels.
export const TOPOLOGY_RESOURCE_GROUPS = []
export const TOPOLOGY_RESOURCE_BY_TYPE = Object.create(null)

export function cloneTopologyResourceGroups() {
  return []
}

export function normalizeTopologyCategory(category) {
  return category === '其他设备' ? '其他' : category || '其他'
}

export function inferTopologyResourceType(resource = {}) {
  const value = `${resource.type || resource.deviceType || ''} ${resource.englishName || ''} ${resource.name || ''}`.toLowerCase()
  if (/core.?switch|核心交换/.test(value)) return 'core-switch'
  if (/layer.?3|三层交换/.test(value)) return 'layer3-switch'
  if (/access.?switch|接入交换/.test(value)) return 'access-switch'
  if (/router|路由/.test(value)) return 'router'
  if (/firewall|防火墙/.test(value)) return 'firewall'
  if (/ids.?ips|入侵检测|入侵防御/.test(value)) return 'ids-ips'
  if (/vpn/.test(value)) return 'vpn-gateway'
  if (/wireless.?ap|\bap\b|无线/.test(value)) return 'wireless-ap'
  if (/ac.?controller|ac控制器/.test(value)) return 'ac-controller'
  if (/server|服务器/.test(value)) return 'server'
  if (/database|数据库/.test(value)) return 'database'
  if (/nas/.test(value)) return 'nas'
  if (/laptop|笔记本/.test(value)) return 'laptop'
  if (/printer|打印机/.test(value)) return 'printer'
  if (/ip.?phone|ip 电话|电话/.test(value)) return 'ip-phone'
  if (/camera|摄像头/.test(value)) return 'camera'
  if (/internet/.test(value)) return 'internet'
  if (/cloud|云/.test(value)) return 'cloud'
  if (/\bpc\b|终端/.test(value)) return 'pc'
  return String(resource.type || resource.deviceType || 'device').toLowerCase().replace(/[^a-z0-9]+/g, '-') || 'device'
}

export function defaultTopologyPorts() {
  // Port capacity belongs to the server-side resource/profile, never the UI.
  return []
}

export function createDefaultDeviceProfiles() {
  return []
}
