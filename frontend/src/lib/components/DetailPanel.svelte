<script lang="ts">
  import {
    selectedLogEntry,
    bottomPanelVisible,
    searchQuery,
    isRegex,
    caseSensitive
  } from '../stores/appState';
  import { toast } from '../stores/toast';
  import { escapeHtml } from '../utils';
  import { Button } from './ui/button';
  import { Badge } from './ui/badge';
  import { Separator } from './ui/separator';
  import { FileText, Copy, ChevronsDown, Code2, FileCode, Sparkles, AlignLeft, Trash2 } from 'lucide-svelte';
  import hljs from 'highlight.js/lib/core';
  import json from 'highlight.js/lib/languages/json';
  import xml from 'highlight.js/lib/languages/xml';
  import { jsonrepair } from 'jsonrepair';
  import xmlFormat from 'xml-formatter';

  hljs.registerLanguage('json', json);
  hljs.registerLanguage('xml', xml);

  let formatMode: 'auto' | 'json' | 'xml' | 'raw' = 'auto';

  function copyText() {
    if (!$selectedLogEntry) return;
    const text = $selectedLogEntry.rawLog || $selectedLogEntry.message || '';
    navigator.clipboard.writeText(text);
    toast.success('Log entry copied to clipboard');
  }

  function clearDetail() {
    selectedLogEntry.set(null);
  }

  function applySearchHighlight(html: string, query: string, isRgx: boolean, isCase: boolean): string {
    if (!query || !query.trim()) return html;
    try {
      const pat = isRgx ? query : query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      // Highlight matching text outside of HTML tags (<...>)
      const regex = new RegExp(`(?![^<]*>)(${pat})`, isCase ? 'g' : 'gi');
      return html.replace(regex, '<mark class="bg-amber-400 text-black px-0.5 rounded-2xs font-semibold">$1</mark>');
    } catch {
      return html;
    }
  }

  function faultTolerantXmlFormat(xmlStr: string): string {
    try {
      let formatted = '';
      let indent = 0;
      const tab = '  ';
      const cleanXml = xmlStr.replace(/>\s*</g, '><').trim();
      const regex = /(<[^>]+>|[^<]+)/g;
      const tokens = cleanXml.match(regex) || [];

      for (let i = 0; i < tokens.length; i++) {
        const token = tokens[i].trim();
        if (!token) continue;

        if (token.startsWith('<?') || token.startsWith('<!') || token.startsWith('<!--')) {
          formatted += tab.repeat(indent) + token + '\n';
        } else if (token.startsWith('</')) {
          indent = Math.max(0, indent - 1);
          formatted += tab.repeat(indent) + token + '\n';
        } else if (token.startsWith('<') && (token.endsWith('/>') || token.endsWith('/ >'))) {
          formatted += tab.repeat(indent) + token + '\n';
        } else if (token.startsWith('<')) {
          const tagName = token.replace(/^<([^\s>/]+).*/, '$1');
          if (i + 2 < tokens.length && !tokens[i + 1].startsWith('<') && tokens[i + 2].trim() === `</${tagName}>`) {
            formatted += tab.repeat(indent) + token + tokens[i + 1].trim() + tokens[i + 2].trim() + '\n';
            i += 2;
          } else {
            formatted += tab.repeat(indent) + token + '\n';
            indent++;
          }
        } else {
          formatted += tab.repeat(indent) + token + '\n';
        }
      }
      return formatted.trimEnd() || xmlStr;
    } catch {
      return xmlStr;
    }
  }

  function repairAndFormatXml(text: string): { success: boolean; formatted: string; pre: string; post: string } {
    let clean = text;
    if (clean.includes('&lt;') && clean.includes('&gt;')) {
      clean = clean.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&amp;/g, '&');
    }

    const firstTag = clean.indexOf('<');
    if (firstTag === -1) {
      return { success: false, formatted: text, pre: '', post: '' };
    }

    const lastTag = clean.lastIndexOf('>');
    if (lastTag === -1 || lastTag <= firstTag) {
      return { success: false, formatted: text, pre: '', post: '' };
    }

    const pre = clean.substring(0, firstTag);
    const candidate = clean.substring(firstTag, lastTag + 1);
    const post = clean.substring(lastTag + 1);

    // Tier 1: Try xmlFormat library
    try {
      const formatted = xmlFormat(candidate, {
        indentation: '  ',
        collapseContent: true,
        lineSeparator: '\n',
        throwOnFailure: true
      });
      return { success: true, formatted, pre, post };
    } catch {
      // Tier 2: Fault-tolerant tag-by-tag tokenizer
      try {
        const formatted = faultTolerantXmlFormat(candidate);
        return { success: true, formatted, pre, post };
      } catch {
        return { success: false, formatted: text, pre: '', post: '' };
      }
    }
  }

  function repairAndFormatJson(text: string): { success: boolean; formatted: string; pre: string; post: string } {
    const firstBrace = text.indexOf('{');
    const firstBracket = text.indexOf('[');
    let startIdx = -1;
    if (firstBrace !== -1 && firstBracket !== -1) {
      startIdx = Math.min(firstBrace, firstBracket);
    } else if (firstBrace !== -1) {
      startIdx = firstBrace;
    } else if (firstBracket !== -1) {
      startIdx = firstBracket;
    }

    if (startIdx !== -1) {
      const pre = text.substring(0, startIdx);
      const candidate = text.substring(startIdx);

      // Attempt jsonrepair on substring
      try {
        const repaired = jsonrepair(candidate);
        const parsed = JSON.parse(repaired);
        return { success: true, formatted: JSON.stringify(parsed, null, 2), pre, post: '' };
      } catch {
        // If trailing text after JSON exists, try finding last closing bracket/brace
        const lastBrace = candidate.lastIndexOf('}');
        const lastBracket = candidate.lastIndexOf(']');
        const endIdx = Math.max(lastBrace, lastBracket);
        if (endIdx !== -1 && endIdx > 0) {
          const subCandidate = candidate.substring(0, endIdx + 1);
          const post = candidate.substring(endIdx + 1);
          try {
            const repaired = jsonrepair(subCandidate);
            const parsed = JSON.parse(repaired);
            return { success: true, formatted: JSON.stringify(parsed, null, 2), pre, post };
          } catch {}
        }
      }
    }

    // Direct jsonrepair attempt on whole text
    try {
      const repaired = jsonrepair(text);
      const parsed = JSON.parse(repaired);
      return { success: true, formatted: JSON.stringify(parsed, null, 2), pre: '', post: '' };
    } catch {
      return { success: false, formatted: text, pre: '', post: '' };
    }
  }

  function getHighlightedContent(entry: any): { isHtml: boolean; content: string; detectedType?: string } {
    if (!entry) return { isHtml: false, content: '' };
    const text = entry.rawLog || entry.message || '';
    const q = $searchQuery ? $searchQuery.trim() : '';

    if (formatMode === 'raw') {
      const esc = escapeHtml(text);
      return { isHtml: true, content: q ? applySearchHighlight(esc, q, $isRegex, $caseSensitive) : esc };
    }

    // Mode: JSON (forced)
    if (formatMode === 'json') {
      const res = repairAndFormatJson(text);
      if (res.success) {
        let highlighted = hljs.highlight(res.formatted, { language: 'json' }).value;
        if (q) highlighted = applySearchHighlight(highlighted, q, $isRegex, $caseSensitive);
        const preEsc = escapeHtml(res.pre);
        const postEsc = escapeHtml(res.post);
        const preHighlighted = q ? applySearchHighlight(preEsc, q, $isRegex, $caseSensitive) : preEsc;
        const postHighlighted = q ? applySearchHighlight(postEsc, q, $isRegex, $caseSensitive) : postEsc;
        return {
          isHtml: true,
          content: (preHighlighted ? preHighlighted + '\n' : '') + highlighted + (postHighlighted ? '\n' + postHighlighted : ''),
          detectedType: 'JSON'
        };
      } else {
        const esc = escapeHtml(text);
        return { isHtml: true, content: q ? applySearchHighlight(esc, q, $isRegex, $caseSensitive) : esc };
      }
    }

    // Mode: XML (forced)
    if (formatMode === 'xml') {
      const res = repairAndFormatXml(text);
      if (res.success) {
        let highlighted = hljs.highlight(res.formatted, { language: 'xml' }).value;
        if (q) highlighted = applySearchHighlight(highlighted, q, $isRegex, $caseSensitive);
        const preEsc = escapeHtml(res.pre);
        const postEsc = escapeHtml(res.post);
        const preHighlighted = q ? applySearchHighlight(preEsc, q, $isRegex, $caseSensitive) : preEsc;
        const postHighlighted = q ? applySearchHighlight(postEsc, q, $isRegex, $caseSensitive) : postEsc;
        return {
          isHtml: true,
          content: (preHighlighted ? preHighlighted + '\n' : '') + highlighted + (postHighlighted ? '\n' + postHighlighted : ''),
          detectedType: 'XML'
        };
      } else {
        const esc = escapeHtml(text);
        return { isHtml: true, content: q ? applySearchHighlight(esc, q, $isRegex, $caseSensitive) : esc };
      }
    }

    // Mode: AUTO (Intelligently detects XML or JSON in the log line)
    // 1. Try XML first if '<' and '>' exist with tag structure
    const hasXmlHint = text.includes('<') && text.includes('>') && /<[a-zA-Z0-9_\-:]+[\s>]/.test(text);
    if (hasXmlHint) {
      const res = repairAndFormatXml(text);
      if (res.success) {
        let highlighted = hljs.highlight(res.formatted, { language: 'xml' }).value;
        if (q) highlighted = applySearchHighlight(highlighted, q, $isRegex, $caseSensitive);
        const preEsc = escapeHtml(res.pre);
        const postEsc = escapeHtml(res.post);
        const preHighlighted = q ? applySearchHighlight(preEsc, q, $isRegex, $caseSensitive) : preEsc;
        const postHighlighted = q ? applySearchHighlight(postEsc, q, $isRegex, $caseSensitive) : postEsc;
        return {
          isHtml: true,
          content: (preHighlighted ? preHighlighted + '\n' : '') + highlighted + (postHighlighted ? '\n' + postHighlighted : ''),
          detectedType: 'XML'
        };
      }
    }

    // 2. Try JSON if '{' or '[' exists with key/array structure
    const hasJsonHint = (text.includes('{') || text.includes('[')) && /[{\s]*["'\w]+[\s]*:/.test(text);
    if (hasJsonHint || text.includes('{')) {
      const res = repairAndFormatJson(text);
      if (res.success) {
        let highlighted = hljs.highlight(res.formatted, { language: 'json' }).value;
        if (q) highlighted = applySearchHighlight(highlighted, q, $isRegex, $caseSensitive);
        const preEsc = escapeHtml(res.pre);
        const postEsc = escapeHtml(res.post);
        const preHighlighted = q ? applySearchHighlight(preEsc, q, $isRegex, $caseSensitive) : preEsc;
        const postHighlighted = q ? applySearchHighlight(postEsc, q, $isRegex, $caseSensitive) : postEsc;
        return {
          isHtml: true,
          content: (preHighlighted ? preHighlighted + '\n' : '') + highlighted + (postHighlighted ? '\n' + postHighlighted : ''),
          detectedType: 'JSON'
        };
      }
    }

    // Fallback: Raw text with highlight
    const esc = escapeHtml(text);
    return {
      isHtml: true,
      content: q ? applySearchHighlight(esc, q, $isRegex, $caseSensitive) : esc
    };
  }

  function getLevelVariant(level: string): 'default' | 'destructive' | 'secondary' | 'outline' {
    switch (level?.toUpperCase()) {
      case 'ERROR':
      case 'FATAL':
        return 'destructive';
      case 'WARN':
      case 'WARNING':
        return 'default';
      case 'INFO':
        return 'secondary';
      default:
        return 'outline';
    }
  }
</script>

<section class="h-full w-full bg-background flex flex-col select-none overflow-hidden">
  <!-- Header -->
  <div class="h-8 border-b border-border flex items-center justify-between px-3 bg-muted/30 shrink-0">
    <div class="flex items-center gap-2 overflow-hidden">
      <FileText class="w-3.5 h-3.5 text-muted-foreground shrink-0" />
      <span class="font-semibold text-[11px] text-foreground tracking-wide shrink-0">Log Entry Inspector</span>
      {#if $selectedLogEntry}
        <Badge variant={getLevelVariant($selectedLogEntry.level)} class="px-1.5 py-0 text-[10px] font-mono font-bold shrink-0">
          {$selectedLogEntry.level || 'LOG'}
        </Badge>
        <span class="text-[10px] font-mono text-muted-foreground shrink-0">Line: {$selectedLogEntry.lineNumber}</span>
        {#if $selectedLogEntry.timestamp}
          <span class="text-[10px] font-mono text-muted-foreground truncate">| {$selectedLogEntry.timestamp}</span>
        {/if}
        {#if $selectedLogEntry.thread}
          <span class="text-[10px] font-mono text-muted-foreground truncate">| [{$selectedLogEntry.thread}]</span>
        {/if}
        {#if $selectedLogEntry.logger}
          <span class="text-[10px] font-mono text-muted-foreground truncate">| {$selectedLogEntry.logger}</span>
        {/if}
      {/if}
    </div>

    <!-- Actions & Format Mode Selector -->
    <div class="flex items-center gap-1 shrink-0">
      <!-- Mode Toggle Group -->
      <div class="flex items-center bg-muted/70 rounded-[var(--radius-sm)] p-0.5 border border-border/50 text-[10px]">
        <button
          type="button"
          class="px-2 py-0.5 rounded-[var(--radius-xs)] font-medium transition-all flex items-center gap-1 {formatMode === 'auto' ? 'bg-primary text-primary-foreground shadow-xs font-semibold' : 'text-muted-foreground hover:text-foreground'}"
          on:click={() => formatMode = 'auto'}
          title="Auto-detect & prettify JSON or XML structures automatically"
        >
          <Sparkles class="w-3 h-3" />
          <span>Auto</span>
        </button>

        <button
          type="button"
          class="px-2 py-0.5 rounded-[var(--radius-xs)] font-medium transition-all flex items-center gap-1 {formatMode === 'json' ? 'bg-amber-600 text-white shadow-xs font-semibold' : 'text-muted-foreground hover:text-foreground'}"
          on:click={() => formatMode = 'json'}
          title="Force repair & prettify JSON structure"
        >
          <Code2 class="w-3 h-3" />
          <span>JSON</span>
        </button>

        <button
          type="button"
          class="px-2 py-0.5 rounded-[var(--radius-xs)] font-medium transition-all flex items-center gap-1 {formatMode === 'xml' ? 'bg-sky-600 text-white shadow-xs font-semibold' : 'text-muted-foreground hover:text-foreground'}"
          on:click={() => formatMode = 'xml'}
          title="Force prettify XML structure"
        >
          <FileCode class="w-3 h-3" />
          <span>XML</span>
        </button>

        <button
          type="button"
          class="px-2 py-0.5 rounded-[var(--radius-xs)] font-medium transition-all flex items-center gap-1 {formatMode === 'raw' ? 'bg-background text-foreground shadow-xs font-semibold border border-border' : 'text-muted-foreground hover:text-foreground'}"
          on:click={() => formatMode = 'raw'}
          title="Show raw original log text without formatting"
        >
          <AlignLeft class="w-3 h-3" />
          <span>Raw</span>
        </button>
      </div>

      <Separator orientation="vertical" class="h-4 mx-0.5" />

      <Button
        variant="outline"
        size="xs"
        class="gap-1 text-[10.5px]"
        on:click={copyText}
        disabled={!$selectedLogEntry}
        title="Copy complete log line to clipboard"
      >
        <Copy class="w-3 h-3" />
        <span>Copy</span>
      </Button>

      <Button
        variant="ghost"
        size="icon"
        class="h-6 w-6 text-muted-foreground hover:text-foreground"
        on:click={clearDetail}
        disabled={!$selectedLogEntry}
        title="Clear Inspector"
      >
        <Trash2 class="w-3 h-3" />
      </Button>

      <Button
        variant="ghost"
        size="icon"
        class="h-6 w-6 text-muted-foreground hover:text-foreground"
        on:click={() => bottomPanelVisible.set(false)}
        title="Hide Inspector Panel (Ctrl+J)"
      >
        <ChevronsDown class="w-3.5 h-3.5" />
      </Button>
    </div>
  </div>

  <!-- Content Box -->
  <div class="flex-1 overflow-auto p-3 bg-background font-mono text-[11.5px] leading-relaxed text-foreground select-text whitespace-pre-wrap break-all">
    {#if $selectedLogEntry}
      {@const res = getHighlightedContent($selectedLogEntry)}
      {#if res.isHtml}
        {@html res.content}
      {:else}
        {res.content}
      {/if}
    {:else}
      <div class="h-full flex items-center justify-center text-muted-foreground text-xs select-none">
        Click any row in the log table above to inspect its complete stack trace and details (Double-click jumps to original line)
      </div>
    {/if}
  </div>
</section>


