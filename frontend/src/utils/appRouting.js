export const APP_MODE_STORAGE_KEY = 'app_mode'
export const AUTH_REDIRECT_STORAGE_KEY = 'auth_redirect'

const USER_PAGE_PATHS = {
  'new-chat': '/new',
  'chat-search': '/chat-search',
  projects: '/projects',
  requirements: '/requirements',
  files: '/files',
  topology: '/topology',
  report: '/report',
  risk: '/risk',
  export: '/export',
  versions: '/versions'
}

function normalizePathname(pathname) {
  const path = typeof pathname === 'string' ? pathname.trim() : ''
  if (!path || path === '/') return '/'
  return path.replace(/\/+$/, '') || '/'
}

export function parseAppRoute(pathname, adminPages) {
  const path = normalizePathname(pathname)
  const adminPath = path.match(/^\/admin(?:\/([^/]+))?$/)

  if (adminPath) {
    const requestedPage = adminPath[1]
    const normalizedPage = requestedPage === 'knowledge'
      ? 'assets'
      : ['blacklist', 'security-events'].includes(requestedPage)
        ? 'security'
        : ['icon-categories', 'line-styles', 'system-assets'].includes(requestedPage)
          ? 'devices'
          : ['system-rules', 'system', 'monitoring'].includes(requestedPage)
            ? 'system-management'
            : requestedPage === 'rag'
              ? 'rag-config'
            : requestedPage
    const adminPage = adminPages.includes(normalizedPage) ? normalizedPage : 'dashboard'
    if (requestedPage && !adminPages.includes(normalizedPage)) {
      return { mode: 'not-found', adminPage: 'dashboard', chatId: null, userPage: 'new-chat' }
    }
    return { mode: 'admin', adminPage, chatId: null, userPage: 'new-chat' }
  }

  const publicConversationPath = path.match(/^\/c\/([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})$/i)
  const legacyChatPath = path.match(/^\/chat\/([A-Za-z0-9_-]{32,})$/)
  const publicId = publicConversationPath?.[1] || legacyChatPath?.[1] || null
  if (publicId) {
    return { mode: 'user', adminPage: 'dashboard', chatId: publicId, userPage: 'chat' }
  }
  if (path === '/new' || path === '/chat' || path === '/chat/new') {
    return { mode: 'user', adminPage: 'dashboard', chatId: null, userPage: 'new-chat' }
  }
  if (path.startsWith('/c/') || path.startsWith('/chat/')) {
    return { mode: 'not-found', adminPage: 'dashboard', chatId: null, userPage: 'new-chat' }
  }
  const projectPath = path.match(/^\/project\/([^/]+)$/)
  const projectId = projectPath?.[1] || null
  if (projectId && (/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(projectId) || /^\d+$/.test(projectId))) {
    return { mode: 'user', adminPage: 'dashboard', chatId: null, userPage: 'topology', projectId }
  }

  const userPage = Object.entries(USER_PAGE_PATHS).find(([, routePath]) => routePath === path)?.[0]
  if (userPage) {
    return { mode: 'user', adminPage: 'dashboard', chatId: null, userPage }
  }

  if (path !== '/') {
    return { mode: 'not-found', adminPage: 'dashboard', chatId: null, userPage: 'new-chat' }
  }

  return { mode: 'user', adminPage: 'dashboard', chatId: null, userPage: 'new-chat' }
}

export function resolveInitialAppRoute({ pathname, savedMode, adminPages }) {
  const route = parseAppRoute(pathname, adminPages)
  if (route.mode === 'admin') return route

  return normalizePathname(pathname) === '/' && savedMode === 'admin'
    ? { mode: 'admin', adminPage: 'dashboard', chatId: null, userPage: 'new-chat' }
    : route
}

export function resolveAuthenticatedAppRoute({ pathname, savedMode, adminPages, isAdmin }) {
  const normalizedPathname = normalizePathname(pathname)
  if (normalizedPathname === '/login') {
    return isAdmin
      ? { mode: 'admin', adminPage: 'dashboard', chatId: null, userPage: 'new-chat' }
      : { mode: 'user', adminPage: 'dashboard', chatId: null, userPage: 'new-chat' }
  }
  const parsed = parseAppRoute(normalizedPathname, adminPages)
  if (parsed.mode === 'not-found') return parsed
  if (parsed.mode === 'admin' && !isAdmin) return { ...parsed, mode: 'forbidden' }
  if (isAdmin && (normalizedPathname === '/' || normalizedPathname === '/login')) {
    return { mode: 'admin', adminPage: 'dashboard', chatId: null, userPage: 'new-chat' }
  }

  return resolveInitialAppRoute({ pathname: normalizedPathname, savedMode: isAdmin ? savedMode : 'user', adminPages })
}

export function adminPathFor(page) {
  if (page === 'dashboard') return '/admin'
  if (page === 'system-management') return '/admin/system'
  if (page === 'assets') return '/admin/knowledge'
  if (page === 'rag-config') return '/admin/rag'
  return `/admin/${encodeURIComponent(page)}`
}

export function userPathFor(page, projectId = null, chatId = null) {
  if (page === 'chat' && typeof chatId === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(chatId)) return `/c/${encodeURIComponent(chatId)}`
  if (page === 'topology' && (typeof projectId === 'string' || Number(projectId) > 0)) return `/project/${encodeURIComponent(String(projectId))}`
  return USER_PAGE_PATHS[page] || '/'
}

export function isKnownAppPath(pathname, adminPages) {
  // Use the same alias and identifier validation as the route resolver.
  return normalizePathname(pathname) === '/login' || parseAppRoute(pathname, adminPages).mode !== 'not-found'
}

