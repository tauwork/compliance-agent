import { reactive } from 'vue'
import { getBootstrap } from './api'

export const session = reactive({
  ready: false,
  user: null,
  menus: [],
  libraryIds: [],
  users: [],
  catalog: []
})

export async function loadSession() {
  const data = await getBootstrap()
  session.user = data.user
  session.menus = data.menus || []
  session.libraryIds = data.libraryIds || []
  session.users = data.users || []
  session.catalog = data.catalog || []
  session.ready = true
  if (data.user?.id) localStorage.setItem('pcm-user-id', String(data.user.id))
  return session
}

export async function setCurrentUser(id) {
  localStorage.setItem('pcm-user-id', String(id))
  return loadSession()
}
