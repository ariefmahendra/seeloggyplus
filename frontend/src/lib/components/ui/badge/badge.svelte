<script lang="ts">
  import { cn } from '../../../utils';

  type Variant = 'default' | 'secondary' | 'destructive' | 'outline';

  export let variant: Variant = 'default';
  let className: string = '';
  export { className as class };

  const variants: Record<Variant, string> = {
    default: 'border-transparent bg-primary text-primary-foreground shadow hover:bg-primary/80',
    secondary: 'border-transparent bg-secondary text-secondary-foreground hover:bg-secondary/80',
    destructive: 'border-transparent bg-destructive text-destructive-foreground shadow hover:bg-destructive/80',
    outline: 'text-foreground border-border'
  };
</script>

<div
  class={cn(
    'inline-flex items-center rounded-[var(--radius-sm)] border px-1.5 py-0.5 text-[10.5px] font-semibold transition-colors focus:outline-none focus:ring-1 focus:ring-ring select-none',
    variants[variant] || variants.default,
    className
  )}
  {...$$restProps}
>
  <slot />
</div>
