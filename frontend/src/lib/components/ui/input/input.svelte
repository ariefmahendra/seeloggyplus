<script lang="ts">
  import { cn } from '../../../utils';

  export let type: string = 'text';
  export let value: string | number = '';
  export let placeholder: string = '';
  export let disabled: boolean = false;
  export let readonly: boolean = false;
  export let min: string | number | undefined = undefined;
  export let max: string | number | undefined = undefined;
  export let step: string | number | undefined = undefined;
  export let id: string | undefined = undefined;
  export let spellcheck: boolean = true;
  let className: string = '';
  export { className as class };
</script>

{#if type === 'number'}
  <input
    {id}
    type="number"
    bind:value
    {placeholder}
    {disabled}
    {readonly}
    {min}
    {max}
    {step}
    class={cn(
      'flex h-8 w-full rounded-[var(--radius-sm)] border border-input bg-background px-2.5 py-1 text-xs shadow-2xs transition-colors file:border-0 file:bg-transparent file:text-xs file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 text-foreground',
      className
    )}
    on:input
    on:change
    on:keydown
    on:focus
    on:blur
    {...$$restProps}
  />
{:else if type === 'password'}
  <input
    {id}
    type="password"
    bind:value
    {placeholder}
    {disabled}
    {readonly}
    class={cn(
      'flex h-8 w-full rounded-[var(--radius-sm)] border border-input bg-background px-2.5 py-1 text-xs shadow-2xs transition-colors file:border-0 file:bg-transparent file:text-xs file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 text-foreground',
      className
    )}
    on:input
    on:change
    on:keydown
    on:focus
    on:blur
    {...$$restProps}
  />
{:else}
  <input
    {id}
    type="text"
    bind:value
    {placeholder}
    {disabled}
    {readonly}
    {spellcheck}
    class={cn(
      'flex h-8 w-full rounded-[var(--radius-sm)] border border-input bg-background px-2.5 py-1 text-xs shadow-2xs transition-colors file:border-0 file:bg-transparent file:text-xs file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 text-foreground',
      className
    )}
    on:input
    on:change
    on:keydown
    on:focus
    on:blur
    {...$$restProps}
  />
{/if}
