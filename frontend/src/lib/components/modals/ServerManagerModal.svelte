<script lang="ts">
  import { onMount } from 'svelte';
  import { activeModal, sshServersStore, refreshSSHServers } from '../../stores/appState';
  import { API } from '../../api';
  import { toast } from '../../stores/toast';
  import type { SSHServerModel } from '../../types';
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
  import { Badge } from '../ui/badge';
  import { Server, Plus, Trash2, CheckCircle2, XCircle, RefreshCw } from 'lucide-svelte';

  $: servers = $sshServersStore;
  let selectedServer: SSHServerModel | null = null;
  let isCreatingNew = false;
  let testing = false;
  let testResult: { success: boolean; message: string } | null = null;

  // Form State
  let formName = '';
  let formHost = '';
  let formPort = 22;
  let formUser = '';
  let formPass = '';
  let formDir = '/var/log';

  let isOpen = false;
  $: isOpen = $activeModal === 'server-manager';

  function handleOpenChange(open: boolean) {
    if (!open) activeModal.set(null);
  }

  onMount(async () => {
    await loadServers();
  });

  $: if (isOpen) {
    loadServers();
  }

  async function loadServers() {
    const list = await refreshSSHServers();
    if (list.length > 0 && !selectedServer && !isCreatingNew) {
      selectServer(list[0]);
    }
  }


  function selectServer(s: SSHServerModel) {
    selectedServer = s;
    isCreatingNew = false;
    formName = s.name;
    formHost = s.host;
    formPort = s.port || 22;
    formUser = s.username;
    formPass = s.password || '';
    formDir = s.defaultPath || '/var/log';
    testResult = null;
  }

  function createNew() {
    selectedServer = null;
    isCreatingNew = true;
    formName = 'New Remote Server';
    formHost = '';
    formPort = 22;
    formUser = 'ubuntu';
    formPass = '';
    formDir = '/var/log';
    testResult = null;
  }

  async function handleSave() {
    if (!formName || !formHost || !formUser) {
      toast.warning('Server Name, Host and Username are required');
      return;
    }
    const payload = {
      name: formName,
      host: formHost,
      port: Number(formPort),
      username: formUser,
      password: formPass,
      defaultPath: formDir
    };

    if (selectedServer) {
      await API.updateSSHServer(selectedServer.id, payload);
      toast.success('Server configuration updated');
    } else {
      const res = await API.createSSHServer(payload);
      if (res.success && res.data) {
        selectedServer = res.data;
        isCreatingNew = false;
      }
      toast.success('Server added successfully');
    }
    await loadServers();
  }

  async function handleDelete(id: string) {
    if (!confirm('Are you sure you want to delete this SSH server?')) return;
    await API.deleteSSHServer(id);
    toast.info('Server deleted');
    selectedServer = null;
    isCreatingNew = false;
    await loadServers();
  }

  async function testConnection() {
    if (!formHost || !formUser) {
      toast.warning('Host and Username are required for testing');
      return;
    }
    testing = true;
    testResult = null;
    try {
      const res = await API.testSSHConnection({
        host: formHost,
        port: Number(formPort),
        username: formUser,
        password: formPass
      });
      testResult = {
        success: res.success,
        message: res.message || (res.success ? 'Connected successfully!' : 'Connection failed')
      };
      if (res.success) toast.success('SSH Connection OK');
      else toast.error('SSH Connection failed');
    } catch (e: any) {
      testResult = { success: false, message: e.message || 'Connection error' };
      toast.error('SSH Connection error');
    } finally {
      testing = false;
    }
  }
</script>

<Dialog open={isOpen} onOpenChange={handleOpenChange}>
  <DialogContent class="max-w-2xl flex flex-col p-0 gap-0 overflow-hidden border-border">
    <DialogHeader class="p-4 border-b border-border bg-muted/20 shrink-0">
      <DialogTitle class="text-sm font-semibold flex items-center gap-2">
        <Server class="w-4 h-4 text-primary" />
        <span>SSH Server Management</span>
      </DialogTitle>
      <DialogDescription class="text-xs">
        Configure remote SSH credentials to stream and tail Linux server logs in real-time.
      </DialogDescription>
    </DialogHeader>

    <div class="grid grid-cols-3 min-h-[340px] bg-background">
      <!-- Left Servers List -->
      <div class="border-r border-border p-3 flex flex-col gap-2 bg-muted/10">
        <Button
          variant={isCreatingNew ? 'default' : 'outline'}
          size="sm"
          class="w-full gap-1.5 justify-center text-xs"
          on:click={createNew}
        >
          <Plus class="w-3.5 h-3.5" />
          <span>Add New Server</span>
        </Button>
        <div class="flex-1 overflow-y-auto flex flex-col gap-0.5 mt-1">
          {#each servers as s}
            {@const isSel = selectedServer?.id === s.id && !isCreatingNew}
            <button
              class="w-full text-left px-2.5 py-2 rounded-[var(--radius-sm)] text-xs transition-colors {isSel ? 'bg-primary/15 font-semibold text-foreground' : 'hover:bg-accent text-foreground'}"
              on:click={() => selectServer(s)}
            >
              <div class="font-medium truncate">{s.name}</div>
              <div class="text-[10.5px] text-muted-foreground font-mono truncate">{s.username}@{s.host}</div>
            </button>
          {/each}
        </div>

      </div>


      <!-- Right Edit Form -->
      <div class="col-span-2 p-4 flex flex-col gap-3.5">
        <div class="grid grid-cols-2 gap-2.5">
          <div>
            <label for="server-name" class="text-[11px] font-medium text-foreground block mb-1">Server Name</label>
            <Input id="server-name" bind:value={formName} placeholder="Production Server" class="h-8 text-xs" />
          </div>
          <div>
            <label for="server-host" class="text-[11px] font-medium text-foreground block mb-1">Host / IP</label>
            <Input id="server-host" bind:value={formHost} placeholder="192.168.1.100" class="h-8 text-xs" />
          </div>
        </div>

        <div class="grid grid-cols-3 gap-2.5">
          <div>
            <label for="server-port" class="text-[11px] font-medium text-foreground block mb-1">Port</label>
            <Input id="server-port" type="number" bind:value={formPort} placeholder="22" class="h-8 text-xs" />
          </div>
          <div>
            <label for="server-user" class="text-[11px] font-medium text-foreground block mb-1">Username</label>
            <Input id="server-user" bind:value={formUser} placeholder="ubuntu" class="h-8 text-xs" />
          </div>
          <div>
            <label for="server-pass" class="text-[11px] font-medium text-foreground block mb-1">Password</label>
            <Input id="server-pass" type="password" bind:value={formPass} placeholder="••••••••" class="h-8 text-xs" />
          </div>
        </div>

        <div>
          <label for="server-dir" class="text-[11px] font-medium text-foreground block mb-1">Default Log Directory</label>
          <Input id="server-dir" bind:value={formDir} placeholder="/var/log" class="h-8 text-xs" />
        </div>

        <!-- Connection Test Feedback -->
        {#if testResult}
          <div class="p-2 rounded-[var(--radius-sm)] border text-xs flex items-center gap-2 {testResult.success ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400' : 'bg-destructive/10 border-destructive/30 text-destructive'}">
            {#if testResult.success}
              <CheckCircle2 class="w-4 h-4 shrink-0" />
            {:else}
              <XCircle class="w-4 h-4 shrink-0" />
            {/if}
            <span class="truncate">{testResult.message}</span>
          </div>
        {/if}

        <div class="mt-auto flex items-center justify-between pt-2 border-t border-border">
          <Button
            variant="outline"
            size="sm"
            class="gap-1.5 text-xs"
            on:click={testConnection}
            disabled={testing}
          >
            <RefreshCw class="w-3.5 h-3.5 {testing ? 'animate-spin' : ''}" />
            <span>Test Connection</span>
          </Button>

          <div class="flex items-center gap-2">
            {#if selectedServer}
              <Button
                variant="destructive"
                size="sm"
                class="gap-1 text-xs"
                on:click={() => handleDelete(selectedServer?.id || '')}
              >
                <Trash2 class="w-3.5 h-3.5" />
                <span>Delete</span>
              </Button>
            {/if}
            <Button variant="default" size="sm" class="text-xs" on:click={handleSave}>
              Save Server
            </Button>
          </div>
        </div>
      </div>
    </div>
  </DialogContent>
</Dialog>
