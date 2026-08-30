<script lang="ts">
  import { activeModal } from '../../stores/appState';
  import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogDescription
  } from '../ui/dialog';
  import { Badge } from '../ui/badge';
  import { Keyboard } from 'lucide-svelte';

  let isOpen = false;
  $: isOpen = $activeModal === 'shortcuts';

  function handleOpenChange(open: boolean) {
    if (!open) activeModal.set(null);
  }

  const shortcuts = [
    { key: 'Ctrl + O', desc: 'Open file dialog (Local / SSH)' },
    { key: 'Ctrl + F', desc: 'Focus search input' },
    { key: 'Ctrl + B', desc: 'Toggle Recent Files panel' },
    { key: 'Ctrl + J', desc: 'Toggle Detail / Inspector panel' },
    { key: 'Ctrl + G', desc: 'Go to specific line' },
    { key: 'Ctrl + M', desc: 'Manage SSH remote servers' },
    { key: 'F5', desc: 'Reload active log file' },
    { key: 'F3 / Enter', desc: 'Jump to next search match' },
    { key: 'Shift + F3', desc: 'Jump to previous search match' },
    { key: 'Escape', desc: 'Clear search / Close modal' }
  ];
</script>

<Dialog open={isOpen} onOpenChange={handleOpenChange}>
  <DialogContent class="max-w-md flex flex-col p-4 gap-3.5 border-border">
    <DialogHeader class="border-b border-border pb-3 shrink-0">
      <DialogTitle class="text-sm font-semibold flex items-center gap-2">
        <Keyboard class="w-4 h-4 text-primary" />
        <span>Keyboard Shortcuts</span>
      </DialogTitle>
      <DialogDescription class="text-xs">
        Boost productivity with desktop-grade keyboard accelerators.
      </DialogDescription>
    </DialogHeader>

    <div class="flex flex-col gap-1.5 py-1 max-h-80 overflow-y-auto">
      {#each shortcuts as s}
        <div class="flex items-center justify-between p-2 rounded-[var(--radius-sm)] border border-border bg-background text-xs">
          <span class="text-foreground font-medium">{s.desc}</span>
          <Badge variant="outline" class="font-mono text-[10.5px] px-1.5 py-0">
            {s.key}
          </Badge>
        </div>
      {/each}
    </div>
  </DialogContent>
</Dialog>
