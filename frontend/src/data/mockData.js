export const projects = [
  {
    id: 1,
    name: '三层办公楼网络规划',
    type: '企业办公网络',
    owner: '普通用户',
    status: '已生成拓扑',
    updatedAt: '2026-07-22 08:42',
    mode: 'topo_design',
    terminals: 156,
    files: 4,
    topologyVersion: 'V3'
  },
  {
    id: 2,
    name: '校园实训楼无线覆盖',
    type: '校园网络',
    owner: '普通用户',
    status: '已分析',
    updatedAt: '2026-07-21 19:20',
    mode: 'topo_design',
    terminals: 280,
    files: 2,
    topologyVersion: 'V1'
  },
  {
    id: 3,
    name: '防火墙部署资料问答',
    type: '资料问答',
    owner: '普通用户',
    status: '问答中',
    updatedAt: '2026-07-20 16:05',
    mode: 'qa_only',
    terminals: 0,
    files: 7,
    topologyVersion: '-'
  }
]

export const requirementSummary = {
  scenario: '三层企业办公楼网络',
  scale: '约 150 台办公终端，包含有线办公、无线覆盖、服务器区和访客网络',
  areas: [
    { name: '一楼', detail: '前台、会议室、访客无线、约 48 台终端' },
    { name: '二楼', detail: '综合办公区、打印服务、约 56 台终端' },
    { name: '三楼', detail: '财务部、服务器机房、管理办公室、约 52 台终端' }
  ],
  requirements: ['出口防护', 'VLAN 隔离', '无线覆盖', '服务器区独立', '可扩展接入', '导出工程材料']
}

export const files = [
  { name: '办公楼网络建设需求.docx', type: 'Word', size: '124 KB', status: '已记忆', chunks: 18 },
  { name: '财务部安全隔离说明.pdf', type: 'PDF', size: '312 KB', status: '已记忆', chunks: 24 },
  { name: '无线覆盖点位表.xlsx', type: 'Excel', size: '88 KB', status: '解析完成', chunks: 9 },
  { name: '课程设计要求.txt', type: 'Text', size: '16 KB', status: '已记忆', chunks: 6 }
]

export const reportSections = [
  { title: '项目概述', state: '已生成', summary: '说明项目背景、建设对象和当前设计范围。' },
  { title: '需求分析', state: '已生成', summary: '提取楼层、部门、终端规模、安全隔离和无线覆盖需求。' },
  { title: '总体设计方案', state: '已生成', summary: '采用出口层、安全边界、核心层、接入层和业务区域划分。' },
  { title: '设备需求清单', state: '待复核', summary: '列出路由器、防火墙、核心交换机、接入交换机、AP 和服务器。' },
  { title: '风险与优化建议', state: '已生成', summary: '识别单点故障、访客网络隔离和后续扩展风险。' }
]

export const riskItems = [
  {
    level: '高',
    name: '核心交换机单点故障',
    impact: '核心设备故障会影响办公区、服务器区和无线网络通信。',
    suggestion: '增加备用核心交换机或规划链路冗余。'
  },
  {
    level: '中',
    name: '访客无线访问边界不足',
    impact: '访客终端可能访问内部办公资源。',
    suggestion: '单独设置访客 VLAN，并在防火墙或三层交换机上限制访问策略。'
  },
  {
    level: '中',
    name: '服务器区缺少独立安全策略',
    impact: '办公终端与服务器区边界不清晰，存在横向访问风险。',
    suggestion: '将服务器区划入独立 VLAN 或 DMZ，并配置访问控制。'
  }
]

export const deviceInventory = [
  { type: '出口路由器', count: 1, purpose: '连接运营商互联网出口' },
  { type: '边界防火墙', count: 1, purpose: '实现内外网访问控制和安全防护' },
  { type: '核心交换机', count: 1, purpose: '承担跨区域转发和 VLAN 网关' },
  { type: '接入交换机', count: 6, purpose: '连接办公终端、打印机和 AP' },
  { type: '无线 AP', count: 9, purpose: '覆盖会议室、办公区和访客区域' },
  { type: '服务器', count: 3, purpose: '部署文件、认证和业务服务' }
]

export const adminStats = [
  { label: '用户总数', value: '326', trend: '+18 本周' },
  { label: '项目总数', value: '812', trend: '+42 本周' },
  { label: 'AI 调用次数', value: '9,684', trend: '+12.6%' },
  { label: '文档导出', value: '1,237', trend: '+86 今日' }
]

export const adminUsers = [
  { name: 'student01', role: '普通用户', projects: 8, status: '正常', lastLogin: '2026-07-22 08:31' },
  { name: 'network_lab', role: '普通用户', projects: 18, status: '正常', lastLogin: '2026-07-21 22:12' },
  { name: 'admin', role: '管理员', projects: '-', status: '正常', lastLogin: '2026-07-22 07:58' },
  { name: 'demo_user', role: '普通用户', projects: 3, status: '禁用', lastLogin: '2026-07-18 13:06' }
]

export const promptTemplates = [
  { name: '需求分析模板', type: 'analyze_requirement', version: 'v4', status: '启用' },
  { name: '拓扑生成模板', type: 'generate_topology', version: 'v7', status: '启用' },
  { name: '拓扑问答模板', type: 'qa_topology', version: 'v3', status: '启用' },
  { name: '风险分析模板', type: 'risk_analysis', version: 'v2', status: '停用' }
]

export const logs = [
  { time: '08:46:21', action: 'AI 需求分析', user: 'student01', result: '成功' },
  { time: '08:43:09', action: '拓扑保存', user: 'student01', result: '成功' },
  { time: '08:37:44', action: '上传资料', user: 'network_lab', result: '成功' },
  { time: '08:21:18', action: 'PDF 导出', user: 'student01', result: '成功' }
]

