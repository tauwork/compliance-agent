import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 60000 })

// 统计/链
export const getOverview = () => http.get('/stats/overview').then(r => r.data)
export const getPrecheckFeed = () => http.get('/precheck/feed').then(r => r.data)
export const verifyChain = () => http.get('/audit/chain/verify').then(r => r.data)

// 规则
export const getRules = (status) => http.get('/rules', { params: { status } }).then(r => r.data)
export const ruleTransition = (id, action) => http.post(`/rules/${id}/transition`, null, { params: { action } }).then(r => r.data)

// 政策
export const getPolicies = () => http.get('/policies').then(r => r.data)
export const createPolicy = (p) => http.post('/policies', p).then(r => r.data)
export const getDecomposeView = (id) => http.get(`/policies/${id}/decompose`).then(r => r.data)
export const reviseObligation = (id, o) => http.put(`/policies/obligations/${id}`, o).then(r => r.data)
export const decompose = (id) => http.post(`/agent/decompose/${id}`).then(r => r.data)
export const approveDecompose = (id, approved, comment) =>
  http.post(`/agent/decompose/${id}/approve`, null, { params: { approved, comment } }).then(r => r.data)

// 预审/申报（三场景）
export const submitPrecheck = (payload) => http.post('/precheck', payload).then(r => r.data)
export const submitGuarantee = (payload) => http.post('/precheck/guarantee', payload).then(r => r.data)
export const submitLease = (payload) => http.post('/precheck/lease', payload).then(r => r.data)
export const getDeclarations = () => http.get('/declarations').then(r => r.data)
export const getGuaranteeDeclarations = () => http.get('/guarantee-declarations').then(r => r.data)
export const getLeaseDeclarations = () => http.get('/lease-declarations').then(r => r.data)

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
