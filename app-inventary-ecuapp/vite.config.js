import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// La SPA se publica bajo /inventary/ (http://localhost:3000/inventary). VITE_BASE_PATH permite otro prefijo.
export default defineConfig(({ mode }) => {
  const env = { ...loadEnv(mode, process.cwd(), 'VITE_'), ...process.env };
  const base = ('/' + (env.VITE_BASE_PATH || '/inventary/') + '/').replace(/\/{2,}/g, '/');
  return { base, plugins: [react()], server: { host: '0.0.0.0' } };
});
