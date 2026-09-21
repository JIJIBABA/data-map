import { useEffect, useState, useCallback } from 'react'
import {
  Table,
  Input,
  Space,
  Button,
  Modal,
  Form,
  Popconfirm,
  Select,
  Card,
  Typography,
  App,
} from 'antd'
import type { TableColumnsType } from 'antd'
import {
  EyeOutlined,
  EditOutlined,
  DeleteOutlined,
  ApartmentOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { tableApi, projectApi } from '../../services/api'

const { Title, Text } = Typography

export default function TableInfoManage() {
  const navigate = useNavigate()
  const { message } = App.useApp()
  const [data, setData] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const [projectId, setProjectId] = useState<number | undefined>(undefined)
  const [tableNameKeyword, setTableNameKeyword] = useState('')
  const [projectOptions, setProjectOptions] = useState<any[]>([])
  const [projectMap, setProjectMap] = useState<Record<number, string>>({})
  const [projectFetching, setProjectFetching] = useState(false)
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [editingRecord, setEditingRecord] = useState<any>(null)
  const [form] = Form.useForm()
  const [saving, setSaving] = useState(false)

  const fetchData = useCallback(async () => {
    setLoading(true)
    try {
      const res: any = await tableApi.list({
        projectId,
        tableName: tableNameKeyword || undefined,
      })
      setData(res.data || [])
    } catch (e: any) {
      message.error(e?.message || '加载表列表失败')
    } finally {
      setLoading(false)
    }
  }, [projectId, tableNameKeyword, message])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  const fetchProjects = useCallback(async (keyword?: string) => {
    setProjectFetching(true)
    try {
      const res: any = await projectApi.list(keyword || undefined)
      const list = res.data || []
      setProjectOptions(list.map((p: any) => ({ label: p.appName, value: p.id })))
      setProjectMap((prev) => {
        const next = { ...prev }
        list.forEach((p: any) => {
          next[p.id] = p.appName
        })
        return next
      })
    } catch (e: any) {
      message.error(e?.message || '加载项目列表失败')
    } finally {
      setProjectFetching(false)
    }
  }, [message])

  // 初次加载项目映射，用于表格中展示项目名称
  useEffect(() => {
    fetchProjects()
  }, [fetchProjects])

  const handleSearch = () => fetchData()

  const handleEdit = (record: any) => {
    setEditingRecord(record)
    form.setFieldsValue({ tableComment: record.tableComment })
    setEditModalOpen(true)
  }

  const handleDelete = async (id: number) => {
    try {
      await tableApi.delete(id)
      message.success('删除成功')
      fetchData()
    } catch (e: any) {
      message.error(e?.message || '删除失败')
    }
  }

  const handleEditOk = async () => {
    let values: any
    try {
      values = await form.validateFields()
    } catch {
      return // 校验失败，antd 已内联提示
    }
    setSaving(true)
    try {
      await tableApi.update(editingRecord.id, values.tableComment)
      message.success('修改成功')
      setEditModalOpen(false)
      fetchData()
    } catch (e: any) {
      message.error(e?.message || '修改失败')
    } finally {
      setSaving(false)
    }
  }

  const columns: TableColumnsType<any> = [
    {
      title: '项目',
      dataIndex: 'projectId',
      key: 'projectId',
      width: 160,
      sorter: (a: any, b: any) =>
        (projectMap[a.projectId] || '').localeCompare(projectMap[b.projectId] || ''),
      render: (id: number) =>
        projectMap[id] || <Text type="secondary">{id}</Text>,
    },
    {
      title: '表名',
      dataIndex: 'tableName',
      key: 'tableName',
      width: 240,
      ellipsis: true,
      sorter: (a: any, b: any) => String(a.tableName).localeCompare(String(b.tableName)),
      render: (v: string) => (
        <span className="font-mono" style={{ fontSize: 13 }}>
          {v}
        </span>
      ),
    },
    {
      title: '表含义',
      dataIndex: 'tableComment',
      key: 'tableComment',
      width: 200,
      ellipsis: true,
      sorter: (a: any, b: any) => String(a.tableComment).localeCompare(String(b.tableComment)),
    },
    {
      title: '操作',
      key: 'action',
      width: 300,
      fixed: 'right',
      render: (_: any, record: any) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => navigate(`/tables/${record.id}`)}
          >
            查看
          </Button>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Popconfirm title="确定删除该表？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
          <Button
            type="link"
            size="small"
            icon={<ApartmentOutlined />}
            onClick={() => navigate(`/tables/${record.id}/relations`)}
          >
            关联关系
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>
          表基本信息管理
        </Title>
        <Text type="secondary">维护表含义注释，查看字段与关联关系</Text>
      </div>

      <Card style={{ marginBottom: 16 }} styles={{ body: { paddingBottom: 8 } }}>
        <Space wrap>
          <Select
            showSearch
            allowClear
            placeholder="项目名称"
            style={{ width: 220 }}
            value={projectId}
            onChange={(val) => {
              setProjectId(val)
              setTableNameKeyword('')
            }}
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
            allowClear
          />
        </Space>
      </Card>

      <Card styles={{ body: { padding: 0 } }}>
        <Table
          columns={columns}
          dataSource={data}
          rowKey="id"
          loading={loading}
          scroll={{ x: 900 }}
        />
      </Card>

      <Modal
        title="编辑表含义"
        open={editModalOpen}
        onOk={handleEditOk}
        onCancel={() => setEditModalOpen(false)}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
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
