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
  import { FileText, Cpu, CheckCircle } from 'lucide-svelte';

  let isOpen = false;
  $: isOpen = $activeModal === 'about';

  function handleOpenChange(open: boolean) {
    if (!open) activeModal.set(null);
  }
</script>

<Dialog open={isOpen} onOpenChange={handleOpenChange}>
  <DialogContent class="max-w-md flex flex-col p-5 gap-4 border-border text-center">
    <div class="flex flex-col items-center gap-2">
      <div class="w-12 h-12 bg-primary text-primary-foreground rounded-2xl flex items-center justify-center shadow-lg">
        <FileText class="w-6 h-6" />
      </div>
      <div>
        <h2 class="text-lg font-bold tracking-tight text-foreground">SeeLoggy<span class="text-primary font-black">+</span></h2>
        <Badge variant="outline" class="mt-1 font-mono text-[10px]">Version 0.2.0 (Svelte Edition)</Badge>
      </div>
    </div>

    <DialogDescription class="text-xs text-muted-foreground leading-relaxed px-4 text-center">
      High-performance log analysis tool designed to inspect massive gigabyte-scale logs effortlessly via intelligent indexed streaming, regex parsing, and live SSH tailing.
    </DialogDescription>

    <div class="p-3 bg-muted/40 rounded-[var(--radius-md)] border border-border flex flex-col gap-1.5 text-left text-xs font-mono">
      <div class="flex items-center justify-between text-muted-foreground">
        <span>Engine:</span>
        <span class="text-foreground font-semibold">Java 17 + Javalin Server</span>
      </div>
      <div class="flex items-center justify-between text-muted-foreground">
        <span>Frontend:</span>
        <span class="text-foreground font-semibold">Svelte 4 + Tailwind + shadcn</span>
      </div>
      <div class="flex items-center justify-between text-muted-foreground">
        <span>Virtualization:</span>
        <span class="text-foreground font-semibold">Indexed Chunk Scroller (O(1))</span>
      </div>
    </div>
  </DialogContent>
</Dialog>
