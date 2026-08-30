<script lang="ts">
  import { onMount } from 'svelte';
  import {
    searchQuery,
    isRegex,
    caseSensitive,
    activeLevel,
    currentFile,
    selectedRowIndex,
    selectedLogEntry
  } from '../stores/appState';
  import { API } from '../api';
  import { escapeHtml } from '../utils';
  import { Badge } from './ui/badge';
  import { Button } from './ui/button';
  import { Separator } from './ui/separator';
  import {
    Search,
    ChevronUp,
    ChevronDown,
    X,
    ExternalLink,
    FileText,
    Loader2
  } from 'lucide-svelte';

  export let onSelectMatch: (item: { lineNumber: number; rawLog: string; index: number }) => void = () => {};
  export let onClose: () => void = () => {};

  let matches: any[] = [];
  let totalMatches = 0;
  let activeMatchIndex = 0;
  let isLoading = false;
  let searchDebounce: any;

  // Pagination for search results list
  let page = 0;
  const PAGE_SIZE = 100;
  let hasMore = false;

  const levelRegex = /\b(ERROR|FATAL|WARN|WARNING|INFO|DEBUG|TRACE)\b/i;

  $: {
    const q = $searchQuery;
    const r = $isRegex;
    const c = $caseSensitive;
    const lvl = $activeLevel;
    const file = $currentFile;

    clearTimeout(searchDebounce);
    if (file && q && q.trim().length > 0) {
      searchDebounce = setTimeout(() => {
        fetchSearchResults(true);
      }, 150);
    } else {
      matches = [];
      totalMatches = 0;
      activeMatchIndex = 0;
    }
  }

  async function fetchSearchResults(reset = false) {
    if (!$currentFile || !$searchQuery || $searchQuery.trim().length === 0) return;

    if (reset) {
      page = 0;
      matches = [];
      activeMatchIndex = 0;
    }

    isLoading = true;
    try {
      const res = await API.getLogPage(
        $currentFile.fileId,
        page * PAGE_SIZE,
        PAGE_SIZE,
        $searchQuery,
        $activeLevel,
        $isRegex,
        $caseSensitive
      );

      if (res.success && res.data) {
        totalMatches = res.data.filteredLines !== undefined ? res.data.filteredLines : (res.data.entries ? res.data.entries.length : 0);
        const newEntries = res.data.entries || [];
        if (reset) {
          matches = newEntries;
        } else {
          matches = [...matches, ...newEntries];
        }
        hasMore = matches.length < totalMatches;
      }
    } catch (e) {
      console.error('Failed to fetch search results panel data', e);
    } finally {
      isLoading = false;
    }
  }

  function loadMore() {
    if (isLoading || !hasMore) return;
    page++;
    fetchSearchResults(false);
  }

  function selectMatch(item: any, idx: number) {
    activeMatchIndex = idx;
    onSelectMatch({
      lineNumber: item.lineNumber,
      rawLog: item.rawLog || item.message || '',
      index: idx
    });
  }

  function prevMatch() {
    if (matches.length === 0) return;
    const nextIdx = activeMatchIndex > 0 ? activeMatchIndex - 1 : matches.length - 1;
    selectMatch(matches[nextIdx], nextIdx);
  }

  function nextMatch() {
    if (matches.length === 0) return;
    const nextIdx = activeMatchIndex < matches.length - 1 ? activeMatchIndex + 1 : 0;
    selectMatch(matches[nextIdx], nextIdx);
  }

  function formatHighlightSnippet(text: string, query: string): string {
    if (!text) return '';
    const esc = escapeHtml(text.trim());
    if (!query) return esc;

    try {
      const pat = $isRegex ? query : query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      const regex = new RegExp(`(${pat})`, $caseSensitive ? 'g' : 'gi');
      return esc.replace(regex, '<mark class="bg-amber-400 text-black px-0.5 rounded-2xs font-semibold">$1</mark>');
    } catch {
      return esc;
    }
  }

  function getLevelBadge(text: string): { label: string; variant: 'destructive' | 'default' | 'secondary' | 'outline' } | null {
    const m = levelRegex.exec(text.substring(0, 120));
    if (!m) return null;
    const lvl = m[1].toUpperCase();
    if (lvl === 'ERROR' || lvl === 'FATAL') return { label: lvl, variant: 'destructive' };
    if (lvl === 'WARN' || lvl === 'WARNING') return { label: lvl, variant: 'default' };
    if (lvl === 'INFO') return { label: lvl, variant: 'secondary' };
    return { label: lvl, variant: 'outline' };
  }
</script>

<aside class="h-full w-full bg-background flex flex-col select-none overflow-hidden font-sans">
  <!-- Panel Header -->
  <div class="h-9 border-b border-border flex items-center justify-between px-2.5 bg-muted/40 shrink-0">
    <div class="flex items-center gap-1.5 overflow-hidden">
      <Search class="w-3.5 h-3.5 text-primary shrink-0" />
      <span class="font-semibold text-xs text-foreground tracking-tight shrink-0">Search Results</span>
      <Badge variant="secondary" class="text-[10px] px-1.5 py-0 font-mono font-bold shrink-0">
        {totalMatches.toLocaleString()}
      </Badge>
    </div>

    <!-- Navigation & Close Controls -->
    <div class="flex items-center gap-0.5 shrink-0">
      <Button
        variant="ghost"
        size="icon"
        class="h-6 w-6"
        disabled={matches.length === 0}
        on:click={prevMatch}
        title="Previous match (Shift+Enter)"
      >
        <ChevronUp class="w-3.5 h-3.5" />
      </Button>

      <Button
        variant="ghost"
        size="icon"
        class="h-6 w-6"
        disabled={matches.length === 0}
        on:click={nextMatch}
        title="Next match (Enter)"
      >
        <ChevronDown class="w-3.5 h-3.5" />
      </Button>

      <Separator orientation="vertical" class="h-3.5 mx-0.5" />

      <Button
        variant="ghost"
        size="icon"
        class="h-6 w-6 text-muted-foreground hover:text-foreground"
        on:click={onClose}
        title="Close Search Results Panel (Escape)"
      >
        <X class="w-3.5 h-3.5" />
      </Button>
    </div>
  </div>

  <!-- Query Subheader Indicator -->
  <div class="px-2.5 py-1.5 bg-muted/20 border-b border-border/60 flex items-center justify-between gap-1 text-[11px] text-muted-foreground">
    <span class="truncate">
      Query: <code class="font-mono font-semibold text-foreground bg-muted px-1 py-0.5 rounded text-[10.5px]">{$searchQuery}</code>
    </span>
    <div class="flex items-center gap-1 shrink-0">
      {#if $isRegex}
        <Badge variant="outline" class="text-[9px] px-1 py-0 font-mono text-primary border-primary/40">REGEX</Badge>
      {/if}
      {#if $caseSensitive}
        <Badge variant="outline" class="text-[9px] px-1 py-0 font-mono text-primary border-primary/40">Aa</Badge>
      {/if}
    </div>
  </div>

  <!-- Matches Scrollable List -->
  <div class="flex-1 overflow-y-auto p-1.5 flex flex-col gap-1 select-text">
    {#if isLoading && matches.length === 0}
      <div class="h-32 flex flex-col items-center justify-center text-muted-foreground gap-2">
        <Loader2 class="w-4 h-4 animate-spin text-primary" />
        <span class="text-xs">Searching log entries...</span>
      </div>
    {:else if matches.length === 0}
      <div class="p-6 text-center text-muted-foreground text-xs leading-relaxed">
        No log entries matched "<b>{$searchQuery}</b>".
      </div>
    {:else}
      {#each matches as item, idx (item.lineNumber)}
        {@const raw = item.rawLog || item.message || ''}
        {@const lvlBadge = getLevelBadge(raw)}
        {@const isActive = activeMatchIndex === idx}
        <div
          role="button"
          tabindex="0"
          class="p-2 rounded-[var(--radius-sm)] cursor-pointer transition-all flex flex-col text-left group {isActive ? 'bg-primary/15 font-medium' : 'hover:bg-accent'}"
          on:click={() => selectMatch(item, idx)}
          on:keydown={(e) => e.key === 'Enter' && selectMatch(item, idx)}
        >

          <!-- Item Header: Line number & Level -->
          <div class="flex items-center justify-between gap-1 mb-1 select-none">
            <span class="text-[10px] font-mono font-bold text-foreground flex items-center gap-1">
              <span class="text-muted-foreground">#</span>{item.lineNumber}
            </span>
            <div class="flex items-center gap-1">
              {#if lvlBadge}
                <Badge variant={lvlBadge.variant} class="text-[8.5px] px-1 py-0 h-3.5 font-mono">
                  {lvlBadge.label}
                </Badge>
              {/if}
              <span class="text-[9.5px] text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity flex items-center gap-0.5">
                <ExternalLink class="w-2.5 h-2.5" />
                <span>Jump</span>
              </span>
            </div>
          </div>

          <!-- Highlighted Snippet -->
          <div class="text-[11px] font-mono leading-tight text-foreground/90 truncate">
            {@html formatHighlightSnippet(raw, $searchQuery)}
          </div>
        </div>
      {/each}

      {#if hasMore}
        <div class="p-2 text-center">
          <Button
            variant="outline"
            size="xs"
            class="w-full text-xs gap-1.5"
            disabled={isLoading}
            on:click={loadMore}
          >
            {#if isLoading}
              <Loader2 class="w-3 h-3 animate-spin" />
              <span>Loading more...</span>
            {:else}
              <span>Load more ({totalMatches - matches.length} remaining)...</span>
            {/if}
          </Button>
        </div>
      {/if}
    {/if}
  </div>

  <!-- Panel Footer Status -->
  {#if totalMatches > 0}
    <div class="p-1.5 px-2.5 border-t border-border bg-muted/20 text-[10px] text-muted-foreground flex items-center justify-between font-mono">
      <span>Showing {matches.length} of {totalMatches.toLocaleString()} matches</span>
      {#if activeMatchIndex >= 0 && matches.length > 0}
        <span class="font-semibold text-foreground">Active: #{matches[activeMatchIndex]?.lineNumber}</span>
      {/if}
    </div>
  {/if}
</aside>
