// Retrying reads cannot duplicate a conversation or a submitted user message.
export async function retryAssistantRead(read, wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))) {
  try {
    return await read()
  } catch (error) {
    const status = Number(error?.status)
    const transient = status === 0 || status >= 500 || error?.code === 'NETWORK_ERROR'
    if (!transient) throw error
    await wait(400)
    return read()
  }
}

export function assistantFailureState(error) {
  if (['MODEL_UNAVAILABLE', 'MODEL_DISABLED', 'API_KEY_NOT_CONFIGURED', 'MODEL_ROUTE_NOT_FOUND'].includes(error?.code)) return 'unavailable'
  if (['AUTH_REQUIRED', 'TOKEN_EXPIRED', 'UNAUTHORIZED', 'FORBIDDEN'].includes(error?.code) || [401, 403].includes(error?.status)) return 'error'
  return 'degraded'
}
