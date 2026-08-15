import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Table, Button, Modal, Input, Form, message, Space, Tag } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { fieldApi, tableApi } from '../../services/api'

export default function TableDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [tableInfo, setTableInfo] = useState<any>(null)
  const [fields, setFields] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const [editFieldModalOpen, setEditFieldModalOpen] = useState(false)
  const [editingField, setEditingField] = useState<any>(null)
  const [form] = Form.useForm()
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set())
  const [scenarios, setScenarios] = useState<Record<number, any[]>>({})

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

  const handleToggleExpand = async (fieldId: number) => {
    const newExpanded = new Set(expandedRows)
    if (newExpanded.has(fieldId)) {
      newExpanded.delete(fieldId)
    } else {
      newExpanded.add(fieldId)
      if (!scenarios[fieldId]) {
        const res: any = await fieldApi.usageScenarios(fieldId)
        setScenarios((prev) => ({ ...prev, [fieldId]: res.data || [] }))
      }
    }
    setExpandedRows(newExpanded)
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
          <Button type="link" onClick={() => handleToggleExpand(record.id)}>
            {expandedRows.has(record.id) ? '收起' : '下钻'}
          </Button>
          <Button type="link" onClick={() => handleEditField(record)}>编辑</Button>
        </Space>
      ),
    },
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
        expandable={{
          expandedRowRender: (record) => {
            const list = scenarios[record.id] || []
            if (list.length === 0) return <div style={{ padding: 16, color: '#999' }}>无使用场景数据</div>
            return (
              <Table
                dataSource={list}
                rowKey="id"
                pagination={false}
                size="small"
                columns={[
                  { title: '操作类型', dataIndex: 'operationType', key: 'operationType', width: 80 },
                  { title: '场景描述', dataIndex: 'scenarioDescription', key: 'scenarioDescription' },
                  { title: '方法名称', dataIndex: 'methodName', key: 'methodName' },
                  { title: '写入来源表', dataIndex: 'sourceTableName', key: 'sourceTableName' },
                  { title: '写入来源接口', dataIndex: 'sourceApiName', key: 'sourceApiName' },
                ]}
              />
            )
          },
          expandedRowKeys: Array.from(expandedRows),
          showExpandColumn: false,
        }}
      />

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
