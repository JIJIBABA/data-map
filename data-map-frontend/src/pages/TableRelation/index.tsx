import { useEffect, useState, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Button, Space, Table } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import {
  ReactFlow,
  Handle,
  Position,
  MarkerType,
  useNodesState,
  useEdgesState,
  type Node,
  type Edge,
  type NodeProps,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import dagre from 'dagre'
import { relationApi, tableApi } from '../../services/api'

// Custom table node: shows table name + related fields only
function TableNode({ data }: NodeProps) {
  const fields = (data.fields as string[]) || []
  const isEntry = data.isEntry as boolean

  return (
    <div
      style={{
        background: '#fff',
        border: isEntry ? '2px solid #ff4d4f' : '1px solid #e8e8e8',
        borderRadius: 8,
        minWidth: isEntry ? 260 : 200,
        boxShadow: isEntry
          ? '0 4px 16px rgba(255,77,79,0.2)'
          : '0 2px 8px rgba(0,0,0,0.08)',
      }}
    >
      <Handle type="target" position={Position.Left} />
      <div
        style={{
          background: isEntry ? '#ff4d4f' : '#1677ff',
          color: '#fff',
          padding: isEntry ? '12px 18px' : '10px 16px',
          borderRadius: '8px 8px 0 0',
          fontWeight: 600,
          fontSize: isEntry ? 16 : 14,
        }}
      >
        {data.label as string}
      </div>
      <div style={{ padding: '4px 0' }}>
        {fields.map((field: string, idx: number) => (
          <div
            key={idx}
            style={{
              padding: isEntry ? '8px 18px' : '6px 16px',
              fontSize: isEntry ? 14 : 13,
              color: '#555',
              fontFamily: 'monospace',
              borderBottom:
                idx < fields.length - 1 ? '1px solid #f5f5f5' : 'none',
            }}
          >
            {field}
          </div>
        ))}
        {fields.length === 0 && (
          <div style={{ padding: '8px 16px', fontSize: 12, color: '#999' }}>
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
  const [tableInfo, setTableInfo] = useState<any>(null)
  const [details, setDetails] = useState<any[]>([])
  const [rfNodes, setRfNodes, onNodesChange] = useNodesState<Node>([])
  const [rfEdges, setRfEdges, onEdgesChange] = useEdgesState<Edge>([])

  const fetchData = useCallback(
    async () => {
      try {
        const tres: any = await tableApi.getById(Number(id))
        setTableInfo(tres.data)
        const res: any = await relationApi.getRelations(Number(id))
        const vo = res.data
        setDetails(vo.details || [])

        // Build nodes with associated fields extracted from edges
        const nodes: Node[] = (vo.nodes || []).map((n: any) => {
          const fields = new Set<string>()
          ;(vo.edges || []).forEach((e: any) => {
            if (
              String(e.source) === String(n.id) ||
              e.source === n.id
            )
              fields.add(e.sourceField)
            if (
              String(e.target) === String(n.id) ||
              e.target === n.id
            )
              fields.add(e.targetField)
          })
          const isEntry = String(n.id) === String(id)
          return {
            id: String(n.id),
            type: 'tableNode',
            data: { label: n.label, fields: Array.from(fields), isEntry },
            position: { x: 0, y: 0 },
          }
        })

        const edges: Edge[] = (vo.edges || []).map(
          (e: any, idx: number) => ({
            id: `edge-${idx}`,
            source: String(e.source),
            target: String(e.target),
            label: `${e.sourceField} = ${e.targetField}`,
            type: 'smoothstep',
            markerEnd: {
              type: MarkerType.ArrowClosed,
              width: 16,
              height: 16,
            },
            style: { stroke: '#bbb', strokeWidth: 1.5 },
            labelStyle: { fontSize: 11, fill: '#666' },
            labelBgStyle: { fill: '#fff', fillOpacity: 0.9 },
          }),
        )

        if (nodes.length > 0) {
          const layouted = getLayoutedElements(nodes, edges)
          setRfNodes(layouted.nodes)
          setRfEdges(layouted.edges)
        } else {
          setRfNodes([])
          setRfEdges([])
        }
      } catch (e) {
        console.error(e)
      }
    },
    [id],
  )

  useEffect(() => {
    fetchData()
  }, [fetchData])

  const detailColumns = [
    { title: '左表', dataIndex: 'sourceTable', key: 'sourceTable' },
    { title: '左字段', dataIndex: 'sourceField', key: 'sourceField' },
    { title: '右表', dataIndex: 'targetTable', key: 'targetTable' },
    { title: '右字段', dataIndex: 'targetField', key: 'targetField' },
    { title: '关系类型', dataIndex: 'relationType', key: 'relationType' },
    {
      title: '来源方法',
      dataIndex: 'methodSignature',
      key: 'methodSignature',
    },
  ]

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate('/tables/list')}
        >
          返回
        </Button>
        <span style={{ fontSize: 16, fontWeight: 'bold' }}>
          {tableInfo?.tableName} — 关联关系图
        </span>
      </Space>
      <div
        style={{
          border: '1px solid #f0f0f0',
          borderRadius: 8,
          marginBottom: 16,
          height: 500,
        }}
      >
        <ReactFlow
          key={`${id}`}
          nodes={rfNodes}
          edges={rfEdges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          nodeTypes={nodeTypes}
          fitView
          fitViewOptions={{ padding: 0.3 }}
          attributionPosition="bottom-left"
        />
      </div>
      <Table
        columns={detailColumns}
        dataSource={details}
        rowKey={(_r: any, idx?: number) => String(idx ?? 0)}
      />
    </div>
  )
}
