export interface LogEntry {
  lineNumber: number;
  endLineNumber?: number;
  rawLog?: string;
  message?: string;
  level?: string;
  timestamp?: string;
  thread?: string;
  logger?: string;
}

export interface LogResponseDto {
  fileId: string;
  fileName: string;
  filePath: string;
  source: string;
  totalLines: number;
  filteredLines: number;
  entries: LogEntry[];
}

export interface SSHServerModel {
  id: string;
  name: string;
  host: string;
  port: number;
  username: string;
  password?: string;
  defaultPath?: string;
}

export interface FileItem {
  name: string;
  path: string;
  size: number;
  directory?: boolean;
  isDirectory?: boolean;
  file?: boolean;
  isFile?: boolean;
  formattedSize?: string;
  lastModified?: string;
  modified?: string;
  logFile?: boolean;
  typeDescription?: string;
}


export interface RecentFileItem {
  id: string;
  fileId: string;
  lastOpened: string;
  serverName?: string;
  logFile?: {
    id: string;
    name: string;
    filePath: string;
    size?: string;
    isRemote: boolean;
    sshServerID?: string;
  };
}

export interface SavedFilter {
  id: number;
  name: string;
  pattern: string;
  level?: string;
  regex?: boolean;
  caseSensitive?: boolean;
}

export interface FavoriteItem {
  id: number;
  name: string;
  path: string;
  locationId: string;
}

export interface PreferencesMap {
  [key: string]: string;
}
