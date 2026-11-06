import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // 개발 중에도 same-origin으로 쓰도록 API는 bootRun(8080)으로 넘긴다.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
