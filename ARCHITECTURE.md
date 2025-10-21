# AI 代码补全插件 - 架构设计文档

## 目录

- [系统架构](#系统架构)
- [核心组件](#核心组件)
- [数据流](#数据流)
- [缓存机制](#缓存机制)
- [线程模型](#线程模型)
- [扩展点](#扩展点)

---

## 系统架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────┐
│                    PyCharm IDE                          │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────────┐    ┌──────────────┐   ┌───────────┐  │
│  │ Toggle Action│    │ Edit Action  │   │  Settings │  │
│  │ (Shift+Alt+A)│    │(Shift+Alt+1/3│   │Configurable│ │
│  └──────┬───────┘    └──────┬───────┘   └─────┬─────┘  │
│         │                   │                  │        │
│         v                   v                  v        │
│  ┌──────────────────────────────────────────────────┐   │
│  │         LLMPluginComponent (初始化)              │   │
│  └──────────────────────────────────────────────────┘   │
│         │                                               │
│         ├──> LLMTypedActionHandler (输入监听)           │
│         ├──> TabAcceptHandler (Tab 键处理)              │
│         └──> LLMInlineCompletionManager (补全管理)      │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │              LLMClient (API 调用)                 │   │
│  │   ┌──────────────┐      ┌───────────────┐        │   │
│  │   │ HTTP Client  │      │ Cache System  │        │   │
│  │   │  (OkHttp)    │      │ (LRU + TTL)   │        │   │
│  │   └──────────────┘      └───────────────┘        │   │
│  └──────────────────────────────────────────────────┘   │
│                          │                              │
└──────────────────────────┼──────────────────────────────┘
                           v
                 ┌──────────────────┐
                 │   LLM API 服务   │
                 │ (OpenAI/Azure)   │
                 └──────────────────┘
```

---

## 核心组件

### 1. LLMPluginComponent (插件组件)
**职责**: 插件初始化和组件注册

**主要功能**:
- 注册 `TypedActionHandler` 监听用户输入
- 注册 `TabAcceptHandler` 处理 Tab 键
- 初始化补全管理器

**生命周期**:
```java
projectOpened() → 注册处理器 → 初始化管理器
projectClosed() → 清理资源
```

---

### 2. LLMSettings (设置管理)
**职责**: 持久化存储插件配置

**存储字段**:
| 字段 | 类型 | 默认值 | 说明 |
|-----|------|--------|------|
| apiUrl | String | https://api.openai.com/v1/chat/completions | API 地址 |
| apiKey | String | "" | API 密钥 |
| model | String | gpt-4o-mini | 模型名称 |
| triggerDelayMs | int | 500 | 触发延迟(ms) |
| maxSuggestionLength | int | 150 | 最大建议长度 |

**持久化机制**:
- 使用 IntelliJ Platform 的 `PersistentStateComponent`
- 数据存储在 IDE 配置目录

---

### 3. LLMClient (API 客户端)
**职责**: 调用大模型 API 并管理缓存

#### 3.1 HTTP 客户端配置
```java
OkHttpClient:
  - 代理: 127.0.0.1:7897 (可配置)
  - 连接超时: 5 秒
  - 读取超时: 10 秒
  - 写入超时: 10 秒
  - 连接池: 最大 5 个连接，保活 5 分钟
```

#### 3.2 缓存系统
详见 [缓存机制](#缓存机制) 章节

#### 3.3 请求管理
- **单例模式**: 全局共享一个 `OkHttpClient` 实例
- **请求取消**: 自动取消过期的请求，避免资源浪费
- **错误处理**: 网络错误、超时、API 错误的统一处理

---

### 4. LLMInlineCompletionManager (补全管理器)
**职责**: 管理内联补全的显示和生命周期

**核心方法**:
```java
// 显示补全建议
showInlineSuggestion(Editor editor, String suggestion, int offset)

// 移除补全建议
removeInlineSuggestion(Editor editor)

// 接受补全建议
acceptCurrentSuggestion(Editor editor)
```

**渲染机制**:
- 使用 `InlayModel` 在光标位置插入灰色文本
- 使用 `SimpleInlayRenderer` 自定义渲染样式
- 自动管理 Inlay 的生命周期

---

### 5. LLMTypedActionHandler (输入处理器)
**职责**: 监听用户输入，触发代码补全

**工作流程**:
```
用户输入 → 延迟 500ms → 检查是否启用 → 获取上下文 
→ 调用 LLMClient → 清理结果 → 显示补全
```

**防抖机制**:
- 使用 `ScheduledExecutorService` 实现延迟触发
- 自动取消未完成的旧请求

---

### 6. TabAcceptHandler (Tab 键处理器)
**职责**: 处理 Tab 键接受补全

**逻辑**:
1. 检查是否有活动的补全建议
2. 如果有，接受建议并消费 Tab 键事件
3. 如果没有，传递给默认 Tab 处理器

---

### 7. EditSelectionAction (代码编辑动作)
**职责**: 处理代码分析、优化和注释功能

#### 7.1 功能区分
通过 Action ID 区分不同功能:
- `EditSelectionWithAI` (Shift+Alt+1): 代码优化
- `CommentSelectionWithAI` (Shift+Alt+3): 添加注释

#### 7.2 Diff 显示
- 使用 `java-diff-utils` 生成差异
- 模态对话框显示，锁定编辑器
- 高亮显示新增/删除行

#### 7.3 应用修改
- `ApplyEditAction` (Shift+Alt+2): 应用修改
- 使用 `WriteCommandAction` 确保原子操作
- 自动恢复编辑器可编辑状态

---

### 8. EditorContextUtils (上下文工具)
**职责**: 提供编辑器上下文信息

**核心方法**:
```java
// 获取完整文件内容
getFullFileText(PsiFile file)

// 获取选中文本
getSelectedText(Editor editor)

// 构建优化提示词
buildContextPrompt(PsiFile file, String selectedText)

// 构建注释提示词
buildContextPromptForComment(PsiFile file, String selectedText)
```

---

## 数据流

### 代码补全流程

```
┌─────────────┐
│  用户输入    │
└──────┬──────┘
       │
       v
┌─────────────────────┐
│ TypedActionHandler  │
│  (延迟 500ms)       │
└──────┬──────────────┘
       │
       v
┌─────────────────────┐
│ 检查 LLMState       │
│ (是否启用补全)      │
└──────┬──────────────┘
       │ 是
       v
┌─────────────────────┐
│ 获取编辑器上下文    │
│ (光标位置、文件内容)│
└──────┬──────────────┘
       │
       v
┌─────────────────────┐
│   LLMClient         │
│ 1. 检查缓存         │
│ 2. 调用 API         │
└──────┬──────────────┘
       │
       v
┌─────────────────────┐
│ 清理返回内容        │
│ (去除 Markdown 等)  │
└──────┬──────────────┘
       │
       v
┌─────────────────────┐
│ InlineCompletion    │
│ Manager 显示建议    │
└─────────────────────┘
```

### 代码分析流程

```
┌─────────────┐
│ 选中代码     │
│ Shift+Alt+1  │
└──────┬──────┘
       │
       v
┌──────────────────────┐
│ EditSelectionAction  │
│ 1. 验证选区          │
│ 2. 保存位置信息      │
└──────┬───────────────┘
       │
       v
┌──────────────────────┐
│ 后台线程             │
│ 1. 构建 Prompt       │
│ 2. 调用 LLMClient    │
└──────┬───────────────┘
       │
       v
┌──────────────────────┐
│ 清理 AI 返回         │
└──────┬───────────────┘
       │
       v
┌──────────────────────┐
│ UI 线程              │
│ 1. 显示 Diff 对话框  │
│ 2. 锁定编辑器        │
└──────┬───────────────┘
       │
       ├─> 取消 → 恢复编辑器
       │
       v
  应用修改 (Shift+Alt+2)
       │
       v
┌──────────────────────┐
│ WriteCommandAction   │
│ 替换文本             │
└──────────────────────┘
```

---

## 缓存机制

### 设计目标
- 减少重复的 API 调用
- 提高响应速度
- 降低成本

### 缓存架构

```
┌─────────────────────────────────────────┐
│          Cache Entry                    │
│  ┌───────────────────────────────────┐  │
│  │ result: String                    │  │
│  │ timestamp: long                   │  │
│  │ isExpired(): boolean (60s TTL)    │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘

┌──────────────────────┐  ┌──────────────────────┐
│   主缓存 (cache)     │  │ 会话缓存(sessionCache)│
│   容量: 100          │  │   容量: 50            │
│   策略: LRU          │  │   策略: LRU           │
└──────────────────────┘  └──────────────────────┘
```

### 缓存键生成

```java
generateContextKey(String context):
  1. 预处理: 去除注释和多余空格
  2. 提取特征: class、scope、func、assign
  3. 取最后 300 字符
  4. 计算哈希值
  5. 返回: "features_hash"
```

### 缓存查询策略

```
查询请求
    ↓
检查主缓存
    ↓ 未命中
检查会话缓存
    ↓ 命中
升级到主缓存 ← 返回结果
    ↓ 未命中
调用 API
    ↓
同时写入两级缓存
```

### 缓存特性
- **TTL**: 60 秒过期
- **LRU**: 自动淘汰最少使用的项
- **两级缓存**: 主缓存 + 会话缓存
- **容量限制**: 主缓存 100 条，会话缓存 50 条

---

## 线程模型

### 线程使用场景

| 组件 | 线程类型 | 说明 |
|-----|---------|------|
| LLMTypedActionHandler | ScheduledExecutorService | 延迟触发补全 |
| EditSelectionAction | new Thread() | 后台调用 API |
| LLMClient | OkHttp 线程池 | HTTP 请求 |
| UI 更新 | EDT (UI 线程) | 显示 Inlay、对话框等 |

### 线程安全

#### 1. 共享状态保护
```java
// LLMClient 中的请求取消
private static volatile Call currentCall = null;

// 原子操作
public static void cancelCurrentRequest() {
    Call call = currentCall;
    if (call != null && !call.isCanceled()) {
        call.cancel();
    }
    currentCall = null;
}
```

#### 2. UI 线程调度
```java
// 确保 UI 操作在 EDT 线程
ApplicationManager.getApplication().invokeLater(() -> {
    showDiffDialog(project, original, modified);
});
```

#### 3. 写操作保护
```java
// 使用 WriteCommandAction 保证原子性
WriteCommandAction.runWriteCommandAction(project, () -> {
    document.replaceString(start, end, newText);
});
```

---

## 扩展点

### 1. 自定义 API 服务
修改 `LLMClient.java`:
```java
// 修改 API URL 和请求体格式
String apiUrl = settings.apiUrl; // 可配置

JSONObject json = new JSONObject();
json.put("model", model);
// 根据 API 格式调整请求体
```

### 2. 自定义代理
修改 `createHttpClient()`:
```java
Proxy proxy = new Proxy(
    Proxy.Type.HTTP, 
    new InetSocketAddress("your-proxy", port)
);
```

或注释掉代理:
```java
return new OkHttpClient.Builder()
    // .proxy(proxy)  // 注释掉
    .connectTimeout(5, TimeUnit.SECONDS)
    // ...
```

### 3. 添加新的代码操作
参考 `EditSelectionAction`:

```java
<action id="YourCustomAction"
        class="com.system.demo.LLM.EditSelectionAction"
        text="Your Custom Action"
        description="Your description">
    <keyboard-shortcut first-keystroke="shift alt 4" keymap="$default"/>
</action>
```

在 `EditorContextUtils` 中添加新的 Prompt:
```java
public static String buildCustomPrompt(PsiFile file, String selectedText) {
    return "Your custom prompt: " + selectedText;
}
```

### 4. 自定义缓存策略
修改 `LLMClient.java`:
```java
// 修改缓存容量
private static final int MAX_CACHE_SIZE = 200;

// 修改 TTL
private static final long CACHE_TTL_MS = 120000; // 2 分钟

// 自定义键生成逻辑
private static String generateContextKey(String context) {
    // 自定义逻辑
}
```

---

## 配置管理

### 配置文件位置
```
~/.config/JetBrains/PyCharmCE2019.3/options/
└── other.xml  (包含 LLMSettings)
```

### 配置迁移
升级插件时，配置会自动保留（通过 `PersistentStateComponent`）

---

## 最佳实践

### 1. 性能优化
- ✅ 使用缓存减少 API 调用
- ✅ 后台线程处理耗时操作
- ✅ 自动取消过期请求
- ✅ 连接池复用 HTTP 连接

### 2. 用户体验
- ✅ 模态对话框防止误操作
- ✅ 进度提示（"正在分析..."）
- ✅ 灰色文本区分补全建议
- ✅ 快捷键快速操作

### 3. 错误处理
- ✅ 网络错误静默处理
- ✅ API 错误提示用户
- ✅ 编辑器状态恢复机制

### 4. 安全性
- ✅ API Key 加密存储
- ✅ HTTPS 连接
- ✅ 输入验证
- ✅ 原子操作保证数据一致性

---

## 已知限制与改进方向

### 当前限制
1. 仅支持 PyCharm 2019.3.5
2. 代理地址硬编码
3. 缓存不支持持久化
4. 不支持多光标补全

### 改进方向
1. **多 IDE 支持**: 适配更多 JetBrains IDE
2. **可配置代理**: UI 配置代理设置
3. **持久化缓存**: 缓存保存到磁盘
4. **增量补全**: 支持逐字显示
5. **多光标**: 同时为多个光标提供补全
6. **统计功能**: API 调用次数、成本统计

---

## 开发调试

### 本地运行
```bash
./gradlew runIde
```

### 日志查看
```
Help → Show Log in Finder/Explorer
```

### 调试技巧
1. 在 `build.gradle.kts` 中设置 `localPath`
2. 使用 `System.out.println()` 输出到 IDE 日志
3. 使用断点调试（Run → Debug Plugin）

---

## 许可证

本项目仅供学习和个人使用。

---

**最后更新**: 2025-10-21  
**版本**: 1.0-SNAPSHOT
