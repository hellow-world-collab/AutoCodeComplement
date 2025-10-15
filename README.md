# PyCharm LLM 代码助手插件

一个集成大语言模型（LLM）的 PyCharm 插件，提供智能代码补全和代码优化功能。

## ✨ 功能特性

### 1. **智能代码补全**
- 使用 LLM 提供基于完整文件上下文的代码补全建议
- 可通过快捷键随时启用/禁用
- 异步执行，不阻塞编辑器

### 2. **代码智能优化**
- 选中代码片段，使用 LLM 进行优化改进
- 显示差异预览，确认后再应用修改
- 基于完整文件上下文进行优化

## 🚀 快速开始

### 配置 LLM API

在使用插件前，需要先配置您的 LLM API：

1. 打开 `src/main/java/com/system/demo/LLM/LLMClient.java`
2. 修改以下配置参数：

```java
// API 端点 URL
private static final String API_URL = "https://api.openai.com/v1/chat/completions";

// API 密钥（必须修改）
private static final String API_KEY = "YOUR_API_KEY";

// 使用的模型
private static final String MODEL = "gpt-4o-mini";
```

#### 支持的 LLM 服务

- **OpenAI**: 使用默认配置，只需设置 API_KEY
- **Azure OpenAI**: 修改 API_URL 为您的 Azure 端点
- **自定义 LLM**: 只要兼容 OpenAI 的 API 格式即可

### 构建插件

```bash
# 给 gradlew 添加执行权限
chmod +x gradlew

# 构建插件
./gradlew buildPlugin
```

构建成功后，插件文件位于：`build/distributions/demo-1.0-SNAPSHOT.zip`

### 安装插件

1. 打开 PyCharm
2. 进入 `Settings/Preferences` → `Plugins`
3. 点击齿轮图标 ⚙️ → `Install Plugin from Disk...`
4. 选择构建好的 ZIP 文件
5. 重启 PyCharm

## 📖 使用说明

### 功能 1：智能代码补全

#### 启用/禁用 LLM 补全

使用以下任一方式：

- **快捷键**: `Ctrl + Alt + Shift + L` (Windows/Linux) 或 `Cmd + Alt + Shift + L` (macOS)
- **菜单**: `Tools` → `启用/禁用 LLM 代码补全`

启用后，当您输入代码时：
1. 插件会自动将整个文件内容和光标位置发送给 LLM
2. LLM 返回补全建议
3. 补全建议会出现在代码补全列表中，带有 💡 图标和 "LLM" 标记

> **提示**: 
> - LLM 补全默认是**禁用**的，需要手动启用
> - 状态会持久化保存，重启 PyCharm 后仍然保持

### 功能 2：代码智能优化

#### 使用步骤

1. **选中代码**: 在编辑器中选择要优化的代码片段
2. **触发优化**: 使用以下任一方式：
   - **快捷键**: `Ctrl + Alt + E` (Windows/Linux) 或 `Cmd + Alt + E` (macOS)
   - **右键菜单**: 右键点击选中的代码 → `使用 LLM 改进选中代码`
3. **查看预览**: 弹出差异对比窗口，显示原始代码和优化后的代码
4. **确认应用**: 
   - 点击 **"应用修改"** 按钮替换代码
   - 点击 **"取消"** 放弃修改

#### 优化效果

LLM 会基于以下原则优化代码：
- ✅ 保持原有功能和语义
- ✅ 优化代码结构和可读性
- ✅ 遵循 Python PEP 8 规范
- ✅ 修复明显的 bug 和性能问题
- ✅ 考虑完整文件的上下文

## ⚙️ 配置说明

### LLMClient.java 配置参数

```java
// API 相关
private static final String API_URL = "...";        // API 端点
private static final String API_KEY = "...";        // API 密钥
private static final String MODEL = "gpt-4o-mini";  // 模型名称

// 生成参数
private static final double TEMPERATURE = 0.3;      // 温度 (0.0-2.0)
private static final int MAX_TOKENS = 2000;         // 最大 token 数
private static final int TIMEOUT_SECONDS = 30;      // 超时时间（秒）
```

#### 参数说明

- **TEMPERATURE**: 控制输出的随机性
  - `0.0-0.3`: 更确定、一致的输出（推荐用于代码）
  - `0.7-1.0`: 更有创造性的输出
  
- **MAX_TOKENS**: 限制生成的最大长度
  - 代码补全建议：500-1000
  - 代码优化：1000-2000

- **TIMEOUT_SECONDS**: API 请求超时时间
  - 建议：20-60 秒

## 🎯 最佳实践

### 代码补全

1. **在关键位置使用**: 在函数定义、复杂逻辑等位置使用效果更好
2. **提供足够上下文**: 确保文件中有足够的上下文信息
3. **按需启用**: 不需要时关闭以避免频繁调用 API

### 代码优化

1. **选择合适的代码块**: 
   - ✅ 单个函数或方法
   - ✅ 一段逻辑完整的代码
   - ❌ 避免选择过大或不完整的代码块

2. **检查预览**: 始终检查差异预览，确保修改符合预期

3. **迭代优化**: 可以多次使用优化功能，逐步改进代码

## 🔧 开发调试

### 运行插件（开发模式）

```bash
./gradlew runIde
```

这会启动一个带有插件的 PyCharm 实例，用于测试。

### 查看日志

插件的错误信息会输出到：
- 控制台（开发模式）
- PyCharm 的日志文件: `Help` → `Show Log in Explorer/Finder`

## 📝 快捷键总览

| 功能 | Windows/Linux | macOS |
|------|--------------|-------|
| 启用/禁用 LLM 补全 | `Ctrl + Alt + Shift + L` | `Cmd + Alt + Shift + L` |
| 优化选中代码 | `Ctrl + Alt + E` | `Cmd + Alt + E` |

## 🛠️ 技术栈

- **开发语言**: Java
- **构建工具**: Gradle
- **IDE 平台**: IntelliJ Platform SDK
- **HTTP 客户端**: OkHttp
- **JSON 处理**: org.json

## 📄 项目结构

```
src/main/java/com/system/demo/LLM/
├── LLMClient.java                    # LLM API 客户端
├── LLMCompletionSettings.java        # 状态管理
├── LLMCompletionContributor.java     # 代码补全实现
├── ToggleLLMCompletionAction.java    # 切换开关 Action
├── EditSelectionAction.java          # 代码优化 Action
└── DiffPreviewDialog.java            # 差异预览对话框

src/main/resources/META-INF/
└── plugin.xml                         # 插件配置
```

## ❓ 常见问题

### 1. 代码补全没有出现？
- 检查是否已启用 LLM 补全（`Ctrl+Alt+Shift+L`）
- 确认 API_KEY 配置正确
- 查看日志是否有错误信息

### 2. API 调用超时？
- 增加 `TIMEOUT_SECONDS` 参数
- 检查网络连接
- 考虑使用更快的 LLM 服务

### 3. 补全质量不好？
- 调整 `TEMPERATURE` 参数（降低可提高确定性）
- 使用更强大的模型（如 gpt-4）
- 确保文件有足够的上下文信息

### 4. 如何使用自定义 LLM？
只要您的 LLM 服务兼容 OpenAI API 格式，只需修改 `API_URL` 即可：
```java
// 例如使用本地部署的模型
private static final String API_URL = "http://localhost:8000/v1/chat/completions";
```

## 📮 反馈和贡献

如有问题或建议，欢迎提交 Issue 或 Pull Request！

## 📄 许可证

本项目仅供学习和研究使用。
