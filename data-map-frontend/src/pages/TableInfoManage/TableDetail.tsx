import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Table, Button, Modal, Input, Form, message, Space, Tag, Descriptions, Divider, Empty } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { fieldApi, tableApi } from '../../services/api'

const OP_COLORS: Record<string, string> = {
  READ: 'blue',
  WRITE: 'green',
  UPDATE: 'orange',
  DELETE: 'red',
  UNRESOLVED: 'default',
}

const LAYER_COLORS: Record<string, string> = {
  CONTROLLER: 'blue',
  MQ: 'purple',
  SCHEDULED: 'gold',
  SERVICE: 'cyan',
  MAPPER: 'geekblue',
  OTHER: 'default',
}

function parseJson(s: any): any {
  if (!s) return null
  if (typeof s !== 'string') return s
  try {
    return JSON.parse(s)
  } catch {
    return null
  }
}

function shortName(full: string): string {
  if (!full) return ''
  return full.split('.').pop() || full
}

export default function TableDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [tableInfo, setTableInfo] = useState<any>(null)
  const [fields, setFields] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const [editFieldModalOpen, setEditFieldModalOpen] = useState(false)
  const [editingField, setEditingField] = useState<any>(null)
  const [form] = Form.useForm()

  // 下钻弹框
  const [drillField, setDrillField] = useState<any>(null)
  const [drillOpen, setDrillOpen] = useState(false)
  const [scenarios, setScenarios] = useState<any[]>([])
  const [scenarioLoading, setScenarioLoading] = useState(false)
  const [selected, setSelected] = useState<any>(null)

  const fetchData = async () => {
    setLoading(true)
    try {
      const res: any = await tableApi.getById(Number(id))
      setTableInfo(res.data)
      const fres: any = await fieldApi.listByTable(Number(id))
      setFields(fres.data || [])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchData() }, [id])

  const handleDrillDown = async (record: any) => {
    setDrillField(record)
    setDrillOpen(true)
    setScenarioLoading(true)
    setScenarios([])
    setSelected(null)
    try {
      const res: any = await fieldApi.usageScenarios(record.id)
      const list = res.data || []
      setScenarios(list)
      if (list.length > 0) setSelected(list[0])
    } finally {
      setScenarioLoading(false)
    }
  }

  const handleEditField = (record: any) => {
    setEditingField(record)
    form.setFieldsValue({ fieldComment: record.fieldComment })
    setEditFieldModalOpen(true)
  }

  const handleEditFieldOk = async () => {
    const values = await form.validateFields()
    await fieldApi.update(editingField.id, values.fieldComment)
    message.success('修改成功')
    setEditFieldModalOpen(false)
    fetchData()
  }

  const columns = [
    { title: '字段名', dataIndex: 'fieldName', key: 'fieldName', width: 160 },
    { title: '注释', dataIndex: 'fieldComment', key: 'fieldComment' },
    { title: '类型', dataIndex: 'fieldType', key: 'fieldType', width: 100 },
    {
      title: '手工修改', dataIndex: 'fieldCommentManual', key: 'fieldCommentManual', width: 80,
      render: (v: number) => v === 1 ? <Tag color="orange">是</Tag> : <Tag>否</Tag>,
    },
    {
      title: '操作', key: 'action', width: 160,
      render: (_: any, record: any) => (
        <Space>
          <Button type="link" onClick={() => handleDrillDown(record)}>下钻</Button>
          <Button type="link" onClick={() => handleEditField(record)}>编辑</Button>
        </Space>
      ),
    },
  ]

  // 上部分：场景/方法/入口 列表
  const listColumns = [
    {
      title: '场景', dataIndex: 'operationType', key: 'operationType', width: 90,
      render: (v: string) => <Tag color={OP_COLORS[v] || 'default'}>{v}</Tag>,
    },
    {
      title: '方法', dataIndex: 'methodName', key: 'methodName',
      render: (v: string) => <span style={{ fontSize: 12, wordBreak: 'break-all' }}>{v}</span>,
    },
    {
      title: '入口', key: 'entry', width: 220,
      render: (_: any, r: any) => {
        const e = parseJson(r.entryInfo)
        if (!e) return <span style={{ color: '#999' }}>—</span>
        return (
          <span style={{ fontSize: 12 }}>
            <Tag color={LAYER_COLORS[e.type] || 'default'} style={{ marginRight: 4 }}>{e.type}</Tag>
            {e.path || e.queue || e.cron || shortName(e.apiName || '')}
          </span>
        )
      },
    },
  ]

  const renderEntryDetail = () => {
    if (!selected) return null
    const e = parseJson(selected.entryInfo)
    if (!e) {
      return selected.sourceApiName
        ? <span style={{ fontSize: 12 }}>{selected.sourceApiName}</span>
        : <span style={{ color: '#999' }}>无入口信息</span>
    }
    return (
      <Descriptions size="small" column={3} bordered>
        <Descriptions.Item label="入口类型"><Tag color={LAYER_COLORS[e.type] || 'default'}>{e.type}</Tag></Descriptions.Item>
        <Descriptions.Item label="接口" span={2}>{e.apiName || '—'}</Descriptions.Item>
        {e.httpMethod && <Descriptions.Item label="HTTP 方法">{e.httpMethod}</Descriptions.Item>}
        {e.path && <Descriptions.Item label="路径" span={2}>{e.path}</Descriptions.Item>}
        {e.queue && <Descriptions.Item label="队列" span={3}>{e.queue}</Descriptions.Item>}
        {e.cron && <Descriptions.Item label="Cron" span={3}>{e.cron}</Descriptions.Item>}
      </Descriptions>
    )
  }

  const renderChainDetail = () => {
    if (!selected) return null
    const chain = parseJson(selected.callChain)
    if (!Array.isArray(chain) || chain.length === 0) {
      return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无调用链路" />
    }
    return (
      <div style={{ padding: '4px 0' }}>
        {chain.map((step: any, i: number) => (
          <div key={i}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Tag color={LAYER_COLORS[step.layer] || 'default'} style={{ marginRight: 0 }}>{step.layer || '?'}</Tag>
              <span style={{ fontFamily: 'monospace', fontSize: 12 }}>
                {step.className}.{step.methodName}
              </span>
            </div>
            {i < chain.length - 1 && (
              <div style={{ textAlign: 'center', color: '#999', lineHeight: '16px' }}>↓</div>
            )}
          </div>
        ))}
      </div>
    )
  }

  if (!tableInfo) return <div>加载中...</div>

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/tables/list')}>返回</Button>
        <span style={{ fontSize: 16, fontWeight: 'bold' }}>
          {tableInfo.tableName} — {tableInfo.tableComment}
        </span>
      </Space>
      <Table
        columns={columns}
        dataSource={fields}
        rowKey="id"
        loading={loading}
        pagination={fields.length > 20 ? { pageSize: 20 } : false}
      />

      <Modal
        title={`字段使用场景 — ${drillField?.fieldName || ''}`}
        open={drillOpen}
        onCancel={() => setDrillOpen(false)}
        footer={null}
        width={1000}
      >
        {/* 上部分：场景/方法/入口 列表 */}
        <Table
          columns={listColumns}
          dataSource={scenarios}
          rowKey="id"
          size="small"
          loading={scenarioLoading}
          pagination={scenarios.length > 8 ? { pageSize: 8 } : false}
          onRow={(r) => ({
            onClick: () => setSelected(r),
            style: { cursor: 'pointer' },
          })}
          rowClassName={(r) => (selected && r.id === selected.id ? 'ant-table-row-selected' : '')}
        />

        <Divider style={{ margin: '12px 0' }} />

        {/* 下部分：入口 + 链路信息 */}
        <div style={{ fontSize: 13, fontWeight: 'bold', marginBottom: 8 }}>
          {selected ? '入口与链路信息' : '点击上方行查看详情'}
        </div>
        {selected && (
          <>
            <div style={{ marginBottom: 12 }}>{renderEntryDetail()}</div>
            <div style={{ fontSize: 13, fontWeight: 'bold', marginBottom: 4 }}>调用链路</div>
            {renderChainDetail()}
          </>
        )}
      </Modal>

      <Modal
        title="编辑字段注释"
        open={editFieldModalOpen}
        onOk={handleEditFieldOk}
        onCancel={() => setEditFieldModalOpen(false)}
      >
        <Form form={form}>
          <Form.Item name="fieldComment" label="字段注释" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
