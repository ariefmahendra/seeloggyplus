<script lang="ts">
  import { Menubar as MenubarPrimitive } from 'bits-ui';
  import { cn } from '../../../utils';

  let className: string = '';
  export { className as class };
</script>

<MenubarPrimitive.Item
  class={cn(
    'relative flex cursor-pointer select-none items-center justify-between rounded-xs px-2 py-1 text-[11.5px] outline-none text-foreground data-[highlighted]:bg-accent data-[highlighted]:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
    className
  )}
  on:click
  {...$$restProps}
>
  <slot />
</MenubarPrimitive.Item>
