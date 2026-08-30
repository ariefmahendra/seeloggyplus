<script lang="ts">
  import { Tooltip as TooltipPrimitive } from 'bits-ui';
  import { cn } from '../../../utils';

  let className: string = '';
  export { className as class };
  export let sideOffset: number = 4;
</script>

<TooltipPrimitive.Content
  {sideOffset}
  class={cn(
    'z-50 overflow-hidden rounded-[var(--radius-sm)] border border-border bg-popover px-2.5 py-1 text-[11px] text-popover-foreground shadow-xs animate-in fade-in-0 zoom-in-95 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2',
    className
  )}
  {...$$restProps}
>
  <slot />
</TooltipPrimitive.Content>
