import '@fontsource/barlow-condensed/latin-700.css'
import '@fontsource/barlow-condensed/latin-900.css'
import '@fontsource/zcool-qingke-huangyou/chinese-simplified-400.css'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles.css'

// 在应用启动前注册字体、Pinia 和路由，随后挂载根组件。
createApp(App).use(createPinia()).use(router).mount('#app')
