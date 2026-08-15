import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

api.interceptors.response.use(
  (res) => {
    if (res.data.code !== 0) {
      return Promise.reject(new Error(res.data.message || 'Request failed'))
    }
    return res.data
  },
  (err) => Promise.reject(err),
)

export const projectApi = {
  list: (keyword?: string) => api.get('/projects', { params: { keyword } }),
  create: (data: any) => api.post('/projects', data),
  update: (id: number, data: any) => api.put(`/projects/${id}`, data),
  delete: (id: number) => api.delete(`/projects/${id}`),
}

export const tableApi = {
  list: (params?: { projectId?: number; tableName?: string }) => api.get('/tables', { params }),
  getById: (id: number) => api.get(`/tables/${id}`),
  update: (id: number, tableComment: string) => api.put(`/tables/${id}`, { tableComment }),
  delete: (id: number) => api.delete(`/tables/${id}`),
}

export const fieldApi = {
  listByTable: (tableId: number) => api.get(`/tables/${tableId}/fields`),
  update: (id: number, fieldComment: string) => api.put(`/fields/${id}`, { fieldComment }),
  usageScenarios: (fieldId: number) => api.get(`/fields/${fieldId}/usage-scenarios`),
}

export const relationApi = {
  getRelations: (tableId: number) =>
    api.get(`/relations/${tableId}`),
}

export const queryApi = {
  searchFields: (keyword?: string, projectId?: number) =>
    api.get('/query/search-fields', { params: { keyword, projectId } }),
  findPath: (data: any) => api.post('/query/find-path', data),
}

export default api
