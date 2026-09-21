import { useState } from 'react'
import {
  Tabs,
  Input,
  Table,
  Button,
  Select,
  Space,
  Tag,
  Card,
  Typography,
  App,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { queryApi, projectApi, tableApi, fieldApi } from '../../services/api'

const { Text } = Typography

function FieldSearch() {
  const { message } = App.useApp()
  const [keyword, setKeyword] = useState('')
  const [data, setData] = useState<any[]>([])
  const [loading, setLoading] = useState(false)

  const handleSearch = async () => {
    if (!keyword.trim()) return
    setLoading(true)
    try {
      const res: any = await queryApi.searchFields(keyword)
      setData(res.data || [])
    } catch (e: any) {
      message.error(e?.message || '搜索失败')
    } finally {
      setLoading(false)
    }
  }

  const columns: TableColumnsType<any> = [
    {
      title: '项目',
      dataIndex: 'projectName',
      key: 'projectName',
      width: 160,
      sorter: (a: any, b: any) => String(a.projectName).localeCompare(String(b.projectName)),
    },
    {
      title: '表名',
      dataIndex: 'tableName',
      key: 'tableName',
      width: 200,
      sorter: (a: any, b: any) => String(a.tableName).localeCompare(String(b.tableName)),
      render: (v: string) => (
        <span className="font-mono" style={{ fontSize: 13 }}>
          {v}
        </span>
      ),
    },
    {
      title: '字段名',
      dataIndex: 'fieldName',
      key: 'fieldName',
      width: 160,
      sorter: (a: any, b: any) => String(a.fieldName).localeCompare(String(b.fieldName)),
      render: (v: string) => (
        <span className="font-mono" style={{ fontSize: 13 }}>
          {v}
        </span>
      ),
    },
    {
      title: '备注',
      dataIndex: 'fieldComment',
      key: 'fieldComment',
      sorter: (a: any, b: any) => String(a.fieldComment).localeCompare(String(b.fieldComment)),
    },
  ]

  return (
    <div>
      <Card style={{ marginBottom: 16 }} styles={{ body: { paddingBottom: 8 } }}>
        <Space wrap>
          <Input.Search
            placeholder="输入业务关键词搜索字段"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onSearch={handleSearch}
            style={{ width: 320 }}
            allowClear
          />
        </Space>
      </Card>
      <Card styles={{ body: { padding: 0 } }}>
        <Table
          columns={columns}
          dataSource={data}
          rowKey={(r: any) => `${r.tableId ?? 'x'}-${r.fieldName}`}
          loading={loading}
          scroll={{ x: 680 }}
        />
      </Card>
    </div>
  )
}

function PathQuery() {
  const { message } = App.useApp()
  const [projects, setProjects] = useState<any[]>([])
  const [tables, setTables] = useState<any[]>([])
  const [projectId, setProjectId] = useState<number | undefined>()
  const [startTableId, setStartTableId] = useState<number | undefined>()
  const [targetTableId, setTargetTableId] = useState<number | undefined>()
  const [startFieldName, setStartFieldName] = useState<string | undefined>()
  const [targetFieldName, setTargetFieldName] = useState<string | undefined>()
  const [startFields, setStartFields] = useState<any[]>([])
  const [targetFields, setTargetFields] = useState<any[]>([])
  const [paths, setPaths] = useState<any[]>([])
  const [loading, setLoading] = useState(false)

  const loadProjects = async (keyword?: string) => {
    try {
      const res: any = await projectApi.list(keyword)
      setProjects(res.data || [])
    } catch (e: any) {
      message.error(e?.message || '加载项目失败')
    }
  }

  const loadTables = async (pid: number) => {
    try {
      const res: any = await tableApi.list({ projectId: pid })
      setTables(res.data || [])
    } catch (e: any) {
      message.error(e?.message || '加载表失败')
    }
  }

  const handleProjectChange = (val: number) => {
    setProjectId(val)
    setStartTableId(undefined)
    setTargetTableId(undefined)
    setStartFieldName(undefined)
    setTargetFieldName(undefined)
    setStartFields([])
    setTargetFields([])
    loadTables(val)
  }

  const handleStartTableChange = async (val: number) => {
    setStartTableId(val)
    setStartFieldName(undefined)
    if (val) {
      try {
        const res: any = await fieldApi.listByTable(val)
        setStartFields(res.data || [])
      } catch (e: any) {
        message.error(e?.message || '加载字段失败')
      }
    } else {
      setStartFields([])
    }
  }

  const handleTargetTableChange = async (val: number) => {
    setTargetTableId(val)
    setTargetFieldName(undefined)
    if (val) {
      try {
        const res: any = await fieldApi.listByTable(val)
        setTargetFields(res.data || [])
      } catch (e: any) {
        message.error(e?.message || '加载字段失败')
      }
    } else {
      setTargetFields([])
    }
  }

  const handleSearch = async () => {
    if (!projectId || !startTableId || !targetTableId) {
      message.warning('请填写完整信息')
      return
    }
    setLoading(true)
    try {
      const res: any = await queryApi.findPath({
        projectId,
        startTableId,
        startFieldName,
        targetTableId,
        targetFieldName,
      })
      setPaths(res.data?.paths || [])
    } catch (e: any) {
      message.error(e?.message || '查询路径失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <Card style={{ marginBottom: 16 }} styles={{ body: { paddingBottom: 8 } }}>
        <Space size="middle" wrap>
          <Select
            placeholder="选择项目"
            style={{ width: 180 }}
            showSearch
            onSearch={(v: string) => loadProjects(v)}
            onFocus={() => loadProjects()}
            onChange={handleProjectChange}
            value={projectId}
            filterOption={false}
            options={projects.map((p: any) => ({ label: p.appName, value: p.id }))}
          />
          <Select
            placeholder="起始表"
            style={{ width: 180 }}
            showSearch
            allowClear
            value={startTableId}
            onChange={handleStartTableChange}
            filterOption={(input, option) =>
              ((option?.label ?? '') as string).toLowerCase().includes(input.toLowerCase())
            }
            options={tables.map((t: any) => ({ label: t.tableName, value: t.id }))}
          />
          <Select
            placeholder="起始字段"
            style={{ width: 180 }}
            showSearch
            value={startFieldName}
            onChange={(v: string) => setStartFieldName(v)}
            filterOption={(input, option) =>
              (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
            }
            options={startFields.map((f: any) => ({ label: f.fieldName, value: f.fieldName }))}
            disabled={!startTableId}
            allowClear
          />
          <Select
            placeholder="目标表"
            style={{ width: 180 }}
            showSearch
            allowClear
            value={targetTableId}
            onChange={handleTargetTableChange}
            filterOption={(input, option) =>
              ((option?.label ?? '') as string).toLowerCase().includes(input.toLowerCase())
            }
            options={tables.map((t: any) => ({ label: t.tableName, value: t.id }))}
          />
          <Select
            placeholder="目标字段"
            style={{ width: 180 }}
            showSearch
            value={targetFieldName}
            onChange={(v: string) => setTargetFieldName(v)}
            filterOption={(input, option) =>
              (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
            }
            options={targetFields.map((f: any) => ({ label: f.fieldName, value: f.fieldName }))}
            disabled={!targetTableId}
            allowClear
          />
          <Button type="primary" onClick={handleSearch} loading={loading}>
            查询路径
          </Button>
        </Space>
      </Card>

      {paths.map((path: any, idx: number) => (
        <Card key={idx} style={{ marginBottom: 12 }}>
          <div style={{ marginBottom: 8 }}>
            <Tag color={idx === 0 ? 'blue' : 'default'}>
              {idx === 0 ? '最短路径' : `路径 ${idx + 1}`} (长度: {path.length})
            </Tag>
          </div>
          <div className="font-mono" style={{ fontSize: 13 }}>
            {path.nodes?.join(' → ')}
          </div>
          {path.edges?.length > 0 && (
            <div style={{ marginTop: 6, color: 'rgba(0, 0, 0, 0.65)', fontSize: 12 }}>
              {path.edges.map((e: any, ei: number) => (
                <span key={ei}>
                  {ei > 0 && ' | '}
                  {e.from}.{e.joinField}={e.to}
                </span>
              ))}
            </div>
          )}
        </Card>
      ))}
      {paths.length === 0 && !loading && (
        <Text type="secondary">暂无结果，请选择项目、起始表和目标表后查询</Text>
      )}
    </div>
  )
}

export default function TableLogicQuery() {
  return (
    <Tabs
      items={[
        { key: 'field', label: '按业务含义搜字段', children: <FieldSearch /> },
        { key: 'path', label: '路径查询', children: <PathQuery /> },
      ]}
    />
  )
}
