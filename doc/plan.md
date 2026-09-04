# Android AI 聊天伴侣需求文档

## 1. 项目概述

### 1.1 项目名称

Android AI Chat Companion

### 1.2 项目目标

基于 FloatyAnswer 二次开发一个 Android AI 聊天伴侣。

应用不替换系统输入法，也不持续监听用户屏幕。

用户在微信、Telegram、小红书、抖音、短信等聊天软件中，需要 AI 辅助时，主动点击悬浮按钮，应用才读取当前屏幕聊天内容，识别当前聊天对象以及双方对话内容，并调用用户配置的大语言模型生成推荐回复。

### 1.3 核心原则

1. 不自动扫描屏幕。
2. 只有用户主动点击悬浮按钮后才读取当前屏幕。
3. 不限制具体聊天软件。
4. 尽可能识别当前聊天对象。
5. 尽可能判断每条消息属于“我”还是“对方”。
6. 每个聊天对象维护独立会话和配置。
7. 支持全局 AI 配置。
8. 联系人配置支持按字段继承全局配置。
9. 用户每次调用 AI 时可临时输入本次调用目的。
10. AI 生成结果不自动发送，只提供查看、复制和重新生成。

---

# 2. 整体使用流程

```text
用户打开聊天软件
        ↓
进入某个聊天页面
        ↓
点击 AI 悬浮按钮
        ↓
读取当前屏幕
        ↓
识别：
当前 App
当前聊天对象
当前可见聊天消息
我 / 对方
        ↓
与历史会话合并
        ↓
显示 AI 操作面板
        ↓
用户可选填写：
“本次调用目的”
        ↓
读取联系人 AI 配置
        ↓
联系人字段未自定义？
        ↓
继承全局配置
        ↓
调用大模型
        ↓
生成推荐结果
        ↓
复制 / 重新生成
```

---

# 3. 屏幕识别模块

## 3.1 触发方式

屏幕识别必须由用户主动触发。

禁止：

```text
后台持续读取
自动监听页面
自动监控聊天内容
```

正确流程：

```text
用户点击悬浮球
        ↓
开始一次屏幕读取
        ↓
完成后立即停止
```

每点击一次悬浮按钮，只进行一次当前屏幕分析。

---

## 3.2 当前 App 识别

获取当前应用：

```text
packageName
appName
```

例如：

```text
com.tencent.mm
微信
```

```text
org.telegram.messenger
Telegram
```

系统不能仅支持预定义 App。

未知 App 也应进入通用聊天识别流程。

---

## 3.3 ScreenNode

Accessibility 与 OCR 最终统一转换为：

```text
ScreenNode

text
bounds
packageName
className
viewId
contentDescription
source
```

其中：

```text
source =
ACCESSIBILITY
OCR
```

必须保留文字的屏幕坐标。

---

## 3.4 读取策略

优先：

```text
AccessibilityService
```

如果无法获取有效聊天文本：

```text
Screenshot
    ↓
ML Kit OCR
```

两个来源最终统一进入 Chat Parser。

---

# 4. 聊天页面识别

新增：

```text
ChatPageDetector
```

负责判断当前屏幕是否属于聊天页面。

判断依据可以包括：

```text
页面存在大量连续文本
左右分布明显
顶部存在标题区域
底部可能存在输入区域
存在重复消息布局
```

如果无法确定：

```text
当前页面可能不是聊天页面
```

允许用户：

```text
继续识别
取消
```

---

# 5. 当前聊天对象识别

新增：

```text
ConversationResolver
```

输出：

```text
App
+
Conversation
```

例如：

```text
微信
张三
```

或：

```text
Telegram
Alice
```

或：

```text
小红书
用户123456
```

或：

```text
微信
客服部门交流群
```

建议优先从屏幕顶部 Header 区域识别聊天标题。

---

# 6. 消息识别

## 6.1 消息结构

所有识别后的聊天消息统一转换为：

```text
ChatMessage

id
conversationId
role
content
timestamp
source
bounds
```

role 包含：

```text
SELF
OTHER
SYSTEM
UNKNOWN
```

---

## 6.2 我和对方判断

新增：

```text
RoleClassifier
```

主要根据：

```text
消息气泡位置
文字位置
父节点位置
页面宽度
布局结构
```

判断：

```text
右侧 → SELF
左侧 → OTHER
中间 → SYSTEM / UNKNOWN
```

不能仅依赖文字内容判断。

如果无法判断：

```text
UNKNOWN
```

不能强行猜测。

---

# 7. 消息组合

新增：

```text
MessageClusterer
```

负责将 Accessibility/OCR 获取到的零散文字节点组合成真正的一条聊天消息。

例如：

```text
15:23
张三
今天晚上吃饭吗
```

不能直接保存成三条消息。

应该尽可能形成：

```text
OTHER
今天晚上吃饭吗
```

时间、昵称等信息作为辅助信息处理。

---

# 8. 会话管理

每个聊天对象维护独立 Conversation。

唯一标识建议：

```text
packageName
+
conversationKey
```

例如：

```text
com.tencent.mm + 张三
```

和：

```text
org.telegram.messenger + 张三
```

必须是两个不同会话。

---

# 9. 聊天记录保存

使用 Room 数据库。

主要包含：

## App

```text
id
packageName
appName
```

## Conversation

```text
id
appId
conversationKey
displayName
conversationType
createdAt
lastSeenAt
```

conversationType：

```text
PRIVATE
GROUP
UNKNOWN
```

## Message

```text
id
conversationId
role
content
timestamp
source
```

---

# 10. 消息去重

用户可能多次点击悬浮球。

例如：

第一次：

```text
A
B
C
D
```

第二次：

```text
B
C
D
E
```

最终只能保存：

```text
A
B
C
D
E
```

不能保存成：

```text
A
B
C
D
B
C
D
E
```

需要实现：

```text
MessageSequenceMatcher
```

根据最近消息序列判断新旧消息重叠部分。

---

# 11. 全局 AI 配置

提供“全局 AI 设置”。

字段至少包括：

```text
API Base URL
API Key
Model
System Prompt
Temperature
Max Tokens
```

例如：

```text
API Base URL:
https://xxx.com/v1

API Key:
sk-******

Model:
xxx-model

System Prompt:
你是我的聊天回复助手。
请根据聊天上下文，
按照我的正常聊天风格生成自然回复。
不要出现明显 AI 味。
```

---

# 12. 联系人独立 AI 配置

每个 Conversation 可以拥有自己的 AI 配置。

例如：

```text
微信 / 张三

System Prompt:
这是我的同事，回复要简洁、自然，
不要过于正式。
```

```text
微信 / 李四

System Prompt:
这是公司领导。
回复必须礼貌、正式、简短。
```

---

# 13. 字段级继承机制

这是核心需求之一。

联系人配置不是简单：

```text
全部继承
或者
全部自定义
```

而是每个字段独立选择：

```text
继承全局
或者
自定义
```

例如：

| 配置            | 来源   |
| ------------- | ---- |
| API URL       | 继承全局 |
| API Key       | 继承全局 |
| Model         | 自定义  |
| System Prompt | 自定义  |
| Temperature   | 继承全局 |
| Max Tokens    | 继承全局 |

联系人配置界面示例：

```text
API URL
☑ 使用全局配置

API Key
☑ 使用全局配置

Model
○ 使用全局配置
● 自定义
  gpt-xxx

System Prompt
○ 使用全局配置
● 自定义
  这是我的领导，请保持正式礼貌。

Temperature
☑ 使用全局配置
```

数据库中需要记录每个字段：

```text
inheritGlobal = true / false
```

以及自定义值。

---

# 14. 配置解析规则

每次调用模型之前执行：

```text
ResolvedAIConfig
```

解析顺序：

```text
联系人字段存在自定义
        ↓
使用联系人配置

联系人字段选择继承
        ↓
使用全局配置
```

最终得到：

```text
apiBaseUrl
apiKey
model
systemPrompt
temperature
maxTokens
```

---

# 15. AI 调用面板

用户点击悬浮球并成功读取聊天后，显示 AI 操作面板。

例如：

```text
张三 · 微信

已识别最近 8 条消息

────────────────

本次调用目的（选填）

[                            ]

例如：
帮我委婉拒绝
帮我问一下具体时间
帮我总结他的意思
帮我回复得暧昧一点

────────────────

[生成回复]
```

---

# 16. 本次调用目的

该字段：

```text
Call Purpose
```

必须允许为空。

---

## 16.1 未填写

如果用户不填写：

```text
本次调用目的
```

默认任务：

> 根据联系人 System Prompt 和当前聊天上下文生成合适的推荐回复。

Prompt 逻辑：

```text
System Prompt
+
聊天上下文
+
请生成推荐回复
```

---

## 16.2 已填写

例如用户输入：

```text
帮我委婉拒绝他今晚吃饭
```

最终发送给模型：

```text
System Prompt

+

聊天上下文

+

本次任务：
帮我委婉拒绝他今晚吃饭
```

或者：

```text
分析一下他是不是生气了
```

那么模型就不需要只生成回复。

它应该按照本次目的进行处理。

---

# 17. 模型调用上下文

模型调用至少包含：

```text
最终解析后的 System Prompt
当前 App
当前聊天对象
最近聊天记录
本次调用目的
```

聊天记录格式：

```text
对方：今晚有时间吗？
我：应该有
对方：那一起吃饭？
我：去哪？
对方：我来订地方
```

不要直接将无结构 OCR 文本发送给模型。

---

# 18. AI 输出

默认状态：

```text
推荐回复
```

例如：

```text
可以呀，你订好地方告诉我就行。
```

结果区域提供：

```text
复制
重新生成
```

---

# 19. 复制

点击：

```text
复制
```

将当前 AI 输出完整复制到系统剪贴板。

不自动：

```text
找到输入框
写入输入框
点击发送
```

---

# 20. 重新生成

点击：

```text
重新生成
```

再次使用：

```text
同一个 Conversation
+
同一份聊天上下文
+
同一本次调用目的
+
同一套 AI 配置
```

重新调用模型。

新结果替换当前结果。

允许连续重新生成。

例如：

```text
第一次
→ 不满意

重新生成
→ 第二版

重新生成
→ 第三版
```

---

# 21. 联系人配置入口

AI 面板中应该提供：

```text
⚙ 当前联系人设置
```

点击进入：

```text
微信
张三

AI 配置
```

这样用户无需回主 App 查找联系人。

---

# 22. 联系人管理

主 App 提供：

```text
联系人 / 会话
```

页面。

例如：

```text
微信

张三
李四
客服群


Telegram

Alice
Bob


小红书

用户123
```

点击某个联系人：

```text
查看聊天历史
修改 AI 配置
修改 System Prompt
删除会话
```

---

# 23. 手动修正

由于跨 App 自动识别无法保证 100% 准确，需要允许用户修正。

包括：

```text
联系人名称修正

SELF / OTHER 修正

合并联系人

删除错误消息
```

例如系统错误识别：

```text
联系人：
在线
```

用户修改：

```text
张三
```

以后可以优先使用历史规则。

---

# 24. 悬浮球

保留 FloatyAnswer 悬浮球能力。

默认状态：

```text
✨
```

行为：

```text
单击
→ 开始识别当前屏幕
```

识别过程中：

```text
加载动画
```

成功：

```text
打开 AI 面板
```

失败：

```text
无法识别当前聊天内容

[重试]
[查看识别结果]
```

---

# 25. 调试模式

必须保留一个开发调试模式。

打开后允许查看：

```text
当前 packageName

Accessibility Nodes

OCR Nodes

每个节点文本

bounds

ConversationResolver 结果

MessageClusterer 结果

RoleClassifier 结果
```

例如：

```text
[OTHER]
x=53~492
今晚有时间吗？

[SELF]
x=620~1012
应该有

[OTHER]
x=72~501
一起吃饭？
```

该功能对于微信、Telegram、小红书、抖音等后续适配非常重要。

---

# 26. 第一阶段支持范围

第一阶段优先：

```text
微信
Telegram
系统短信
小红书
抖音
```

但底层设计不能绑定这五个 App。

所有未知 App 默认使用：

```text
GenericChatParser
```

---

# 27. 核心模块结构

建议新增：

```text
chat/
│
├── capture/
│   ├── ScreenCaptureEngine
│   ├── AccessibilityReader
│   └── OcrReader
│
├── model/
│   ├── ScreenNode
│   ├── ChatMessage
│   └── Conversation
│
├── parser/
│   ├── ChatPageDetector
│   ├── ConversationResolver
│   ├── MessageClusterer
│   └── RoleClassifier
│
├── profile/
│   ├── AppProfile
│   └── AppProfileManager
│
├── storage/
│   ├── AppEntity
│   ├── ConversationEntity
│   ├── MessageEntity
│   └── RoomDatabase
│
├── context/
│   ├── MessageDeduplicator
│   └── ConversationContextManager
│
└── ai/
    ├── GlobalAIConfig
    ├── ConversationAIConfig
    ├── ResolvedAIConfig
    ├── AIConfigResolver
    ├── AIClient
    └── PromptBuilder
```

---

# 28. MVP 验收标准

第一版满足以下条件即可认为 MVP 成功：

1. App 启动后显示悬浮球。
2. 不主动、持续读取屏幕。
3. 用户点击悬浮球后才开始读取。
4. 能识别当前 App。
5. 能提取当前屏幕主要聊天文字。
6. 能判断大部分左右聊天气泡属于 SELF 或 OTHER。
7. 能识别当前聊天对象。
8. 不同 App、不同联系人能够建立独立 Conversation。
9. 重复点击读取不会大量产生重复消息。
10. 支持全局 AI API 配置。
11. 支持每个联系人单独设置 AI 配置。
12. 联系人每一个 AI 配置字段都可以独立选择继承全局或自定义。
13. 用户调用 AI 时可以输入“本次调用目的”。
14. 本次调用目的可以为空。
15. 空目的默认根据 System Prompt + 对话上下文生成推荐回复。
16. AI 结果支持复制。
17. AI 结果支持重新生成。
18. Accessibility 读取失败时能够尝试 OCR。
19. 提供节点坐标和角色判断调试界面。

---
