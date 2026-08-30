<script lang="ts">
  import { cn } from '../../../utils';

  let className: string = '';
  export { className as class };
</script>

<span class={cn('ml-auto text-[10px] tracking-widest text-muted-foreground font-mono', className)} {...$$restProps}>
  <slot />
</span>
