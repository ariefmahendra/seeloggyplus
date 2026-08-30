<script lang="ts">
  import { Tabs as TabsPrimitive } from 'bits-ui';
  import { cn } from '../../../utils';

  export let value: string;
  let className: string = '';
  export { className as class };
</script>

<TabsPrimitive.Trigger
  {value}
  class={cn(
    'inline-flex items-center justify-center whitespace-nowrap rounded-[var(--radius-sm)] px-2 py-0.5 text-[10.5px] font-semibold ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50 data-[state=active]:bg-background data-[state=active]:text-foreground data-[state=active]:shadow-xs text-muted-foreground hover:text-foreground cursor-pointer',
    className
  )}
  {...$$restProps}
>
  <slot />
</TabsPrimitive.Trigger>
