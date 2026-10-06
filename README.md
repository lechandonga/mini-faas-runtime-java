# mini-faas-runtime-java

本地小型函数计算运行时：函数是进程内 Java 处理器，通过 HTTP 触发调用，全部在本地进程内完成，不依赖外部服务或中间件。

## 函数注册与版本规则

- 函数通过 `FunctionHandler` 接口实现（`core/FunctionHandler.java`），新增函数只需新增实现类，无需改动运行时核心。
- 注册方式两种：
  - 注解自动注册：实现类加 `@Component` + `@FaasFunction(name = "echo", version = 2)`，启动时由 `FaasFunctionRegistrar` 自动注册，注册冲突会导致启动失败（部署期暴露问题）。
  - 编程式注册：`faasRuntime.register(name, version, handler)`。
- 版本号为正整数，同一函数的多个版本共存；调用时用 `?version=N` 指定版本，不指定时固定落到版本号最大的最新发布版本。
- 以下注册会被明确拒绝并抛出 `RegistrationException`，`reason` 可区分原因，绝不静默覆盖：
  - `DUPLICATE_VERSION`：同一函数同一版本已存在；
  - `HANDLER_MISSING`：处理器为 null；
  - `INVALID_NAME`：名字为空或全空白；
  - `INVALID_VERSION`：版本号非正整数。

## 生命周期、并发与超时语义

- **冷启动与复用**：每个（函数， 版本）的实例在第一次被调用时才执行 `init()`，且只初始化一次；之后的调用复用同一实例。
- **初始化失败**：`init()` 抛异常时返回 `INIT_FAILED`，失败结果被缓存，后续调用直接返回同样的初始化失败，不会反复重试初始化，也不会被后续调用掩盖。
- **并发**：同一实例同时执行的调用数不超过 `faas.max-concurrency-per-instance`（默认 4）。超出上限的请求排队等待执行许可，不会被丢弃。
- **超时**：单次调用超过 `faas.invoke-timeout-ms`（默认 3000ms）后，执行线程被中断（`interrupt`）并返回 `TIMEOUT`。执行许可在任务真正结束时才释放，保证同一实例不会同时执行两次调用；超时只影响当次调用，不污染该函数后续的调用结果。

## 错误分类

统一响应结构，错误只包含分类与业务 message，不含堆栈信息或内部类名：

```json
{ "success": false, "error": { "type": "TIMEOUT", "message": "..." } }
```

| type | 含义 | HTTP 状态 |
| --- | --- | --- |
| `FUNCTION_NOT_FOUND` | 函数未注册 | 404 |
| `VERSION_NOT_FOUND` | 指定版本不存在 | 404 |
| `INVALID_ARGUMENT` | 参数错误（业务抛 `InvalidArgumentException`） | 400 |
| `BUSINESS_ERROR` | 业务异常（其他业务异常，仅透传 message） | 500 |
| `INIT_FAILED` | 冷启动初始化失败 | 500 |
| `TIMEOUT` | 调用超时并已中断 | 504 |
| `INTERNAL_ERROR` | 平台内部错误 | 500 |

## 本地验证

```bash
# 启动（指定端口）
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080

# 调用示例函数 echo：不指定版本 -> 最新 v2（大写回显）
curl -X POST localhost:8080/invoke/echo -d 'hello'
# {"success":true,"result":"v2:HELLO"}

# 指定版本 -> v1（原样回显）
curl -X POST 'localhost:8080/invoke/echo?version=1' -d 'hello'
# {"success":true,"result":"v1:hello"}

# 错误示例：空入参 -> INVALID_ARGUMENT；未知函数 -> FUNCTION_NOT_FOUND
curl -X POST localhost:8080/invoke/echo -d ''
curl -X POST localhost:8080/invoke/ghost -d 'x'
```

## 测试

```bash
mvn test
```

测试覆盖（日志中带 `[场景]` 前缀，可直接看到各业务用例与结果）：

- `FaasRuntimeTests`：冷启动与复用、版本路由、注册拒绝（4 种原因）、并发上限（峰值并发不超过上限且请求不丢失）、超时中断（含超时后后续调用不受污染）、初始化失败缓存、错误映射（4 类）。
- `InvokeControllerTests`：HTTP 调用与版本路由、错误响应结构、HTTP 层超时映射（504/TIMEOUT）。
