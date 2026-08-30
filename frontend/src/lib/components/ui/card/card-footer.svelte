<script lang="ts">
  import { cn } from '../../../utils';

  let className: string = '';
  export { className as class };
</script>

<div class={cn('flex items-center p-4 pt-0', className)} {...$$restProps}>
  <slot />
</div>
