<script lang="ts">
  import { onMount } from 'svelte';
  import { activeModal } from '../../stores/appState';
  import { API } from '../../api';
  import { toast } from '../../stores/toast';
  import type { SSHServerModel, FileItem, FavoriteItem } from '../../types';
  import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogDescription,
    DialogFooter
  } from '../ui/dialog';
  import { Button } from '../ui/button';
  import { Input } from '../ui/input';
  import { Badge } from '../ui/badge';
  import { Tabs, TabsList, TabsTrigger } from '../ui/tabs';
  import { Separator } from '../ui/separator';
  import {
    Folder,
    FileText,
    ChevronRight,
    ArrowUp,
    Star,
    Plus,
    Trash2,
    RefreshCw,
    Server,
    Globe
  } from 'lucide-svelte';

  export let onOpenFile: (req: { path: string; source: string; serverId?: string }) => void = () => {};

  let currentLocation: 'LOCAL' | 'REMOTE' = 'LOCAL';
  let currentPath: string = '/';
  let pathInput: string = '/';
  let files: FileItem[] = [];
  let loading: boolean = false;
  let filterText: string = '';

  let servers: SSHServerModel[] = [];
  let selectedServerId: string = '';

  let favorites: FavoriteItem[] = [];
  let selectedFile: FileItem | null = null;
  let previewLines: string[] = [];
  let loadingPreview: boolean = false;

  let errorMessage: string = '';

  let userHomePath: string = '';

  $: isHomeActive = currentLocation === 'LOCAL' && !selectedServerId && (currentPath === userHomePath || currentPath === userHomePath + '/');
  $: isAppLogsActive = currentLocation === 'LOCAL' && !selectedServerId && currentPath.includes('/javafx/seeloggyplus/logs');
  $: isRootActive = currentLocation === 'LOCAL' && !selectedServerId && currentPath === '/';

  let isOpen = false;
  $: isOpen = $activeModal === 'file-manager';


  function handleOpenChange(open: boolean) {
    if (!open) activeModal.set(null);
  }

  onMount(async () => {
    await loadInitialData();
  });

  async function loadInitialData() {
    try {
      const sRes = await API.getSSHServers();
      if (sRes.success && sRes.data) servers = sRes.data;

      const hRes = await API.getHomeDirectory();
      if (hRes.success && hRes.data) {
        userHomePath = hRes.data;
        currentPath = hRes.data;
        pathInput = hRes.data;
      }
      await navigate(currentPath);
      await loadFavorites();
    } catch (e) {
      console.error(e);
    }
  }


  async function loadFavorites() {
    try {
      const loc = currentLocation === 'REMOTE' ? (selectedServerId || 'REMOTE') : 'LOCAL';
      const res = await API.getFavorites(loc);
      if (res.success && res.data) favorites = res.data;
    } catch (e) {
      console.error(e);
    }
  }

  function isDir(item?: FileItem | null): boolean {
    if (!item) return false;
    return Boolean(item.directory || item.isDirectory);
  }

  async function navigate(targetPath: string) {
    loading = true;
    currentPath = targetPath;
    pathInput = targetPath;
    selectedFile = null;
    previewLines = [];
    files = [];
    errorMessage = '';
    try {
      if (currentLocation === 'LOCAL') {
        const res = await API.browseLocalDirectory(targetPath);
        if (res.success && res.data) {
          files = Array.isArray(res.data) ? res.data : (res.data as any).files || [];
        } else {
          files = [];
          errorMessage = res.message || `Failed to load directory: ${targetPath}`;
          toast.error(errorMessage);
        }
      } else {
        if (!selectedServerId) {
          files = [];
          errorMessage = 'Select an SSH server first';
          loading = false;
          return;
        }
        const res = await API.browseRemoteDirectory(selectedServerId, targetPath);
        if (res.success && res.data) {
          files = Array.isArray(res.data) ? res.data : (res.data as any).files || [];
        } else {
          files = [];
          errorMessage = res.message || 'Connection refused or unreachable on SSH server';
          toast.error(errorMessage);
        }
      }
    } catch (e: any) {
      files = [];
      errorMessage = e?.message || `Failed to load directory: ${targetPath}`;
      toast.error(errorMessage);
    } finally {
      loading = false;
    }
  }


  function goUp() {
    if (!currentPath || currentPath === '/') return;
    const parts = currentPath.split('/').filter(Boolean);
    parts.pop();
    const up = '/' + parts.join('/');
    navigate(up || '/');
  }

  async function addFavorite() {
    if (!currentPath) return;
    const name = currentPath.split('/').filter(Boolean).pop() || currentPath;
    const loc = currentLocation === 'REMOTE' ? (selectedServerId || 'REMOTE') : 'LOCAL';
    await API.addFavorite({
      name,
      path: currentPath,
      isRemote: currentLocation === 'REMOTE',
      sshServerId: selectedServerId || undefined
    });
    toast.success('Added to favorites');
    loadFavorites();
  }

  async function removeFavorite(id: string, e: MouseEvent) {
    e.stopPropagation();
    await API.deleteFavorite(id);
    toast.info('Favorite removed');
    loadFavorites();
  }

  async function loadPreview(file: FileItem) {
    selectedFile = file;
    loadingPreview = true;
    previewLines = [];
    try {
      const res = await API.previewFile(currentLocation, file.path, selectedServerId || undefined, 50);
      if (res.success && res.data) {
        previewLines = res.data;
      }
    } catch (e) {
      console.error('Failed to preview file', e);
    } finally {
      loadingPreview = false;
    }
  }

  function handleFileClick(item: FileItem) {
    if (isDir(item)) {
      navigate(item.path);
    } else {
      loadPreview(item);
    }
  }

  function handleFileDblClick(item: FileItem) {
    if (isDir(item)) {
      navigate(item.path);
    } else {
      openSelected(item.path);
    }
  }


  function openSelected(path?: string) {
    const p = path || selectedFile?.path || pathInput;
    if (!p) {
      toast.warning('Please select a log file');
      return;
    }
    onOpenFile({
      path: p,
      source: currentLocation,
      serverId: selectedServerId || undefined
    });
    activeModal.set(null);
  }

  $: filteredFiles = files.filter(f => {
    if (!filterText) return true;
    return f.name.toLowerCase().includes(filterText.toLowerCase());
  });

  async function switchLocation(loc: 'LOCAL' | 'REMOTE') {
    currentLocation = loc;
    if (loc === 'REMOTE') {
      try {
        const sRes = await API.getSSHServers();
        if (sRes.success && sRes.data) servers = sRes.data;
        if (!selectedServerId && servers.length > 0) {
          selectedServerId = servers[0].id;
        }
        if (selectedServerId) {
          const s = servers.find(x => x.id === selectedServerId);
          await navigate(s?.defaultPath || '/var/log');
        } else {
          files = [];
        }
      } catch (e) {
        console.error(e);
      }
    } else {
      selectedServerId = '';
      await navigate(userHomePath || '/');
    }
    await loadFavorites();
  }
</script>


<Dialog open={isOpen} onOpenChange={handleOpenChange}>
  <DialogContent class="max-w-5xl h-[640px] flex flex-col p-0 gap-0 overflow-hidden border-border">
    <!-- Header -->
    <DialogHeader class="p-4 border-b border-border bg-muted/20 shrink-0">
      <div class="flex items-center justify-between">
        <DialogTitle class="text-sm font-semibold flex items-center gap-2">
          <Folder class="w-4 h-4 text-primary" />
          <span>Unified Log File Manager</span>
        </DialogTitle>
        <div class="mr-6">
          <Tabs value={currentLocation} onValueChange={(val) => switchLocation(val === 'REMOTE' ? 'REMOTE' : 'LOCAL')}>
            <TabsList class="h-7">
              <TabsTrigger value="LOCAL" class="text-xs px-2.5">Local Storage</TabsTrigger>
              <TabsTrigger value="REMOTE" class="text-xs px-2.5 flex items-center gap-1">
                <Server class="w-3 h-3 text-sky-500" />
                <span>Remote SSH</span>
              </TabsTrigger>
            </TabsList>
          </Tabs>
        </div>
      </div>
      <DialogDescription class="text-xs mt-1">
        Browse, bookmark, and inspect local logs or stream live from remote SSH Linux servers.
      </DialogDescription>
    </DialogHeader>

    <!-- Path Bar -->
    <div class="px-4 py-2 border-b border-border flex items-center gap-2 bg-background shrink-0">
      <Button variant="outline" size="icon" class="h-7 w-7 shrink-0" on:click={goUp} title="Go to parent folder">
        <ArrowUp class="w-3.5 h-3.5" />
      </Button>

      <!-- Path Input -->
      <div class="relative flex-1">
        <Input
          bind:value={pathInput}
          on:keydown={(e) => e.key === 'Enter' && navigate(pathInput)}
          class="h-7 text-xs font-mono pr-14"
        />
        <Button
          variant="ghost"
          size="xs"
          class="absolute right-1 top-1 h-5 text-[10px] text-muted-foreground hover:text-foreground"
          on:click={() => navigate(pathInput)}
        >
          Go
        </Button>
      </div>

      <Button variant="outline" size="icon" class="h-7 w-7 shrink-0" on:click={() => navigate(currentPath)} title="Refresh">
        <RefreshCw class="w-3.5 h-3.5 {loading ? 'animate-spin' : ''}" />
      </Button>

      <Button variant="outline" size="sm" class="h-7 gap-1 text-xs shrink-0" on:click={addFavorite} title="Bookmark current folder">
        <Star class="w-3.5 h-3.5 text-amber-500" />
        <span>Bookmark</span>
      </Button>
    </div>

    <!-- Body Layout -->
    <div class="flex-1 flex overflow-hidden">
      <!-- Left Sidebar (Locations & Bookmarks) -->
      <div class="w-52 border-r border-border p-3 flex flex-col gap-3 bg-muted/20 select-none overflow-y-auto shrink-0">
        <!-- Remote Server Picker -->
        {#if currentLocation === 'REMOTE'}
          <div>
            <div class="text-[10px] font-semibold text-muted-foreground mb-1 tracking-wider uppercase">SSH Servers</div>
            {#if servers.length === 0}
              <div class="text-xs text-muted-foreground p-2 text-center border border-dashed border-border rounded-sm">
                No servers configured.
              </div>
            {:else}
              <select
                bind:value={selectedServerId}
                on:change={() => { const s = servers.find(x => x.id === selectedServerId); navigate(s?.defaultPath || '/var/log'); loadFavorites(); }}
                class="w-full bg-background border border-border text-foreground text-xs p-1.5 rounded-[var(--radius-sm)] outline-none"
              >
                <option value="">Select SSH Server...</option>
                {#each servers as s}
                  <option value={s.id}>{s.name} ({s.host})</option>
                {/each}
              </select>
            {/if}
          </div>
        {/if}

        <!-- Quick Locations -->
        <div>
          <div class="text-[10px] font-semibold text-muted-foreground mb-1 tracking-wider uppercase">
            {currentLocation === 'REMOTE' ? 'Remote Locations' : 'Quick Locations'}
          </div>
          <div class="flex flex-col gap-0.5">
            {#if currentLocation === 'REMOTE'}
              {@const currentServer = servers.find(x => x.id === selectedServerId)}
              {@const serverDefPath = currentServer?.defaultPath || '/var/log'}
              <button
                class="w-full flex items-center gap-1.5 px-2 py-1.5 rounded-[var(--radius-sm)] text-xs text-left cursor-pointer transition-all {currentPath === serverDefPath ? 'bg-primary/15 font-semibold text-foreground' : 'hover:bg-accent text-foreground'}"
                on:click={() => navigate(serverDefPath)}
              >
                <Folder class="w-3.5 h-3.5 text-sky-500 shrink-0" />
                <span class="truncate">Default Log Dir</span>
              </button>

              <button
                class="w-full flex items-center gap-1.5 px-2 py-1.5 rounded-[var(--radius-sm)] text-xs text-left cursor-pointer transition-all {currentPath === '/var/log' ? 'bg-primary/15 font-semibold text-foreground' : 'hover:bg-accent text-foreground'}"
                on:click={() => navigate('/var/log')}
              >
                <Folder class="w-3.5 h-3.5 text-sky-500 shrink-0" />
                <span class="truncate">/var/log</span>
              </button>

              <button
                class="w-full flex items-center gap-1.5 px-2 py-1.5 rounded-[var(--radius-sm)] text-xs text-left cursor-pointer transition-all {currentPath === '/' ? 'bg-primary/15 font-semibold text-foreground' : 'hover:bg-accent text-foreground'}"
                on:click={() => navigate('/')}
              >
                <Folder class="w-3.5 h-3.5 text-sky-500 shrink-0" />
                <span class="truncate">Remote Root /</span>
              </button>
            {:else}
              <button
                class="w-full flex items-center gap-1.5 px-2 py-1.5 rounded-[var(--radius-sm)] text-xs text-left cursor-pointer transition-all {isHomeActive ? 'bg-primary/15 font-semibold text-foreground' : 'hover:bg-accent text-foreground'}"
                on:click={async () => { const h = await API.getHomeDirectory(); navigate(h.data || '/'); }}
              >
                <Folder class="w-3.5 h-3.5 text-primary shrink-0" />
                <span class="truncate">User Home</span>
              </button>

              <button
                class="w-full flex items-center gap-1.5 px-2 py-1.5 rounded-[var(--radius-sm)] text-xs text-left cursor-pointer transition-all {isAppLogsActive ? 'bg-primary/15 font-semibold text-foreground' : 'hover:bg-accent text-foreground'}"
                on:click={() => navigate('/run/media/arief/work/desktop/javafx/seeloggyplus/logs')}
              >
                <Folder class="w-3.5 h-3.5 text-primary shrink-0" />
                <span class="truncate">App Logs Folder</span>
              </button>

              <button
                class="w-full flex items-center gap-1.5 px-2 py-1.5 rounded-[var(--radius-sm)] text-xs text-left cursor-pointer transition-all {isRootActive ? 'bg-primary/15 font-semibold text-foreground' : 'hover:bg-accent text-foreground'}"
                on:click={() => navigate('/')}
              >
                <Folder class="w-3.5 h-3.5 text-primary shrink-0" />
                <span class="truncate">Root /</span>
              </button>
            {/if}
          </div>
        </div>

        <Separator />

        <!-- Bookmarks / Favorites -->
        <div class="flex-1">
          <div class="text-[10px] font-semibold text-muted-foreground mb-1 tracking-wider uppercase">Bookmarks</div>
          {#if favorites.length === 0}
            <div class="text-[11px] text-muted-foreground p-2">No bookmarked folders yet</div>
          {:else}
            <div class="flex flex-col gap-1">
              {#each favorites as f}
                {@const isFavActive = currentPath === f.path}
                <div
                  role="button"
                  tabindex="0"
                  class="flex items-center justify-between px-2 py-1.5 rounded-[var(--radius-sm)] text-xs cursor-pointer group transition-all {isFavActive ? 'bg-primary/15 font-semibold text-foreground' : 'hover:bg-accent'}"
                  on:click={() => {
                    if (f.sshServerId) {
                      currentLocation = 'REMOTE';
                      selectedServerId = f.sshServerId;
                    }
                    navigate(f.path);
                  }}
                  on:keydown={(e) => e.key === 'Enter' && navigate(f.path)}
                >

                  <span class="truncate font-medium text-foreground">{f.name}</span>
                  <button
                    class="opacity-0 group-hover:opacity-100 text-muted-foreground hover:text-destructive p-0.5"
                    on:click={(e) => removeFavorite(f.id, e)}
                  >
                    <Trash2 class="w-3 h-3" />
                  </button>
                </div>
              {/each}
            </div>
          {/if}
        </div>

      </div>


      <!-- Center File Browser -->
      <div class="flex-1 flex flex-col bg-background overflow-hidden">
        <!-- Filter Box -->
        <div class="p-2 border-b border-border bg-background flex items-center gap-2 shrink-0">
          <Input
            type="text"
            placeholder="Search files in directory..."
            bind:value={filterText}
            class="h-7 text-xs"
          />
          <Badge variant="outline" class="font-mono text-xs px-2 h-7">
            {filteredFiles.length} items
          </Badge>
        </div>

        <!-- File List -->
        <div class="flex-1 overflow-y-auto p-2">
          {#if loading}
            <div class="h-full flex items-center justify-center text-muted-foreground text-xs">
              <RefreshCw class="w-4 h-4 animate-spin mr-2" />
              <span>Scanning directory...</span>
            </div>
          {:else if errorMessage}
            <div class="h-full flex flex-col items-center justify-center gap-2 text-destructive text-xs p-6 text-center select-none">
              <div class="p-2.5 rounded-full bg-destructive/10 text-destructive mb-1">
                <Server class="w-7 h-7" />
              </div>
              <span class="font-semibold text-foreground text-sm">Connection Failed</span>
              <span class="text-muted-foreground max-w-md text-xs">{errorMessage}</span>
              <div class="flex items-center gap-2 mt-2">
                <Button variant="outline" size="xs" class="gap-1" on:click={() => navigate(currentPath)}>
                  <RefreshCw class="w-3.5 h-3.5" />
                  <span>Retry</span>
                </Button>
                <Button variant="default" size="xs" class="gap-1" on:click={() => activeModal.set('server-manager')}>
                  <span>Check Server Settings</span>
                </Button>
              </div>
            </div>
          {:else if currentLocation === 'REMOTE' && servers.length === 0}
            <div class="h-full flex flex-col items-center justify-center gap-2 text-muted-foreground text-xs p-6 text-center select-none">
              <Server class="w-8 h-8 text-muted-foreground/50" />
              <span class="font-semibold text-foreground">No SSH Servers Configured</span>
              <span class="text-muted-foreground text-[11px]">Configure an SSH server to browse and tail remote server logs.</span>
              <Button variant="default" size="xs" class="gap-1 mt-1" on:click={() => activeModal.set('server-manager')}>
                <Plus class="w-3.5 h-3.5" />
                <span>Configure SSH Server</span>
              </Button>
            </div>
          {:else if filteredFiles.length === 0}
            <div class="h-full flex items-center justify-center text-muted-foreground text-xs">
              Folder is empty or no files match search
            </div>

          {:else}
            <div class="grid grid-cols-1 gap-0.5">
              {#each filteredFiles as file}
                {@const isSel = selectedFile?.path === file.path}
                <div
                  role="button"
                  tabindex="0"
                  class="flex items-center justify-between px-2.5 py-1.5 rounded-[var(--radius-sm)] text-xs cursor-pointer border transition-colors {isSel ? 'bg-primary text-primary-foreground font-semibold border-primary' : 'hover:bg-accent text-foreground border-transparent'}"
                  on:click={() => handleFileClick(file)}
                  on:dblclick={() => handleFileDblClick(file)}
                  on:keydown={(e) => e.key === 'Enter' && handleFileClick(file)}
                >
                  <div class="flex items-center gap-2 truncate">
                    {#if isDir(file)}
                      <Folder class="w-4 h-4 text-amber-400 shrink-0" />
                    {:else}
                      <FileText class="w-4 h-4 text-blue-400 shrink-0" />
                    {/if}
                    <span class="truncate">{file.name}</span>
                  </div>
                  <div class="flex items-center gap-3 text-[11px] opacity-80 shrink-0 font-mono">
                    {#if !isDir(file)}
                      <span>{file.formattedSize || ''}</span>
                    {/if}
                    <span>{file.lastModified || ''}</span>
                  </div>
                </div>
              {/each}
            </div>
          {/if}
        </div>
      </div>

      <!-- Right Log Preview Drawer -->
      <div class="w-72 border-l border-border bg-muted/10 flex flex-col overflow-hidden shrink-0">
        <div class="p-2 border-b border-border bg-muted/20 flex items-center justify-between text-muted-foreground shrink-0">
          <span class="font-semibold text-[10.5px]">Preview (Last 50 lines)</span>
          {#if selectedFile && !isDir(selectedFile)}
            <span class="font-mono text-[9.5px]">{selectedFile.formattedSize || ''}</span>
          {/if}
        </div>
        <div class="flex-1 overflow-auto p-2 font-mono text-[10.5px] leading-tight text-foreground select-text whitespace-pre bg-background">
          {#if loadingPreview}
            <div class="h-full flex items-center justify-center text-muted-foreground text-xs">
              <RefreshCw class="w-3.5 h-3.5 animate-spin mr-1.5" />
              <span>Loading preview...</span>
            </div>
          {:else if selectedFile && !isDir(selectedFile)}
            {#if previewLines.length > 0}
              {#each previewLines as line}
                <div class="py-0.5 truncate">{line}</div>
              {/each}
            {:else}
              <div class="h-full flex items-center justify-center text-muted-foreground text-xs p-3 text-center">
                Empty file or no preview available
              </div>
            {/if}
          {:else}
            <div class="h-full flex items-center justify-center text-muted-foreground text-xs p-4 text-center">
              Select any log file to preview contents
            </div>
          {/if}
        </div>
      </div>

    </div>

    <!-- Footer -->
    <DialogFooter class="p-3 border-t border-border bg-muted/20 flex items-center justify-between shrink-0">
      <div class="text-xs text-muted-foreground truncate max-w-md font-mono">
        {#if selectedFile}
          Selected: {selectedFile.path} ({selectedFile.formattedSize || ''})
        {:else}
          Tip: Double click any log file to open immediately
        {/if}
      </div>
      <div class="flex items-center gap-2">
        <Button variant="outline" size="sm" on:click={() => activeModal.set(null)}>
          Cancel
        </Button>
        <Button variant="default" size="sm" on:click={() => openSelected()}>
          Open Log File
        </Button>
      </div>
    </DialogFooter>
  </DialogContent>
</Dialog>

