import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Table,
  Button,
  Modal,
  Input,
  Form,
  Space,
  Tag,
  Descriptions,
  Divider,
  Empty,
  Card,
  Typography,
  Spin,
  Select,
  App,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { ArrowLeftOutlined, ArrowDownOutlined, DownloadOutlined } from '@ant-design/icons'
import { fieldApi, tableApi } from '../../services/api'
import { OP_COLORS, LAYER_COLORS } from '../../theme'
import * as XLSX from 'xlsx'
import { saveAs } from 'file-saver'

const { Title, Text } = Typography

function parseJson(s: any): any {
  if (!s) return null
  if (typeof s !== 'string') return s
  try {
    return JSON.parse(s)
  } catch {
    return null
  }
}

export default function TableDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { message } = App.useApp()
  const [tableInfo, setTableInfo] = useState<any>(null)
  const [fields, setFields] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const [fieldKeyword, setFieldKeyword] = useState('')
  const [editFieldModalOpen, setEditFieldModalOpen] = useState(false)
  const [editingField, setEditingField] = useState<any>(null)
  const [form] = Form.useForm()
  const [saving, setSaving] = useState(false)

  // 下钻弹框
  const [drillField, setDrillField] = useState<any>(null)
  const [drillOpen, setDrillOpen] = useState(false)
  const [scenarios, setScenarios] = useState<any[]>([])
  const [scenarioLoading, setScenarioLoading] = useState(false)
  const [selected, setSelected] = useState<any>(null)
  const [scenarioType, setScenarioType] = useState<string>()

  // 按场景类型筛选下钻列表
  const scenarioTypes = Array.from(
    new Set(scenarios.map((s: any) => s.operationType).filter(Boolean)),
  )
  const filteredScenarios = scenarioType
    ? scenarios.filter((s: any) => s.operationType === scenarioType)
    : scenarios

  const fetchData = async () => {
    setLoading(true)
    try {
      const res: any = await tableApi.getById(Number(id))
      setTableInfo(res.data)
      const fres: any = await fieldApi.listByTable(Number(id))
      setFields(fres.data || [])
    } catch (e: any) {
      message.error(e?.message || '加载表详情失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchData()
  }, [id])

  const handleDrillDown = async (record: any) => {
    setDrillField(record)
    setDrillOpen(true)
    setScenarioLoading(true)
    setScenarios([])
    setSelected(null)
    setScenarioType(undefined)
    try {
      const res: any = await fieldApi.usageScenarios(record.id)
      const list = res.data || []
      setScenarios(list)
      if (list.length > 0) setSelected(list[0])
    } catch (e: any) {
      message.error(e?.message || '加载使用场景失败')
    } finally {
      setScenarioLoading(false)
    }
  }

  const handleExportEntry = () => {
    if (!filteredScenarios || filteredScenarios.length === 0) {
      message.warning('暂无场景可导出')
      return
    }
    const rows = filteredScenarios.map((s: any) => {
      const e = parseJson(s.entryInfo)
      return {
        场景: s.operationType || '',
        方法: s.methodName || '',
        方法注释: s.methodDescription || '',
        入口类型: e?.type || '',
        接口: e?.apiName || '',
        HTTP方法: e?.httpMethod || '',
        路径: e?.path || '',
        队列: e?.queue || '',
        Cron: e?.cron || '',
        源API: s.sourceApiName || '',
      }
    })
    const ws = XLSX.utils.json_to_sheet(rows)
    // 列宽：方法/路径/接口加宽，其余默认
    ws['!cols'] = [
      { wch: 10 }, { wch: 50 }, { wch: 30 }, { wch: 12 }, { wch: 36 },
      { wch: 10 }, { wch: 40 }, { wch: 24 }, { wch: 24 }, { wch: 24 },
    ]
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '入口信息')
    const wbout = XLSX.write(wb, { type: 'array', bookType: 'xlsx' })
    saveAs(new Blob([wbout], { type: 'application/octet-stream' }),
      `${drillField?.fieldName || '场景'}_入口信息.xlsx`)
  }

  const handleEditField = (record: any) => {
    setEditingField(record)
    form.setFieldsValue({ fieldComment: record.fieldComment })
    setEditFieldModalOpen(true)
  }

  const handleEditFieldOk = async () => {
    let values: any
    try {
      values = await form.validateFields()
    } catch {
      return // 校验失败，antd 已内联提示
    }
    setSaving(true)
    try {
      await fieldApi.update(editingField.id, values.fieldComment)
      message.success('修改成功')
      setEditFieldModalOpen(false)
      fetchData()
    } catch (e: any) {
      message.error(e?.message || '修改失败')
    } finally {
      setSaving(false)
    }
  }

  const columns: TableColumnsType<any> = [
    {
      title: '字段名',
      dataIndex: 'fieldName',
      key: 'fieldName',
      width: 180,
      sorter: (a: any, b: any) => String(a.fieldName).localeCompare(String(b.fieldName)),
      render: (v: string) => (
        <span className="font-mono" style={{ fontSize: 13 }}>
          {v}
        </span>
      ),
    },
    {
      title: '注释',
      dataIndex: 'fieldComment',
      key: 'fieldComment',
      sorter: (a: any, b: any) => String(a.fieldComment).localeCompare(String(b.fieldComment)),
    },
    {
      title: '类型',
      dataIndex: 'fieldType',
      key: 'fieldType',
      width: 120,
      sorter: (a: any, b: any) => String(a.fieldType).localeCompare(String(b.fieldType)),
      render: (v: string) => (
        <span className="font-mono" style={{ fontSize: 12 }}>
          {v}
        </span>
      ),
    },
    {
      title: '手工修改',
      dataIndex: 'fieldCommentManual',
      key: 'fieldCommentManual',
      width: 100,
      render: (v: number) =>
        v === 1 ? <Tag color="orange">是</Tag> : <Tag>否</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_: any, record: any) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => handleDrillDown(record)}>
            下钻
          </Button>
          <Button type="link" size="small" onClick={() => handleEditField(record)}>
            编辑
          </Button>
        </Space>
      ),
    },
  ]

  // 上部分：场景/方法 列表
  const listColumns: TableColumnsType<any> = [
    {
      title: '场景',
      dataIndex: 'operationType',
      key: 'operationType',
      width: 90,
      render: (v: string) => <Tag color={OP_COLORS[v] || 'default'}>{v}</Tag>,
    },
    {
      title: '方法',
      dataIndex: 'methodName',
      key: 'methodName',
      render: (v: string) => (
        <span className="font-mono" style={{ fontSize: 12, wordBreak: 'break-all' }}>
          {v}
        </span>
      ),
    },
  ]

  const renderEntryDetail = () => {
    if (!selected) return null
    const e = parseJson(selected.entryInfo)
    if (!e) {
      return selected.sourceApiName ? (
        <span style={{ fontSize: 12 }}>{selected.sourceApiName}</span>
      ) : (
        <Text type="secondary">无入口信息</Text>
      )
    }
    return (
      <Descriptions size="small" column={3} bordered>
        <Descriptions.Item label="入口类型">
          <Tag color={LAYER_COLORS[e.type] || 'default'}>{e.type}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="接口" span={2}>
          {e.apiName || '-'}
        </Descriptions.Item>
        {e.httpMethod && (
          <Descriptions.Item label="HTTP 方法">{e.httpMethod}</Descriptions.Item>
        )}
        {e.path && (
          <Descriptions.Item label="路径" span={2}>
            {e.path}
          </Descriptions.Item>
        )}
        {e.queue && (
          <Descriptions.Item label="队列" span={3}>
            {e.queue}
          </Descriptions.Item>
        )}
        {e.cron && (
          <Descriptions.Item label="Cron" span={3}>
            {e.cron}
          </Descriptions.Item>
        )}
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
              <Tag color={LAYER_COLORS[step.layer] || 'default'} style={{ marginRight: 0 }}>
                {step.layer || '?'}
              </Tag>
              <span className="font-mono" style={{ fontSize: 12 }}>
                {step.className}.{step.methodName}
              </span>
            </div>
            {i < chain.length - 1 && (
              <div
                style={{
                  textAlign: 'center',
                  color: 'rgba(0, 0, 0, 0.45)',
                  lineHeight: '16px',
                }}
              >
                <ArrowDownOutlined style={{ fontSize: 10 }} />
              </div>
            )}
          </div>
        ))}
      </div>
    )
  }

  if (!tableInfo) {
    return (
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: 320,
        }}
      >
        <Spin size="large" />
      </div>
    )
  }

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 12 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/tables/list')}>
          返回
        </Button>
        <div>
          <Title level={4} style={{ margin: 0 }}>
            {tableInfo.tableName}
          </Title>
          <Text type="secondary">{tableInfo.tableComment || '无表含义'}</Text>
        </div>
      </div>

      <Card styles={{ body: { padding: 0 } }}>
        <div style={{ padding: '12px 16px 0' }}>
          <Input.Search
            placeholder="搜索字段名或注释"
            allowClear
            value={fieldKeyword}
            onChange={(e) => setFieldKeyword(e.target.value)}
            style={{ width: 280 }}
          />
        </div>
        <Table
          columns={columns}
          dataSource={
            fieldKeyword.trim()
              ? fields.filter((f: any) =>
                  (f.fieldName || '').toLowerCase().includes(fieldKeyword.toLowerCase()) ||
                  (f.fieldComment || '').toLowerCase().includes(fieldKeyword.toLowerCase())
                )
              : fields
          }
          rowKey="id"
          loading={loading}
          pagination={fields.length > 20 ? { pageSize: 20 } : false}
          scroll={{ x: 700 }}
        />
      </Card>

      <Modal
        title={`${drillField?.fieldName || ''} 的使用场景`}
        open={drillOpen}
        onCancel={() => setDrillOpen(false)}
        footer={null}
        width={1000}
      >
        {/* 上部分：场景/方法/入口 列表 */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
          <Space size={8}>
            <Select
              placeholder="按场景筛选"
              allowClear
              value={scenarioType}
              onChange={(v?: string) => {
                setScenarioType(v)
                if (v && selected && selected.operationType !== v) setSelected(null)
              }}
              style={{ width: 160 }}
              options={scenarioTypes.map((t) => ({ label: t, value: t }))}
            />
            <Text type="secondary" style={{ fontSize: 12 }}>
              点击行查看入口与链路详情
            </Text>
          </Space>
          <Button
            type="primary"
            ghost
            size="small"
            icon={<DownloadOutlined />}
            onClick={handleExportEntry}
            disabled={!filteredScenarios.length}
          >
            导出入口信息
          </Button>
        </div>
        <Table
          columns={listColumns}
          dataSource={filteredScenarios}
          rowKey="id"
          size="small"
          loading={scenarioLoading}
          pagination={filteredScenarios.length > 8 ? { pageSize: 8 } : false}
          onRow={(r) => ({
            onClick: () => setSelected(r),
            tabIndex: 0,
            onKeyDown: (e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                setSelected(r)
              }
            },
            style: { cursor: 'pointer' },
          })}
          rowClassName={(r) => (selected && r.id === selected.id ? 'ant-table-row-selected' : '')}
        />

        <Divider style={{ margin: '12px 0' }} />

        {/* 下部分：入口 + 链路信息 */}
        <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
          {selected ? '入口与链路信息' : '点击上方行查看详情'}
        </div>
        {selected && (
          <>
            <div style={{ marginBottom: 12 }}>{renderEntryDetail()}</div>
            <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 4 }}>调用链路</div>
            {renderChainDetail()}
          </>
        )}
      </Modal>

      <Modal
        title="编辑字段注释"
        open={editFieldModalOpen}
        onOk={handleEditFieldOk}
        onCancel={() => setEditFieldModalOpen(false)}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
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
