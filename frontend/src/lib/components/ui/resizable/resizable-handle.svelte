<script lang="ts">
  import * as ResizablePrimitive from 'paneforge';
  import { GripVertical } from 'lucide-svelte';
  import { cn } from '../../../utils';

  export let withHandle: boolean = false;
  let className: string = '';
  export { className as class };
</script>

<ResizablePrimitive.PaneResizer
  class={cn(
    'relative flex items-center justify-center bg-border focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring focus-visible:ring-offset-1 hover:bg-ring/60 transition-colors select-none data-[direction=horizontal]:w-[4px] data-[direction=horizontal]:h-full data-[direction=horizontal]:cursor-col-resize data-[direction=vertical]:h-[4px] data-[direction=vertical]:w-full data-[direction=vertical]:cursor-row-resize [&[data-direction=vertical]>div]:rotate-90',
    className
  )}
  {...$$restProps}
>
  {#if withHandle}
    <div class="z-20 flex h-4 w-3 items-center justify-center rounded-xs border border-border bg-card shadow-xs transition-colors hover:border-primary/50">
      <GripVertical class="h-2.5 w-2.5 text-muted-foreground" />
    </div>
  {/if}
</ResizablePrimitive.PaneResizer>



