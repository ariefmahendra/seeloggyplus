<script lang="ts">
  import { cn } from '../../../utils';

  let className: string = '';
  export { className as class };
</script>

<p class={cn('text-xs text-muted-foreground', className)} {...$$restProps}>
  <slot />
</p>
