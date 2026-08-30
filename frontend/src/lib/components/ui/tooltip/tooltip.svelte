<script lang="ts">
  import { Tooltip as TooltipPrimitive } from 'bits-ui';

  export let open: boolean = false;
  export let onOpenChange: ((open: boolean) => void) | undefined = undefined;
  export let delayDuration: number = 200;
</script>

<TooltipPrimitive.Root bind:open {onOpenChange} {delayDuration} {...$$restProps}>
  <slot />
</TooltipPrimitive.Root>
