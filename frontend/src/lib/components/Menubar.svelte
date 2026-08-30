<script lang="ts">
  import {
    currentFile,
    activeModal,
    currentTheme,
    leftPanelVisible,
    bottomPanelVisible,
    isTailing
  } from '../stores/appState';
  import { toast } from '../stores/toast';
  import {
    Menubar,
    MenubarMenu,
    MenubarTrigger,
    MenubarContent,
    MenubarItem,
    MenubarSeparator,
    MenubarShortcut
  } from './ui/menubar';
  import { Button } from './ui/button';
  import { Badge } from './ui/badge';
  import {
    FileText,
    FolderOpen,
    RefreshCw,
    Download,
    FileSpreadsheet,
    FileCode,
    Power,
    Check,
    ArrowRightCircle,
    SunMoon,
    Server,
    Bookmark,
    Settings,
    Keyboard,
    Info,
    Moon,
    Sun,
    Globe,
    Eye
  } from 'lucide-svelte';

  export let onReload: () => void = () => {};
  export let onToggleTail: () => void = () => {};
  export let onExport: (format: string) => void = () => {};
  export let onClearSession: () => void = () => {};

  function toggleTheme() {
    currentTheme.update(t => {
      const next = t === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', next);
      localStorage.setItem('seeloggy_theme', next);
      toast.info(`Switched to ${next} theme`);
      return next;
    });
  }
</script>

<header class="h-[32px] bg-background border-b border-border flex items-center px-2 gap-2 z-50 select-none shrink-0">
  <!-- Brand -->
  <div class="flex items-center gap-1.5 pr-2 border-r border-border">
    <div class="w-5 h-5 bg-primary text-primary-foreground rounded-[var(--radius-sm)] flex items-center justify-center">
      <FileText class="w-3 h-3" />
    </div>
    <span class="font-bold text-xs text-foreground tracking-tight">SeeLoggy<span class="text-muted-foreground font-extrabold">+</span></span>
  </div>

  <!-- Official shadcn-svelte Menubar -->
  <Menubar class="border-none bg-transparent h-7 p-0 space-x-0.5">
    <!-- File Menu -->
    <MenubarMenu>
      <MenubarTrigger>File</MenubarTrigger>
      <MenubarContent>
        <MenubarItem on:click={() => activeModal.set('file-manager')}>
          <span class="flex items-center gap-1.5"><FolderOpen class="w-3.5 h-3.5" /> Open Log File...</span>
          <MenubarShortcut>Ctrl+O</MenubarShortcut>
        </MenubarItem>
        <MenubarItem on:click={onToggleTail}>
          <span class="flex items-center gap-1.5"><Eye class="w-3.5 h-3.5 {$isTailing ? 'text-emerald-500' : ''}" /> {$isTailing ? 'Pause Live Tail' : 'Start Live Tail'}</span>
          <MenubarShortcut>Ctrl+T</MenubarShortcut>
        </MenubarItem>
        <MenubarItem on:click={onReload}>
          <span class="flex items-center gap-1.5"><RefreshCw class="w-3.5 h-3.5" /> Reload Log</span>
          <MenubarShortcut>F5</MenubarShortcut>
        </MenubarItem>
        <MenubarSeparator />
        <MenubarItem on:click={() => onExport('txt')}>
          <span class="flex items-center gap-1.5"><Download class="w-3.5 h-3.5" /> Export as Text (.txt)</span>
        </MenubarItem>

        <MenubarItem on:click={() => onExport('csv')}>
          <span class="flex items-center gap-1.5"><FileSpreadsheet class="w-3.5 h-3.5" /> Export as CSV (.csv)</span>
        </MenubarItem>
        <MenubarItem on:click={() => onExport('json')}>
          <span class="flex items-center gap-1.5"><FileCode class="w-3.5 h-3.5" /> Export as JSON (.json)</span>
        </MenubarItem>
        <MenubarSeparator />
        <MenubarItem class="text-destructive focus:text-destructive" on:click={onClearSession}>
          <span class="flex items-center gap-1.5"><Power class="w-3.5 h-3.5" /> Close / Clear Session</span>
          <MenubarShortcut>Alt+F4</MenubarShortcut>
        </MenubarItem>
      </MenubarContent>
    </MenubarMenu>

    <!-- View Menu -->
    <MenubarMenu>
      <MenubarTrigger>View</MenubarTrigger>
      <MenubarContent>
        <MenubarItem on:click={() => leftPanelVisible.update(v => !v)}>
          <span class="flex items-center gap-1.5">
            <Check class="w-3.5 h-3.5 {$leftPanelVisible ? 'opacity-100' : 'opacity-0'}" /> Show Recent Files
          </span>
          <MenubarShortcut>Ctrl+B</MenubarShortcut>
        </MenubarItem>
        <MenubarItem on:click={() => bottomPanelVisible.update(v => !v)}>
          <span class="flex items-center gap-1.5">
            <Check class="w-3.5 h-3.5 {$bottomPanelVisible ? 'opacity-100' : 'opacity-0'}" /> Show Detail Panel
          </span>
          <MenubarShortcut>Ctrl+J</MenubarShortcut>
        </MenubarItem>
        <MenubarSeparator />
        <MenubarItem on:click={() => activeModal.set('goto-line')}>
          <span class="flex items-center gap-1.5"><ArrowRightCircle class="w-3.5 h-3.5" /> Go To Line...</span>
          <MenubarShortcut>Ctrl+G</MenubarShortcut>
        </MenubarItem>
        <MenubarItem on:click={toggleTheme}>
          <span class="flex items-center gap-1.5"><SunMoon class="w-3.5 h-3.5" /> Toggle Dark/Light Theme</span>
        </MenubarItem>
      </MenubarContent>
    </MenubarMenu>

    <!-- Settings Menu -->
    <MenubarMenu>
      <MenubarTrigger>Settings</MenubarTrigger>
      <MenubarContent>
        <MenubarItem on:click={() => activeModal.set('server-manager')}>
          <span class="flex items-center gap-1.5"><Server class="w-3.5 h-3.5" /> Server Management...</span>
          <MenubarShortcut>Ctrl+M</MenubarShortcut>
        </MenubarItem>
        <MenubarItem on:click={() => activeModal.set('filter-manager')}>
          <span class="flex items-center gap-1.5"><Bookmark class="w-3.5 h-3.5" /> Saved Filters...</span>
        </MenubarItem>
        <MenubarSeparator />
        <MenubarItem on:click={() => activeModal.set('preferences')}>
          <span class="flex items-center gap-1.5"><Settings class="w-3.5 h-3.5" /> Preferences...</span>
        </MenubarItem>
      </MenubarContent>
    </MenubarMenu>

    <!-- Help Menu -->
    <MenubarMenu>
      <MenubarTrigger>Help</MenubarTrigger>
      <MenubarContent>
        <MenubarItem on:click={() => activeModal.set('shortcuts')}>
          <span class="flex items-center gap-1.5"><Keyboard class="w-3.5 h-3.5" /> Keyboard Shortcuts</span>
          <MenubarShortcut>F1</MenubarShortcut>
        </MenubarItem>
        <MenubarSeparator />
        <MenubarItem on:click={() => activeModal.set('about')}>
          <span class="flex items-center gap-1.5"><Info class="w-3.5 h-3.5" /> About SeeLoggy+</span>
        </MenubarItem>
      </MenubarContent>
    </MenubarMenu>
  </Menubar>

  <!-- Active File Badge in Header -->
  {#if $currentFile}
    <Badge variant="outline" class="gap-1.5 max-w-[380px] truncate h-6 font-normal">
      {#if $currentFile.source === 'REMOTE'}
        <Globe class="w-3.5 h-3.5 text-blue-400 shrink-0" />
      {:else}
        <FileText class="w-3.5 h-3.5 text-muted-foreground shrink-0" />
      {/if}
      <span class="truncate">{$currentFile.fileName}</span>
    </Badge>
  {:else}
    <Badge variant="secondary" class="gap-1.5 text-muted-foreground h-6 font-normal">
      <span>No file loaded</span>
    </Badge>
  {/if}

  <!-- Right Fast Actions -->
  <div class="ml-auto flex items-center gap-1.5">
    <Button
      variant="default"
      size="sm"
      on:click={() => activeModal.set('file-manager')}
      title="Open Log File (Ctrl+O)"
    >
      <FolderOpen class="w-3.5 h-3.5" />
      <span>Open Log...</span>
    </Button>
    <Button
      variant="ghost"
      size="icon"
      on:click={toggleTheme}
      title="Toggle Dark/Light Theme"
    >
      {#if $currentTheme === 'dark'}
        <Moon class="w-3.5 h-3.5" />
      {:else}
        <Sun class="w-3.5 h-3.5" />
      {/if}
    </Button>
  </div>
</header>
