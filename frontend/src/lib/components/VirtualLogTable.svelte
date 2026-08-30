<script lang="ts">
  import { onMount, tick } from 'svelte';
  import {
    currentFile,
    selectedRowIndex,
    selectedEntry,
    autoScroll,
    searchQuery,
    isRegex,
    caseSensitive,
    activeLevel,
    totalLinesCount,
    visibleLinesCount,
    activeModal
  } from '../stores/appState';
  import { toast } from '../stores/toast';
  import { API } from '../api';
  import { escapeHtml } from '../utils';
  import type { LogEntry } from '../types';
  import { FileSearch, FolderOpen, Copy, ExternalLink, Filter, RotateCcw } from 'lucide-svelte';
  import { Button } from './ui/button';
  import { Badge } from './ui/badge';

  export const onGotoLine: (line: number) => void = () => {};
  export let onClearFilterAndJump: ((originalLine: number, entry: LogEntry) => void) | undefined = undefined;
  export let matchCurrent: number = 0;
  export let matchTotal: number = 0;

  let viewportEl: HTMLDivElement;
  let contentEl: HTMLDivElement;

  const rowHeight = 22;
  const overscan = 20;
  const CHUNK_SIZE = 500;
  const MAX_CACHED_CHUNKS = 100;

  let chunkCache: Record<number, LogEntry[]> = {};
  let chunkAccessTimes = new Map<number, number>();
  let pendingChunks = new Set<number>();
  let chunkAbortControllers = new Map<number, AbortController>();
  let cacheVersion = 0;
  let isTicking = false;

  let totalCount = 0;
  let visibleStart = 0;
  let visibleEnd = 0;
  let scale = 1.0;
  let displayHeight = 0;

  // Context menu state
  let showContextMenu = false;
  let contextMenuX = 0;
  let contextMenuY = 0;
  let contextMenuIndex = -1;
  let contextMenuEntry: LogEntry | null = null;

  const levelRegex = /\b(ERROR|FATAL|WARN|WARNING|INFO|DEBUG|TRACE)\b/i;

  // Memoized Search Regex
  let compiledRegex: RegExp | null = null;
  let cachedQuery = '';
  let cachedIsRegex = false;
  let cachedCase = false;

  $: {
    const q = $searchQuery ? $searchQuery.trim() : '';
    if (q !== cachedQuery || $isRegex !== cachedIsRegex || $caseSensitive !== cachedCase) {
      cachedQuery = q;
      cachedIsRegex = $isRegex;
      cachedCase = $caseSensitive;
      if (q) {
        try {
          const pat = $isRegex ? q : q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
          compiledRegex = new RegExp(`(${pat})`, $caseSensitive ? 'g' : 'gi');
        } catch {
          compiledRegex = null;
        }
      } else {
        compiledRegex = null;
      }
      cacheVersion++;
    }
  }


  $: if ($currentFile) {
    if ($visibleLinesCount !== undefined && $visibleLinesCount !== null) {
      totalCount = $visibleLinesCount;
    } else {
      totalCount = $totalLinesCount || $currentFile.totalLines || 0;
    }
    updateDimensions(totalCount);
  } else {
    totalCount = 0;
    updateDimensions(0);
  }


  // Reactive visible rows slice - updates immediately when cacheVersion or window changes!
  $: visibleRows = (() => {
    const _ = cacheVersion;
    const rows: { index: number; entry: LogEntry | null }[] = [];
    if (totalCount === 0) return rows;
    for (let i = visibleStart; i < visibleEnd; i++) {
      const chunkIdx = Math.floor(i / CHUNK_SIZE);
      const chunk = chunkCache[chunkIdx];
      const entry = chunk ? chunk[i % CHUNK_SIZE] || null : null;
      rows.push({ index: i, entry });
    }
    return rows;
  })();

  onMount(() => {
    calculateVisibleWindow();

    function closeMenuOnClick() {
      if (showContextMenu) showContextMenu = false;
    }
    window.addEventListener('click', closeMenuOnClick);
    return () => {
      window.removeEventListener('click', closeMenuOnClick);
      for (const ctrl of chunkAbortControllers.values()) {
        ctrl.abort();
      }
      chunkAbortControllers.clear();
      if (liveRafId !== null) {
        window.cancelAnimationFrame(liveRafId);
        liveRafId = null;
      }
    };
  });

  function updateDimensions(total: number) {
    const unscaled = total * rowHeight;
    const maxHeight = 30000000;
    scale = unscaled > maxHeight ? maxHeight / unscaled : 1.0;
    displayHeight = Math.min(unscaled, maxHeight);
    calculateVisibleWindow();
  }

  function evictOldChunks(currentStartChunk: number, currentEndChunk: number) {
    const keys = Object.keys(chunkCache).map(Number);
    if (keys.length <= MAX_CACHED_CHUNKS) return;

    const protectedRangeStart = Math.max(0, currentStartChunk - 2);
    const protectedRangeEnd = currentEndChunk + 2;

    const candidates = keys.filter(k => k < protectedRangeStart || k > protectedRangeEnd);
    candidates.sort((a, b) => (chunkAccessTimes.get(a) || 0) - (chunkAccessTimes.get(b) || 0));

    const toRemoveCount = keys.length - MAX_CACHED_CHUNKS;
    for (let i = 0; i < Math.min(toRemoveCount, candidates.length); i++) {
      const k = candidates[i];
      delete chunkCache[k];
      chunkAccessTimes.delete(k);
    }
  }

  export async function resetAndReload(initialEntries?: LogEntry[]) {
    for (const ctrl of chunkAbortControllers.values()) {
      ctrl.abort();
    }
    chunkAbortControllers.clear();
    chunkCache = {};
    chunkAccessTimes.clear();
    pendingChunks.clear();
    cacheVersion++;

    if ($currentFile) {
      totalCount = $currentFile.totalLines || 0;
      totalLinesCount.set(totalCount);
      visibleLinesCount.set(totalCount);
      updateDimensions(totalCount);

      if (viewportEl) viewportEl.scrollTop = 0;

      if (initialEntries && initialEntries.length > 0) {
        chunkCache[0] = initialEntries;
        chunkAccessTimes.set(0, performance.now());
        cacheVersion++;
      } else {
        await requestChunk(0);
      }
      calculateVisibleWindow();
    }
  }

  export function reload() {
    resetAndReload();
  }

  export async function applyFilters() {
    for (const ctrl of chunkAbortControllers.values()) {
      ctrl.abort();
    }
    chunkAbortControllers.clear();
    chunkCache = {};
    chunkAccessTimes.clear();
    pendingChunks.clear();
    cacheVersion++;

    if (!$currentFile) return;

    try {
      // Main table keeps full stream lines (only filters by Level if active, search is highlighted in-place)
      const res = await API.getLogPage(
        $currentFile.fileId,
        0,
        CHUNK_SIZE,
        '',
        $activeLevel,
        false,
        false
      );

      if (res.success && res.data) {
        totalCount = res.data.filteredLines !== undefined ? res.data.filteredLines : res.data.totalLines;
        visibleLinesCount.set(totalCount);
        updateDimensions(totalCount);
        if (res.data.entries) {
          chunkCache[0] = res.data.entries;
          chunkAccessTimes.set(0, performance.now());
          cacheVersion++;
        }
        calculateVisibleWindow();
      }
    } catch (e) {
      console.error('Failed to apply filters', e);
    }
  }


  export function clearView() {
    for (const ctrl of chunkAbortControllers.values()) {
      ctrl.abort();
    }
    chunkAbortControllers.clear();
    chunkCache = {};
    chunkAccessTimes.clear();
    pendingChunks.clear();
    cacheVersion++;
    totalCount = 0;
    totalLinesCount.set(0);
    visibleLinesCount.set(0);
    selectedRowIndex.set(-1);
    selectedEntry.set(null);
    matchCurrent = 0;
    matchTotal = 0;
    updateDimensions(0);
  }

  // High-performance micro-batched live entry ingestion
  let liveEntriesBuffer: LogEntry[] = [];
  let liveRafId: number | null = null;
  let pendingLiveTotal: number | undefined = undefined;

  export function handleLiveEntries(newEntries: LogEntry[], newTotalLines?: number) {
    if (!newEntries || newEntries.length === 0) return;
    for (let i = 0; i < newEntries.length; i++) {
      liveEntriesBuffer.push(newEntries[i]);
    }
    if (newTotalLines !== undefined) {
      pendingLiveTotal = newTotalLines;
    }

    if (liveRafId === null) {
      liveRafId = window.requestAnimationFrame(flushLiveEntries);
    }
  }

  function flushLiveEntries() {
    liveRafId = null;
    if (liveEntriesBuffer.length === 0) return;

    const flushed = liveEntriesBuffer;
    liveEntriesBuffer = [];

    if (pendingLiveTotal !== undefined) {
      totalCount = pendingLiveTotal;
      pendingLiveTotal = undefined;
    } else {
      totalCount += flushed.length;
    }

    totalLinesCount.set(totalCount);
    visibleLinesCount.set(totalCount);
    updateDimensions(totalCount);

    const lastChunkIdx = Math.floor((totalCount - 1) / CHUNK_SIZE);
    if (chunkCache[lastChunkIdx]) {
      const chunk = chunkCache[lastChunkIdx];
      for (let i = 0; i < flushed.length; i++) {
        if (chunk.length < CHUNK_SIZE) {
          chunk.push(flushed[i]);
        }
      }
      chunkAccessTimes.set(lastChunkIdx, performance.now());
      cacheVersion++;
    }

    if ($autoScroll && viewportEl) {
      viewportEl.scrollTop = viewportEl.scrollHeight;
    }

    calculateVisibleWindow();
  }

  export function scrollToLine(line: number) {
    const idx = Math.max(0, Math.min(totalCount - 1, line - 1));
    selectRow(idx);
    scrollToIndex(idx);
  }

  export function prevMatch() {
    if (matchTotal <= 0) return;
    if (matchCurrent > 1) {
      matchCurrent--;
    } else {
      matchCurrent = matchTotal;
    }
    const idx = matchCurrent - 1;
    selectRow(idx);
    scrollToIndex(idx);
  }

  export function nextMatch() {
    if (matchTotal <= 0) return;
    if (matchCurrent < matchTotal) {
      matchCurrent++;
    } else {
      matchCurrent = 1;
    }
    const idx = matchCurrent - 1;
    selectRow(idx);
    scrollToIndex(idx);
  }

  function handleScroll() {
    if (!isTicking) {
      window.requestAnimationFrame(() => {
        calculateVisibleWindow();
        isTicking = false;
      });
      isTicking = true;
    }
  }

  function calculateVisibleWindow() {
    if (totalCount === 0) {
      visibleStart = 0;
      visibleEnd = 0;
      return;
    }

    const scrollTop = viewportEl ? viewportEl.scrollTop : 0;
    const viewportHeight = (viewportEl && viewportEl.clientHeight > 0) ? viewportEl.clientHeight : 800;
    const virtualTop = scrollTop / scale;

    visibleStart = Math.max(0, Math.floor(virtualTop / rowHeight) - overscan);
    visibleEnd = Math.min(totalCount, Math.ceil((virtualTop + viewportHeight / scale) / rowHeight) + overscan);

    if ($currentFile) {
      const startChunk = Math.floor(visibleStart / CHUNK_SIZE);
      const endChunk = Math.floor(visibleEnd / CHUNK_SIZE);

      // Cancel stale in-flight requests that are no longer needed
      for (const [cIdx, controller] of chunkAbortControllers.entries()) {
        if (cIdx < startChunk - 4 || cIdx > endChunk + 4) {
          controller.abort();
          chunkAbortControllers.delete(cIdx);
          pendingChunks.delete(cIdx);
        }
      }

      // Update access times for cached chunks
      const now = performance.now();
      for (let c = startChunk; c <= endChunk; c++) {
        if (chunkCache[c]) {
          chunkAccessTimes.set(c, now);
        }
      }

      // Request missing chunks in visible range
      for (let c = startChunk; c <= endChunk; c++) {
        if (!chunkCache[c] && !pendingChunks.has(c)) {
          requestChunk(c);
        }
      }

      // Evict old chunks outside viewport if over budget
      evictOldChunks(startChunk, endChunk);
    }
  }

  async function requestChunk(chunkIdx: number) {
    if (!$currentFile || pendingChunks.has(chunkIdx) || chunkCache[chunkIdx]) return;

    pendingChunks.add(chunkIdx);
    const controller = new AbortController();
    chunkAbortControllers.set(chunkIdx, controller);
    const offset = chunkIdx * CHUNK_SIZE;

    try {
      const res = await API.getLogPage(
        $currentFile.fileId,
        offset,
        CHUNK_SIZE,
        '',
        $activeLevel,
        false,
        false,
        controller.signal
      );


      pendingChunks.delete(chunkIdx);
      chunkAbortControllers.delete(chunkIdx);

      if (res.success && res.data && res.data.entries) {
        chunkCache[chunkIdx] = res.data.entries;
        chunkAccessTimes.set(chunkIdx, performance.now());
        cacheVersion++;
      }
    } catch (e: any) {
      if (e.name !== 'AbortError') {
        pendingChunks.delete(chunkIdx);
        chunkAbortControllers.delete(chunkIdx);
      }
    }
  }

  function getEntry(index: number): LogEntry | null {
    const chunkIdx = Math.floor(index / CHUNK_SIZE);
    const chunk = chunkCache[chunkIdx];
    if (chunk) {
      chunkAccessTimes.set(chunkIdx, performance.now());
      return chunk[index % CHUNK_SIZE] || null;
    }
    return null;
  }


  export function selectRow(index: number) {
    if (index < 0 || index >= totalCount) return;
    selectedRowIndex.set(index);
    const chunkIdx = Math.floor(index / CHUNK_SIZE);
    const chunk = chunkCache[chunkIdx];
    const entry = chunk ? chunk[index % CHUNK_SIZE] : null;
    if (entry) {
      selectedEntry.set(entry);
    } else if ($currentFile) {
      API.getLogPage($currentFile.fileId, index, 1, $searchQuery, $activeLevel, $isRegex, $caseSensitive).then(res => {
        if (res.success && res.data && res.data.entries && res.data.entries[0]) {
          selectedEntry.set(res.data.entries[0]);
        }
      });
    }
    scrollToIndex(index);
  }

  export function handleDoubleClick(index: number) {
    const entry = getEntry(index);
    const originalLineNumber = entry ? (entry.lineNumber || (index + 1)) : (index + 1);

    const isFiltered = Boolean(($searchQuery && $searchQuery.trim().length > 0) || ($activeLevel && $activeLevel !== 'ALL'));

    if (isFiltered) {
      // 1-to-1 Desktop Behavior: Clear active filter and jump straight to original line in full view
      if (onClearFilterAndJump && entry) {
        onClearFilterAndJump(originalLineNumber, entry);
      } else {
        searchQuery.set('');
        activeLevel.set('ALL');
        applyFilters().then(() => {
          tick().then(() => {
            scrollToLine(originalLineNumber);
            if (entry) selectedEntry.set(entry);
          });
        });
      }
    } else {
      selectRow(index);
    }
  }

  function handleContextMenu(e: MouseEvent, index: number) {
    e.preventDefault();
    selectRow(index);
    contextMenuIndex = index;
    contextMenuEntry = getEntry(index);
    contextMenuX = Math.min(window.innerWidth - 200, e.clientX);
    contextMenuY = Math.min(window.innerHeight - 200, e.clientY);
    showContextMenu = true;
  }

  function copyCurrentLine() {
    if (contextMenuEntry) {
      const text = contextMenuEntry.rawLog || contextMenuEntry.message || '';
      navigator.clipboard.writeText(text);
      toast.success('Line copied to clipboard');
    }
    showContextMenu = false;
  }

  function jumpToOriginalLine() {
    if (contextMenuIndex >= 0) {
      handleDoubleClick(contextMenuIndex);
    }
    showContextMenu = false;
  }

  function filterBySelectedLevel() {
    if (contextMenuEntry) {
      const text = contextMenuEntry.rawLog || contextMenuEntry.message || '';
      const m = levelRegex.exec(text.substring(0, 120));
      if (m) {
        activeLevel.set(m[1].toUpperCase());
        applyFilters();
      }
    }
    showContextMenu = false;
  }

  export function scrollToIndex(index: number) {
    if (!viewportEl) return;
    const rowTop = index * rowHeight * scale;
    const rowBottom = rowTop + rowHeight;
    const viewTop = viewportEl.scrollTop;
    const viewBottom = viewTop + (viewportEl.clientHeight || 500);

    if (rowTop < viewTop) {
      viewportEl.scrollTop = rowTop;
    } else if (rowBottom > viewBottom) {
      viewportEl.scrollTop = rowBottom - (viewportEl.clientHeight || 500);
    }
  }

  function formatHighlight(text: string, query?: string): string {
    if (!text) return '';
    const esc = escapeHtml(text);
    if (!compiledRegex || !cachedQuery) return esc;

    // Fast-path: skip regex replace if plain query is not in the text
    if (!cachedIsRegex) {
      const hasMatch = cachedCase
        ? text.includes(cachedQuery)
        : text.toLowerCase().includes(cachedQuery.toLowerCase());
      if (!hasMatch) return esc;
    }

    try {
      compiledRegex.lastIndex = 0;
      return esc.replace(compiledRegex, '<mark class="bg-amber-400 text-black px-0.5 rounded-2xs font-semibold">$1</mark>');
    } catch {
      return esc;
    }
  }


  function getLevelClass(text: string): string {
    const m = levelRegex.exec(text.substring(0, 120));
    if (!m) return '';
    const lvl = m[1].toUpperCase();
    if (lvl === 'ERROR' || lvl === 'FATAL') return 'text-[var(--level-error-text)] font-semibold';
    if (lvl === 'WARN' || lvl === 'WARNING') return 'text-[var(--level-warn-text)] font-medium';
    if (lvl === 'INFO') return 'text-foreground';
    if (lvl === 'DEBUG' || lvl === 'TRACE') return 'text-muted-foreground';
    return '';
  }

  function handleKeydown(e: KeyboardEvent) {
    if (totalCount === 0) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      selectRow(Math.min(totalCount - 1, $selectedRowIndex + 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      selectRow(Math.max(0, $selectedRowIndex - 1));
    } else if (e.key === 'PageDown') {
      e.preventDefault();
      selectRow(Math.min(totalCount - 1, $selectedRowIndex + 25));
    } else if (e.key === 'PageUp') {
      e.preventDefault();
      selectRow(Math.max(0, $selectedRowIndex - 25));
    } else if (e.key === 'Home') {
      e.preventDefault();
      selectRow(0);
    } else if (e.key === 'End') {
      e.preventDefault();
      selectRow(totalCount - 1);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      handleDoubleClick($selectedRowIndex);
    } else if (e.key === 'c' && (e.ctrlKey || e.metaKey)) {
      if ($selectedEntry) {
        const text = $selectedEntry.rawLog || $selectedEntry.message || '';
        navigator.clipboard.writeText(text);
        toast.success('Line copied to clipboard');
      }
    }
  }
</script>

<div class="flex-1 flex flex-col overflow-hidden bg-background relative min-h-[120px] h-full w-full">
  <!-- Header -->
  <div class="h-[26px] bg-muted/50 border-b border-border flex items-center text-[11px] font-semibold text-muted-foreground select-none z-10 shrink-0">
    <div class="w-[64px] text-right pr-2.5 border-r border-border leading-[26px]">#</div>
    <div class="flex-1 px-2.5 leading-[26px]">Raw Log Output Stream</div>
  </div>

  {#if !$currentFile}
    <!-- No File Loaded Empty State -->
    <div class="flex-1 flex flex-col items-center justify-center text-muted-foreground gap-2.5 p-5 text-center">
      <div class="w-14 h-14 bg-muted border border-border rounded-xl flex items-center justify-center text-foreground mb-0.5">
        <FileSearch class="w-7 h-7" />
      </div>
      <div class="text-sm font-semibold text-foreground">No Log File Loaded</div>
      <div class="text-xs max-w-[400px] leading-relaxed mb-1">
        Open a local file or connect to a remote SSH server to analyze logs with high-performance virtual rendering.
      </div>
      <Button variant="default" size="sm" class="gap-1.5" on:click={() => activeModal.set('file-manager')}>
        <FolderOpen class="w-3.5 h-3.5" />
        <span>Open Log File... (Ctrl+O)</span>
      </Button>
    </div>
  {:else if totalCount === 0}
    <!-- Empty Search / Filter Results State -->
    <div class="flex-1 flex flex-col items-center justify-center text-muted-foreground gap-2.5 p-8 text-center select-none">
      <div class="w-12 h-12 bg-muted/60 border border-border/60 rounded-xl flex items-center justify-center text-muted-foreground mb-1">
        <FileSearch class="w-6 h-6" />
      </div>
      <div class="text-sm font-semibold text-foreground">No Matching Log Entries</div>
      <div class="text-xs max-w-[420px] leading-relaxed text-muted-foreground">
        No log lines matched your current filter criteria
        {#if $searchQuery && $searchQuery.trim().length > 0}
          for query <code class="px-1.5 py-0.5 bg-muted rounded font-mono text-foreground font-semibold">{$searchQuery}</code>
        {/if}
        {#if $activeLevel && $activeLevel !== 'ALL'}
          (Level: <Badge variant="outline" class="text-[10px] px-1 py-0 font-mono">{$activeLevel}</Badge>)
        {/if}.
      </div>
      <Button
        variant="outline"
        size="sm"
        class="gap-1.5 mt-2 text-xs"
        on:click={() => { searchQuery.set(''); activeLevel.set('ALL'); isRegex.set(false); caseSensitive.set(false); applyFilters(); toast.info('Filters reset'); }}
      >
        <RotateCcw class="w-3.5 h-3.5" />
        <span>Reset Search & Filters</span>
      </Button>
    </div>
  {:else}
    <!-- Infinite Virtual Viewport -->
    <div
      bind:this={viewportEl}
      on:scroll={handleScroll}
      on:keydown={handleKeydown}
      tabindex="0"
      role="region"
      aria-label="Log Entries"
      class="flex-1 overflow-y-auto overflow-x-auto relative font-mono text-[11.5px] outline-none select-text"
    >

      <div bind:this={contentEl} style="height: {displayHeight}px;" class="relative w-full">
        {#each visibleRows as row (row.index)}
          {@const i = row.index}
          {@const entry = row.entry}
          {@const rawText = entry?.rawLog || entry?.message || ''}
          {@const isSelected = $selectedRowIndex === i}
          <div
            style="top: {Math.round(i * rowHeight * scale)}px; height: {rowHeight}px;"
            class="absolute left-0 right-0 grid grid-cols-[64px_1fr] items-center cursor-pointer whitespace-nowrap transition-colors {isSelected ? 'bg-blue-500/20 text-foreground font-medium' : 'hover:bg-accent/60'}"
            role="button"
            tabindex="0"
            on:click={() => selectRow(i)}
            on:dblclick={() => handleDoubleClick(i)}
            on:contextmenu={(e) => handleContextMenu(e, i)}
            on:keydown={(e) => e.key === 'Enter' && selectRow(i)}
          >
            <!-- Gutter Line Number -->
            <div class="text-[10.5px] text-right pr-2.5 leading-[22px] border-r border-border select-none font-mono transition-colors {isSelected ? 'bg-blue-600 text-white font-bold' : 'text-muted-foreground bg-background'}">
              {entry ? (entry.lineNumber || i + 1) : i + 1}
            </div>

            <!-- Raw Text Line -->
            <div class="px-2.5 overflow-hidden text-ellipsis whitespace-pre leading-[22px] font-mono text-[11.5px] {isSelected ? 'text-foreground font-medium' : (entry ? getLevelClass(rawText) : 'text-muted-foreground opacity-40 italic')}">
              {#if entry}
                {@html formatHighlight(rawText, $searchQuery)}
              {:else}
                Loading line {i + 1}...
              {/if}
            </div>
          </div>

        {/each}

      </div>
    </div>
  {/if}

  <!-- Right-Click Context Menu -->
  {#if showContextMenu}
    <div
      style="left: {contextMenuX}px; top: {contextMenuY}px;"
      class="fixed z-50 min-w-[180px] bg-popover text-popover-foreground rounded-[var(--radius-sm)] shadow-xl border border-border py-1 text-xs select-none animate-in fade-in-0 zoom-in-95"
      on:click|stopPropagation
    >
      <button
        class="w-full text-left px-3 py-1.5 hover:bg-accent hover:text-accent-foreground flex items-center gap-2"
        on:click={copyCurrentLine}
      >
        <Copy class="w-3.5 h-3.5" />
        <span>Copy Line (Ctrl+C)</span>
      </button>

      {#if ($searchQuery && $searchQuery.trim().length > 0) || ($activeLevel && $activeLevel !== 'ALL')}
        <button
          class="w-full text-left px-3 py-1.5 hover:bg-accent hover:text-accent-foreground flex items-center gap-2 font-medium"
          on:click={jumpToOriginalLine}
        >
          <ExternalLink class="w-3.5 h-3.5 text-primary" />
          <span>Go to Original Line</span>
        </button>
      {/if}

      <button
        class="w-full text-left px-3 py-1.5 hover:bg-accent hover:text-accent-foreground flex items-center gap-2"
        on:click={filterBySelectedLevel}
      >
        <Filter class="w-3.5 h-3.5" />
        <span>Filter by this Level</span>
      </button>

      <div class="h-px bg-border my-1"></div>

      <button
        class="w-full text-left px-3 py-1.5 hover:bg-accent hover:text-accent-foreground flex items-center gap-2 text-muted-foreground"
        on:click={() => { searchQuery.set(''); activeLevel.set('ALL'); applyFilters(); showContextMenu = false; }}
      >
        <RotateCcw class="w-3.5 h-3.5" />
        <span>Clear All Filters</span>
      </button>
    </div>
  {/if}
</div>
