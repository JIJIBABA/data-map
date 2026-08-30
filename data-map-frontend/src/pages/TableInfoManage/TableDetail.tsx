import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Table, Button, Modal, Input, Form, message, Space, Tag } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { fieldApi, tableApi } from '../../services/api'

const OP_COLORS: Record<string, string> = {
  READ: 'blue',
  WRITE: 'green',
  UPDATE: 'orange',
  DELETE: 'red',
  UNRESOLVED: 'default',
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
    try {
      const res: any = await fieldApi.usageScenarios(record.id)
      setScenarios(res.data || [])
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

  const renderEntry = (s: any) => {
    const e = parseJson(s.entryInfo)
    const apiName = s.sourceApiName
    if (!e) {
      return apiName ? <span style={{ fontSize: 12 }}>{shortName(apiName)}</span> : <span style={{ color: '#999' }}>—</span>
    }
    const detail = e.path || e.queue || e.cron || ''
    return (
      <div>
        <Tag style={{ marginRight: 4 }}>{e.type}</Tag>
        <span style={{ fontSize: 12 }}>{detail || shortName(e.apiName || apiName || '')}</span>
      </div>
    )
  }

  const renderChain = (s: any) => {
    const chain = parseJson(s.callChain)
    if (!Array.isArray(chain) || chain.length === 0) {
      return <span style={{ color: '#999' }}>—</span>
    }
    return (
      <div>
        {chain.map((step: any, i: number) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center' }}>
            {i > 0 && <span style={{ color: '#999', marginRight: 4 }}>↓</span>}
            <span style={{ fontSize: 12 }}>
              <Tag color="blue" style={{ marginRight: 4, fontSize: 10 }}>{step.layer || ''}</Tag>
              {shortName(step.className)}.{step.methodName}
            </span>
          </div>
        ))}
      </div>
    )
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

  const scenarioColumns = [
    {
      title: '场景', dataIndex: 'operationType', key: 'operationType', width: 90,
      render: (v: string) => <Tag color={OP_COLORS[v] || 'default'}>{v}</Tag>,
    },
    {
      title: '方法含义', dataIndex: 'methodDescription', key: 'methodDescription', width: 160,
      render: (v: string) => v || <span style={{ color: '#999' }}>—</span>,
    },
    {
      title: '直接修改方法', dataIndex: 'methodName', key: 'methodName',
      render: (v: string) => <span style={{ fontSize: 12 }}>{v}</span>,
    },
    { title: '方法入口', key: 'entry', width: 200, render: (_: any, r: any) => renderEntry(r) },
    { title: '调用链路', key: 'chain', render: (_: any, r: any) => renderChain(r) },
  ]

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
        <Table
          columns={scenarioColumns}
          dataSource={scenarios}
          rowKey="id"
          size="small"
          loading={scenarioLoading}
          pagination={scenarios.length > 10 ? { pageSize: 10 } : false}
        />
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
