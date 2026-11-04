import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// In development the page is served by Vite and /api goes to the Spring backend (same origin from the browser's
// point of view). Use GAJAMITRA_SECURE_COOKIES=false for that backend, because http://localhost is not https.
export default defineConfig({
  plugins: [react()],
  server: { proxy: { '/api': 'http://127.0.0.1:8080' } },
  test: { environment: 'jsdom', setupFiles: ['./src/test/setup.ts'] },
})
