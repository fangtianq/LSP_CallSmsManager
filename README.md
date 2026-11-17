# CallSmsManager · 通话记录 / 短信 增删工具箱（LSPosed 模块 + App）

一个 APK，两副面孔：

- **普通 App**：列表浏览 + 新增记录 + 按条件删除（时间 / 号码 / 来电去电 / 来信去信 / 正文关键字）。
- **LSPosed 模块**：注入 `com.android.providers.telephony` 与 `com.android.providers.contacts` 两个
  provider 进程，在里面替 App 执行真正落库的动作。

目标机型：红米 K70 Ultra（HyperOS，Android 14/15，已 root + LSPosed）。

---

## 1. 为什么不直接做个普通 App 写库

Android 10+ 起，`WRITE_SMS` / `WRITE_CALL_LOG` 属于**受限权限**：

- 普通签名的应用即使声明了也拿不到，用户也没法手动授予；
- 系统在 **AMS 解析 provider 阶段**就已经拒绝（典型日志：
  `Permission Denial: writing com.android.providers.telephony.SmsProvider uri ... requires android.permission.WRITE_SMS`）；
- 所以「直连 ContentResolver」这条路在大多数机子上必然失败。

三条可行路线对比：

| 方案 | 是否需要 root | 数据一致性 | 代价 |
|---|---|---|---|
| A. 成为默认短信 / 默认拨号应用 | 否 | 好 | 会接管全部短信/通话，侵入性极强 |
| B. root + `su -c content ...` 命令 | 是 | 一般（shell 引号/文本解析，易出脏数据） | 值要转义，`content query` 输出靠文本解析 |
| C. **LSPosed 模块在 provider 进程内执行**（本项目主通道） | 是 | 好（走 provider 正常 insert/delete，`threads` 表、变更通知由系统维护） | 需要装模块 + 勾 scope |

结论：**LSPosed 不是唯一解，但在这个需求下它是最干净的解**——写入发生在 provider 进程内，
属于「自己访问自己」，权限检查直接放行；而且不需要改系统分区、不需要抢默认应用角色。

本项目把三条路都留了口子：`直连 → 模块 → root` 依次尝试，谁通用谁，UI 上会显示当前用的是哪条通道。

---

## 2. 装 & 启用

```bash
# 本地编译（需要 JDK 17 + Android SDK 34，local.properties 里配 sdk.dir）
./gradlew assembleDebug        # 产物：app/build/outputs/apk/debug/app-debug.apk
```

> **在线出包**：推送 `v*` 形式的 tag（如 `v1.1.3`）会自动触发 GitHub Actions
> 构建 release APK 并发布到 [Releases](../../releases)；也可在 Actions 页面手动
> Run workflow 下载 APK。release 包默认用 debug 签名，可直接安装。

1. 安装 APK（debug 包用默认 debug 签名，后续升级要一直用同一签名）。
2. 打开 **LSPosed** → 模块 → 启用「通话短信工具箱」。
3. 点「作用域」，**只勾这两个**：
   - `com.android.providers.telephony`（短信）
   - `com.android.providers.contacts`（通话记录）
4. 勾完必须让这两个进程重启一次才生效：

   ```bash
   su -c "kill $(su -c 'pidof com.android.providers.telephony')"
   su -c "kill $(su -c 'pidof com.android.providers.contacts')"
   ```
   或者干脆重启手机（更省事）。
5. 打开 App → 点右上角「自检」，看到

   ```
   ✅ LSPosed 模块（provider 进程内执行）
   ✅ 通话记录 → com.android.providers.contacts  pid=... uid=...
   ✅ 短信记录 → com.android.providers.telephony  pid=... uid=...
   ```

   就说明通道就绪。列表顶部状态栏会显示「通道 LSPosed 模块」。

---

## 2.5 配置向导（1.1.0）

右上角「配置」= `SetupActivity`，把"为什么不通 / 现在该点哪"直接摆在面前：

- **结论卡**：一句话说清当前状态（模块就绪 / 只有 root 兜底 / 一个通道都没有）+ 下一步该干什么
- **四条明细**：① 已安装 APK 的模块元数据（会真的去解压你装的那个 APK 读 `META-INF/xposed/*`，
  校验入口清单格式）②短信 provider ③通话 provider ④root
- **按钮**：重新检测 / 授权 root / 打开 LSPosed / 重启短信·通话服务 / 手动强制停止 / 复制诊断
- 三个通道**全不可用**时，主界面顶部会出现黄色提示条，首次还会自动跳进配置页

> 1.1.0 起 `module.prop` 的 `minApiVersion` 从 102 降到 **101**（去掉了仅 102 才有的
> `HookBuilder.setId`），这样只支持 API 101 的旧 LSPosed 也能加载本模块。

### root 通道：`Cannot run program "su": error=13`

这条报错**不代表没 root**，而是"找到了 su 但当前应用的 SELinux 域不允许 exec"。所以改成逐条路径探测并把结果全部展示：

```
su（PATH） / /system/bin/su / /system/xbin/su / /sbin/su
/debug_ramdisk/su（Magisk） / /data/adb/magisk/su
/data/adb/ksu/bin/su（KernelSU） / /data/adb/ap/bin/su（APatch）
最后再兜一层：sh -c "su -c id"
```

配置页的「诊断详情」会把每条路径的真实输出列出来，卡在哪一条一目了然。
点「授权 root」会用 15s 超时重新探测（留出点授权框的时间）。

## 3. 功能

### 通话记录

- 列表：号码 / 类型（来电、去电、未接、拒接、已拦截、语音信箱）/ 时间 / 时长
- 新增：指定号码、**指定时间**、类型、时长
- 删除：
  - 长按单条 → 精确删除（按 `_id`）
  - 「按条件删除」→ 号码（模糊）+ 时间区间 + 类型
  - 「清空」→ 全清（二次确认）
- 删除前会先统计匹配条数并弹出确认。

### 短信

- 列表：号码 / 类型（收件、发件、草稿、发件箱、失败、待发）/ 时间 / 正文摘要
- 新增：指定号码、**指定时间**、收信/发信、正文
- 删除：同上，另加「正文关键字」条件

### 其它

- 「自检」：逐条通道探测 + 打印 provider pid/uid
- 「日志」：进程内环形日志（300 条），现场没电脑时可直接看错误原因
- 所有写操作失败都会**自动降级**并保留失败原因（状态栏 / 日志里能看到是谁失败了）

---

## 4. 代码结构

```
app/src/main/java/cn/ftqp/callsms/
├── core/                      # 与「怎么落库」无关的通用层
│   ├── Target.java            # 两类目标：uri、号码列/时间列/类型列、类型枚举
│   ├── Filter.java            # 条件 → selection / 字面量 SQL（root 通道用）
│   ├── Router.java            # 引擎路由：直连 → 模块 → root，粘住上次成功的
│   ├── Engine.java            # 通道接口
│   ├── DirectEngine.java      # 直连 ContentResolver（大多数机型会被拒）
│   ├── ModuleEngine.java      # 有序广播 → provider 进程
│   ├── RootEngine.java        # su + /system/bin/content
│   ├── Bridge.java            # App ↔ provider 进程的协议常量
│   ├── Json.java / Times.java / Logs.java / OpResult.java / Shell.java
├── xposed/
│   ├── ModuleEntry.java       # API 102 入口（Hook ContentProvider.attachInfo）
│   └── ProviderBridge.java    # 跑在 provider 进程里的执行器
└── ui/                        # 纯代码建 View，不依赖 material
    ├── MainActivity.java
    ├── RecordsPanel.java
    └── Ui.java
app/src/main/resources/META-INF/xposed/
├── java_init.list             # 入口类
├── module.prop                # minApi=102 targetApi=102 staticScope exceptionMode=protective
└── scope.list                 # 两个 provider 包
```

关键实现点：

- **Hook 点**：`android.content.ContentProvider#attachInfo(Context, ProviderInfo)`。
  框架类、签名稳定，provider 启动必然经过；在它返回后把 `arg0`（provider 的 Context）交给
  `ProviderBridge`，注册一个动态广播接收器。`attachInfo` 是实例方法，`getThisObject()` 就是 provider 本体。
- **为什么用广播而不是 AIDL/Binder**：模块是注入进别人进程的，没有可 declare 的 Service 组件；
  动态广播是唯一零侵入的「反向调用」方式。用**有序广播**拿回执（`setResultData`），失败/无接收者能立刻区分。
- **限额**：单次最多回传 200 行、单字段 800 字（Binder 单次事务 1MB）。
- **安全**：`uri` 白名单（只允许 `content://sms`、`content://call_log/calls`），API 34+ 校验
  `getSentFromPackage()` 是否为 `io.github.fangtianq.callsms`；另在 Manifest 里声明了 signature 级
  `io.github.fangtianq.callsms.permission.BRIDGE` 备用。
- **不做的事**：不 hook `system_server`、不 hook SystemUI、不改资源、不碰 `framework`。
  scope 出问题最多影响短信/通话两个 provider 进程，LSPosed 里关掉模块即可恢复。

---

## 5. 已知限制 / 排错

| 现象 | 原因 | 处理 |
|---|---|---|
| 自检里模块 ❌ | scope 没勾 / 勾了没重启进程 / 模块没启用 | 勾 scope → 重启手机 |
| 只有通话 ✅、短信 ❌ | 只勾了一个包 | 两个都要勾 |
| 显示「通道 root」 | 模块没生效，走了 su 兜底 | 看自检；root 通道对正文含换行的短信解析不全 |
| 写入报 `no such column: new/is_read/presentation` | 该 ROM 的 `calls` 表少了可选列 | 已做自动降级（去掉可选列重试一次） |
| 写入报 `Permission Denial` | 走的是直连通道 | 说明模块没生效，属预期；修好模块即消失 |
| LSPosed 里看不到模块 / 提示 API 版本不符 | LSPosed 太老 | 1.1.0 已把 `minApiVersion` 降到 101；仍不行就升级 LSPosed |
| 配置页说「模块元数据有问题」 | 你装的是旧包（入口清单里带 `#` 注释，LSPosed 会把它当类名 → 静默不加载） | 重装最新 APK 后重启手机 |
| `Cannot run program "su": error=13` | 找到 su 但应用域不允许 exec（不是没 root） | 看配置页「诊断详情」的逐路径结果；在 Magisk/KernelSU 里给本应用授权 |
| 短信写进去 MIUI 短信不刷新 | 会话列表缓存 | 强停短信 App 再打开 |
| 长按记录提示「缺少 _id」 | **1.1.1 已修**：root 通道解析 `content query` 输出时，把 `Row: 0 _id=1` 的行号吃进了第一个列名，键变成 `"0 _id"` | 升级到 1.1.1+；即便仍拿不到 `_id`，现在会自动退回「号码+时间+类型」组合删除 |

---

## 6. 依赖说明

- `libxposed/api` **不打进 APK**，用 `compileOnly`。仓库里以 `app/libs/libxposed-api-102.jar`
  形式内置（取自官方 `io.github.libxposed:api:102.0.0` 的 `classes.jar`），
  好处是离线也能编译；想走仓库依赖就把 `app/build.gradle.kts` 里那行换成
  `compileOnly("io.github.libxposed:api:102.0.0")`。
- UI 只用 `androidx.appcompat`，不引入 material / recyclerview，减少依赖面。

---

## 6.5 应用图标

图标由 `tools/IconGen.java` 从 `tools/icon-src.jpg` 现场生成（不依赖 PIL/ImageMagick，只用 JDK）：

```bash
E:/000/jdk17/bin/java tools/IconGen.java tools/icon-src.jpg app/src/main/res
```

- 先按人像视觉中心（高度 48% 处）裁正方形，再双三次插值缩放到 48/72/96/144/192
- 自适应图标前景层：108dp 画布、内容只占中间 72dp（66.7%）+ 四角小圆角，
  这样圆形/方圆/水滴遮罩都不会切到脸；背景色取自照片平均色 `#7C8187`

换图只需替换 `tools/icon-src.jpg` 后重跑上面那条命令。

## 7. 后续可以加的

- 批量导入（CSV：号码,时间,类型,时长/正文）
- 按「重复号码 + 时间窗」去重后再写
- 写前预览 diff（将写入哪些行）
- 定时任务：每天固定时间清理超过 N 天的记录
- 把条件保存成「规则」，一键复用
