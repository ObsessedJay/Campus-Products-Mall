# 校集前端

校园文创限时发售系统的 Vue 3 前端。一个应用承载学生端、运营端和管理端，并通过角色路由区分工作区。

## 技术栈

- Vue 3、TypeScript、Vite
- Vue Router、Pinia、Axios
- Lucide 图标、Fontsource 展示字体
- Vitest

## 本地运行

```powershell
cd Frontend
npm install
npm run dev
```

默认地址为 `http://127.0.0.1:5173/`。Vite 将 `/api` 和 `/ws` 代理到本机 `http://localhost:8080`；如需修改后端地址，复制 `.env.example` 为 `.env.local` 并调整 `VITE_API_PROXY_TARGET`。

后端未启动时，公开商品和活动页面会显示明确标注的演示数据。登录、预约、支付、退款和核销等写操作不会在演示模式下伪装成功。

## 验证命令

```powershell
npm run typecheck
npm test
npm run build
```

生产构建输出到 `Frontend/dist/`。

## 目录说明

- `src/views/`：学生、运营和管理页面
- `src/components/`：共享导航、状态、商品和空状态组件
- `src/services/`：Axios 客户端和后端 API 封装
- `src/stores/`：登录状态与本地体验账号
- `src/router/`：路由和角色访问控制
- `src/data/`：后端不可用时的已标注演示数据
- `src/types/`：与 Spring Boot DTO 对齐的 TypeScript 类型
