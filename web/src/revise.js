export const FIELDS = {
  asset: [
    ['assetName', '资产名称'], ['assetCategory', '资产类别'], ['bookValue', '账面价值（万元）'],
    ['appraisedValue', '评估值（万元）'], ['assessmentDate', '评估基准日'], ['disposalMode', '处置方式'],
    ['exemption', '豁免情形'], ['resolutionNo', '决策决议编号'], ['listingPrice', '拟挂牌价（万元）'],
    ['dealPrice', '成交价（万元）']
  ],
  guarantee: [
    ['guaranteeee', '被担保方'], ['guaranteeType', '担保类型'], ['guaranteeAmount', '本次担保金额（万元）'],
    ['balanceRatio', '担保余额占净资产比例'], ['guaranteeeeRating', '被担保方评级'],
    ['isHoldingSub', '是否本集团控股子企业'], ['counterGuarantee', '是否落实反担保'], ['resolutionNo', '决策决议编号']
  ],
  lease: [
    ['assetName', '租赁资产名称'], ['assetType', '资产类型'], ['pricingMode', '定价方式'],
    ['leaseTermMonths', '租期（月）'], ['monthlyRent', '租金（万元/月）'], ['marketRentRef', '市场参考租金（万元/月）'],
    ['isRenewal', '是否到期续签'], ['resolutionNo', '决策决议编号']
  ]
}

const SCENARIO_NAME = { asset: '资产处置', guarantee: '对外担保', lease: '资产租赁' }

export function scenarioName(code) {
  return SCENARIO_NAME[code] || code
}

function todayStr() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function hitWhy(list) {
  return list.map(h => `${h.code} ${h.name}`).join('；')
}

export function reviseForm(scenario, form, hitList) {
  const next = JSON.parse(JSON.stringify(form || {}))
  const reasons = {}
  const mark = (key, value, why) => {
    if (typeof value === 'number' && typeof next[key] === 'number' && Math.abs(next[key] - value) < 1e-9) return
    if (next[key] === value) return
    next[key] = value
    reasons[key] = why
  }
  const picked = (pred) => (hitList || []).filter(pred)
  if (scenario === 'asset') {
    const enter = picked(h => h.code === 'R1' || (h.name || '').includes('进场'))
    const reassess = picked(h => h.code === 'R2' || ((h.name || '').includes('评估') && (h.name || '').includes('有效期')))
    const listing = picked(h => h.code === 'R3' || (h.name || '').includes('挂牌价'))
    const resolution = picked(h => h.code === 'R4' || (h.name || '').includes('决议') || (h.name || '').includes('决策'))
    const deal = picked(h => h.code === 'R5' || (h.name || '').includes('成交'))
    if (enter.length) mark('disposalMode', '进场挂牌转让', hitWhy(enter))
    if (reassess.length) mark('assessmentDate', todayStr(), hitWhy(reassess))
    if (listing.length) mark('listingPrice', Number(next.appraisedValue || 0), hitWhy(listing))
    if (resolution.length && !String(next.resolutionNo || '').trim()) mark('resolutionNo', '待补-党委会/董事会决议', hitWhy(resolution))
    if (deal.length) mark('dealPrice', Math.round(Number(next.listingPrice || 0) * 0.9 * 100) / 100, hitWhy(deal))
  } else if (scenario === 'guarantee') {
    const ratio = picked(h => h.code === 'G1' || (h.name || '').includes('余额'))
    const rating = picked(h => h.code === 'G2' || (h.name || '').includes('资质') || (h.name || '').includes('评级'))
    const resolution = picked(h => h.code === 'G3' || (h.name || '').includes('决议') || (h.name || '').includes('决策'))
    const counter = picked(h => h.code === 'G4' || (h.name || '').includes('反担保'))
    if (ratio.length) mark('balanceRatio', 0.5, hitWhy(ratio))
    if (rating.length) mark('guaranteeeeRating', 'BBB', hitWhy(rating))
    if (counter.length) mark('counterGuarantee', true, hitWhy(counter))
    if (resolution.length && !String(next.resolutionNo || '').trim()) mark('resolutionNo', '待补-党委会/董事会决议', hitWhy(resolution))
  } else if (scenario === 'lease') {
    const auction = picked(h => h.code === 'L1' || (h.name || '').includes('竞价'))
    const term = picked(h => h.code === 'L2' || (h.name || '').includes('租期'))
    const rent = picked(h => h.code === 'L3' || (h.name || '').includes('租金'))
    const renewal = picked(h => h.code === 'L4' || (h.name || '').includes('续签'))
    if (auction.length) mark('pricingMode', '公开竞价', hitWhy(auction))
    if (term.length) mark('leaseTermMonths', 60, hitWhy(term))
    if (rent.length) mark('monthlyRent', Number(next.marketRentRef || 0), hitWhy(rent))
    if (renewal.length) {
      mark('isRenewal', false, hitWhy(renewal))
      if (!String(next.resolutionNo || '').trim()) mark('resolutionNo', '待补-重新履行决策决议', hitWhy(renewal))
    }
  }
  return { next, reasons }
}

export function showValue(key, value) {
  if (value === null || value === undefined || value === '') return '未填'
  if (key === 'exemption') return value ? '符合国家规定非公开协议情形' : '无'
  if (key === 'isHoldingSub' || key === 'isRenewal') return value ? '是' : '否'
  if (key === 'counterGuarantee') return value ? '已落实' : '未落实'
  if (key === 'balanceRatio') return `${Number(value).toFixed(2)}（${Math.round(Number(value) * 100)}%）`
  if (typeof value === 'number') return String(value)
  return String(value)
}

export function revisionRows(scenario, form, hitList) {
  const { next, reasons } = reviseForm(scenario, form, hitList)
  return (FIELDS[scenario] || []).map(([key, label]) => ({
    key, label,
    before: showValue(key, form?.[key]),
    after: showValue(key, next[key]),
    changed: !!reasons[key],
    why: reasons[key] || ''
  }))
}
