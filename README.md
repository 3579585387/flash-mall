# FlashMall 闪购商城 · 高并发电商秒杀平台

> 一个可以完整跑起来的高并发电商秒杀项目：C 端商城 + B 端商家后台，
> 核心解决**秒杀瞬时高并发下的不超卖、不雪崩、订单最终一致**。

技术栈：`Spring Boot 3.2` · `MyBatis-Plus` · `MySQL` · `Redis` · `Redis Stream` · `Caffeine` · `WebSocket` · `AOP`

---

## 一、项目解决什么问题

秒杀活动开始的一瞬间，几千个请求同时打在同一条 SKU 上。如果直接查库扣库存，
会出现三类问题：**超卖**（库存扣成负数）、**重复下单**（一个人抢到多单）、**数据库被瞬时打穿**。

本项目围绕三条线设计：

| 链路 | 做法 | 目标 |
|---|---|---|
| 削峰写 | Redis + Lua 原子预扣库存 → Redis Stream 异步落库 | 接口不做数据库操作，RT 稳定在毫秒级 |
| 抗压读 | Caffeine → Redis（逻辑过期）→ MySQL 三级缓存 | 挡住 90%+ 的数据库访问 |
| 兜底正确性 | 唯一索引 + 状态机 CAS + 消息幂等 + 库存流水回补 | 不超卖、不重单、不丢单、不重复回补 |

---

## 二、快速开始

### 1. 环境要求

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 17+ | 本项目在 JDK 17 / 21 上语法与构建验证通过 |
| Maven | 3.6+ | 用 IDEA 自带 Maven 也可以 |
| MySQL | 5.7 / 8.x / 9.x | **库会自动创建**，只需账号有建库权限 |
| Redis | **5.0+**（推荐 6/7） | 用到 Stream，必须 5.0 以上 |



### 2. 改一处配置

打开 `mall-server/src/main/resources/application.yml`，只改**数据库密码**，其余可保持默认：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/flash_mall?createDatabaseIfNotExist=true&...   # 库自动创建
    username: root
    password: 123456      # ← 改成你自己的 MySQL 密码
  data:
    redis:
      host: 127.0.0.1
      port: 6379
```

建表脚本和种子数据在 `db/schema.sql`、`db/data.sql`，应用启动时自动执行，
**全部是 `CREATE TABLE IF NOT EXISTS` 和 `INSERT IGNORE`，可以反复启动，不会覆盖你改过的数据**。

### 3. 启动

IDEA 里直接运行 `com.flashmall.FlashMallApplication`，或者命令行：

```bash
mvn clean package -DskipTests
java -jar mall-server/target/flash-mall.jar
```

启动成功后先访问自检接口，确认 MySQL 和 Redis 都通了：

```bash
curl http://localhost:8080/api/health
# {"code":200,"msg":"success","data":{"redis":"OK","mysql":"OK","goodsCount":5}}
```

如果这里是 `FAIL`，说明 Redis 没启动或者数据库密码不对，日志里会有明确提示。

---

## 三、5 分钟跑通完整业务链路

IDEA 用户可以直接打开 `scripts/api-test.http`，每个请求点一下就能跑，token 会自动传递；
命令行用户按下面顺序执行。

```bash
# 0) 环境自检
curl http://localhost:8080/api/health

# 1) 拿验证码（演示项目直接把验证码返回，真实项目接短信）
curl -X POST "http://localhost:8080/api/auth/send-code?phone=13800000001"
# → 返回 6 位数字，例如 482913

# 2) 登录，拿到 token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800000001","code":"482913"}'
# → {"data":{"token":"xxxx","userId":1,...}}   下面用 $TOKEN 代指

# 3) 浏览商品（游客即可）
curl http://localhost:8080/api/goods/page?categoryId=2
curl http://localhost:8080/api/goods/3        # 详情走三级缓存

# 4) 加购物车 + 下单
curl -X POST http://localhost:8080/api/cart/add \
  -H "Authorization: $TOKEN" -H "Content-Type: application/json" \
  -d '{"skuId":4,"quantity":2}'

curl -X POST http://localhost:8080/api/order/submit \
  -H "Authorization: $TOKEN" -H "Content-Type: application/json" \
  -d '{"receiverName":"张三","receiverPhone":"13800000001","receiverAddr":"杭州市西湖区","remark":"尽快发货"}'
# → 拿到 orderNo

# 5) 模拟支付回调（可以重复调用同一个 orderNo，验证幂等）
curl -X POST http://localhost:8080/api/pay/notify \
  -H "Content-Type: application/json" \
  -d '{"orderNo":"<上一步的orderNo>","tradeNo":"wx_trade_001"}'
```

### 跑秒杀（核心链路）

```bash
# 6) 抢购（需要登录 token）
curl -X POST http://localhost:8080/api/seckill/1/3 -H "Authorization: $TOKEN"
# → {"data":{"orderNo":"...","status":"PROCESSING"}}

# 7) 轮询结果（消费者异步落库完成后变成 SUCCESS）
curl http://localhost:8080/api/seckill/result/<orderNo> -H "Authorization: $TOKEN"
```

### 验证「不超卖、不重复」

这是写简历最需要拿得出手的证据，两条 SQL 就够：

```sql
-- 秒杀库存应为 0（种子数据初始是 100）
SELECT seckill_stock FROM tb_seckill_sku WHERE activity_id = 1 AND sku_id = 3;

-- 成功订单数应等于初始秒杀库存，且每个用户最多 1 单
SELECT COUNT(*) FROM tb_orders WHERE activity_id = 1;
SELECT user_id, COUNT(*) c FROM tb_orders WHERE activity_id = 1 GROUP BY user_id HAVING c > 1;
```

再多点几次抢购按钮，会依次看到：`超出限购数量` → 库存扣完后 `已售罄`
（售罄标记生效后，后续请求在 Lua 脚本第一行就被挡掉）。

---

## 四、目录结构

```
flash-mall
├── pom.xml              父工程：所有依赖 version 统一在这里管理，子模块不写 version
├── mall-common          通用层：Result / 异常 / 常量 / 枚举 / ThreadLocal 登录上下文 / SpEL 工具
├── mall-pojo            实体层：entity / dto / vo
└── mall-server          应用层：全部业务与技术实现 + 配置 + 消费者 + 定时任务
    ├── src/main/java/com/flashmall
    │   ├── annotation     自定义注解（AutoFill / Idempotent / RateLimit）
    │   ├── aspect         AOP 切面（公共字段填充 / 幂等 / 限流）
    │   ├── cache          三级缓存模板（CacheTemplate / RedisData）
    │   ├── config         MVC、WebSocket、Jackson、MyBatis-Plus 配置
    │   ├── controller     C 端接口（controller）与 B 端接口（controller/admin）
    │   ├── handler        全局异常处理
    │   ├── interceptor    三层拦截器（Token 解析 / 后台校验 / C 端校验）
    │   ├── mapper         15 个 MyBatis-Plus Mapper
    │   ├── seckill        秒杀：Lua 脚本封装、抢购、异步落库、Stream 消费者
    │   ├── service        业务层
    │   ├── task           超时关单定时任务
    │   ├── utils          RedisIdWorker 全局 ID 生成器
    │   └── websocket      WebSocket 来单提醒
    └── src/main/resources
        ├── application.yml
        ├── db/schema.sql        建表脚本（15 张表）
        ├── db/data.sql          种子数据（5 个商品 / 6 个 SKU / 1 场秒杀活动）
        └── lua/seckill.lua      秒杀原子脚本
```

此外：`docs/` 是设计文档，`scripts/api-test.http` 是可直接在 IDEA 里跑的接口测试。

模块依赖方向：`server → (common, pojo)`；`common` / `pojo` 不反向依赖业务，避免循环依赖。
Maven 多模块结构与 `sky-take-out` 保持一致：父工程 `packaging=pom` 只做版本与模块管理，三个子模块各司其职。


---

## 五、技术亮点（面试重点，附代码位置）

| 亮点 | 实现位置 | 一句话说明 |
|---|---|---|
| **Lua 原子秒杀** | `lua/seckill.lua` + `SeckillService#seckill` | 活动校验 + 库存扣减 + 一人一单 + 消息投递，一次 Redis 调用内原子完成 |
| **Stream 异步削峰** | `seckill/SeckillOrderConsumer` | 多线程消费 + 幂等表 + 业务异常回补 + 系统异常重试 + PENDING 兜底 + 死信队列 |
| **三级缓存** | `cache/CacheTemplate` | 空值缓存防穿透、互斥锁 / 逻辑过期防击穿、TTL 抖动 + 本地缓存防雪崩 |
| **全局唯一 ID** | `utils/RedisIdWorker` | 31 位时间戳 + 32 位序列号，趋势递增、不暴露业务量 |
| **缓存重建锁** | `cache/CacheTemplate` | `SET NX EX` 互斥锁 + 逻辑过期异步重建，避免热点 key 击穿 |
| **订单状态机** | `OrderService#transit`、`OrderCancelService` | 所有流转走 `WHERE status = 旧值` 的 CAS，避免并发覆盖 |
| **支付回调幂等** | `PayNotifyController` + `OrderService#paySuccess` | `tb_payment(order_no, channel)` 唯一索引，重复回调直接返回成功 |
| **超时关单** | `task/OrderTimeoutTask` | Redis ZSet 延迟队列（秒级）+ 数据库扫表兜底，双保险 |
| **AOP 统一横切** | `annotation/*` + `aspect/*` | `@AutoFill` 公共字段填充、`@Idempotent` 防重复提交、`@RateLimit` 限流 |
| **双端鉴权** | `interceptor/*` | 三层拦截器：解析 token → 后台校验 → C 端校验；Token 滑动续期 |
| **实时提醒** | `websocket/WebSocketServer` | 支付成功推送来单提醒，会话容器用并发 Map |

---

## 六、接口一览

| 分组 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 自检 | GET | `/api/health` | MySQL / Redis 连通性 |
| 登录 | POST | `/api/auth/send-code?phone=` | 发送验证码（演示直接返回） |
| | POST | `/api/auth/login` | 手机号 + 验证码登录 |
| | POST | `/api/admin/auth/login` | 后台登录（admin / 123456） |
| 商品 | GET | `/api/goods/categories` | 分类列表 |
| | GET | `/api/goods/page` | 商品分页（游客可访问） |
| | GET | `/api/goods/{skuId}` | 商品详情（三级缓存） |
| 购物车 | GET/POST/PUT/DELETE | `/api/cart`、`/add`、`/{id}/quantity`、`/{id}` | 增删改查 |
| 订单 | POST | `/api/order/submit` | 提交订单（事务 + 条件扣库存） |
| | GET | `/api/order/page`、`/api/order/{orderNo}` | 我的订单 |
| | POST | `/api/order/{orderNo}/pay` | 模拟预支付 |
| | POST | `/api/order/{orderNo}/cancel` | 取消订单（回补库存） |
| 支付 | POST | `/api/pay/notify` | 支付回调（幂等，可重复调用） |
| 秒杀 | POST | `/api/seckill/{activityId}/{skuId}` | 抢购 |
| | GET | `/api/seckill/result/{orderNo}` | 抢购结果轮询 |
| 后台 | GET | `/api/admin/goods/page` | 商品管理 |
| | POST/PUT | `/api/admin/goods`、`/{id}/status` | 新增 / 改价 / 上下架 |
| | POST | `/api/admin/seckill/publish` | 发布秒杀活动（自动预热） |
| | GET | `/api/admin/seckill/{activityId}` | 活动详情（含 DB / Redis 库存对比） |
| | GET/PUT | `/api/admin/order/page`、`/{orderNo}/confirm`、`/deliver`、`/complete` | 订单履约 |
| | GET | `/api/admin/dashboard` | 运营看板 |

---

## 七、设计文档

代码是这套设计的**最小可运行实现**，完整的方案论证、库表设计、面试问答在这里：

| 文档 | 内容 |
|---|---|
| [docs/01-项目定位与技术架构](docs/01-项目定位与技术架构.md) | 背景、角色、模块划分、秒杀时序图、订单状态机 |
| [docs/02-数据库设计](docs/02-数据库设计.md) | 表结构、索引设计、防超卖防重的约束方案 |
| [docs/03-核心难点与解决方案](docs/03-核心难点与解决方案.md) | 12 个技术专题（缓存三兄弟、Lua、异步下单、状态机、回调幂等…） |
| [docs/04-简历文案](docs/04-简历文案.md) | 三版简历 bullet + 关键词 + 反面教材 |
| [docs/05-面试问答](docs/05-面试问答.md) | 30 个高频追问与答法 |
| [docs/06-压测与量化指标](docs/06-压测与量化指标.md) | JMeter 压测方法与指标口径 |
| [docs/07-落地路线与真实性自检](docs/07-落地路线与真实性自检.md) | 分阶段实施路径、MVP 边界、防被拆穿清单 |

> ⚠️ 设计文档里的 18 张表是完整方案；**代码实现了其中 15 张核心表**
> （省掉了收货地址、优惠券、退款流水、门店 GEO 等扩展表，主链路不受影响）。
> 简历上的数字一定要用 `docs/06` 的方法自己压测后填写。

---

## 八、已知边界（面试可以主动说明）

- 支付是**模拟实现**：没有真实商户号，`/api/pay/notify` 代替微信回调，真实验签流程在代码注释里说明。
- 秒杀订单**不带收货地址**，落库时用占位信息；真实业务应在支付前补充。
- 保持**单体多模块**，没有引入 Spring Cloud；模块边界已按可拆分方式设计。
- 消息中间件用 **Redis Stream** 而非 RocketMQ：省一个中间件，但缺少事务消息、延迟队列、专业监控，生产环境建议替换。
- 统计接口直接聚合查询，数据量大了应改为预计算统计表或 OLAP。

## 九、License

MIT
