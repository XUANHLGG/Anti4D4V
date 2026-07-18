# Anti4D4V

一个简单的 Purpur/Paper 插件，用于防止特定前缀或 ID 的玩家进入服务器，或在进入后进行刷屏等违规行为。

## 功能特性

- **名字过滤**：支持通过前缀或正则表达式拦截特定玩家加入。
- **IP 段过滤**：支持 `x` 通配符和 CIDR，例如 `113.135.x.x`、`36.4x.x.x`、`1.2.3.x`、`113.135.0.0/16`。
- **聊天过滤**：支持通过正则表达式拦截违规聊天内容。
- **多种惩罚**：支持 `BAN_IP`（封禁 IP 和账号）、`BAN`（仅封禁账号）、`KICK`（仅踢出）三种动作。IP 段命中时会固定封禁当前 IP 和玩家名。
- **提示权限**：拦截提示只会发送给拥有 `anti4d4v.notify` 权限的玩家，可在权限插件中将该节点设为 `false` 来屏蔽提示。
- **权限豁免**：支持权限组 `anti4d4v.exempt`，拥有此权限的玩家将免疫封禁和踢出。
- **动态管理**：支持通过指令添加/移除屏蔽词和更改处罚措施。
- **多动作处罚**：`BAN_IP`、`BAN`、`KICK` 可同时启用，例如 `BAN,BAN_IP`。
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

# IP 段黑名单，支持 x 通配符和 CIDR
# 示例: "113.135.x.x"、"36.4x.x.x"、"1.2.3.x"、"113.135.0.0/16"
banned-ip-ranges: [ ]

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

### 命令

- `/anti4d4v reload` - 重载配置文件
- `/anti4d4v blacklist add <关键词>` - 添加聊天黑名单
- `/anti4d4v blacklist remove <关键词>` - 移除聊天黑名单
- `/anti4d4v blacklist list` - 列出所有聊天黑名单
- `/anti4d4v action <BAN_IP|BAN|KICK>` - 更改处罚措施
- `/anti4d4v packetloss <add|remove|list> [玩家]` - 管理丢包模式
- `/anti4d4v iprange add <IP段>` - 添加 IP 段封禁规则
- `/anti4d4v iprange remove <IP段>` - 移除 IP 段封禁规则
- `/anti4d4v iprange list` - 查看当前 IP 段封禁规则

### 权限

- `anti4d4v.admin` - 管理员权限
- `anti4d4v.exempt` - 豁免权限，拥有此权限的玩家将免疫封禁和踢出
- `anti4d4v.notify` - 接收拦截提示，默认所有玩家拥有

`action` 命令支持一次设置多个动作，例如 `/anti4d4v action BAN BAN_IP`。配置文件也支持 `action: BAN,BAN_IP` 或 YAML 列表。

## 编译

使用 Gradle 进行编译：
```bash
./gradlew build
```
编译后的文件位于 `build/libs/` 目录下。

## 开源协议

本项目采用 [MIT](LICENSE) 协议开源。
