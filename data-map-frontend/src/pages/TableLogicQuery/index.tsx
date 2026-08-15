import { useState } from 'react'
import { Tabs, Input, Table, Button, Select, Space, message, Tag } from 'antd'
import { queryApi, projectApi, tableApi, fieldApi } from '../../services/api'

function FieldSearch() {
  const [keyword, setKeyword] = useState('')
  const [data, setData] = useState<any[]>([])
  const [loading, setLoading] = useState(false)

  const handleSearch = async () => {
    if (!keyword.trim()) return
    setLoading(true)
    try {
      const res: any = await queryApi.searchFields(keyword)
      setData(res.data || [])
    } finally {
      setLoading(false)
    }
  }

  const columns = [
    { title: '项目', dataIndex: 'projectName', key: 'projectName', width: 150 },
    { title: '表名', dataIndex: 'tableName', key: 'tableName', width: 180 },
    { title: '字段名', dataIndex: 'fieldName', key: 'fieldName', width: 150 },
    { title: '备注', dataIndex: 'fieldComment', key: 'fieldComment' },
  ]

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Input.Search
          placeholder="输入业务关键词搜索字段"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onSearch={handleSearch}
          style={{ width: 320 }}
        />
      </Space>
      <Table columns={columns} dataSource={data} rowKey="fieldName" loading={loading} />
    </div>
  )
}

function PathQuery() {
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
    const res: any = await projectApi.list(keyword)
    setProjects(res.data || [])
  }

  const loadTables = async (pid: number) => {
    const res: any = await tableApi.list({ projectId: pid })
    setTables(res.data || [])
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
      const res: any = await fieldApi.listByTable(val)
      setStartFields(res.data || [])
    } else {
      setStartFields([])
    }
  }

  const handleTargetTableChange = async (val: number) => {
    setTargetTableId(val)
    setTargetFieldName(undefined)
    if (val) {
      const res: any = await fieldApi.listByTable(val)
      setTargetFields(res.data || [])
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
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <Space style={{ marginBottom: 16 }} size="middle" wrap>
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
          value={startTableId}
          onChange={handleStartTableChange}
          filterOption={false}
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
          value={targetTableId}
          onChange={handleTargetTableChange}
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
        <Button type="primary" onClick={handleSearch} loading={loading}>查询路径</Button>
      </Space>

      {paths.map((path: any, idx: number) => (
        <div key={idx} style={{ marginBottom: 16, padding: 12, border: '1px solid #f0f0f0', borderRadius: 8 }}>
          <Tag color={idx === 0 ? 'blue' : 'default'}>
            {idx === 0 ? '最短路径' : `路径 ${idx + 1}`} (长度: {path.length})
          </Tag>
          <div style={{ marginTop: 8, fontSize: 14 }}>
            {path.nodes?.join(' → ')}
          </div>
          <div style={{ marginTop: 4, color: '#666', fontSize: 12 }}>
            {path.edges?.map((e: any, ei: number) => (
              <span key={ei}>
                {ei > 0 && ' | '}
                {e.from}.{e.joinField}={e.to}
              </span>
            ))}
          </div>
        </div>
      ))}
      {paths.length === 0 && !loading && (
        <div style={{ color: '#999' }}>暂无结果，请选择项目、起始表和目标表后查询</div>
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
