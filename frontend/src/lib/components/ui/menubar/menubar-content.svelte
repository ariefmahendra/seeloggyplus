<script lang="ts">
  import { Menubar as MenubarPrimitive } from 'bits-ui';
  import { cn } from '../../../utils';

  let className: string = '';
  export { className as class };
  export let align: 'start' | 'center' | 'end' = 'start';
  export let sideOffset: number = 4;
</script>

<MenubarPrimitive.Content
  {align}
  {sideOffset}
  class={cn(
    'z-50 min-w-[200px] overflow-hidden rounded-[var(--radius-sm)] border border-border bg-popover p-1 text-popover-foreground shadow-[var(--shadow-popover)] data-[state=open]:animate-in data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[side=bottom]:slide-in-from-top-2 data-[side=top]:slide-in-from-bottom-2',
    className
  )}
  {...$$restProps}
>
  <slot />
</MenubarPrimitive.Content>
