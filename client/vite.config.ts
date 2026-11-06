import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // 개발 중에도 same-origin으로 쓰도록 API는 bootRun(8080), 미디어(WHIP/WHEP/HLS)는 로컬 Caddy(infra, 8000)로 넘긴다.
    proxy: {
      '/api': 'http://localhost:8080',
      '/media': 'http://localhost:8000',
    },
  },
})
