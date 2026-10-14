import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the Vite server forwards API calls, so the browser only ever
// talks to one origin (no CORS setup needed).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/analytics': {
        target: 'http://localhost:8000',
        rewrite: (path) => path.replace(/^\/analytics/, ''),
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.js',
  },
});
