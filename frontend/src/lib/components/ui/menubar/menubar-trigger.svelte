<script lang="ts">
  import { Menubar as MenubarPrimitive } from 'bits-ui';
  import { cn } from '../../../utils';

  let className: string = '';
  export { className as class };
</script>

<MenubarPrimitive.Trigger
  class={cn(
    'flex cursor-pointer select-none items-center rounded-xs px-2 py-0.5 text-[11.5px] font-medium outline-none text-foreground hover:bg-accent hover:text-accent-foreground data-[state=open]:bg-accent data-[state=open]:text-accent-foreground',
    className
  )}
  {...$$restProps}
>
  <slot />
</MenubarPrimitive.Trigger>
