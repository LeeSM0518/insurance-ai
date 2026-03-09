import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq, req) => {
            const timestamp = new Date().toLocaleTimeString('ko-KR', { hour12: false });
            console.log(`\x1b[36m[${timestamp}] → ${req.method} ${req.url}\x1b[0m`);
            // request body 수집
            const chunks: Buffer[] = [];
            req.on('data', (chunk: Buffer) => chunks.push(chunk));
            req.on('end', () => {
              if (chunks.length > 0) {
                const body = Buffer.concat(chunks).toString('utf-8');
                console.log(`\x1b[90m  Request Body: ${body}\x1b[0m`);
              }
            });
          });
          proxy.on('proxyRes', (proxyRes, req) => {
            const timestamp = new Date().toLocaleTimeString('ko-KR', { hour12: false });
            const status = proxyRes.statusCode ?? 0;
            const color = status < 400 ? '\x1b[32m' : '\x1b[31m';
            // response body 수집
            const chunks: Buffer[] = [];
            proxyRes.on('data', (chunk: Buffer) => chunks.push(chunk));
            proxyRes.on('end', () => {
              console.log(`${color}[${timestamp}] ← ${req.method} ${req.url} ${status}\x1b[0m`);
              if (chunks.length > 0) {
                const body = Buffer.concat(chunks).toString('utf-8');
                console.log(`\x1b[90m  Response Body: ${body}\x1b[0m`);
              }
            });
          });
        },
      },
    },
  },
});
