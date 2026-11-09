import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: process.env.HOST || '127.0.0.1',
    port: Number(process.env.PORT || 3000),
    strictPort: true,
    open: process.env.BROWSER !== 'none' && !process.env.CI,
  },
  build: {
    // 기존 Gradle/JAR 정적 리소스 경로와 브라우저 지원 범위를 유지한다.
    outDir: 'build',
    assetsDir: 'static',
    target: 'es2015',
    sourcemap: true,
    rolldownOptions: {
      output: {
        entryFileNames: 'static/js/[name]-[hash].js',
        chunkFileNames: 'static/js/[name]-[hash].js',
        assetFileNames: 'static/[name]-[hash][extname]',
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/setupTests.js'],
    include: ['src/**/*.{test,spec}.{js,jsx}'],
    environmentOptions: { jsdom: { url: 'http://localhost/' } },
  },
});
