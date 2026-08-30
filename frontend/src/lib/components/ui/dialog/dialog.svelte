<script lang="ts">
  import { Dialog as DialogPrimitive } from 'bits-ui';

  export let open: boolean = false;
  export let onOpenChange: ((open: boolean) => void) | undefined = undefined;
</script>

<DialogPrimitive.Root bind:open {onOpenChange} {...$$restProps}>
  <slot />
</DialogPrimitive.Root>
