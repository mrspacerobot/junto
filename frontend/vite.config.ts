import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'path';

const backendUrl = `http://localhost:${process.env.E2E_BACKEND_PORT ?? 8080}`;

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: backendUrl,
        changeOrigin: true,
      },
      '/v3': {
        target: backendUrl,
        changeOrigin: true,
      },
      '/ws': {
        target: backendUrl,
        changeOrigin: true,
        ws: true,
      },
    },
  },
});
