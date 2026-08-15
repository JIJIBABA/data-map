import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Layout, Menu } from 'antd'
import { TableOutlined } from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import TableInfoManage from './pages/TableInfoManage'
import TableDetail from './pages/TableInfoManage/TableDetail'
import TableRelation from './pages/TableRelation'
import TableLogicQuery from './pages/TableLogicQuery'

const { Sider, Content } = Layout

const menuItems = [
  {
    key: '/tables',
    icon: <TableOutlined />,
    label: '表信息管理',
    children: [
      { key: '/tables/list', label: '表基本信息管理' },
      { key: '/tables/query', label: '表逻辑信息查询' },
    ],
  },
]

function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()

  const selectedKey = (() => {
    if (location.pathname.startsWith('/tables/query')) return '/tables/query'
    return '/tables/list'
  })()

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider width={220} theme="dark">
        <div style={{ color: '#fff', fontSize: 18, textAlign: 'center', padding: '16px 0', fontWeight: 'bold' }}>
          数据地图
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Content style={{ margin: 16, padding: 24, background: '#fff', borderRadius: 8 }}>
          <Routes>
            <Route path="/" element={<Navigate to="/tables/list" />} />
            <Route path="/tables/list" element={<TableInfoManage />} />
            <Route path="/tables/:id" element={<TableDetail />} />
            <Route path="/tables/:id/relations" element={<TableRelation />} />
            <Route path="/tables/query" element={<TableLogicQuery />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AppLayout />
    </BrowserRouter>
  )
}
