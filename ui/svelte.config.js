import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

export default {
  preprocess: vitePreprocess(),
  // 移动端内部工具，a11y 提示不阻断构建
  onwarn: (warning, handler) => {
    if (warning.code && warning.code.startsWith('a11y')) return;
    handler(warning);
  }
};
