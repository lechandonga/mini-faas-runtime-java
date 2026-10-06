# mini-faas-runtime-java

一个本地小型的函数计算（FaaS）运行时：函数由进程内 Java 处理器充当，通过 HTTP 触发调用，
全部在本地进程内完成，不依赖外部服务或中间件。重点保证冷启动、并发与超时场景下行为可预期。

## 快速开始

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080

# 调用示例函数（不指定版本 -> 最新发布版本）
curl -X POST http://localhost:8080/invoke/echo -H 'Content-Type: application/json' -d '{"message":"hello"}'

# 指定版本
curl -X POST 'http://localhost:8080/invoke/echo?version=v1' -H 'Content-Type: application/json' -d '{"message":"hello"}'
```

## 函数注册与版本规则

- 新增函数无需修改运行时核心：实现 `FunctionHandler` 并标注 `@FaasFunction(name, version)`，
  Spring 启动时自动注册（见 `example/EchoFunction`）。也可以通过 `FunctionRegistry.register(name, version, factory)` 编程式注册。
- 同一函数名的多个版本共存；调用时通过 `?version=` 指定版本，不指定时固定路由到**最新发布版本**（按版本号字典序最大者）。
- 注册失败会被明确拒绝并抛出 `RegistrationException`，原因可区分，绝不静默覆盖：
  - `INVALID_NAME`：函数名为空；
  - `INVALID_VERSION`：版本号为空；
  - `MISSING_HANDLER`：处理器工厂缺失；
  - `DUPLICATE_VERSION`：同名同版本已存在。

## 生命周期与并发/超时语义

- **冷启动**：每个版本第一次被调用时才创建实例并执行 `init()`，且只初始化一次；之后所有调用复用同一实例。
- **初始化失败**：`init()` 抛异常后该版本实例进入失败态，后续调用直接返回 `INIT_ERROR`，不会反复初始化。
- **并发上限**：同一实例同时执行的调用数不超过 `faas.max-concurrency-per-instance`（默认 4）。
  超限请求会排队等待最多 `faas.queue-timeout-ms`（默认 5000ms）；仍拿不到执行槽位时明确返回
  `OVERLOADED`（HTTP 429），请求不会被静默丢弃。
- **超时**：单次调用超过 `faas.invoke-timeout-ms`（默认 10000ms）后，执行线程被中断（interrupt），
  返回 `TIMEOUT`（HTTP 504）。超时只影响本次调用，实例许可被释放，后续调用结果不受污染。

配置项（`application.properties`）：

```properties
faas.max-concurrency-per-instance=4
faas.invoke-timeout-ms=10000
faas.queue-timeout-ms=5000
```

## 错误分类

所有错误对外返回统一结构，**不包含堆栈信息与内部类名**：

```json
{"requestId": "...", "code": "TIMEOUT", "message": "function echo:v1 timed out after 10000ms"}
```

| code                 | 含义                     | HTTP 状态 |
|----------------------|--------------------------|-----------|
| `FUNCTION_NOT_FOUND` | 函数或版本不存在         | 404       |
| `BAD_REQUEST`        | 请求体不是合法 JSON      | 400       |
| `BUSINESS_ERROR`     | 函数业务代码抛出的异常   | 500       |
| `TIMEOUT`            | 调用超时，执行已被中断   | 504       |
| `INIT_ERROR`         | 冷启动初始化失败         | 500       |
| `OVERLOADED`         | 并发已满且排队超时       | 429       |
| `INTERNAL_ERROR`     | 平台内部错误             | 500       |

## 测试与本地验证

```bash
mvn test
```

测试覆盖（日志中可见每个用例的覆盖情况）：

- `RuntimeBehaviorTests`：注册规则（重复版本/处理器缺失/非法名字）、版本路由、冷启动只初始化一次与实例复用、
  初始化失败粘性（不反复初始化）、并发上限与超限明确拒绝、超时中断与后续调用不受污染、错误分类。
- `InvokeApiTests`：HTTP 端到端——示例函数调用、业务异常/超时/不存在/非法 JSON 的错误结构与状态码，
  并断言响应中无堆栈与内部类名。

## 代码结构

- `runtime/`：运行时核心（注册表、实例生命周期、并发准入、超时中断、错误模型），不依赖 Spring MVC，可独立测试。
- `web/`：HTTP 触发入口与全局异常映射。
- `example/EchoFunction`：示例函数。
