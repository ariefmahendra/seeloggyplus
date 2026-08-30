<script lang="ts">
  import { Button as ButtonPrimitive } from 'bits-ui';
  import { cn } from '../../../utils';

  type Variant = 'default' | 'destructive' | 'outline' | 'secondary' | 'ghost' | 'link';
  type Size = 'default' | 'sm' | 'xs' | 'lg' | 'icon';

  export let variant: Variant = 'default';
  export let size: Size = 'default';
  export let disabled: boolean = false;
  export let type: 'button' | 'submit' | 'reset' = 'button';
  export let href: string | undefined = undefined;
  let className: string = '';
  export { className as class };

  const variants: Record<Variant, string> = {
    default: 'bg-primary text-primary-foreground shadow hover:bg-primary/90',
    destructive: 'bg-destructive text-destructive-foreground shadow-sm hover:bg-destructive/90',
    outline: 'border border-input bg-background shadow-xs hover:bg-accent hover:text-accent-foreground',
    secondary: 'bg-secondary text-secondary-foreground shadow-xs hover:bg-secondary/80',
    ghost: 'hover:bg-accent hover:text-accent-foreground',
    link: 'text-primary underline-offset-4 hover:underline'
  };

  const sizes: Record<Size, string> = {
    default: 'h-8 px-3 py-1.5 text-xs',
    sm: 'h-7 px-2.5 py-1 text-[11px]',
    xs: 'h-6 px-2 py-0.5 text-[10px]',
    lg: 'h-9 px-4 text-sm',
    icon: 'h-7 w-7 p-0'
  };
</script>

<ButtonPrimitive.Root
  {type}
  {disabled}
  {href}
  class={cn(
    'inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-[var(--radius-sm)] font-medium transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50 select-none cursor-pointer',
    variants[variant] || variants.default,
    sizes[size] || sizes.default,
    className
  )}
  on:click
  {...$$restProps}
>
  <slot />
</ButtonPrimitive.Root>
