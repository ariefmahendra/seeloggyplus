<script lang="ts">
  import { cn } from '../../../utils';

  let className: string = '';
  export { className as class };
</script>

<div
  class={cn(
    'rounded-[var(--radius-lg)] border border-border bg-card text-card-foreground shadow-xs',
    className
  )}
  {...$$restProps}
>
  <slot />
</div>
