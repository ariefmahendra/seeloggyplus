<script lang="ts">
  import { Menubar as MenubarPrimitive } from 'bits-ui';
  import { cn } from '../../../utils';

  let className: string = '';
  export { className as class };
</script>

<MenubarPrimitive.Root
  class={cn(
    'flex h-7 items-center space-x-1 rounded-[var(--radius-sm)] border border-border bg-background p-1',
    className
  )}
  {...$$restProps}
>
  <slot />
</MenubarPrimitive.Root>
