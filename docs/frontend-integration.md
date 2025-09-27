# 前端联调约定

## 最小 SOCP 消息示例

### USER_HELLO (前端连接时)
```json
{
  "type": "USER_HELLO",
  "from": "frontend-dev",
  "to": "server",
  "ts": 1700000000000,
  "payload": {
    "client": "frontend-dev",
    "pubkey": "dev-pubkey",
    "enc_pubkey": "dev-enc-pubkey"
  },
  "sig": "dev-mock"
}
```

### HEARTBEAT (25秒间隔)
```json
{
  "type": "HEARTBEAT",
  "from": "frontend-dev",
  "to": "server",
  "ts": 1700000000000,
  "payload": {},
  "sig": "dev-mock"
}
```

### MSG_DIRECT (用户发送消息)
```json
{
  "type": "MSG_DIRECT",
  "from": "frontend-dev",
  "to": "target-user",
  "ts": 1700000000000,
  "payload": {
    "ciphertext": "Hello World",
    "sender_pub": "dev-pubkey",
    "content_sig": "dev-sig"
  },
  "sig": "dev-mock"
}
```

## 开发约定

- **签名占位**: 所有 `sig` 字段使用 `"dev-mock"` 占位
- **后续集成**: 等 Person B 提供真实加密实现后替换
- **Schema严格遵守**: 前端严格按 `src/main/resources/socp.json` 组包
- **开发桩端口**: `devserver.ChatServer` 使用 8080 端口
- **前端开发端口**: Vite 默认使用 5173 端口
- **环境配置**: 通过 `frontend/.env.development` 中的 `VITE_WS_URL` 配置WebSocket地址

## 启动顺序

1. 启动后端: `mvn -q exec:java -Dexec.mainClass=devserver.ChatServer`
2. 启动前端: `cd frontend && npm run dev`
3. 浏览器访问: `http://localhost:5173`

## Open Questions / TODO

### 与 Person A（DHT/路由）的对接点
- 前端在线列表需要 `{id, pubkey, last_heartbeat}` 格式的用户数据
- 消息路由：是否直接用 `to=user_id` 还是需要特殊格式？
- DHT节点查询接口返回什么数据结构？

### 与 Person B（加密）的对接点
- `crypto.CryptoPorts` 接口定义是否合适？
- RSA密钥长度：推荐2048位还是4096位？
- AES加密模式：GCM、CBC还是CTR？优先推荐GCM
- 何时提供实现以替换当前的 `sig:"dev-mock"` 占位？

### 与 Person C（WebSocket）的对接点
- 正式WSS服务器使用什么端口？
- 是否需要TLS证书配置？
- 心跳间隔建议：当前前端设置25秒，服务端是否对齐？
- 限流策略：当前开发桩10条/秒，正式服务器是否统一？

### 与 Person D（协议/Schema）的对接点
- 前端展示需要的额外字段是否要加入Schema？
  - 用户在线状态：USER_ADVERTISE的meta字段是否加status？
  - 文件传输进度：FILE_CHUNK是否需要progress字段？
- Schema扩展流程：如何提出建议并统一修改？