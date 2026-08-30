import type {
  LogResponseDto,
  SSHServerModel,
  FileItem,
  RecentFileItem,
  SavedFilter,
  FavoriteItem,
  PreferencesMap,
  LogEntry
} from './types';

export const API = {
  async get<T>(endpoint: string, params: Record<string, any> = {}, signal?: AbortSignal): Promise<{ success: boolean; data?: T; message?: string }> {
    const url = new URL(endpoint, window.location.origin);
    Object.keys(params).forEach(key => {
      if (params[key] !== undefined && params[key] !== null) {
        url.searchParams.append(key, String(params[key]));
      }
    });
    const res = await fetch(url.toString(), { signal });
    return res.json();
  },


  async post<T>(endpoint: string, body: any = {}): Promise<{ success: boolean; data?: T; message?: string }> {
    const res = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    return res.json();
  },

  async delete<T>(endpoint: string): Promise<{ success: boolean; data?: T; message?: string }> {
    const res = await fetch(endpoint, { method: 'DELETE' });
    return res.json();
  },

  // System
  triggerGC: () => API.post<{ freedMB?: number; usedMemoryMB?: number; freedMemoryFormatted?: string }>('/api/system/gc'),
  getPreferences: () => API.get<PreferencesMap>('/api/preferences'),
  updatePreferences: (map: PreferencesMap) => API.post('/api/preferences', map),

  // Recent Files
  getRecentFiles: () => API.get<RecentFileItem[]>('/api/recent-files'),
  clearRecentFiles: () => API.delete('/api/recent-files'),
  deleteRecentFile: (id: string) => API.delete(`/api/recent-files/${encodeURIComponent(id)}`),

  // File System
  getHomeDirectory: () => API.get<string>('/api/fs/home'),
  browseLocalDirectory: (path: string) => API.get<FileItem[]>('/api/fs/local', { path }),
  browseRemoteDirectory: (serverId: string, path: string) => API.get<FileItem[]>('/api/fs/remote', { serverId, path }),
  getFavorites: (locationId?: string) => API.get<FavoriteItem[]>('/api/fs/favorites', locationId ? { locationId } : {}),
  addFavorite: (data: { name: string; path: string; locationId?: string; isRemote?: boolean; sshServerId?: string }) => API.post('/api/fs/favorites', data),
  deleteFavorite: (id: string | number) => API.delete(`/api/fs/favorites/${id}`),
  previewFile: (source: string, path: string, serverId?: string, lines = 80) =>
    API.get<string[]>('/api/fs/preview', { source, path, serverId, lines }),


  // Servers
  getSSHServers: () => API.get<SSHServerModel[]>('/api/servers'),
  createSSHServer: (server: Partial<SSHServerModel>) => API.post('/api/servers', server),
  updateSSHServer: (id: string, server: Partial<SSHServerModel>) => API.post('/api/servers', { ...server, id }),
  deleteSSHServer: (id: string) => API.delete(`/api/servers/${id}`),
  testSSHConnection: (server: Partial<SSHServerModel>) => API.post('/api/servers/test', server),

  // Filters
  getFilters: () => API.get<SavedFilter[]>('/api/filters'),
  saveFilter: (filter: Partial<SavedFilter>) => API.post('/api/filters', filter),
  deleteFilter: (id: string | number) => API.delete(`/api/filters/${id}`),

  // Logs
  openLogFile: (req: { source: string; path: string; serverId?: string }) => API.post<LogResponseDto>('/api/logs/open', req),
  getLogPage: (
    fileId: string,
    offset = 0,
    limit = 500,
    search?: string,
    level?: string,
    isRegex = false,
    caseSensitive = false,
    signal?: AbortSignal
  ) =>
    API.get<LogResponseDto>(
      '/api/logs/page',
      {
        fileId,
        offset,
        limit,
        search: search || undefined,
        level: level === 'ALL' ? undefined : level,
        isRegex: isRegex ? true : undefined,
        caseSensitive: caseSensitive ? true : undefined
      },
      signal
    ),
  stopTail: (fileId: string) => API.post(`/api/logs/stop-tail/${fileId}`),
  getExportUrl: (fileId: string, format: string) => `/api/logs/export?fileId=${encodeURIComponent(fileId)}&format=${encodeURIComponent(format)}`
};

export function subscribeLiveTail(
  fileId: string,
  onEvent: (event: { newEntries?: LogEntry[]; totalLines?: number }) => void,
  onError?: (err: any) => void
): () => void {
  const es = new EventSource(`/api/logs/tail?fileId=${encodeURIComponent(fileId)}`);
  
  let queue: LogEntry[] = [];
  let rafId: number | null = null;

  function flush() {
    rafId = null;
    if (queue.length > 0) {
      const batch = queue;
      queue = [];
      onEvent({ newEntries: batch });
    }
  }

  es.addEventListener('log', (event: MessageEvent) => {
    try {
      const data = JSON.parse(event.data);
      queue.push(data);
      if (rafId === null) {
        rafId = window.requestAnimationFrame(flush);
      }
    } catch (e) {
      console.error('SSE parse error', e);
    }
  });

  es.addEventListener('batch', (event: MessageEvent) => {
    try {
      const data = JSON.parse(event.data);
      onEvent(data);
    } catch (e) {
      console.error('SSE batch parse error', e);
    }
  });

  es.onerror = (err) => {
    if (onError) onError(err);
  };

  return () => {
    if (rafId !== null) {
      window.cancelAnimationFrame(rafId);
      rafId = null;
    }
    queue = [];
    es.close();
  };
}

