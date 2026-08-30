<script lang="ts">
  import { Dialog as DialogPrimitive } from 'bits-ui';
  import { cn } from '../../../utils';

  let className: string = '';
  export { className as class };
</script>

<DialogPrimitive.Title
  class={cn('text-sm font-semibold leading-none tracking-tight flex items-center gap-2 text-foreground', className)}
  {...$$restProps}
>
  <slot />
</DialogPrimitive.Title>
