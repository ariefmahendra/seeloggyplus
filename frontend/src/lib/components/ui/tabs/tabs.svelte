<script lang="ts">
  import { Tabs as TabsPrimitive } from 'bits-ui';

  export let value: string = '';
  export let onValueChange: ((value: string) => void) | undefined = undefined;
</script>

<TabsPrimitive.Root bind:value {onValueChange} {...$$restProps}>
  <slot />
</TabsPrimitive.Root>
