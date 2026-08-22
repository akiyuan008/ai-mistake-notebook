# 简错题 · AI 智能错题本（Android 版）

天空蓝玻璃拟态风格的 AI 智能错题本。WebView 壳 + 单文件网页应用，体积小（约 2MB），无需登录、无付费功能。

## 功能

- **内置相机**：APP 内直接取景拍摄（非系统相机），支持手电筒、连续多张拍摄，拍完统一进入裁剪
- **内置相册**：APP 内相册网格（读取本机媒体库），也支持从系统多选导入、PDF / 粘贴导入
- **八点透视裁剪**：每个框 4 角点 + 4 边中点，拖角做透视矫正、拖边微调；支持同时多个框
- **拼接**：跨区域的同一道题可标记为拼接组，提取时自动竖直拼成一张
- **扫描增强**：提取时自动去阴影去底色、提亮纸张、加深字迹（扫描仪效果），可开关
- **AI 解析队列**：逐个解析入错题库；可配置任意 OpenAI 兼容多模态接口（如 qwen-vl-max）真实识题
- **错题库**：列表式管理，搜索、科目 / 掌握状态 / 高频筛选，详情、编辑、删除、标为掌握、错误次数累计，LaTeX 渲染
- **组卷**：抽题条件设置（考点 / 学科 / 错误次数 / 时间范围 / 掌握状态）→ 随机组卷 / 手动组卷 → 确认题目 → 创建试卷 → 预览（姓名 / 班级 / 日期 + 作答区）→ 打印 / PDF
- **学习统计**：多时间范围、科目分布、薄弱知识点、7 天趋势
- **Supabase 云同步**：设置中填入项目 URL + anon key 即开启
- **数据管理**：JSON 导出备份 / 导入恢复 / 清空

## 构建

```bash
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

## GitHub Actions

每次 push 到 main 自动构建 APK 并上传 artifact；打 tag（v*）时自动附加到 Release。

## 技术栈

- Android：Kotlin + WebView（AGP 9 / compileSdk 36 / minSdk 24）
  - `AlbumBridge`：JS 桥接，读取系统媒体库（内置相册）
  - `onPermissionRequest`：WebView 内置相机（getUserMedia）权限
- 网页应用：原生 HTML / CSS / JS 单文件（`app/src/main/assets/index.html`）
  - pdf.js + KaTeX（CDN），IndexedDB 本地存储
  - 透视裁剪：纯 JS 单应性变换（8 参数线性方程组 + 双线性采样）
- 后端（可选）：Supabase（mistakes / papers 两张表）

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
```
