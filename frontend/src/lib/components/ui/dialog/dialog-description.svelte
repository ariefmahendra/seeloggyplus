<script lang="ts">
  import { Dialog as DialogPrimitive } from 'bits-ui';
  import { cn } from '../../../utils';

  let className: string = '';
  export { className as class };
</script>

<DialogPrimitive.Description
  class={cn('text-xs text-muted-foreground', className)}
  {...$$restProps}
>
  <slot />
</DialogPrimitive.Description>
