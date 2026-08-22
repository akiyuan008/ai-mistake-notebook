# 简错题 · AI 智能错题本（Android 版）

天空蓝玻璃拟态风格的 AI 智能错题本。WebView 壳 + 单文件网页应用，体积小（约 2MB），无需登录、无付费功能。

## 功能

- **错题提取**：图片 / PDF 导入，支持拖拽、粘贴；**连拍模式**打开相机连续拍摄
- **多框选裁剪**：在试卷预览图上连续拖拽多个选区，一次批量提取；选区可移动 / 拉伸 / 单独删除
- **页面组织**：旋转、删除、调整页面顺序
- **AI 解析队列**：逐个解析入错题库；可配置任意 OpenAI 兼容多模态接口（如 qwen-vl-max）实现真实识题，未配置为演示模式
- **错题库**：搜索、科目 / 掌握状态 / 高频筛选，详情、编辑、删除、标为掌握、错误次数累计，LaTeX 公式渲染
- **智能组卷**：按科目 / 知识点 / 高频筛选选题 → A4 试卷预览 → 附答案开关 → 打印 / 导出 PDF → 存为试卷
- **学习统计**：多时间范围、科目分布、薄弱知识点、7 天趋势
- **Supabase 云同步**：设置中填入项目 URL + anon key 即开启，本地 + 云端双份保存
- **数据管理**：JSON 导出备份 / 导入恢复

## 构建

```bash
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

## GitHub Actions

每次 push 到 main 自动构建 APK 并上传 artifact；打 tag（v*）时自动附加到 Release。

## 技术栈

- Android：Kotlin + WebView（AGP 9 / compileSdk 36 / minSdk 24）
- 网页应用：原生 HTML / CSS / JS 单文件（位于 `app/src/main/assets/index.html`），pdf.js + KaTeX（CDN），IndexedDB 本地存储
- 后端（可选）：Supabase（mistakes / papers 两张表，设置页配置连接）

## Supabase 表结构

```sql
create table mistakes (
  id text primary key,
  subject text, knowledge text, topic text,
  question text, answer text, analysis text,
  image text, error_count int default 1,
  mastered bool default false, parsed_by text,
  created_at timestamptz, updated_at timestamptz
);
create table papers (
  id text primary key,
  type text, name text, subjects text,
  count int, questions jsonb,
  created_at timestamptz
);
-- 无登录场景：关闭 RLS 或为 anon 配置宽松策略
```
