# Anti4D4V

一个简单的 Paper/Folia 插件，用于防止特定前缀或 ID 的玩家进入服务器，或在进入后进行刷屏等违规行为。

## 功能特性

- **名字过滤**：支持通过前缀或正则表达式拦截特定玩家加入。
- **聊天过滤**：支持通过正则表达式拦截违规聊天内容。
- **多种惩罚**：支持 `BAN_IP`（封禁 IP 和账号）、`BAN`（仅封禁账号）、`KICK`（仅踢出）三种动作。
- **性能优化**：启动时预编译所有正则表达式，确保高效匹配。
- **异步处理**：聊天和登录检查均在异步事件中处理，不影响服务器主线程性能。
- **热重载**：支持 `/anti4d4v reload` 命令实时重载配置。

## 配置文件 (config.yml)

```yaml
# 名字前缀黑名单
banned-name-prefixes:
  - "4D4V_"

# 名字正则表达式黑名单
banned-name-regex: [ ]

# 聊天正则表达式黑名单
banned-chat-regex:
  - ".*4d4v\\.top.*"

# 命中后的执行动作: BAN_IP, BAN, KICK
action: BAN_IP

# 是否全服广播拦截消息
broadcast: true

# 拦截提示消息
messages:
  kick-reason: "您的账号或行为触发了安全防护机制。"
  broadcast-msg: "§f玩家 %player% 因触发黑名单规则被拦截。"
```

## 命令与权限

- `/anti4d4v reload`: 重载配置文件。
- 权限: `anti4d4v.admin`

## 编译

使用 Gradle 进行编译：
```bash
./gradlew build
```
编译后的文件位于 `build/libs/` 目录下。

## 开源协议

本项目采用 [MIT](LICENSE) 协议开源。
