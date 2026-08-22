# 简错题 · AI 智能错题本（Android 原生版）

**v5.0 起为纯原生应用**：Kotlin + Jetpack Compose + CameraX，不再使用 WebView。

## 功能

- **深色模式**：全局支持，三态切换（自动跟随系统 / 浅色 / 深色），设置页一键切换
- **原生相机**：CameraX 取景，连续多张拍摄，拍完直接进入八点框选裁剪
- **相册导入**：系统相册多选（原生流畅）
- **八点透视裁剪**：4 角点 + 4 边中点，拖角透视矫正、拖边微调，多框并行
- **扫描增强**：提取时自动去阴影去底色、提亮纸张、加深字迹（可开关）
- **AI 解析**：可配置任意 OpenAI 兼容多模态接口（如 qwen-vl-max），设置页支持「获取模型列表」
- **错题库**：列表式管理，搜索、科目/掌握状态/高频筛选，详情、编辑、删除、标为掌握
- **智能组卷**：抽题条件（考点/学科/错误次数/时间范围/掌握状态）→ 随机/手动组卷 → 创建试卷 → 原生 PDF 生成（A4 排版 + 作答区）→ 打印/分享
- **学习统计**：多时间范围、科目分布、薄弱知识点
- **Supabase 云同步**：设置页填项目 URL + anon key 即开启，自动合并云端错题
- **数据管理**：JSON 导出备份、清空数据

## 技术栈

- UI：Jetpack Compose（Material3），深色/浅色双主题
- 相机：CameraX（camera-core / camera2 / lifecycle / view）
- 图片：Coil 加载，纯 Kotlin 实现透视单应性变换 + 扫描增强
- 存储：JSON 文件持久化（轻量免依赖）
- 网络：OkHttp（Supabase REST + AI 接口）
- PDF：android.graphics.pdf.PdfDocument 原生生成

## 构建

```bash
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

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
