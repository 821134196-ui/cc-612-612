import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

export default defineConfig({
  plugins: [svelte()],
  build: {
    // 直接构建进 Quarkus 静态资源目录，后端单进程即可对外服务
    outDir: '../backend/src/main/resources/META-INF/resources',
    emptyOutDir: true
  },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' }
  }
})
