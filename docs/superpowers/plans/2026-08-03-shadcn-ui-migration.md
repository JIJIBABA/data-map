# shadcn/ui UI 优化迁移 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 data-map-frontend 从 Ant Design 迁移到 shadcn/ui + Tailwind CSS + MagicUI，实现可折叠侧边栏、现代化数据表格、卡片容器布局及微交互动画。

**Architecture:** 采用方案A（稳妥路线）— shadcn/ui 作为核心 UI 框架提供布局/侧边栏/表格/表单组件，Tailwind CSS 处理所有样式，MagicUI 提供 AnimatedCard/ShineInput 等动效增强。保留 @xyflow/react（ReactFlow 关系图谱）和 dagre（布局算法）不变。API 层（services/api.ts）完全不动。

**Tech Stack:** React 18 + TypeScript + Vite + Tailwind CSS 3 + shadcn/ui (Radix) + TanStack Table + MagicUI + sonner (toast)

## Global Constraints

- 保持 React 18 + TypeScript + Vite 不变
- 保持 @xyflow/react + dagre 关系图谱不变
- API 层 `src/services/api.ts` 零改动
- 业务逻辑（fetchData/handleEdit/handleDelete 等）保持不变，仅替换 UI 组件
- 所有文案（中文）保持不变
- 路由结构不变
- Ant Design (antd + @ant-design/icons) 最后移除

## File Structure

```
src/
├── components/
│   └── ui/          # shadcn/ui 组件（由 CLI 生成）
├── hooks/           # 自定义 hooks（use-mobile 等）
├── lib/
│   └── utils.ts     # cn() 工具函数
├── App.tsx          # 新的 AppLayout（shadcn Sidebar）
├── main.tsx         # 入口文件
├── index.css        # Tailwind 指令 + CSS 变量
└── pages/
    ├── TableInfoManage/
    │   ├── index.tsx        # 表列表页（shadcn DataTable）
    │   └── TableDetail.tsx  # 表详情页
    ├── TableRelation/
    │   └── index.tsx        # 关系图谱页（保留 ReactFlow）
    └── TableLogicQuery/
        └── index.tsx        # 逻辑查询页
```

---

### Task 1: 安装 Tailwind CSS + shadcn/ui 基础设施

**Files:**
- Modify: `package.json`
- Create: `tailwind.config.js`
- Create: `postcss.config.js`
- Modify: `vite.config.ts`
- Create: `src/index.css`
- Create: `src/lib/utils.ts`
- Modify: `src/main.tsx`
- Create: `components.json`

**Interfaces:**
- Produces: Tailwind CSS 可用，`cn()` 工具函数可用，CSS 变量主题系统就绪

- [ ] **Step 1: 安装 Tailwind CSS 及其依赖**

```bash
cd /Applications/project/data-map/data-map-frontend && \
npm install -D tailwindcss@3 postcss autoprefixer && \
npx tailwindcss init -p
```

- [ ] **Step 2: 配置 tailwind.config.js**

写入 `tailwind.config.js`:

```js
/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    container: {
      center: true,
      padding: '2rem',
      screens: { '2xl': '1400px' },
    },
    extend: {
      colors: {
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))',
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))',
        },
        popover: {
          DEFAULT: 'hsl(var(--popover))',
          foreground: 'hsl(var(--popover-foreground))',
        },
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))',
        },
        sidebar: {
          DEFAULT: 'hsl(var(--sidebar-background))',
          foreground: 'hsl(var(--sidebar-foreground))',
          primary: 'hsl(var(--sidebar-primary))',
          'primary-foreground': 'hsl(var(--sidebar-primary-foreground))',
          accent: 'hsl(var(--sidebar-accent))',
          'accent-foreground': 'hsl(var(--sidebar-accent-foreground))',
          border: 'hsl(var(--sidebar-border))',
          ring: 'hsl(var(--sidebar-ring))',
        },
      },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
      },
      keyframes: {
        'accordion-down': {
          from: { height: '0' },
          to: { height: 'var(--radix-accordion-content-height)' },
        },
        'accordion-up': {
          from: { height: 'var(--radix-accordion-content-height)' },
          to: { height: '0' },
        },
      },
      animation: {
        'accordion-down': 'accordion-down 0.2s ease-out',
        'accordion-up': 'accordion-up 0.2s ease-out',
      },
    },
  },
  plugins: [],
}
```

- [ ] **Step 3: 配置 postcss.config.js**

确保 `postcss.config.js` 内容为:

```js
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
}
```

- [ ] **Step 4: 更新 vite.config.ts**

```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
```

- [ ] **Step 5: 创建 src/lib/utils.ts**

```ts
import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
```

```bash
cd /Applications/project/data-map/data-map-frontend && npm install clsx tailwind-merge
```

- [ ] **Step 6: 创建 src/index.css（shadcn 主题 CSS 变量）**

写入 `src/index.css`:

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  :root {
    --background: 0 0% 100%;
    --foreground: 222.2 84% 4.9%;
    --card: 0 0% 100%;
    --card-foreground: 222.2 84% 4.9%;
    --popover: 0 0% 100%;
    --popover-foreground: 222.2 84% 4.9%;
    --primary: 222.2 47.4% 11.2%;
    --primary-foreground: 210 40% 98%;
    --secondary: 210 40% 96.1%;
    --secondary-foreground: 222.2 47.4% 11.2%;
    --muted: 210 40% 96.1%;
    --muted-foreground: 215.4 16.3% 46.9%;
    --accent: 210 40% 96.1%;
    --accent-foreground: 222.2 47.4% 11.2%;
    --destructive: 0 84.2% 60.2%;
    --destructive-foreground: 210 40% 98%;
    --border: 214.3 31.8% 91.4%;
    --input: 214.3 31.8% 91.4%;
    --ring: 222.2 84% 4.9%;
    --radius: 0.5rem;
    --sidebar-background: 222.2 47.4% 11.2%;
    --sidebar-foreground: 210 40% 98%;
    --sidebar-primary: 210 40% 98%;
    --sidebar-primary-foreground: 222.2 47.4% 11.2%;
    --sidebar-accent: 217.2 32.6% 17.5%;
    --sidebar-accent-foreground: 210 40% 98%;
    --sidebar-border: 217.2 32.6% 17.5%;
    --sidebar-ring: 212.7 26.8% 83.9%;
  }

  .dark {
    --background: 222.2 84% 4.9%;
    --foreground: 210 40% 98%;
    --card: 222.2 84% 4.9%;
    --card-foreground: 210 40% 98%;
    --popover: 222.2 84% 4.9%;
    --popover-foreground: 210 40% 98%;
    --primary: 210 40% 98%;
    --primary-foreground: 222.2 47.4% 11.2%;
    --secondary: 217.2 32.6% 17.5%;
    --secondary-foreground: 210 40% 98%;
    --muted: 217.2 32.6% 17.5%;
    --muted-foreground: 215 20.2% 65.1%;
    --accent: 217.2 32.6% 17.5%;
    --accent-foreground: 210 40% 98%;
    --destructive: 0 62.8% 30.6%;
    --destructive-foreground: 210 40% 98%;
    --border: 217.2 32.6% 17.5%;
    --input: 217.2 32.6% 17.5%;
    --ring: 212.7 26.8% 83.9%;
    --sidebar-background: 222.2 84% 4.9%;
    --sidebar-foreground: 210 40% 98%;
    --sidebar-primary: 210 40% 98%;
    --sidebar-primary-foreground: 222.2 84% 4.9%;
    --sidebar-accent: 217.2 32.6% 17.5%;
    --sidebar-accent-foreground: 210 40% 98%;
    --sidebar-border: 217.2 32.6% 17.5%;
    --sidebar-ring: 212.7 26.8% 83.9%;
  }
}

@layer base {
  * {
    @apply border-border;
  }
  body {
    @apply bg-background text-foreground;
  }
}
```

- [ ] **Step 7: 更新 src/main.tsx**

```tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
```

- [ ] **Step 8: 初始化 shadcn/ui**

```bash
cd /Applications/project/data-map/data-map-frontend && \
npx shadcn@latest init -d
```

这会创建 `components.json`。

- [ ] **Step 9: 验证基础设施**

```bash
cd /Applications/project/data-map/data-map-frontend && npm run dev
```

确认项目能正常启动，无报错。访问 http://localhost:5173 确认 Tailwind 的 base 样式生效（body 背景、字体变化）。

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat: add Tailwind CSS + shadcn/ui infrastructure"
```

---

### Task 2: 安装 shadcn/ui 组件库

**Files:**
- Create: `src/components/ui/*.tsx` (shadcn CLI 生成)
- Create: `src/hooks/use-mobile.tsx`
- Modify: `package.json`

**Interfaces:**
- Produces: 所有需要的 shadcn/ui 组件可用

- [ ] **Step 1: 添加 shadcn 组件**

```bash
cd /Applications/project/data-map/data-map-frontend && \
npx shadcn@latest add sidebar -y && \
npx shadcn@latest add button -y && \
npx shadcn@latest add input -y && \
npx shadcn@latest add table -y && \
npx shadcn@latest add card -y && \
npx shadcn@latest add dialog -y && \
npx shadcn@latest add form -y && \
npx shadcn@latest add tabs -y && \
npx shadcn@latest add select -y && \
npx shadcn@latest add badge -y && \
npx shadcn@latest add alert-dialog -y && \
npx shadcn@latest add dropdown-menu -y && \
npx shadcn@latest add separator -y && \
npx shadcn@latest add tooltip -y && \
npx shadcn@latest add scroll-area -y && \
npx shadcn@latest add sheet -y && \
npx shadcn@latest add breadcrumb -y && \
npx shadcn@latest add skeleton -y
```

- [ ] **Step 2: 安装额外依赖**

```bash
cd /Applications/project/data-map/data-map-frontend && \
npm install @tanstack/react-table sonner @radix-ui/react-icons lucide-react
```

- [ ] **Step 3: 创建自定义 DataTable 组件**

写入 `src/components/ui/data-table.tsx`:

```tsx
'use client'

import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  useReactTable,
  getPaginationRowModel,
  type SortingState,
  getSortedRowModel,
} from '@tanstack/react-table'
import { useState } from 'react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface DataTableProps<TData, TValue> {
  columns: ColumnDef<TData, TValue>[]
  data: TData[]
  loading?: boolean
  pageSize?: number
  onRowClick?: (row: TData) => void
}

export function DataTable<TData, TValue>({
  columns,
  data,
  loading = false,
  pageSize = 10,
  onRowClick,
}: DataTableProps<TData, TValue>) {
  const [sorting, setSorting] = useState<SortingState>([])
  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    onSortingChange: setSorting,
    getSortedRowModel: getSortedRowModel(),
    state: { sorting },
    initialState: { pagination: { pageSize } },
  })

  if (loading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="h-10 bg-muted animate-pulse rounded" />
        ))}
      </div>
    )
  }

  return (
    <div className="rounded-md border">
      <Table>
        <TableHeader>
          {table.getHeaderGroups().map((headerGroup) => (
            <TableRow key={headerGroup.id}>
              {headerGroup.headers.map((header) => (
                <TableHead key={header.id}>
                  {header.isPlaceholder
                    ? null
                    : flexRender(header.column.columnDef.header, header.getContext())}
                </TableHead>
              ))}
            </TableRow>
          ))}
        </TableHeader>
        <TableBody>
          {table.getRowModel().rows?.length ? (
            table.getRowModel().rows.map((row) => (
              <TableRow
                key={row.id}
                data-state={row.getIsSelected() && 'selected'}
                className={cn(onRowClick && 'cursor-pointer')}
                onClick={() => onRowClick?.(row.original)}
              >
                {row.getVisibleCells().map((cell) => (
                  <TableCell key={cell.id}>
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </TableCell>
                ))}
              </TableRow>
            ))
          ) : (
            <TableRow>
              <TableCell colSpan={columns.length} className="h-24 text-center">
                暂无数据
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
      <div className="flex items-center justify-between px-4 py-3 border-t">
        <div className="text-sm text-muted-foreground">
          共 {table.getFilteredRowModel().rows.length} 条
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => table.previousPage()}
            disabled={!table.getCanPreviousPage()}
          >
            上一页
          </Button>
          <span className="text-sm text-muted-foreground">
            {table.getState().pagination.pageIndex + 1} / {table.getPageCount()}
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => table.nextPage()}
            disabled={!table.getCanNextPage()}
          >
            下一页
          </Button>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: add shadcn/ui components and custom DataTable"
```

---

### Task 3: 安装 MagicUI 动效组件

**Files:**
- Create: `src/components/magicui/animated-card.tsx`
- Create: `src/components/magicui/shine-input.tsx`
- Modify: `package.json`
- Modify: `tailwind.config.js`

**Interfaces:**
- Produces: `AnimatedCard`, `ShineInput` 组件可用

- [ ] **Step 1: 安装 magicui 依赖**

```bash
cd /Applications/project/data-map/data-map-frontend && \
npm install magicui
```

但 MagicUI 是组件集合，不需要完整安装包。手动写入需要的组件即可。

- [ ] **Step 2: 创建 AnimatedCard 组件**

写入 `src/components/magicui/animated-card.tsx`:

```tsx
'use client'

import { cn } from '@/lib/utils'
import type { HTMLAttributes, ReactNode } from 'react'

interface AnimatedCardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode
}

export function AnimatedCard({ children, className, ...props }: AnimatedCardProps) {
  return (
    <div className={cn('group relative', className)} {...props}>
      <div className="absolute -inset-0.5 bg-gradient-to-r from-blue-500 via-purple-500 to-pink-500 rounded-xl opacity-0 blur transition duration-500 group-hover:opacity-30" />
      <div className="relative rounded-xl border bg-card text-card-foreground shadow-sm">
        {children}
      </div>
    </div>
  )
}
```

- [ ] **Step 3: 创建 ShineInput 组件**

写入 `src/components/magicui/shine-input.tsx`:

```tsx
'use client'

import { Input, type InputProps } from '@/components/ui/input'
import { cn } from '@/lib/utils'

export function ShineInput({ className, ...props }: InputProps) {
  return (
    <div className="relative group">
      <div className="absolute -inset-0.5 bg-gradient-to-r from-blue-500 to-purple-500 rounded-lg opacity-0 blur transition duration-300 group-hover:opacity-20 group-focus-within:opacity-30" />
      <Input
        className={cn('relative', className)}
        {...props}
      />
    </div>
  )
}
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: add MagicUI AnimatedCard and ShineInput components"
```

---

### Task 4: 重构 App.tsx 布局 — shadcn Sidebar

**Files:**
- Modify: `src/App.tsx` (完整重写)
- Create: `src/components/app-sidebar.tsx`

**Interfaces:**
- Consumes: shadcn Sidebar 组件, `cn()` from `@/lib/utils`
- Produces: `AppSidebar` 组件 (可折叠侧边栏 + 分组菜单), `AppLayout` 带 TooltipProvider

- [ ] **Step 1: 创建 src/components/app-sidebar.tsx**

```tsx
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarHeader,
  SidebarFooter,
} from '@/components/ui/sidebar'
import { Table2, Search, GitBranch, ChevronDown } from 'lucide-react'
import { useNavigate, useLocation } from 'react-router-dom'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@radix-ui/react-collapsible'
import { cn } from '@/lib/utils'

const menuGroup = {
  label: '表信息管理',
  icon: Table2,
  items: [
    { key: '/tables/list', label: '表基本信息管理', icon: Table2 },
    { key: '/tables/query', label: '表逻辑信息查询', icon: Search },
  ],
}

export function AppSidebar() {
  const navigate = useNavigate()
  const location = useLocation()

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" asChild>
              <div className="flex items-center gap-2">
                <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
                  <Table2 className="size-4" />
                </div>
                <div className="flex flex-col gap-0.5 leading-none">
                  <span className="font-semibold">数据地图</span>
                  <span className="text-xs text-sidebar-foreground/60">Data Map</span>
                </div>
              </div>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>{menuGroup.label}</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {menuGroup.items.map((item) => {
                const isActive =
                  item.key === '/tables/query'
                    ? location.pathname.startsWith('/tables/query')
                    : location.pathname === item.key ||
                      (item.key === '/tables/list' &&
                        !location.pathname.startsWith('/tables/query'))
                return (
                  <SidebarMenuItem key={item.key}>
                    <SidebarMenuButton
                      isActive={isActive}
                      onClick={() => navigate(item.key)}
                      tooltip={item.label}
                    >
                      {item.icon && <item.icon className="size-4" />}
                      <span>{item.label}</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                )
              })}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
      <SidebarFooter>
        <div className="text-xs text-sidebar-foreground/40 px-2">
          Data Map v1.0
        </div>
      </SidebarFooter>
    </Sidebar>
  )
}
```

- [ ] **Step 2: 重写 src/App.tsx**

```tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { SidebarProvider, SidebarInset } from '@/components/ui/sidebar'
import { TooltipProvider } from '@/components/ui/tooltip'
import { AppSidebar } from '@/components/app-sidebar'
import TableInfoManage from './pages/TableInfoManage'
import TableDetail from './pages/TableInfoManage/TableDetail'
import TableRelation from './pages/TableRelation'
import TableLogicQuery from './pages/TableLogicQuery'

function AppLayout() {
  return (
    <TooltipProvider delayDuration={300}>
      <SidebarProvider defaultOpen>
        <AppSidebar />
        <SidebarInset>
          <div className="flex flex-1 flex-col gap-4 p-4 pt-4">
            <Routes>
              <Route path="/" element={<Navigate to="/tables/list" />} />
              <Route path="/tables/list" element={<TableInfoManage />} />
              <Route path="/tables/:id" element={<TableDetail />} />
              <Route path="/tables/:id/relations" element={<TableRelation />} />
              <Route path="/tables/query" element={<TableLogicQuery />} />
            </Routes>
          </div>
        </SidebarInset>
      </SidebarProvider>
    </TooltipProvider>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AppLayout />
    </BrowserRouter>
  )
}
```

- [ ] **Step 3: 验证 Sidebar 效果**

```bash
cd /Applications/project/data-map/data-map-frontend && npm run dev
```

验证：
- 侧边栏渲染正常，有"数据地图"标题
- 菜单项可点击导航
- 可折叠（icon 模式），折叠后有 tooltip
- 当前路由高亮
- 暗色侧边栏主题

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: rewrite App.tsx with shadcn Sidebar layout"
```

---

### Task 5: 重构 TableInfoManage — Card + DataTable + ShineInput

**Files:**
- Modify: `src/pages/TableInfoManage/index.tsx` (完整重写)

**Interfaces:**
- Consumes: `DataTable`, `AnimatedCard`, `ShineInput`, `Button`, `Dialog`, `Input`, `Form`, `AlertDialog`, `Badge` from shadcn/ui
- Uses: `tableApi`, `projectApi` from `@/services/api` (不变)

- [ ] **Step 1: 重写 TableInfoManage/index.tsx**

```tsx
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { type ColumnDef } from '@tanstack/react-table'
import { toast } from 'sonner'
import { DataTable } from '@/components/ui/data-table'
import { AnimatedCard } from '@/components/magicui/animated-card'
import { ShineInput } from '@/components/magicui/shine-input'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Eye, Pencil, Trash2, GitBranch, Search } from 'lucide-react'
import { tableApi, projectApi } from '../../services/api'

interface TableRecord {
  id: number
  projectId: number
  tableName: string
  tableComment: string
}

export default function TableInfoManage() {
  const navigate = useNavigate()
  const [data, setData] = useState<TableRecord[]>([])
  const [loading, setLoading] = useState(false)
  const [projectKeyword, setProjectKeyword] = useState('')
  const [tableNameKeyword, setTableNameKeyword] = useState('')
  const [editOpen, setEditOpen] = useState(false)
  const [editingRecord, setEditingRecord] = useState<TableRecord | null>(null)
  const [editValue, setEditValue] = useState('')
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)

  const fetchData = async () => {
    setLoading(true)
    try {
      const res: any = await tableApi.list({ tableName: tableNameKeyword || undefined })
      const tables = res.data || []
      if (projectKeyword) {
        const pres: any = await projectApi.list(projectKeyword)
        const projectIds = (pres.data || []).map((p: any) => p.id)
        setData(tables.filter((t: any) => projectIds.includes(t.projectId)))
      } else {
        setData(tables)
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchData() }, [])

  const handleSearch = () => fetchData()

  const handleEdit = (record: TableRecord) => {
    setEditingRecord(record)
    setEditValue(record.tableComment)
    setEditOpen(true)
  }

  const handleEditOk = async () => {
    if (!editingRecord || !editValue.trim()) return
    await tableApi.update(editingRecord.id, editValue)
    toast.success('修改成功')
    setEditOpen(false)
    fetchData()
  }

  const handleDeleteConfirm = async () => {
    if (deletingId === null) return
    await tableApi.delete(deletingId)
    toast.success('删除成功')
    setDeleteOpen(false)
    setDeletingId(null)
    fetchData()
  }

  const columns: ColumnDef<TableRecord>[] = [
    {
      accessorKey: 'projectId',
      header: '项目ID',
      size: 80,
      cell: ({ row }) => (
        <Badge variant="secondary">{row.getValue('projectId')}</Badge>
      ),
    },
    {
      accessorKey: 'tableName',
      header: '表名',
      size: 200,
      cell: ({ row }) => (
        <span className="font-mono text-sm">{row.getValue('tableName')}</span>
      ),
    },
    {
      accessorKey: 'tableComment',
      header: '表含义',
      cell: ({ row }) => (
        <span className="text-muted-foreground">{row.getValue('tableComment')}</span>
      ),
    },
    {
      id: 'actions',
      header: '操作',
      size: 280,
      cell: ({ row }) => (
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="sm"
            onClick={(e) => { e.stopPropagation(); navigate(`/tables/${row.original.id}`) }}
          >
            <Eye className="size-3.5 mr-1" />
            查看
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={(e) => { e.stopPropagation(); handleEdit(row.original) }}
          >
            <Pencil className="size-3.5 mr-1" />
            编辑
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={(e) => {
              e.stopPropagation()
              setDeletingId(row.original.id)
              setDeleteOpen(true)
            }}
            className="text-destructive hover:text-destructive"
          >
            <Trash2 className="size-3.5 mr-1" />
            删除
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={(e) => { e.stopPropagation(); navigate(`/tables/${row.original.id}/relations`) }}
          >
            <GitBranch className="size-3.5 mr-1" />
            关联关系
          </Button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      <AnimatedCard className="p-4">
        <div className="flex items-center gap-3">
          <div className="relative flex-1 max-w-xs">
            <ShineInput
              placeholder="项目名称（模糊搜索）"
              value={projectKeyword}
              onChange={(e) => setProjectKeyword(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            />
          </div>
          <div className="relative flex-1 max-w-xs">
            <ShineInput
              placeholder="表名（模糊搜索）"
              value={tableNameKeyword}
              onChange={(e) => setTableNameKeyword(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            />
          </div>
          <Button onClick={handleSearch}>
            <Search className="size-4 mr-2" />
            搜索
          </Button>
        </div>
      </AnimatedCard>

      <AnimatedCard className="p-4">
        <DataTable columns={columns} data={data} loading={loading} pageSize={15} />
      </AnimatedCard>

      {/* Edit Dialog */}
      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>编辑表含义 — {editingRecord?.tableName}</DialogTitle>
          </DialogHeader>
          <div className="py-4">
            <Input
              value={editValue}
              onChange={(e) => setEditValue(e.target.value)}
              placeholder="请输入表含义"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditOpen(false)}>取消</Button>
            <Button onClick={handleEditOk}>保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation */}
      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>
              确定删除此表信息？此操作不可撤销。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleDeleteConfirm} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat: rewrite TableInfoManage with shadcn DataTable + Cards + MagicUI"
```

---

### Task 6: 重构 TableDetail — 字段下钻表格

**Files:**
- Modify: `src/pages/TableInfoManage/TableDetail.tsx` (完整重写)

**Interfaces:**
- Consumes: `DataTable`, `Button`, `Dialog`, `Input`, `Badge`, `AnimatedCard`
- Uses: `fieldApi`, `tableApi` from `@/services/api` (不变)

- [ ] **Step 1: 重写 TableDetail.tsx**

```tsx
import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { type ColumnDef } from '@tanstack/react-table'
import { toast } from 'sonner'
import { DataTable } from '@/components/ui/data-table'
import { AnimatedCard } from '@/components/magicui/animated-card'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { ArrowLeft, ChevronDown, ChevronRight, Pencil } from 'lucide-react'
import { fieldApi, tableApi } from '../../services/api'

interface FieldRecord {
  id: number
  fieldName: string
  fieldComment: string
  fieldType: string
  fieldCommentManual: number
}

interface ScenarioRecord {
  id: number
  operationType: string
  scenarioDescription: string
  methodName: string
  sourceTableName: string
  sourceApiName: string
}

export default function TableDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [tableInfo, setTableInfo] = useState<any>(null)
  const [fields, setFields] = useState<FieldRecord[]>([])
  const [loading, setLoading] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [editingField, setEditingField] = useState<FieldRecord | null>(null)
  const [editValue, setEditValue] = useState('')
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set())
  const [scenarios, setScenarios] = useState<Record<number, ScenarioRecord[]>>({})

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

  const handleEditField = (record: FieldRecord) => {
    setEditingField(record)
    setEditValue(record.fieldComment)
    setEditOpen(true)
  }

  const handleEditOk = async () => {
    if (!editingField || !editValue.trim()) return
    await fieldApi.update(editingField.id, editValue)
    toast.success('修改成功')
    setEditOpen(false)
    fetchData()
  }

  const scenarioColumns: ColumnDef<ScenarioRecord>[] = [
    { accessorKey: 'operationType', header: '操作类型', size: 80 },
    { accessorKey: 'scenarioDescription', header: '场景描述' },
    { accessorKey: 'methodName', header: '方法名称' },
    { accessorKey: 'sourceTableName', header: '写入来源表' },
    { accessorKey: 'sourceApiName', header: '写入来源接口' },
  ]

  const columns: ColumnDef<FieldRecord>[] = [
    {
      accessorKey: 'fieldName',
      header: '字段名',
      size: 160,
      cell: ({ row }) => (
        <span className="font-mono text-sm">{row.getValue('fieldName')}</span>
      ),
    },
    {
      accessorKey: 'fieldComment',
      header: '注释',
      cell: ({ row }) => (
        <span className="text-muted-foreground">{row.getValue('fieldComment')}</span>
      ),
    },
    {
      accessorKey: 'fieldType',
      header: '类型',
      size: 100,
      cell: ({ row }) => (
        <Badge variant="outline" className="font-mono text-xs">{row.getValue('fieldType')}</Badge>
      ),
    },
    {
      accessorKey: 'fieldCommentManual',
      header: '手工修改',
      size: 80,
      cell: ({ row }) =>
        row.getValue('fieldCommentManual') === 1 ? (
          <Badge variant="destructive">是</Badge>
        ) : (
          <Badge variant="secondary">否</Badge>
        ),
    },
    {
      id: 'actions',
      header: '操作',
      size: 140,
      cell: ({ row }) => (
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => handleToggleExpand(row.original.id)}
          >
            {expandedRows.has(row.original.id) ? (
              <ChevronDown className="size-3.5 mr-1" />
            ) : (
              <ChevronRight className="size-3.5 mr-1" />
            )}
            {expandedRows.has(row.original.id) ? '收起' : '下钻'}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => handleEditField(row.original)}
          >
            <Pencil className="size-3.5 mr-1" />
            编辑
          </Button>
        </div>
      ),
    },
  ]

  if (!tableInfo) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">加载中...</p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <AnimatedCard className="p-4">
        <div className="flex items-center gap-3">
          <Button variant="ghost" onClick={() => navigate('/tables/list')}>
            <ArrowLeft className="size-4 mr-2" />
            返回
          </Button>
          <h1 className="text-lg font-semibold">
            {tableInfo.tableName}
            <span className="text-muted-foreground font-normal ml-2">
              — {tableInfo.tableComment}
            </span>
          </h1>
        </div>
      </AnimatedCard>

      <AnimatedCard className="p-4">
        <DataTable columns={columns} data={fields} loading={loading} pageSize={20} />
      </AnimatedCard>

      {/* Expanded row scenarios rendered below the table */}
      {Array.from(expandedRows).map((fieldId) => {
        const list = scenarios[fieldId] || []
        const field = fields.find((f) => f.id === fieldId)
        return (
          <AnimatedCard key={fieldId} className="p-4 ml-8 border-l-2 border-l-primary">
            <h3 className="text-sm font-medium mb-3">
              字段 "{field?.fieldName}" 的使用场景
            </h3>
            {list.length === 0 ? (
              <p className="text-sm text-muted-foreground py-4 text-center">
                无使用场景数据
              </p>
            ) : (
              <DataTable columns={scenarioColumns} data={list} pageSize={5} />
            )}
          </AnimatedCard>
        )
      })}

      {/* Edit Dialog */}
      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>编辑字段注释 — {editingField?.fieldName}</DialogTitle>
          </DialogHeader>
          <div className="py-4">
            <Input
              value={editValue}
              onChange={(e) => setEditValue(e.target.value)}
              placeholder="请输入字段注释"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditOpen(false)}>取消</Button>
            <Button onClick={handleEditOk}>保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat: rewrite TableDetail with shadcn DataTable + expandable scenarios"
```

---

### Task 7: 重构 TableRelation — 保留 ReactFlow + shadcn 样式

**Files:**
- Modify: `src/pages/TableRelation/index.tsx`

**Interfaces:**
- Consumes: `Button`, `Card`, `Badge`, `DataTable`, `AnimatedCard`
- Uses: `relationApi`, `tableApi` (不变) + ReactFlow + dagre (不变)

- [ ] **Step 1: 重写 TableRelation/index.tsx**

```tsx
import { useEffect, useState, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { type ColumnDef } from '@tanstack/react-table'
import { Button } from '@/components/ui/button'
import { AnimatedCard } from '@/components/magicui/animated-card'
import { DataTable } from '@/components/ui/data-table'
import { Badge } from '@/components/ui/badge'
import { ArrowLeft } from 'lucide-react'
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

// --- Custom TableNode (styles updated) ---
function TableNode({ data }: NodeProps) {
  const fields = (data.fields as string[]) || []
  const isEntry = data.isEntry as boolean

  return (
    <div
      className="rounded-lg min-w-[200px] shadow-md"
      style={{
        border: isEntry ? '2px solid #ef4444' : '1px solid hsl(var(--border))',
        minWidth: isEntry ? 260 : 200,
        boxShadow: isEntry
          ? '0 4px 20px rgba(239,68,68,0.25)'
          : '0 2px 8px rgba(0,0,0,0.08)',
      }}
    >
      <Handle type="target" position={Position.Left} />
      <div
        className="text-white font-semibold rounded-t-lg"
        style={{
          background: isEntry ? '#ef4444' : 'hsl(var(--primary))',
          padding: isEntry ? '12px 18px' : '10px 16px',
          fontSize: isEntry ? 16 : 14,
        }}
      >
        {data.label as string}
      </div>
      <div className="bg-card py-1">
        {fields.map((field: string, idx: number) => (
          <div
            key={idx}
            className="text-muted-foreground font-mono border-b border-border last:border-b-0"
            style={{
              padding: isEntry ? '8px 18px' : '6px 16px',
              fontSize: isEntry ? 14 : 13,
            }}
          >
            {field}
          </div>
        ))}
        {fields.length === 0 && (
          <div className="py-2 px-4 text-xs text-muted-foreground">
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

  edges.forEach((edge) => g.setEdge(edge.source, edge.target))
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
      return { ...node, position: { x: pos.x - width / 2, y: pos.y - height / 2 } }
    }),
    edges,
  }
}

// Direction options
const directionOptions = [
  { value: 'all', label: '全部' },
  { value: 'direct', label: '仅直接关联' },
  { value: 'upstream', label: '仅上游' },
  { value: 'downstream', label: '仅下游' },
]

export default function TableRelation() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [tableInfo, setTableInfo] = useState<any>(null)
  const [direction, setDirection] = useState('all')
  const [details, setDetails] = useState<any[]>([])
  const [rfNodes, setRfNodes, onNodesChange] = useNodesState<Node>([])
  const [rfEdges, setRfEdges, onEdgesChange] = useEdgesState<Edge>([])

  const fetchData = useCallback(
    async (dir: string) => {
      try {
        const tres: any = await tableApi.getById(Number(id))
        setTableInfo(tres.data)
        const apiDir = dir === 'direct' ? 'all' : dir
        const res: any = await relationApi.getRelations(Number(id), apiDir)
        let vo = res.data

        if (dir === 'direct') {
          const directNodeIds = new Set<string>()
          directNodeIds.add(String(id))
          const directEdges = (vo.edges || []).filter((e: any) => {
            if (String(e.source) === String(id)) { directNodeIds.add(String(e.target)); return true }
            if (String(e.target) === String(id)) { directNodeIds.add(String(e.source)); return true }
            return false
          })
          vo = {
            ...vo,
            nodes: (vo.nodes || []).filter((n: any) => directNodeIds.has(String(n.id))),
            edges: directEdges,
            details: (vo.details || []).filter(
              (d: any) => String(d.sourceTableId) === String(id) || String(d.targetTableId) === String(id),
            ),
          }
        }
        setDetails(vo.details || [])

        const nodes: Node[] = (vo.nodes || []).map((n: any) => {
          const fields = new Set<string>()
          ;(vo.edges || []).forEach((e: any) => {
            if (String(e.source) === String(n.id) || e.source === n.id) fields.add(e.sourceField)
            if (String(e.target) === String(n.id) || e.target === n.id) fields.add(e.targetField)
          })
          return {
            id: String(n.id),
            type: 'tableNode',
            data: { label: n.label, fields: Array.from(fields), isEntry: String(n.id) === String(id) },
            position: { x: 0, y: 0 },
          }
        })

        const edges: Edge[] = (vo.edges || []).map((e: any, idx: number) => ({
          id: `edge-${idx}`,
          source: String(e.source),
          target: String(e.target),
          label: `${e.sourceField} = ${e.targetField}`,
          type: 'smoothstep',
          markerEnd: { type: MarkerType.ArrowClosed, width: 16, height: 16 },
          style: { stroke: '#bbb', strokeWidth: 1.5 },
          labelStyle: { fontSize: 11, fill: '#666' },
          labelBgStyle: { fill: '#fff', fillOpacity: 0.9 },
        }))

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

  useEffect(() => { fetchData(direction) }, [fetchData, direction])

  const detailColumns: ColumnDef<any>[] = [
    { accessorKey: 'sourceTable', header: '左表' },
    { accessorKey: 'sourceField', header: '左字段' },
    { accessorKey: 'targetTable', header: '右表' },
    { accessorKey: 'targetField', header: '右字段' },
    { accessorKey: 'relationType', header: '关系类型', cell: ({ row }) => (
      <Badge variant="outline">{row.getValue('relationType')}</Badge>
    )},
    { accessorKey: 'methodSignature', header: '来源方法' },
  ]

  return (
    <div className="space-y-4">
      <AnimatedCard className="p-4">
        <div className="flex items-center gap-3 mb-4">
          <Button variant="ghost" onClick={() => navigate('/tables/list')}>
            <ArrowLeft className="size-4 mr-2" />
            返回
          </Button>
          <h1 className="text-lg font-semibold">
            {tableInfo?.tableName}
            <span className="text-muted-foreground font-normal ml-2">— 关联关系图</span>
          </h1>
        </div>
        <div className="flex gap-2 mb-4">
          {directionOptions.map((opt) => (
            <Button
              key={opt.value}
              variant={direction === opt.value ? 'default' : 'outline'}
              size="sm"
              onClick={() => setDirection(opt.value)}
            >
              {opt.label}
            </Button>
          ))}
        </div>
        <div className="border rounded-lg h-[500px] bg-background">
          <ReactFlow
            key={`${id}-${direction}`}
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
      </AnimatedCard>

      <AnimatedCard className="p-4">
        <h2 className="text-sm font-medium mb-3">关联详情</h2>
        <DataTable columns={detailColumns} data={details} pageSize={10} />
      </AnimatedCard>
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat: rewrite TableRelation with shadcn styling + ReactFlow preserved"
```

---

### Task 8: 重构 TableLogicQuery — Tabs + Select + 路径展示

**Files:**
- Modify: `src/pages/TableLogicQuery/index.tsx` (完整重写)

**Interfaces:**
- Consumes: `Tabs`, `Card`, `ShineInput`, `Button`, `Select`, `DataTable`, `Badge`, `AnimatedCard`
- Uses: `queryApi`, `projectApi`, `tableApi`, `fieldApi` (不变)

- [ ] **Step 1: 重写 TableLogicQuery/index.tsx**

```tsx
import { useState } from 'react'
import { type ColumnDef } from '@tanstack/react-table'
import { toast } from 'sonner'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { AnimatedCard } from '@/components/magicui/animated-card'
import { ShineInput } from '@/components/magicui/shine-input'
import { Button } from '@/components/ui/button'
import { DataTable } from '@/components/ui/data-table'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { Search } from 'lucide-react'
import { queryApi, projectApi, tableApi, fieldApi } from '../../services/api'

// --- FieldSearch Tab ---
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

  const columns: ColumnDef<any>[] = [
    { accessorKey: 'projectName', header: '项目', size: 150 },
    {
      accessorKey: 'tableName',
      header: '表名',
      size: 180,
      cell: ({ row }) => (
        <span className="font-mono text-sm">{row.getValue('tableName')}</span>
      ),
    },
    {
      accessorKey: 'fieldName',
      header: '字段名',
      size: 150,
      cell: ({ row }) => (
        <span className="font-mono text-sm">{row.getValue('fieldName')}</span>
      ),
    },
    {
      accessorKey: 'fieldComment',
      header: '备注',
      cell: ({ row }) => (
        <span className="text-muted-foreground">{row.getValue('fieldComment')}</span>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-sm">
          <ShineInput
            placeholder="输入业务关键词搜索字段"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
          />
        </div>
        <Button onClick={handleSearch}>
          <Search className="size-4 mr-2" />
          搜索
        </Button>
      </div>
      <DataTable columns={columns} data={data} loading={loading} pageSize={10} />
    </div>
  )
}

// --- PathQuery Tab ---
function PathQuery() {
  const [projects, setProjects] = useState<any[]>([])
  const [tables, setTables] = useState<any[]>([])
  const [projectId, setProjectId] = useState<string | undefined>()
  const [startTableId, setStartTableId] = useState<string | undefined>()
  const [targetTableId, setTargetTableId] = useState<string | undefined>()
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

  const handleProjectChange = (val: string) => {
    setProjectId(val)
    setStartTableId(undefined)
    setTargetTableId(undefined)
    setStartFieldName(undefined)
    setTargetFieldName(undefined)
    setStartFields([])
    setTargetFields([])
    loadTables(Number(val))
  }

  const handleStartTableChange = async (val: string) => {
    setStartTableId(val)
    setStartFieldName(undefined)
    if (val) {
      const res: any = await fieldApi.listByTable(Number(val))
      setStartFields(res.data || [])
    } else {
      setStartFields([])
    }
  }

  const handleTargetTableChange = async (val: string) => {
    setTargetTableId(val)
    setTargetFieldName(undefined)
    if (val) {
      const res: any = await fieldApi.listByTable(Number(val))
      setTargetFields(res.data || [])
    } else {
      setTargetFields([])
    }
  }

  const handleSearch = async () => {
    if (!projectId || !startTableId || !targetTableId) {
      toast.warning('请填写完整信息')
      return
    }
    setLoading(true)
    try {
      const res: any = await queryApi.findPath({
        projectId: Number(projectId),
        startTableId: Number(startTableId),
        startFieldName,
        targetTableId: Number(targetTableId),
        targetFieldName,
      })
      setPaths(res.data?.paths || [])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-4">
      <AnimatedCard className="p-4">
        <div className="flex flex-wrap items-center gap-3">
          <Select value={projectId} onValueChange={handleProjectChange}>
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder="选择项目" />
            </SelectTrigger>
            <SelectContent>
              {projects.map((p: any) => (
                <SelectItem key={p.id} value={String(p.id)}>{p.appName}</SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select value={startTableId} onValueChange={handleStartTableChange}>
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder="起始表" />
            </SelectTrigger>
            <SelectContent>
              {tables.map((t: any) => (
                <SelectItem key={t.id} value={String(t.id)}>{t.tableName}</SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select value={startFieldName} onValueChange={setStartFieldName} disabled={!startTableId}>
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder="起始字段" />
            </SelectTrigger>
            <SelectContent>
              {startFields.map((f: any) => (
                <SelectItem key={f.fieldName} value={f.fieldName}>{f.fieldName}</SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select value={targetTableId} onValueChange={handleTargetTableChange}>
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder="目标表" />
            </SelectTrigger>
            <SelectContent>
              {tables.map((t: any) => (
                <SelectItem key={t.id} value={String(t.id)}>{t.tableName}</SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select value={targetFieldName} onValueChange={setTargetFieldName} disabled={!targetTableId}>
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder="目标字段" />
            </SelectTrigger>
            <SelectContent>
              {targetFields.map((f: any) => (
                <SelectItem key={f.fieldName} value={f.fieldName}>{f.fieldName}</SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Button onClick={handleSearch} disabled={loading}>
            {loading ? '查询中...' : '查询路径'}
          </Button>
        </div>
      </AnimatedCard>

      {paths.map((path: any, idx: number) => (
        <AnimatedCard key={idx} className="p-4">
          <div className="flex items-center gap-2 mb-3">
            <Badge variant={idx === 0 ? 'default' : 'secondary'}>
              {idx === 0 ? '最短路径' : `路径 ${idx + 1}`} (长度: {path.length})
            </Badge>
          </div>
          <p className="text-sm font-mono">{path.nodes?.join(' → ')}</p>
          <p className="text-xs text-muted-foreground mt-2">
            {path.edges?.map((e: any, ei: number) => (
              <span key={ei}>
                {ei > 0 && ' | '}
                {e.from}.{e.joinField}={e.to}
              </span>
            ))}
          </p>
        </AnimatedCard>
      ))}
      {paths.length === 0 && !loading && (
        <p className="text-muted-foreground text-center py-8">
          暂无结果，请选择项目、起始表和目标表后查询
        </p>
      )}
    </div>
  )
}

// --- Main ---
export default function TableLogicQuery() {
  return (
    <Tabs defaultValue="field">
      <TabsList className="mb-4">
        <TabsTrigger value="field">按业务含义搜字段</TabsTrigger>
        <TabsTrigger value="path">路径查询</TabsTrigger>
      </TabsList>
      <TabsContent value="field">
        <FieldSearch />
      </TabsContent>
      <TabsContent value="path">
        <PathQuery />
      </TabsContent>
    </Tabs>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat: rewrite TableLogicQuery with shadcn Tabs + Select + Cards"
```

---

### Task 9: 添加 sonner Toast 提供者 + 清理 Ant Design 依赖

**Files:**
- Modify: `src/App.tsx`
- Modify: `package.json`

**Interfaces:**
- Produces: Toast 通知系统就绪, Ant Design 完全移除

- [ ] **Step 1: 在 App.tsx 中添加 Toaster**

修改 `src/App.tsx` 中，在 `BrowserRouter` 内添加 `<Toaster />`:

```tsx
import { Toaster } from 'sonner'

// 在 <BrowserRouter> 内，<AppLayout /> 之前或之后添加:
<Toaster position="top-right" richColors />
```

完整的 App.tsx 变为:

```tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { SidebarProvider, SidebarInset } from '@/components/ui/sidebar'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Toaster } from 'sonner'
import { AppSidebar } from '@/components/app-sidebar'
import TableInfoManage from './pages/TableInfoManage'
import TableDetail from './pages/TableInfoManage/TableDetail'
import TableRelation from './pages/TableRelation'
import TableLogicQuery from './pages/TableLogicQuery'

function AppLayout() {
  return (
    <TooltipProvider delayDuration={300}>
      <SidebarProvider defaultOpen>
        <AppSidebar />
        <SidebarInset>
          <div className="flex flex-1 flex-col gap-4 p-4 pt-4">
            <Routes>
              <Route path="/" element={<Navigate to="/tables/list" />} />
              <Route path="/tables/list" element={<TableInfoManage />} />
              <Route path="/tables/:id" element={<TableDetail />} />
              <Route path="/tables/:id/relations" element={<TableRelation />} />
              <Route path="/tables/query" element={<TableLogicQuery />} />
            </Routes>
          </div>
        </SidebarInset>
      </SidebarProvider>
    </TooltipProvider>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <Toaster position="top-right" richColors />
      <AppLayout />
    </BrowserRouter>
  )
}
```

- [ ] **Step 2: 移除 Ant Design 依赖**

```bash
cd /Applications/project/data-map/data-map-frontend && \
npm uninstall antd @ant-design/icons
```

- [ ] **Step 3: 最终验证**

```bash
cd /Applications/project/data-map/data-map-frontend && npm run dev
```

完整验证清单：
- [ ] 侧边栏：渲染正常、菜单可点击导航、可折叠/展开
- [ ] 表信息管理：搜索框可用、表格数据显示、编辑/删除弹窗正常
- [ ] 表详情：字段列表显示、下钻/收起功能、编辑字段注释
- [ ] 关系图谱：ReactFlow 渲染、方向切换按钮、关联详情表格
- [ ] 逻辑查询：Tab 切换、字段搜索、路径查询选择器
- [ ] 暗色/浅色主题：添加 `dark` class 到 `<html>` 可切换

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: add Toaster, remove antd, final cleanup"
```

---

### Task 10: 暗色模式切换 + 收尾优化

**Files:**
- Modify: `src/App.tsx`
- Create: `src/components/theme-toggle.tsx`
- Modify: `src/components/app-sidebar.tsx`

**Interfaces:**
- Produces: 主题切换按钮在侧边栏 footer 中

- [ ] **Step 1: 使用 next-themes 实现主题切换**

```bash
cd /Applications/project/data-map/data-map-frontend && npm install next-themes
```

注意：next-themes 虽名为 next，但也支持纯 React/Vite 项目。

- [ ] **Step 2: 创建主题 Provider 和 Toggle**

修改 `src/App.tsx` 包裹 `ThemeProvider`:

```tsx
import { ThemeProvider } from 'next-themes'

// 包裹 AppLayout
export default function App() {
  return (
    <BrowserRouter>
      <ThemeProvider attribute="class" defaultTheme="light" enableSystem={false}>
        <Toaster position="top-right" richColors />
        <AppLayout />
      </ThemeProvider>
    </BrowserRouter>
  )
}
```

- [ ] **Step 3: 在侧边栏 Footer 添加主题切换**

更新 `src/components/app-sidebar.tsx` 的 `SidebarFooter`:

```tsx
// 在 imports 中添加:
import { useTheme } from 'next-themes'
import { Sun, Moon } from 'lucide-react'

// 在 AppSidebar 组件中添加:
const { theme, setTheme } = useTheme()

// SidebarFooter 替换为:
<SidebarFooter>
  <SidebarMenu>
    <SidebarMenuItem>
      <SidebarMenuButton
        onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
        tooltip={theme === 'dark' ? '切换到浅色模式' : '切换到暗色模式'}
      >
        {theme === 'dark' ? <Sun className="size-4" /> : <Moon className="size-4" />}
        <span>{theme === 'dark' ? '浅色模式' : '暗色模式'}</span>
      </SidebarMenuButton>
    </SidebarMenuItem>
  </SidebarMenu>
  <div className="text-xs text-sidebar-foreground/40 px-2">
    Data Map v1.0
  </div>
</SidebarFooter>
```

- [ ] **Step 4: 最终验证 + Commit**

```bash
git add -A
git commit -m "feat: add dark/light theme toggle"
```

---

## Self-Review Checklist

1. **Spec coverage:**
   - [x] 可折叠侧边栏 (shadcn Sidebar) → Task 4
   - [x] 分组菜单 → Task 4
   - [x] 暗色/浅色双主题 → Task 10
   - [x] 现代化数据表格 (shadcn DataTable + TanStack Table) → Task 2, 5, 6, 7, 8
   - [x] 分页、行 hover、行操作按钮 → Task 2 (DataTable)
   - [x] Card 包裹内容 → Task 5, 6, 7, 8
   - [x] 高级搜索输入框 (ShineInput) → Task 3, 5
   - [x] MagicUI AnimatedCard → Task 3, 5, 6, 7, 8
   - [x] 动画增强 → Task 3

2. **Placeholder scan:** 无 TBD/TODO/占位符

3. **Type consistency:**
   - `DataTable` 接口: `columns: ColumnDef<TData, TValue>[]`, `data: TData[]`, `loading?: boolean`, `pageSize?: number` — 所有使用处一致
   - `AnimatedCard` 接口: `children: ReactNode`, `className?: string` — 所有使用处一致
   - `ShineInput` 接口: 继承 `InputProps` — 一致
   - API 函数签名: 所有 `tableApi.*`, `fieldApi.*`, `relationApi.*`, `queryApi.*` 保持不变
