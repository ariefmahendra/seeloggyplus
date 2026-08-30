<script lang="ts">
  import { onMount } from 'svelte';
  import {
    currentFile,
    leftPanelVisible,
    bottomPanelVisible,
    searchQuery,
    searchPanelVisible,
    selectedEntry,
    activeModal,
    isTailing,
    refreshRecentFiles
  } from './lib/stores/appState';

  import { toast } from './lib/stores/toast';
  import { API, subscribeLiveTail } from './lib/api';

  // shadcn UI Components
  import { ResizablePaneGroup, ResizablePane, ResizableHandle } from './lib/components/ui/resizable';
  import ToastContainer from './lib/components/ui/ToastContainer.svelte';

  // Application Layout & Feature Components
  import Menubar from './lib/components/Menubar.svelte';
  import Toolbar from './lib/components/Toolbar.svelte';
  import RecentFilesSidebar from './lib/components/RecentFilesSidebar.svelte';
  import VirtualLogTable from './lib/components/VirtualLogTable.svelte';
  import SearchResultPanel from './lib/components/SearchResultPanel.svelte';
  import DetailPanel from './lib/components/DetailPanel.svelte';
  import StatusBar from './lib/components/StatusBar.svelte';

  // Modal Dialogs
  import FileManagerModal from './lib/components/modals/FileManagerModal.svelte';
  import ServerManagerModal from './lib/components/modals/ServerManagerModal.svelte';
  import FilterManagerModal from './lib/components/modals/FilterManagerModal.svelte';
  import PreferencesModal from './lib/components/modals/PreferencesModal.svelte';
  import GotoLineModal from './lib/components/modals/GotoLineModal.svelte';
  import AboutModal from './lib/components/modals/AboutModal.svelte';
  import ShortcutsModal from './lib/components/modals/ShortcutsModal.svelte';

  let logTableRef: any;
  let recentFilesRef: any;
  let tailUnsubscribe: (() => void) | null = null;

  let matchCurrent = 0;
  let matchTotal = 0;

  function handleSelectMatch(item: { lineNumber: number; rawLog: string; index: number }) {
    if (logTableRef) {
      logTableRef.scrollToLine(item.lineNumber);
      selectedEntry.set({ lineNumber: item.lineNumber, rawLog: item.rawLog, message: item.rawLog, parsed: false, parsedFields: {} });
    }
    matchCurrent = item.index + 1;
    toast.info(`Jumped to line #${item.lineNumber.toLocaleString()}`);
  }


  onMount(() => {
    refreshRecentFiles();

    // Keyboard shortcuts
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'o') {
        e.preventDefault();
        activeModal.set('file-manager');
      } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'b') {
        e.preventDefault();
        leftPanelVisible.update(v => !v);
      } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'j') {
        e.preventDefault();
        bottomPanelVisible.update(v => !v);
      } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'g') {
        e.preventDefault();
        activeModal.set('goto-line');
      } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'm') {
        e.preventDefault();
      } else if ((e.ctrlKey || e.metaKey) && (e.key === 't' || e.key === 'T')) {
        e.preventDefault();
        handleToggleTail();
      } else if (e.key === 'F5') {
        e.preventDefault();
        handleReload();
      } else if (e.key === 'F1') {
        e.preventDefault();
        activeModal.set('shortcuts');
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      if (tailUnsubscribe) tailUnsubscribe();
    };
  });

  async function handleOpenFile(req: { path: string; source: string; serverId?: string; tail?: boolean }) {
    try {
      if (tailUnsubscribe) {
        tailUnsubscribe();
        tailUnsubscribe = null;
        isTailing.set(false);
      }

      toast.info(`Opening ${req.path.split('/').pop()}...`);
      const res = await API.openLogFile(req);
      if (res.success && res.data) {
        currentFile.set(res.data);
        toast.success(`Loaded ${res.data.fileName} (${res.data.totalLines.toLocaleString()} lines)`);
        
        if (logTableRef) {
          logTableRef.resetAndReload(res.data.entries);
        }
        await refreshRecentFiles();

        if (req.tail) {
          setTimeout(() => {
            handleToggleTail();
            autoScroll.set(true);
          }, 150);
        }
      } else {
        toast.error(res.message || 'Failed to open log file');
      }
    } catch (e: any) {
      toast.error(e.message || 'Error opening file');
    }
  }



  function handleSearchChange() {
    if (logTableRef) {
      logTableRef.applyFilters();
    }
  }

  function handleReload() {
    if (logTableRef) {
      logTableRef.reload();
      toast.info('Log file reloaded');
    }
  }

  function handleToggleTail() {
    if (!$currentFile) {
      toast.warning('Open a log file first to start tailing');
      return;
    }

    if ($isTailing) {
      if (tailUnsubscribe) {
        tailUnsubscribe();
        tailUnsubscribe = null;
      }
      isTailing.set(false);
      toast.info('Live tail paused');
    } else {
      isTailing.set(true);
      toast.success('Live tail streaming started');
      tailUnsubscribe = subscribeLiveTail($currentFile.fileId, (event) => {
        if (logTableRef && event.newEntries) {
          logTableRef.handleLiveEntries(event.newEntries, event.totalLines);
        }
      });
    }
  }

  function handleClearLog() {
    if (logTableRef) {
      logTableRef.clearView();
      toast.info('Viewport cleared');
    }
  }

  async function handleExport(format: string) {
    if (!$currentFile) {
      toast.warning('Open a log file before exporting');
      return;
    }
    const url = API.getExportUrl($currentFile.fileId, format);
    window.open(url, '_blank');
    toast.success(`Exporting log as .${format}...`);
  }

  function handleClearSession() {
    if (tailUnsubscribe) {
      tailUnsubscribe();
      tailUnsubscribe = null;
    }
    isTailing.set(false);
    currentFile.set(null);
    if (logTableRef) {
      logTableRef.clearView();
    }
    toast.info('Session closed');
  }

  async function handleClearFilterAndJump(originalLine: number, entry: any) {
    searchQuery.set('');
    activeLevel.set('ALL');
    if (logTableRef) {
      await logTableRef.resetAndReload();
      await tick();
      logTableRef.scrollToLine(originalLine);
      selectedEntry.set(entry);
    }
    toast.info(`Jumped to original line ${originalLine.toLocaleString()}`);
  }

  function handleGotoLine(line: number) {
    if (logTableRef) {
      logTableRef.scrollToLine(line);
    }
  }

  function handlePrevMatch() {
    if (logTableRef) logTableRef.prevMatch();
  }

  function handleNextMatch() {
    if (logTableRef) logTableRef.nextMatch();
  }
</script>

<div class="h-screen w-screen flex flex-col overflow-hidden bg-background text-foreground select-none">
  <!-- Desktop Menubar -->
  <Menubar
    onReload={handleReload}
    onToggleTail={handleToggleTail}
    onExport={handleExport}
    onClearSession={handleClearSession}
  />


  <!-- Interactive Toolbar -->
  <Toolbar
    onSearchChange={handleSearchChange}
    onReload={handleReload}
    onToggleTail={handleToggleTail}
    onClearLog={handleClearLog}
    onExport={handleExport}
    onPrevMatch={handlePrevMatch}
    onNextMatch={handleNextMatch}
    {matchCurrent}
    {matchTotal}
  />

  <!-- Main Resizable Workspace with shadcn Paneforge Components -->
  <main class="flex-1 w-full h-full overflow-hidden relative">
    <ResizablePaneGroup direction="horizontal" class="h-full w-full">
      <!-- Left Panel: Recent Files History -->
      {#if $leftPanelVisible}
        <ResizablePane defaultSize={18} minSize={12} maxSize={35} class="h-full border-r border-border">
          <RecentFilesSidebar
            bind:this={recentFilesRef}
            onOpenFile={handleOpenFile}
          />
        </ResizablePane>
        <ResizableHandle withHandle />
      {/if}

      <!-- Center Workspace (Log Viewer + Inspector + Right Search Drawer) -->
      <ResizablePane defaultSize={$leftPanelVisible ? 82 : 100} class="h-full">
        <ResizablePaneGroup direction="horizontal" class="h-full w-full">
          <!-- Main Content (VirtualLogTable + DetailPanel) -->
          <ResizablePane defaultSize={$searchPanelVisible && $searchQuery ? 75 : 100} class="h-full">
            <ResizablePaneGroup direction="vertical" class="h-full w-full">
              <!-- Top: Infinite Virtual Scroller Log Table -->
              <ResizablePane defaultSize={$bottomPanelVisible ? 70 : 100} minSize={25} class="h-full relative overflow-hidden">
                <VirtualLogTable
                  bind:this={logTableRef}
                  onGotoLine={handleGotoLine}
                  onClearFilterAndJump={handleClearFilterAndJump}
                  bind:matchCurrent
                  bind:matchTotal
                />
              </ResizablePane>

              <!-- Bottom: Log Entry Inspector / Detail Panel -->
              {#if $bottomPanelVisible}
                <ResizableHandle withHandle />
                <ResizablePane defaultSize={30} minSize={15} maxSize={65} class="h-full border-t border-border">
                  <DetailPanel />
                </ResizablePane>
              {/if}
            </ResizablePaneGroup>
          </ResizablePane>

          <!-- Right Side: Search Results Panel (Matching Desktop SearchResultPanel) -->
          {#if $searchPanelVisible && $searchQuery}
            <ResizableHandle withHandle />
            <ResizablePane defaultSize={25} minSize={15} maxSize={50} class="h-full border-l border-border bg-background">
              <SearchResultPanel
                onSelectMatch={handleSelectMatch}
                onClose={() => searchPanelVisible.set(false)}
              />
            </ResizablePane>
          {/if}
        </ResizablePaneGroup>
      </ResizablePane>
    </ResizablePaneGroup>
  </main>


  <!-- Bottom Metrics Status Bar -->
  <StatusBar />

  <!-- Toast Notification Floating Container -->
  <ToastContainer />

  <!-- All shadcn Dialog Modals -->
  <FileManagerModal onOpenFile={handleOpenFile} />
  <ServerManagerModal />
  <FilterManagerModal />
  <PreferencesModal />
  <GotoLineModal onGoto={handleGotoLine} />
  <AboutModal />
  <ShortcutsModal />
</div>
