<script lang="ts">
  import { Tabs as TabsPrimitive } from 'bits-ui';
  import { cn } from '../../../utils';

  let className: string = '';
  export { className as class };
</script>

<TabsPrimitive.List
  class={cn(
    'inline-flex h-7 items-center justify-center rounded-[var(--radius-sm)] bg-muted p-0.5 text-muted-foreground border border-border',
    className
  )}
  {...$$restProps}
>
  <slot />
</TabsPrimitive.List>
