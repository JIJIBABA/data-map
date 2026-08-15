import { useEffect, useState, useCallback } from 'react'
import { Table, Input, Space, Button, Modal, message, Form, Popconfirm, Select } from 'antd'
import { useNavigate } from 'react-router-dom'
import { tableApi, projectApi } from '../../services/api'

export default function TableInfoManage() {
  const navigate = useNavigate()
  const [data, setData] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const [projectId, setProjectId] = useState<number | undefined>(undefined)
  const [tableNameKeyword, setTableNameKeyword] = useState('')
  const [projectOptions, setProjectOptions] = useState<any[]>([])
  const [projectFetching, setProjectFetching] = useState(false)
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [editingRecord, setEditingRecord] = useState<any>(null)
  const [form] = Form.useForm()

  const fetchData = useCallback(async () => {
    setLoading(true)
    try {
      const res: any = await tableApi.list({
        projectId,
        tableName: tableNameKeyword || undefined,
      })
      setData(res.data || [])
    } finally {
      setLoading(false)
    }
  }, [projectId, tableNameKeyword])

  useEffect(() => { fetchData() }, [fetchData])

  const fetchProjects = async (keyword?: string) => {
    setProjectFetching(true)
    try {
      const res: any = await projectApi.list(keyword || undefined)
      setProjectOptions((res.data || []).map((p: any) => ({
        label: p.appName,
        value: p.id,
      })))
    } finally {
      setProjectFetching(false)
    }
  }

  const handleSearch = () => fetchData()

  const handleEdit = (record: any) => {
    setEditingRecord(record)
    form.setFieldsValue({ tableComment: record.tableComment })
    setEditModalOpen(true)
  }

  const handleDelete = async (id: number) => {
    await tableApi.delete(id)
    message.success('删除成功')
    fetchData()
  }

  const handleEditOk = async () => {
    const values = await form.validateFields()
    await tableApi.update(editingRecord.id, values.tableComment)
    message.success('修改成功')
    setEditModalOpen(false)
    fetchData()
  }

  const columns = [
    { title: '项目ID', dataIndex: 'projectId', key: 'projectId', width: 80 },
    { title: '表名', dataIndex: 'tableName', key: 'tableName', width: 200 },
    { title: '表含义', dataIndex: 'tableComment', key: 'tableComment' },
    {
      title: '操作', key: 'action', width: 320,
      render: (_: any, record: any) => (
        <Space>
          <Button type="link" onClick={() => navigate(`/tables/${record.id}`)}>查看</Button>
          <Button type="link" onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除?" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" danger>删除</Button>
          </Popconfirm>
          <Button type="link" onClick={() => navigate(`/tables/${record.id}/relations`)}>关联关系</Button>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select
          showSearch
          allowClear
          placeholder="项目名称"
          style={{ width: 220 }}
          value={projectId}
          onChange={(val) => { setProjectId(val); setTableNameKeyword('') }}
          onSearch={fetchProjects}
          onFocus={() => fetchProjects()}
          filterOption={false}
          notFoundContent={projectFetching ? '搜索中...' : '无匹配项目'}
          options={projectOptions}
        />
        <Input.Search
          placeholder="表名（模糊搜索）"
          value={tableNameKeyword}
          onChange={(e) => setTableNameKeyword(e.target.value)}
          onSearch={handleSearch}
          style={{ width: 220 }}
        />
      </Space>
      <Table columns={columns} dataSource={data} rowKey="id" loading={loading} />

      <Modal
        title="编辑表含义"
        open={editModalOpen}
        onOk={handleEditOk}
        onCancel={() => setEditModalOpen(false)}
      >
        <Form form={form}>
          <Form.Item name="tableComment" label="表含义" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
