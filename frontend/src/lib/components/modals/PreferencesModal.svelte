<script lang="ts">
  import { onMount } from 'svelte';
  import { activeModal } from '../../stores/appState';
  import { API } from '../../api';
  import { toast } from '../../stores/toast';
  import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogDescription,
    DialogFooter
  } from '../ui/dialog';
  import { Button } from '../ui/button';
  import { Input } from '../ui/input';
  import { Settings } from 'lucide-svelte';

  let maxRecent = 10;
  let encoding = 'UTF-8';
  let timeout = 15;

  let isOpen = false;
  $: isOpen = $activeModal === 'preferences';

  function handleOpenChange(open: boolean) {
    if (!open) activeModal.set(null);
  }

  onMount(async () => {
    try {
      const res = await API.getPreferences();
      if (res.success && res.data) {
        maxRecent = res.data.maxRecentFiles || 10;
        encoding = res.data.defaultEncoding || 'UTF-8';
        timeout = res.data.sshTimeout || 15;
      }
    } catch (e) {
      console.error(e);
    }
  });

  async function save() {
    try {
      await API.updatePreferences({
        maxRecentFiles: Number(maxRecent),
        defaultEncoding: encoding,
        sshTimeout: Number(timeout)
      });
      toast.success('Preferences saved successfully');
      activeModal.set(null);
    } catch {
      toast.error('Failed to save preferences');
    }
  }
</script>

<Dialog open={isOpen} onOpenChange={handleOpenChange}>
  <DialogContent class="max-w-md flex flex-col p-4 gap-4 border-border">
    <DialogHeader class="border-b border-border pb-3 shrink-0">
      <DialogTitle class="text-sm font-semibold flex items-center gap-2">
        <Settings class="w-4 h-4 text-primary" />
        <span>Application Preferences</span>
      </DialogTitle>
      <DialogDescription class="text-xs">
        Customize log parsing buffer defaults, SSH timeouts, and history limits.
      </DialogDescription>
    </DialogHeader>

    <div class="flex flex-col gap-3.5 py-1">
      <div>
        <label for="pref-max-recent" class="text-[11px] font-medium text-foreground block mb-1">Max Recent Files in History</label>
        <Input id="pref-max-recent" type="number" bind:value={maxRecent} min="1" class="h-8 text-xs" />
      </div>

      <div>
        <label for="pref-encoding" class="text-[11px] font-medium text-foreground block mb-1">Default File Encoding</label>
        <Input id="pref-encoding" bind:value={encoding} class="h-8 text-xs font-mono" />
      </div>

      <div>
        <label for="pref-timeout" class="text-[11px] font-medium text-foreground block mb-1">SSH Connection Timeout (Seconds)</label>
        <Input id="pref-timeout" type="number" bind:value={timeout} min="5" class="h-8 text-xs" />
      </div>
    </div>

    <DialogFooter class="border-t border-border pt-3 flex items-center justify-end gap-2 shrink-0">
      <Button variant="outline" size="sm" on:click={() => activeModal.set(null)}>
        Cancel
      </Button>
      <Button variant="default" size="sm" on:click={save}>
        Save Preferences
      </Button>
    </DialogFooter>
  </DialogContent>
</Dialog>
