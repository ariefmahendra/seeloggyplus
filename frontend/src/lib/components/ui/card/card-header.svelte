<script lang="ts">
  import { cn } from '../../../utils';

  let className: string = '';
  export { className as class };
</script>

<div class={cn('flex flex-col space-y-1.5 p-4', className)} {...$$restProps}>
  <slot />
</div>
