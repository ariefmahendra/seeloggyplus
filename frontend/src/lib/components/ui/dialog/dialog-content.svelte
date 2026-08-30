<script lang="ts">
  import { Dialog as DialogPrimitive } from 'bits-ui';
  import { X } from 'lucide-svelte';
  import DialogOverlay from './dialog-overlay.svelte';
  import { cn } from '../../../utils';

  let className: string = '';
  export { className as class };
</script>

<DialogPrimitive.Portal>
  <DialogOverlay />
  <DialogPrimitive.Content
    class={cn(
      'fixed left-[50%] top-[50%] z-50 grid w-full max-w-lg translate-x-[-50%] translate-y-[-50%] gap-4 border border-border bg-background p-5 shadow-lg duration-150 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 rounded-[var(--radius-lg)] text-foreground',
      className
    )}
    {...$$restProps}
  >
    <slot />
    <DialogPrimitive.Close
      class="absolute right-3.5 top-3.5 rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-1 focus:ring-ring disabled:pointer-events-none p-1 text-muted-foreground hover:text-foreground hover:bg-accent"
    >
      <X class="h-3.5 w-3.5" />
      <span class="sr-only">Close</span>
    </DialogPrimitive.Close>
  </DialogPrimitive.Content>
</DialogPrimitive.Portal>
