# AI Code Completion Plugin for PyCharm 2019.3.5

[English](#english) | [中文](#中文)

---

## 中文

### 功能介绍

为 PyCharm 2019.3.5 社区版提供基于大语言模型的智能代码助手，包含两大核心功能：

#### 1. 🚀 智能代码补全
- **Shift + Alt + A**：开启/关闭 AI 补全
- **自动触发**：输入时自动显示灰色补全建议
- **Tab 键接受**：按 Tab 键接受当前建议

#### 2. 🔧 代码分析与优化
- **Shift + Alt + E**：将选中代码发送给 AI 分析
- **Diff 对比**：专业的差异对比视图
- **Shift + Alt + R**：应用 AI 的修改建议

### 快速开始

1. **安装插件**
   - 下载 `build/distributions/demo-1.0-SNAPSHOT.zip`
   - PyCharm: `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk...`
   - 重启 PyCharm

2. **配置 API**
   - `Settings` → `AI Code Completion`
   - 填写 API URL 和 API Key
   - 点击 Apply 保存

3. **开始使用**
   - 按 `Shift + Alt + A` 开启补全
   - 正常编写代码，等待灰色建议
   - 按 `Tab` 接受建议

### 配置项

| 配置 | 说明 | 默认值 |
|------|------|--------|
| API URL | 大模型 API 地址 | https://api.openai.com/v1/chat/completions |
| API Key | API 密钥 | (必填) |
| Model | 模型名称 | gpt-4o-mini |
| 触发延迟 | 输入延迟(ms) | 500 |
| 最大长度 | 建议最大字符数 | 150 |

### 支持的 API

支持所有兼容 OpenAI Chat Completions API 格式的服务：
- ✅ OpenAI GPT 系列
- ✅ Azure OpenAI
- ✅ 其他兼容服务（OneAPI、本地模型等）

### 构建说明

```bash
# 构建插件
./gradlew build

# 输出位置
# build/distributions/demo-1.0-SNAPSHOT.zip
```

### 技术栈

- **语言**：Java 8
- **构建工具**：Gradle 8.7
- **IDE SDK**：PyCharm 2019.3.5 (Build 193.*)
- **依赖**：OkHttp 4.12.0, org.json 20240303

### 文档

- 📖 [详细使用说明](USAGE.md)
- 📋 [项目总结](PROJECT_SUMMARY.md)

### 系统要求

- PyCharm Community Edition 2019.3.5
- Java 8 或更高版本
- 网络连接（访问 LLM API）

---

## English

### Features

An intelligent code assistant plugin for PyCharm 2019.3.5 Community Edition, powered by Large Language Models:

#### 1. 🚀 Smart Code Completion
- **Shift + Alt + A**: Toggle AI completion on/off
- **Auto-trigger**: Shows gray inline suggestions while typing
- **Tab to accept**: Press Tab to accept suggestions

#### 2. 🔧 Code Analysis & Refactoring
- **Shift + Alt + E**: Send selected code to AI for analysis
- **Diff View**: Professional diff comparison view
- **Shift + Alt + R**: Apply AI suggestions

### Quick Start

1. **Install Plugin**
   - Download `build/distributions/demo-1.0-SNAPSHOT.zip`
   - PyCharm: `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk...`
   - Restart PyCharm

2. **Configure API**
   - Go to `Settings` → `AI Code Completion`
   - Enter API URL and API Key
   - Click Apply to save

3. **Start Using**
   - Press `Shift + Alt + A` to enable completion
   - Type code normally, wait for gray suggestions
   - Press `Tab` to accept

### Configuration

| Setting | Description | Default |
|---------|-------------|---------|
| API URL | LLM API endpoint | https://api.openai.com/v1/chat/completions |
| API Key | Your API key | (Required) |
| Model | Model name | gpt-4o-mini |
| Trigger Delay | Input delay (ms) | 500 |
| Max Length | Max suggestion chars | 150 |

### Supported APIs

Supports all services compatible with OpenAI Chat Completions API format:
- ✅ OpenAI GPT series
- ✅ Azure OpenAI
- ✅ Other compatible services (OneAPI, local models, etc.)

### Build Instructions

```bash
# Build plugin
./gradlew build

# Output location
# build/distributions/demo-1.0-SNAPSHOT.zip
```

### Tech Stack

- **Language**: Java 8
- **Build Tool**: Gradle 8.7
- **IDE SDK**: PyCharm 2019.3.5 (Build 193.*)
- **Dependencies**: OkHttp 4.12.0, org.json 20240303

### Documentation

- 📖 [Detailed Usage Guide](USAGE.md)
- 📋 [Project Summary](PROJECT_SUMMARY.md)

### Requirements

- PyCharm Community Edition 2019.3.5
- Java 8 or higher
- Network connection (for LLM API access)

---

## License

For learning and personal use only.

## Contact

Email: 3472237739@qq.com

---

**Build Status**: ✅ Success  
**Plugin Package**: `build/distributions/demo-1.0-SNAPSHOT.zip`
