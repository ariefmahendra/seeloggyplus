<script lang="ts">
  import { cn } from '../../../utils';

  let className: string = '';
  export { className as class };
</script>

<h3 class={cn('font-semibold leading-none tracking-tight text-foreground', className)} {...$$restProps}>
  <slot />
</h3>
