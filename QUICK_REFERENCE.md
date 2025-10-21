# 快速参考卡片 | Quick Reference Card

## 🎯 快捷键 | Shortcuts

| 快捷键               | 功能 | Function |
|-------------------|------|----------|
| `Shift + Alt + A` | 开启/关闭 AI 补全 | Toggle AI Completion |
| `Tab`             | 接受补全建议 | Accept Suggestion |
| `Shift + Alt + 1` | 分析和优化选中代码 | Analyze & Optimize Code |
| `Shift + Alt + 3` | 为选中代码加注释 | Add Comments to Code |
| `Shift + Alt + 2` | 应用 AI 修改 | Apply AI Changes |

## 📦 安装步骤 | Installation

```bash
# 1. 构建插件 | Build plugin
./gradlew build

# 2. 安装插件 | Install plugin
# PyCharm → Settings → Plugins → ⚙️ → Install Plugin from Disk
# 选择: build/distributions/demo-1.0-SNAPSHOT.zip

# 3. 重启 PyCharm | Restart PyCharm
```

## ⚙️ 必需配置 | Required Configuration

```
Settings → AI Code Completion

✅ API URL: https://api.openai.com/v1/chat/completions
✅ API Key: sk-... (你的密钥 | Your API key)
✅ Model: gpt-4o-mini
```

## 📝 使用示例 | Usage Example

### 代码补全 | Code Completion
```python
# 1. 按 Shift+Alt+A 开启 | Press Shift+Alt+A to enable
# 2. 输入代码 | Type code:
def calculate_

# 3. 看到灰色建议 | See gray suggestion:
def calculate_sum(a, b):

# 4. 按 Tab 接受 | Press Tab to accept
```

### 代码优化 | Code Optimization
```python
# 1. 选中代码 | Select code:
for i in range(10):
    result.append(i * 2)

# 2. 按 Shift+Alt+1 | Press Shift+Alt+1
# 3. 查看 Diff 对比 | View Diff
# 4. 按 Shift+Alt+2 应用 | Press Shift+Alt+2 to apply
```

### 添加注释 | Add Comments
```python
# 1. 选中代码 | Select code:
def process_data(data):
    return sum([x for x in data if x > 0])

# 2. 按 Shift+Alt+3 | Press Shift+Alt+3
# 3. AI 自动添加注释 | AI adds comments
# 4. 查看对比并应用 | Review and apply
```

## 🔧 故障排查 | Troubleshooting

| 问题 | 解决方案 | Problem | Solution |
|------|----------|---------|----------|
| 补全不工作 | 检查是否已开启(Shift+Alt+A) | No completion | Check if enabled (Shift+Alt+A) |
| API 错误 | 检查 API Key 和网络连接 | API error | Verify API Key and network |
| 代理问题 | 检查代理 127.0.0.1:7897 | Proxy issue | Check proxy 127.0.0.1:7897 |
| 补全太慢 | 增加触发延迟时间 | Too slow | Increase trigger delay |
| 建议太长 | 减小最大建议长度 | Too long | Reduce max suggestion length |
| 编辑器锁定 | 关闭 Diff 对话框 | Editor locked | Close Diff dialog |

## 📚 文档 | Documentation

- 📖 [README.md](README.md) - 项目概述 | Project overview
- 📋 [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) - 技术细节 | Technical details  
- 🎓 [USAGE.md](USAGE.md) - 详细使用指南 | Detailed usage guide

## 🌐 支持的 API | Supported APIs

✅ OpenAI GPT series  
✅ Azure OpenAI  
✅ OneAPI  
✅ Local models (compatible with OpenAI API format)

## 💡 提示 | Tips

1. **首次使用**：务必配置 API Key | Must configure API Key
2. **网络**：需要访问 API 地址 | Need API access
3. **代理**：默认使用 127.0.0.1:7897 | Default proxy 127.0.0.1:7897
4. **性能**：可调整触发延迟 | Adjustable trigger delay
5. **缓存**：自动缓存 60 秒，减少 API 调用 | Auto cache 60s, reduce API calls
6. **费用**：注意 API 调用成本 | Mind API costs
7. **锁定**：查看 Diff 时编辑器自动锁定 | Editor auto-locks when viewing Diff

---

**构建状态 | Build Status**: ✅ 成功 | Success  
**插件版本 | Plugin Version**: 1.0-SNAPSHOT  
**兼容版本 | Compatible**: PyCharm Community Edition 2019.3.5
