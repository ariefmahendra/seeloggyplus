import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import path from 'path';

export default defineConfig({
  plugins: [svelte()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  build: {
    outDir: path.resolve(__dirname, '../src/main/resources/web'),
    emptyOutDir: true,
    target: 'esnext'
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8088'
    }
  }
});
