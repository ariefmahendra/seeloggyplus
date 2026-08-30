<script lang="ts">
  import { Tooltip as TooltipPrimitive } from 'bits-ui';
</script>

<TooltipPrimitive.Trigger asChild let:builder {...$$restProps}>
  <slot {builder} />
</TooltipPrimitive.Trigger>
