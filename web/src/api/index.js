import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 60000 })
http.interceptors.request.use(cfg => {
  cfg.headers['X-User-Id'] = localStorage.getItem('pcm-user-id') || '1'
  return cfg
})

// 统计/链
export const getOverview = () => http.get('/stats/overview').then(r => r.data)
export const getPrecheckFeed = () => http.get('/precheck/feed').then(r => r.data)
export const verifyChain = () => http.get('/audit/chain/verify').then(r => r.data)

// 规则
export const getRules = (status) => http.get('/rules', { params: { status } }).then(r => r.data)
export const ruleTransition = (id, action) => http.post(`/rules/${id}/transition`, null, { params: { action } }).then(r => r.data)
export const toggleRule = (id) => http.put(`/rules/${id}/toggle`).then(r => r.data)

// 政策
export const getPolicyTree = () => http.get('/policies/tree').then(r => r.data)
export const createCategory = (name) => http.post('/policies/categories', { name }).then(r => r.data)
export const createLibrary = (body) => http.post('/policies/libraries', body).then(r => r.data)
export const getPolicies = (params) => http.get('/policies', { params }).then(r => r.data)
export const getPolicy = (id) => http.get(`/policies/${id}`).then(r => r.data)
export const createPolicy = (p) => http.post('/policies', p).then(r => r.data)
export const updatePolicy = (id, p) => http.put(`/policies/${id}`, p).then(r => r.data)
export const getChunks = (id) => http.get(`/policies/${id}/chunks`).then(r => r.data)
export const rebuildChunks = (id) => http.post(`/policies/${id}/chunks/rebuild`).then(r => r.data)
export const saveChunk = (id, chunkId, body) => http.put(`/policies/${id}/chunks/${chunkId}`, body).then(r => r.data)
export const deleteChunk = (id, chunkId) => http.delete(`/policies/${id}/chunks/${chunkId}`).then(r => r.data)
export const getQas = (id) => http.get(`/policies/${id}/qas`).then(r => r.data)
export const generateQas = (id, replace) => http.post(`/policies/${id}/qas/generate`, null, { params: { replace } }).then(r => r.data)
export const createQa = (id, body) => http.post(`/policies/${id}/qas`, body).then(r => r.data)
export const deleteQa = (id, qaId) => http.delete(`/policies/${id}/qas/${qaId}`).then(r => r.data)
export const getPolicyRules = (id) => http.get(`/policies/${id}/rules`).then(r => r.data)
export const decompose = (id) => http.post(`/agent/decompose/${id}`).then(r => r.data)

// 系统管理
export const getBootstrap = () => http.get('/system/bootstrap').then(r => r.data)
export const createUser = (body) => http.post('/system/users', body).then(r => r.data)
export const updateUser = (id, body) => http.put(`/system/users/${id}`, body).then(r => r.data)
export const getRoles = () => http.get('/system/roles').then(r => r.data)
export const getRoleGrants = (id) => http.get(`/system/roles/${id}`).then(r => r.data)
export const saveRoleGrants = (id, body) => http.put(`/system/roles/${id}/grants`, body).then(r => r.data)

// 预审/申报（三场景）
export const submitPrecheck = (payload) => http.post('/precheck', payload).then(r => r.data)
export const submitGuarantee = (payload) => http.post('/precheck/guarantee', payload).then(r => r.data)
export const submitLease = (payload) => http.post('/precheck/lease', payload).then(r => r.data)
export const getDeclarations = () => http.get('/declarations').then(r => r.data)
export const getGuaranteeDeclarations = () => http.get('/guarantee-declarations').then(r => r.data)
export const getLeaseDeclarations = () => http.get('/lease-declarations').then(r => r.data)

// 全量巡检
export const runInspection = (scenario) => http.post('/inspections', null, { params: { scenario } }).then(r => r.data)
export const listInspections = () => http.get('/inspections').then(r => r.data)
export const getInspection = (id) => http.get(`/inspections/${id}`).then(r => r.data)

// 风险事件
export const getRiskEvents = (params) => http.get('/risk-events', { params }).then(r => r.data)
export const updateRiskStatus = (id, status) => http.put(`/risk-events/${id}/status`, null, { params: { status } }).then(r => r.data)

// 工单
export const getTickets = (params) => http.get('/tickets', { params }).then(r => r.data)
export const getTicketStats = () => http.get('/tickets/stats').then(r => r.data)
export const createTicket = (t) => http.post('/tickets', t).then(r => r.data)
export const ticketTransition = (id, action, operator, comment) =>
  http.post(`/tickets/${id}/transition`, null, { params: { action, operator, comment } }).then(r => r.data)

// Agent
export const analyze = (riskEventId) => http.post(`/agent/analyze/${riskEventId}`).then(r => r.data)
export const listAnalyze = (riskEventId) => http.get('/agent/analyze', { params: { riskEventId } }).then(r => r.data)
export const approveAnalyze = (id, approved, operator) =>
  http.post(`/agent/analyze/${id}/approve`, null, { params: { approved, operator } }).then(r => r.data)
export const chat = (question) => http.post('/agent/chat', { question }).then(r => r.data)
