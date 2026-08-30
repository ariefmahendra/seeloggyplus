<script lang="ts">
  import {
    searchQuery,
    isRegex,
    caseSensitive,
    activeLevel,
    isTailing,
    autoScroll,
    currentFile,
    leftPanelVisible,
    bottomPanelVisible,
    searchPanelVisible
  } from '../stores/appState';
  import { toast } from '../stores/toast';
  import { Button } from './ui/button';
  import { Input } from './ui/input';
  import { Tabs, TabsList, TabsTrigger } from './ui/tabs';
  import { Separator } from './ui/separator';
  import {
    Folder,
    FileText,
    Search,
    X,
    ChevronUp,
    ChevronDown,
    RotateCw,
    Eye,
    ArrowDown,
    Trash2,
    XCircle,
    PanelRight
  } from 'lucide-svelte';

  export let onSearchChange: () => void = () => {};
  export let onReload: () => void = () => {};
  export let onToggleTail: () => void = () => {};
  export let onClearLog: () => void = () => {};
  export let onExport: (format: string) => void = () => {};
  export let onPrevMatch: () => void = () => {};
  export let onNextMatch: () => void = () => {};

  export let matchCurrent: number = 0;
  export let matchTotal: number = 0;

  let searchTimeout: any;

  function handleSearchInput(e: Event) {
    const val = (e.target as HTMLInputElement).value;
    searchQuery.set(val);
    if (val && val.trim().length > 0) {
      searchPanelVisible.set(true);
    }
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => onSearchChange(), 120);
  }

  function clearSearch() {
    searchQuery.set('');
    searchPanelVisible.set(false);
    onSearchChange();
  }

  function clearAllFilters() {
    searchQuery.set('');
    isRegex.set(false);
    caseSensitive.set(false);
    activeLevel.set('ALL');
    searchPanelVisible.set(false);
    onSearchChange();
    toast.info('All filters cleared');
  }


  function handleExportChange(e: Event) {
    const target = e.target as HTMLSelectElement;
    if (target.value) {
      onExport(target.value);
      target.value = '';
    }
  }

  const levels = ['ALL', 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'];
</script>

<div class="h-[36px] bg-muted/60 border-b border-border flex items-center px-2 gap-1.5 z-40 select-none shrink-0">
  <!-- Left & Bottom Panel Toggles -->
  <Button
    variant={$leftPanelVisible ? 'secondary' : 'ghost'}
    size="icon"
    class="h-7 w-7 {$leftPanelVisible ? 'border border-border' : ''}"
    on:click={() => leftPanelVisible.update(v => !v)}
    title="Toggle Recent Files Panel (Ctrl+B)"
  >
    <Folder class="w-3.5 h-3.5" />
  </Button>
  <Button
    variant={$bottomPanelVisible ? 'secondary' : 'ghost'}
    size="icon"
    class="h-7 w-7 {$bottomPanelVisible ? 'border border-border' : ''}"
    on:click={() => bottomPanelVisible.update(v => !v)}
    title="Toggle Detail Panel (Ctrl+J)"
  >
    <FileText class="w-3.5 h-3.5" />
  </Button>

  <Separator orientation="vertical" class="h-4 mx-0.5" />

  <!-- Search Box with shadcn Input -->
  <span class="text-[11px] font-medium text-foreground">Search:</span>
  <div class="relative flex items-center min-w-[220px] max-w-[380px] flex-1">
    <Search class="absolute left-2 w-3 h-3 text-muted-foreground pointer-events-none" />
    <Input
      type="text"
      placeholder="Include (Search)..."
      value={$searchQuery}
      on:input={handleSearchInput}
      spellcheck={false}
      class="pl-7 pr-7 h-7 font-mono text-[11.5px]"
    />
    {#if $searchQuery}
      <button on:click={clearSearch} class="absolute right-2 text-muted-foreground hover:text-foreground">
        <X class="w-3 h-3" />
      </button>
    {/if}
  </div>

  <!-- Regex & Case Toggles -->
  <Button
    variant={$isRegex ? 'default' : 'outline'}
    size="xs"
    on:click={() => { isRegex.update(v => !v); onSearchChange(); }}
    title="Toggle Regex Search"
  >
    Regex
  </Button>
  <Button
    variant={$caseSensitive ? 'default' : 'outline'}
    size="xs"
    on:click={() => { caseSensitive.update(v => !v); onSearchChange(); }}
    title="Toggle Case Sensitive Search"
  >
    Case Sensitive
  </Button>

  <Button variant="ghost" size="icon" class="h-7 w-7 text-muted-foreground hover:text-foreground" on:click={onSearchChange} title="Apply Filters">
    <Search class="w-3.5 h-3.5" />
  </Button>

  <Button variant="ghost" size="icon" class="h-7 w-7 text-muted-foreground hover:text-foreground" on:click={clearAllFilters} title="Clear All Filters">
    <XCircle class="w-3.5 h-3.5" />
  </Button>

  <!-- Search Navigator -->
  <div class="flex items-center bg-background border border-border rounded-[var(--radius-sm)] h-7 px-1">
    <Button
      variant="ghost"
      size="icon"
      class="h-5 w-5 text-muted-foreground hover:text-foreground disabled:opacity-30"
      on:click={onPrevMatch}
      disabled={matchTotal === 0}
      title="Previous Match"
    >
      <ChevronUp class="w-3 h-3" />
    </Button>
    <span class="text-[10px] font-mono text-muted-foreground px-1.5 whitespace-nowrap">
      {matchTotal > 0 ? `${matchCurrent} of ${matchTotal}` : '0 of 0'}
    </span>
    <Button
      variant="ghost"
      size="icon"
      class="h-5 w-5 text-muted-foreground hover:text-foreground disabled:opacity-30"
      on:click={onNextMatch}
      disabled={matchTotal === 0}
      title="Next Match"
    >
      <ChevronDown class="w-3 h-3" />
    </Button>
  </div>

  <Button
    variant={$searchPanelVisible ? 'secondary' : 'ghost'}
    size="icon"
    class="h-7 w-7 {$searchPanelVisible ? 'border border-border text-primary' : 'text-muted-foreground hover:text-foreground'}"
    on:click={() => searchPanelVisible.update(v => !v)}
    title="Toggle Search Results Panel"
  >
    <PanelRight class="w-3.5 h-3.5" />
  </Button>

  <Separator orientation="vertical" class="h-4 mx-0.5" />


  <!-- Reload -->
  <Button variant="ghost" size="icon" class="h-7 w-7 text-foreground" on:click={onReload} title="Reload Log (F5)">
    <RotateCw class="w-3.5 h-3.5" />
  </Button>

  <!-- Tail Mode Toggle -->
  <Button
    variant={$isTailing ? 'default' : 'outline'}
    size="sm"
    class="gap-1.5 transition-all {$isTailing ? 'bg-emerald-600 hover:bg-emerald-700 text-white font-semibold shadow-xs' : ''}"
    on:click={onToggleTail}
    title="Toggle Live Tail Streaming Mode (Ctrl+T)"
  >
    {#if $isTailing}
      <span class="relative flex h-2 w-2">
        <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-200 opacity-75"></span>
        <span class="relative inline-flex rounded-full h-2 w-2 bg-emerald-300"></span>
      </span>
      <Eye class="w-3.5 h-3.5" />
      <span>Tail: ON</span>
    {:else}
      <Eye class="w-3.5 h-3.5 text-muted-foreground" />
      <span>Tail: OFF</span>
    {/if}
  </Button>

  <!-- Smart Follow Auto-scroll -->
  <Button
    variant={$autoScroll ? 'default' : 'outline'}
    size="sm"
    class="gap-1.5 transition-all {$autoScroll ? 'bg-sky-600 hover:bg-sky-700 text-white font-semibold shadow-xs' : ''}"
    on:click={() => autoScroll.update(v => !v)}
    title="Smart Follow (Auto-scroll to newest lines)"
  >
    <ArrowDown class="w-3.5 h-3.5" />
    <span>Follow: {$autoScroll ? 'ON' : 'OFF'}</span>
  </Button>


  <Separator orientation="vertical" class="h-4 mx-0.5" />

  <!-- Clear View -->
  <Button variant="ghost" size="icon" class="h-7 w-7 text-muted-foreground hover:text-foreground" on:click={onClearLog} title="Clear Log View">
    <Trash2 class="w-3.5 h-3.5" />
  </Button>

  <Separator orientation="vertical" class="h-4 mx-0.5" />

  <!-- Level Segmented Buttons using shadcn Tabs -->
  <Tabs value={$activeLevel} onValueChange={(lvl) => { activeLevel.set(lvl); onSearchChange(); }}>
    <TabsList class="h-7">
      {#each levels as lvl}
        <TabsTrigger value={lvl} class="text-[10px] px-1.5 py-0.5 font-semibold">{lvl}</TabsTrigger>
      {/each}
    </TabsList>
  </Tabs>

  <!-- Export Dropdown -->
  <div class="ml-auto">
    <select
      class="bg-background border border-border text-foreground text-[11.5px] px-2 h-7 rounded-[var(--radius-sm)] outline-none cursor-pointer focus:border-ring"
      on:change={handleExportChange}
      title="Export Log File"
    >
      <option value="">Export...</option>
      <option value="txt">Plain Text (.txt)</option>
      <option value="csv">CSV (.csv)</option>
      <option value="json">JSON (.json)</option>
    </select>
  </div>
</div>
