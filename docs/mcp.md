# 屁岱连接 MCP

在屁岱设置中填入远程 MCP 服务的 `https://…/mcp` 地址和可选 Bearer 令牌，点「测试连接」后启用。令牌按当前校园账号保存到安全存储。Android 的 localhost 指手机自身，不能直接填写电脑的 localhost。

对话中可以要求「列出 MCP 工具」或请求清单操作。屁岱先读取远程工具清单，再按工具名称和参数调用。每次调用都会显示服务地址、工具和完整参数；允许后才发送。拒绝、停止生成或切换会话不会继续等待中的调用。已发送的远程写入无法由关闭界面撤回，断线或会话过期时不会自动重试写入。

此版本支持 Streamable HTTP（JSON/SSE）、初始化、2025-06-18 / 2025-03-26 协商、会话标识、工具分页与调用。只允许 HTTPS，回环地址可用 HTTP；不会跟随重定向发送凭据。暂不支持旧版独立 SSE 端点、stdio、OAuth 交互、resources/prompts/sampling。需要这些的服务应先使用兼容的远程桥接。

滴答清单等服务需要用户自行部署或选择可信的兼容 MCP 桥接，并完成该服务自身授权；应用不预置任何第三方凭据。

协议参考：https://modelcontextprotocol.io/specification/2025-06-18/basic/transports
