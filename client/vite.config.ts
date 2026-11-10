import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // 휴대폰 확인용 Cloudflare Quick Tunnel 주소(실행마다 바뀐다)를 허용한다.
    allowedHosts: ['.trycloudflare.com'],
    // 개발 중에도 same-origin으로 쓰도록 API는 bootRun(8080), 미디어(WHIP/WHEP/HLS)와 녹화(/vod)는 로컬 Caddy(infra, 8000)로 넘긴다.
    proxy: {
      // 문자열 축약형은 Host를 8080으로 바꿔(changeOrigin) 서버 CORS가 브라우저 Origin(5173)을 교차 출처로 보고 403을 낸다.
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
      '/media': 'http://localhost:8000',
      '/vod': 'http://localhost:8000',
    },
  },
})
