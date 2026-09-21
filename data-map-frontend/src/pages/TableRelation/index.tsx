import { useEffect, useState, useCallback, useMemo } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Button, Space, Table, Card, Empty, Tag, Typography, App, Segmented } from 'antd'
import type { TableColumnsType } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import {
  ReactFlow,
  Handle,
  Position,
  MarkerType,
  type Node,
  type Edge,
  type NodeProps,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import dagre from 'dagre'
import { relationApi, tableApi } from '../../services/api'
import { COLOR_PRIMARY } from '../../theme'

const { Title, Text } = Typography

// 单强调色 + 中性色：入口表用主色，其余表用 off-black 灰
const ENTRY_BG = COLOR_PRIMARY
const OTHER_BG = '#334155'
const EDGE_STROKE = '#b0b7c3'

// Custom table node: shows table name + related fields only
function TableNode({ data }: NodeProps) {
  const fields = (data.fields as string[]) || []
  const isEntry = data.isEntry as boolean

  return (
    <div
      style={{
        background: '#fff',
        border: isEntry ? `2px solid ${ENTRY_BG}` : '1px solid #e5e7eb',
        borderRadius: 8,
        minWidth: isEntry ? 260 : 200,
        boxShadow: isEntry
          ? '0 4px 16px rgba(22, 119, 255, 0.18)'
          : '0 2px 8px rgba(0, 0, 0, 0.06)',
      }}
    >
      <Handle type="target" position={Position.Left} />
      <div
        style={{
          background: isEntry ? ENTRY_BG : OTHER_BG,
          color: '#fff',
          padding: isEntry ? '12px 18px' : '10px 16px',
          borderRadius: '8px 8px 0 0',
          fontWeight: 600,
          fontSize: isEntry ? 16 : 14,
        }}
      >
        {data.label as string}
        {isEntry && (
          <span
            style={{
              float: 'right',
              fontSize: 11,
              fontWeight: 500,
              opacity: 0.9,
            }}
          >
            当前表
          </span>
        )}
      </div>
      <div style={{ padding: '4px 0' }}>
        {fields.map((field: string, idx: number) => (
          <div
            key={idx}
            style={{
              padding: isEntry ? '8px 18px' : '6px 16px',
              fontSize: isEntry ? 14 : 13,
              color: 'rgba(0, 0, 0, 0.65)',
              fontFamily: 'var(--font-mono)',
              borderBottom: idx < fields.length - 1 ? '1px solid #f5f5f5' : 'none',
            }}
          >
            {field}
          </div>
        ))}
        {fields.length === 0 && (
          <div style={{ padding: '8px 16px', fontSize: 12, color: 'rgba(0, 0, 0, 0.65)' }}>
            (无关联字段)
          </div>
        )}
      </div>
      <Handle type="source" position={Position.Right} />
    </div>
  )
}

const nodeTypes = { tableNode: TableNode }

function getLayoutedElements(nodes: Node[], edges: Edge[]) {
  const g = new dagre.graphlib.Graph()
  g.setDefaultEdgeLabel(() => ({}))
  g.setGraph({ rankdir: 'LR', nodesep: 60, ranksep: 180 })

  nodes.forEach((node) => {
    const fieldCount = ((node.data?.fields as any[]) || []).length
    const isEntry = node.data?.isEntry as boolean
    const rowH = isEntry ? 37 : 33
    const headerH = isEntry ? 52 : 44
    const width = isEntry ? 260 : 200
    const height = headerH + fieldCount * rowH
    g.setNode(node.id, { width, height })
  })

  edges.forEach((edge) => {
    g.setEdge(edge.source, edge.target)
  })

  dagre.layout(g)

  return {
    nodes: nodes.map((node) => {
      const pos = g.node(node.id)
      const fieldCount = ((node.data?.fields as any[]) || []).length
      const isEntry = node.data?.isEntry as boolean
      const rowH = isEntry ? 37 : 33
      const headerH = isEntry ? 52 : 44
      const width = isEntry ? 260 : 200
      const height = headerH + fieldCount * rowH
      return {
        ...node,
        position: { x: pos.x - width / 2, y: pos.y - height / 2 },
      }
    }),
    edges,
  }
}

export default function TableRelation() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { message } = App.useApp()
  const [tableInfo, setTableInfo] = useState<any>(null)
  const [loading, setLoading] = useState(false)
  const [viewMode, setViewMode] = useState<'direct' | 'all'>('direct')
  const [fullData, setFullData] = useState<{ nodes: any[]; edges: any[]; details: any[] } | null>(null)

  const fetchData = useCallback(async () => {
    setLoading(true)
    try {
      const tres: any = await tableApi.getById(Number(id))
      setTableInfo(tres.data)
      const res: any = await relationApi.getRelations(Number(id))
      setFullData(res.data || { nodes: [], edges: [], details: [] })
    } catch (e: any) {
      message.error(e?.message || '加载关联关系失败')
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  // 依据 viewMode 过滤（直接关联 = 仅当前表 + 直接相邻表；全部 = 整个连通分量），并在渲染期完成 dagre 布局，
  // 保证 key 重挂载时 fitView 拿到的是当前视图的正确节点（而非上一次视图的旧节点）
  const { nodes: rfNodes, edges: rfEdges, details } = useMemo(() => {
    if (!fullData) return { nodes: [] as Node[], edges: [] as Edge[], details: [] as any[] }
    const curId = String(id)
    const direct = viewMode === 'direct'

    let edges = fullData.edges || []
    let detailRows = fullData.details || []
    let nodes = fullData.nodes || []

    if (direct) {
      edges = edges.filter((e: any) => String(e.source) === curId || String(e.target) === curId)
      detailRows = detailRows.filter(
        (d: any) => String(d.sourceTableId) === curId || String(d.targetTableId) === curId,
      )
      const neighborIds = new Set<string>()
      edges.forEach((e: any) => {
        neighborIds.add(String(e.source))
        neighborIds.add(String(e.target))
      })
      nodes = nodes.filter((n: any) => String(n.id) === curId || neighborIds.has(String(n.id)))
    }

    const rfNodes: Node[] = nodes.map((n: any) => {
      const fields = new Set<string>()
      edges.forEach((e: any) => {
        if (String(e.source) === String(n.id)) fields.add(e.sourceField)
        if (String(e.target) === String(n.id)) fields.add(e.targetField)
      })
      return {
        id: String(n.id),
        type: 'tableNode',
        data: { label: n.label, fields: Array.from(fields), isEntry: String(n.id) === curId },
        position: { x: 0, y: 0 },
      }
    })

    const rfEdges: Edge[] = edges.map((e: any, idx: number) => ({
      id: `edge-${idx}`,
      source: String(e.source),
      target: String(e.target),
      label: `${e.sourceField} = ${e.targetField}`,
      type: 'smoothstep',
      markerEnd: { type: MarkerType.ArrowClosed, width: 16, height: 16 },
      style: { stroke: EDGE_STROKE, strokeWidth: 1.5 },
      labelStyle: { fontSize: 11, fill: 'rgba(0, 0, 0, 0.65)' },
      labelBgStyle: { fill: '#fff', fillOpacity: 0.9 },
    }))

    if (rfNodes.length === 0) return { nodes: rfNodes, edges: rfEdges, details: detailRows }
    const laidOut = getLayoutedElements(rfNodes, rfEdges)
    return { nodes: laidOut.nodes, edges: laidOut.edges, details: detailRows }
  }, [fullData, viewMode, id])

  const detailColumns: TableColumnsType<any> = [
    {
      title: '左表',
      dataIndex: 'sourceTable',
      key: 'sourceTable',
      render: (v: string) => (
        <span className="font-mono" style={{ fontSize: 13 }}>
          {v}
        </span>
      ),
    },
    { title: '左字段', dataIndex: 'sourceField', key: 'sourceField' },
    {
      title: '右表',
      dataIndex: 'targetTable',
      key: 'targetTable',
      render: (v: string) => (
        <span className="font-mono" style={{ fontSize: 13 }}>
          {v}
        </span>
      ),
    },
    { title: '右字段', dataIndex: 'targetField', key: 'targetField' },
    {
      title: '关系类型',
      dataIndex: 'relationType',
      key: 'relationType',
      render: (v: string) => <Tag>{v}</Tag>,
    },
    {
      title: '来源方法',
      dataIndex: 'methodSignature',
      key: 'methodSignature',
      render: (v: string) => (
        <span className="font-mono" style={{ fontSize: 12, wordBreak: 'break-all' }}>
          {v}
        </span>
      ),
    },
  ]

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 12 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/tables/list')}>
          返回
        </Button>
        <div>
          <Title level={4} style={{ margin: 0 }}>
            {tableInfo?.tableName}
          </Title>
          <Text type="secondary">关联关系图</Text>
        </div>
      </div>

      <Card style={{ marginBottom: 16 }} styles={{ body: { padding: 0 } }}>
        <div
          style={{
            padding: '12px 16px 0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: 8,
          }}
        >
          <Space size="small">
            <Text type="secondary" style={{ fontSize: 12 }}>
              图例
            </Text>
            <Tag color="blue">当前表</Tag>
            <Tag>关联表</Tag>
          </Space>
          <Space size="small">
            <Text type="secondary" style={{ fontSize: 12 }}>
              {rfNodes.length} 张表 · {rfEdges.length} 条边
            </Text>
            <Segmented
              size="small"
              options={[
                { label: '直接关联', value: 'direct' },
                { label: '全部关联', value: 'all' },
              ]}
              value={viewMode}
              onChange={(v) => setViewMode(v as 'direct' | 'all')}
            />
          </Space>
        </div>
        {!loading && rfNodes.length === 0 ? (
          <Empty style={{ padding: '80px 0' }} description="暂无关联关系" />
        ) : (
          <div style={{ height: 500 }}>
            <ReactFlow
              key={`${id}-${viewMode}`}
              nodes={rfNodes}
              edges={rfEdges}
              nodeTypes={nodeTypes}
              fitView
              fitViewOptions={{ padding: 0.3 }}
              attributionPosition="bottom-left"
            />
          </div>
        )}
      </Card>

      <Card styles={{ body: { padding: 0 } }}>
        <div style={{ padding: '12px 16px 0', fontSize: 13, fontWeight: 600 }}>关联详情</div>
        <Table
          columns={detailColumns}
          dataSource={details}
          rowKey={(r: any) =>
            `${r.sourceTableId}-${r.sourceField}-${r.targetTableId}-${r.targetField}`
          }
          size="small"
          loading={loading}
          scroll={{ x: 900 }}
        />
      </Card>
    </div>
  )
}
