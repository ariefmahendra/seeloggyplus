import { writable } from 'svelte/store';
import type { LogResponseDto, LogEntry, SSHServerModel, FavoriteItem, PreferencesMap, RecentFileItem } from '../types';
import { API } from '../api';

export const currentFile = writable<{
  fileId: string;
  fileName: string;
  filePath: string;
  source: string;
  serverId?: string;
  totalLines: number;
} | null>(null);

export const activeEntries = writable<LogEntry[]>([]);
export const selectedEntry = writable<LogEntry | null>(null);
export const selectedLogEntry = selectedEntry;
export const selectedRowIndex = writable<number>(-1);

export const searchQuery = writable<string>('');
export const isRegex = writable<boolean>(false);
export const caseSensitive = writable<boolean>(false);
export const activeLevel = writable<string>('ALL');

export const isTailing = writable<boolean>(false);
export const autoScroll = writable<boolean>(false);

export const totalLinesCount = writable<number>(0);
export const filteredLinesCount = writable<number>(0);
export const visibleLinesCount = filteredLinesCount;

export const leftPanelVisible = writable<boolean>(true);
export const bottomPanelVisible = writable<boolean>(true);
export const searchPanelVisible = writable<boolean>(false);


export const currentTheme = writable<string>(localStorage.getItem('seeloggy_theme') || 'dark');

export const activeModal = writable<string | null>(null);

// Centralized Recent Files Store
export const recentFilesStore = writable<RecentFileItem[]>([]);

// Centralized SSH Servers Store
export const sshServersStore = writable<SSHServerModel[]>([]);

export async function refreshSSHServers(): Promise<SSHServerModel[]> {
  try {
    const res = await API.getSSHServers();
    if (res.success && res.data) {
      sshServersStore.set(res.data);
      return res.data;
    }
  } catch (e) {
    console.error('Failed to refresh SSH servers', e);
  }
  return [];
}


export async function refreshRecentFiles() {
  try {
    const res = await API.getRecentFiles();
    if (res.success && res.data) {
      recentFilesStore.set(res.data);
    }
  } catch (e) {
    console.error('Failed to refresh recent files', e);
  }
}

export async function removeRecentFile(fileId: string) {
  // Optimistic update: remove immediately from store
  recentFilesStore.update(items => items.filter(item => {
    const lf = item.logFile;
    if (lf && (lf.id === fileId || lf.filePath === fileId)) return false;
    if (item.id === fileId || item.fileId === fileId) return false;
    return true;
  }));

  try {
    const res = await API.deleteRecentFile(fileId);
    if (!res.success) {
      refreshRecentFiles();
    }
  } catch (e) {
    console.error('Failed to delete recent file', e);
    refreshRecentFiles();
  }
}

export async function clearAllRecentFiles() {
  recentFilesStore.set([]);
  try {
    await API.clearRecentFiles();
  } catch (e) {
    console.error('Failed to clear recent files', e);
    refreshRecentFiles();
  }
}


// Active SSE tail event source
let activeTailEs: EventSource | null = null;

export function setTailEventSource(es: EventSource | null) {
  if (activeTailEs && activeTailEs !== es) {
    activeTailEs.close();
  }
  activeTailEs = es;
}

export function getTailEventSource(): EventSource | null {
  return activeTailEs;
}

