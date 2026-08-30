<script lang="ts">
  import * as ResizablePrimitive from 'paneforge';
  import { cn } from '../../../utils';

  export let direction: 'horizontal' | 'vertical' = 'horizontal';
  export let autoSaveId: string | undefined = undefined;
  let className: string = '';
  export { className as class };
</script>

<ResizablePrimitive.PaneGroup
  {direction}
  {autoSaveId}
  class={cn('flex h-full w-full data-[panel-group-direction=vertical]:flex-col', className)}
  {...$$restProps}
>
  <slot />
</ResizablePrimitive.PaneGroup>
