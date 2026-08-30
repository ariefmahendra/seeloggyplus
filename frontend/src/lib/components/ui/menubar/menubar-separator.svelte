<script lang="ts">
  import { Menubar as MenubarPrimitive } from 'bits-ui';
  import { cn } from '../../../utils';

  let className: string = '';
  export { className as class };
</script>

<MenubarPrimitive.Separator class={cn('-mx-1 my-1 h-px bg-border', className)} {...$$restProps} />
