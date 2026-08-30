<script lang="ts">
  import { activeModal, totalLinesCount } from '../../stores/appState';
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
  import { ArrowRightCircle } from 'lucide-svelte';

  export let onGoto: (line: number) => void = () => {};

  let targetLine: number = 1;
  let isOpen = false;
  $: isOpen = $activeModal === 'goto-line';

  function handleOpenChange(open: boolean) {
    if (!open) activeModal.set(null);
  }

  function submit() {
    if (targetLine >= 1 && targetLine <= $totalLinesCount) {
      onGoto(targetLine);
      activeModal.set(null);
    }
  }
</script>

<Dialog open={isOpen} onOpenChange={handleOpenChange}>
  <DialogContent class="max-w-sm flex flex-col p-4 gap-4 border-border">
    <DialogHeader class="border-b border-border pb-3 shrink-0">
      <DialogTitle class="text-sm font-semibold flex items-center gap-2">
        <ArrowRightCircle class="w-4 h-4 text-primary" />
        <span>Go to Line</span>
      </DialogTitle>
      <DialogDescription class="text-xs">
        Quickly navigate viewport to an exact line number.
      </DialogDescription>
    </DialogHeader>

    <div class="flex flex-col gap-2 py-1">
      <label for="goto-line-input" class="text-[11px] font-medium text-foreground">
        Enter line number (1 to {$totalLinesCount.toLocaleString()}):
      </label>
      <Input
        id="goto-line-input"
        type="number"
        min="1"
        max={$totalLinesCount}
        bind:value={targetLine}
        on:keydown={(e) => e.key === 'Enter' && submit()}
        class="h-8 text-xs font-mono"
      />
    </div>

    <DialogFooter class="border-t border-border pt-3 flex items-center justify-end gap-2 shrink-0">
      <Button variant="outline" size="sm" on:click={() => activeModal.set(null)}>
        Cancel
      </Button>
      <Button variant="default" size="sm" on:click={submit}>
        Jump to Line
      </Button>
    </DialogFooter>
  </DialogContent>
</Dialog>
