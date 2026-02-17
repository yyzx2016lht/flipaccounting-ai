# FlipAccounting-AI (翻转记账 AI 版)

FlipAccounting-AI 是一款创新的 Android 记账应用，旨在通过直观的“翻转”手势和强大的 AI 辅助，让记账变得无感且高效。它利用手机传感器检测翻转动作，结合 AI 智能识别消费场景，并配合 Shizuku 赋予的高级权限实现自动化操作。

## ✨ 核心功能

*   **🔄 翻转触发 (Flip Detection)**
    *   利用加速度传感器和陀螺仪检测手机翻转动作，作为快速启动记账或触发特定自动化流程的快捷方式。
    *   支持后台运行监测，无需打开应用即可响应。

*   **🤖 AI 智能助手 (AI Assistant)**
    *   内置 `AIService` 和 `AiAssistant`，利用 AI 模型分析消费数据。
    *   智能分类：根据输入的金额或描述，自动推荐最匹配的消费分类。
    *   自然语言记账：支持通过语音或文本输入，让 AI 自动解析金额、类别和备注。

*   **🛡️ Shizuku 高级集成**
    *   通过 `Shizuku` 授权，无需 Root 即可执行系统级操作。
    *   实现更深度的自动化控制，例如在特定应用（如支付软件）界面自动提取信息或执行脚本。
    *   **权限说明**：本应用虽然使用了 Shizuku，但核心功能（翻转检测、AI 识别）**无需 Root** 即可使用。仅部分需要注入输入或读取其他应用界面的高级功能依赖 Shizuku。

*   **📱 悬浮窗交互 (Overlay Interface)**
    *   使用 `OverlayService` 提供全局悬浮窗界面。
    *   在任何界面下都能快速呼出记账面板，不打断当前操作流（例如在微信/支付宝支付完成后直接记账）。
    *   支持自定义悬浮窗位置和透明度。

*   **📊 资产与分类管理**
    *   支持多资产账户管理（现金、银行卡、虚拟账户等）。
    *   自定义收支分类，支持层级管理。
    *   内置丰富的图标库 (`CategoryIconHelper`)，美观且易于识别。

*   **📝 日志与调试**
    *   内置日志查看器 (`LogViewerActivity`)，方便开发者或高级用户排查问题。

## 🛠️ 技术栈

本项目基于现代 Android 技术栈构建：

*   **语言**: Kotlin (主要), Java (部分工具类)
*   **构建工具**: Gradle (Kotlin DSL)
*   **架构**: MVVM (Activity + ViewModel + Repository 模式)
*   **核心库**:
    *   **Android Jetpack**: Core KTX, Appcompat, Lifecycle, ViewModel
    *   **UI**: Material Design 3, XML Layouts
    *   **网络**: Retrofit + OkHttp (用于 AI 接口通信)
    *   **系统交互**: WindowManager (悬浮窗), SensorManager (传感器)
    *   **特权操作**: [Shizuku API](https://shizuku.rikka.app/)
    *   **图片加载**: Glide
    *   **JSON 解析**: Gson

## 📦 依赖环境

*   **Android SDK**: Compile SDK 34 / Target SDK 34
*   **Min SDK**: 24 (Android 7.0+)
*   **JDK**: Java 17

## 🚀 快速开始

1.  **环境配置**
    *   确保安装了 Android Studio Ladybug 或更新版本。
    *   JDK 17 或更高版本。
    *   配置好 `local.properties` 中的 SDK 路径。

2.  **编译运行**
    *   连接 Android 设备或启动模拟器。
    *   运行 `app` 模块。

3.  **初次设置**
    *   **授予权限**：
        *   **悬浮窗权限**：必须授予，否则无法显示记账面板。
        *   **主要服务权限**：确保应用可以在后台运行，建议加入电池优化白名单。
        *   **Shizuku 授权**（可选）：如果您安装了 Shizuku 并希望使用高级自动化功能，请在首次启动时授予权限。

## 🔒 权限说明

为了实现核心功能，应用需要请求以下敏感权限：

*   `SYSTEM_ALERT_WINDOW`: 用于在其他应用上层显示悬浮窗记账界面。
*   `FOREGROUND_SERVICE`: 保证翻转检测服务在后台持续运行，不被系统查杀。
*   `RECORD_AUDIO`: 用于语音记账功能（如果开启）。
*   `QUERY_ALL_PACKAGES`: 用于检测支付应用运行状态（需 Shizuku 配合）。

## 🤝 贡献

欢迎提交 Issue 或 Pull Request！如果您有更好的 AI 模型集成思路或对翻转算法有改进建议，请不吝赐教。

## 🙏 致谢

特别感谢 [AutoAccounting](https://github.com/AutoAccountingOrg/AutoAccounting) 项目：

*   本项目的核心 **翻转检测机制 (Flip Detection)** 参考了该项目的开源实现。
*   许多业务逻辑处理和自动化流程的设计灵感，均源于作者长期使用 AutoAccounting 的体验与思考。

感谢 AutoAccounting 团队为开源社区做出的贡献！

## 🤖 AI 辅助声明

本项目是 **面向 AI 编程 (AI-Oriented Programming)** 的实践产物。核心代码与逻辑实现深度依赖于现代 AI 编程助手：

*   **GitHub Copilot**
*   **Google Gemini**

特别感谢 **GitHub** 和 **Google** 对学生开发者的慷慨支持，让创意能够更轻松地转化为现实。

---
*Generated with ❤️ by GitHub Copilot*
