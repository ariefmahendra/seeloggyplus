<script lang="ts">
  import { Tabs as TabsPrimitive } from 'bits-ui';
  import { cn } from '../../../utils';

  export let value: string;
  let className: string = '';
  export { className as class };
</script>

<TabsPrimitive.Content
  {value}
  class={cn(
    'mt-2 ring-offset-background focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring',
    className
  )}
  {...$$restProps}
>
  <slot />
</TabsPrimitive.Content>
