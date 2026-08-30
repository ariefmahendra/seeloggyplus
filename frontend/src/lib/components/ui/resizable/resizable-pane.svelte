<script lang="ts">
  import * as ResizablePrimitive from 'paneforge';
  import { cn } from '../../../utils';

  export let defaultSize: number | undefined = undefined;
  export let minSize: number | undefined = undefined;
  export let maxSize: number | undefined = undefined;
  export let order: number | undefined = undefined;
  let className: string = '';
  export { className as class };
</script>

<ResizablePrimitive.Pane
  {defaultSize}
  {minSize}
  {maxSize}
  {order}
  class={cn(className)}
  {...$$restProps}
>
  <slot />
</ResizablePrimitive.Pane>
