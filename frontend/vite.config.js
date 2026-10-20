import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const backendUrl = env.BACKEND_URL || 'http://localhost:8080';

  return {
    plugins: [react()],
    base: '/ui/',
    build: {
      outDir: '../src/main/resources/static/ui',
      emptyOutDir: true
    },
    server: {
      port: Number(env.FRONTEND_PORT) || 5173,
      proxy: {
        '/api': backendUrl,
        '/ws': { target: backendUrl.replace(/^http/, 'ws'), ws: true }
      }
    }
  };
});
