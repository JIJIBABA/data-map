import type { ThemeConfig } from 'antd'

// 单一强调色：沿用项目既有主色蓝，全站锁死不再散落其它色
export const COLOR_PRIMARY = '#1677ff'

// 中性色（侧边栏 off-black，非纯黑；正文灰保持 WCAG AA 对比度）
export const SIDER_BG = '#111827'

export const FONT_SANS =
  "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Arial, sans-serif"

export const FONT_MONO =
  "'SF Mono', 'JetBrains Mono', Menlo, Consolas, 'Courier New', monospace"

// 语义化 Tag 色（仅用于「操作类型 / 调用分层」的数据编码，非装饰）
export const OP_COLORS: Record<string, string> = {
  READ: 'blue',
  WRITE: 'green',
  UPDATE: 'orange',
  DELETE: 'red',
  UNRESOLVED: 'default',
}

export const LAYER_COLORS: Record<string, string> = {
  CONTROLLER: 'blue',
  MQ: 'purple',
  SCHEDULED: 'gold',
  SERVICE: 'cyan',
  MAPPER: 'geekblue',
  OTHER: 'default',
}

export const themeConfig: ThemeConfig = {
  token: {
    colorPrimary: COLOR_PRIMARY,
    colorInfo: COLOR_PRIMARY,
    colorLink: COLOR_PRIMARY,
    borderRadius: 8,
    fontSize: 14,
    fontFamily: FONT_SANS,
    // 提升次要/占位文字对比度，满足 WCAG AA（默认 0.45 偏灰）
    colorTextSecondary: 'rgba(0, 0, 0, 0.65)',
    colorTextTertiary: 'rgba(0, 0, 0, 0.45)',
    colorTextPlaceholder: 'rgba(0, 0, 0, 0.45)',
    colorBgLayout: '#f5f6f8',
  },
  components: {
    Layout: {
      siderBg: SIDER_BG,
      bodyBg: '#f5f6f8',
      triggerBg: '#0b1220',
      triggerColor: 'rgba(255, 255, 255, 0.68)',
    },
    Menu: {
      darkItemBg: SIDER_BG,
      darkSubMenuItemBg: '#0b1220',
      darkItemColor: 'rgba(255, 255, 255, 0.68)',
      darkItemHoverColor: '#ffffff',
      darkItemHoverBg: 'rgba(255, 255, 255, 0.08)',
      darkItemSelectedBg: COLOR_PRIMARY,
      darkItemSelectedColor: '#ffffff',
    },
    Table: {
      headerBg: '#fafafa',
      headerColor: 'rgba(0, 0, 0, 0.88)',
    },
  },
}
