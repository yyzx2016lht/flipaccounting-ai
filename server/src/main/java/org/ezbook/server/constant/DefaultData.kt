/*
 * Copyright (C) 2024 ankio(ankio@ankio.net)
 * Licensed under the Apache License, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-3.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.ezbook.server.constant

/**
 * 设置项默认值
 * 按照设置页面顺序组织，与 Setting.kt 一一对应，便于管理和维护
 */
object DefaultData {

    // ===================================================================
    // 记账设置 (settings_recording.xml)
    // ===================================================================

    // -------- 记账应用 --------
    val BOOK_APP = "com.mutangtech.qianji"                          // 默认账本应用包名
    val MANUAL_SYNC = false                                         // 手动同步模式默认关闭
    val DELAYED_SYNC_THRESHOLD: Int = 0                             // 延迟同步阈值默认0（实时同步）

    // -------- 记录方式 --------
    val AUTO_RECORD_BILL: Boolean = false                           // 自动记录账单默认关闭
    val LANDSCAPE_DND: Boolean = true                                // 横屏勿扰模式默认开启

    // -------- 账单识别 --------
    val AUTO_GROUP = false                                          // 自动去重默认关闭
    val AUTO_GROUP_TIME_THRESHOLD = 180                             // 自动去重时间阈值（秒），默认180秒
    val AUTO_TRANSFER_RECOGNITION = false                           // 自动识别转账账单默认关闭
    val AUTO_TRANSFER_TIME_THRESHOLD = 120                          // 转账账单合并时间阈值（秒），默认120秒
    val AI_BILL_RECOGNITION: Boolean = false                        // 使用AI识别账单默认关闭

    // -------- 账单管理 --------
    val SHOW_RULE_NAME = true                                       // 显示规则名称默认开启
    val SETTING_FEE = false                                         // 手续费默认关闭
    val SETTING_TAG: Boolean = false                                // 标签功能默认关闭
    val NOTE_FORMAT: String = "【商户名称】【商品名称】"              // 备注格式默认值

    // -------- 账单标记 --------
    val BILL_FLAG_NOT_COUNT: Boolean = false                        // 不计收支标记默认关闭
    val BILL_FLAG_NOT_BUDGET: Boolean = false                       // 不计预算标记默认关闭

    // -------- 分类管理 --------
    val AUTO_CREATE_CATEGORY = false                                // 自动创建分类默认关闭
    val AI_CATEGORY_RECOGNITION: Boolean = false                    // 使用AI识别分类默认关闭

    // -------- 资产管理 --------
    val SETTING_ASSET_MANAGER = false                               // 资产管理默认关闭
    val SETTING_CURRENCY_MANAGER = false                            // 多币种默认关闭
    val SETTING_BASE_CURRENCY = "CNY"                              // 本位币默认人民币
    val SETTING_SELECTED_CURRENCIES =
        "CNY,USD,EUR,JPY,GBP,CHF,AUD,CAD,NZD,HKD,TWD,MOP,KRW,SGD,THB,MYR,IDR,VND,INR" // 默认常用币种
    val SETTING_REIMBURSEMENT = false                               // 报销功能默认关闭
    val SETTING_DEBT = false                                        // 债务功能默认关闭
    val AUTO_ASSET_MAPPING: Boolean = false                         // 记住资产映射默认关闭
    val AI_ASSET_MAPPING: Boolean = false                            // 使用AI进行资产映射默认关闭

    // -------- 账本配置 --------
    val SETTING_BOOK_MANAGER = false                               // 多账本默认关闭
    val DEFAULT_BOOK_NAME = "默认账本"                               // 默认账本名称

    // ===================================================================
    // 交互设置 (settings_interaction.xml)
    // ===================================================================

    // -------- 提醒设置 --------
    val TOAST_POSITION: String = "bottom"                           // 提醒默认位置：底部
    val SHOW_SUCCESS_POPUP = true                                   // 成功提示弹窗默认开启
    val LOAD_SUCCESS: Boolean = false                               // 加载成功默认关闭
    val SHOW_DUPLICATED_POPUP: Boolean = true                       // 重复提示弹窗默认开启

    // -------- OCR识别 --------
    val OCR_FLIP_TRIGGER: Boolean = true                            // 翻转手机触发默认开启
    val OCR_SHOW_ANIMATION: Boolean = true                          // OCR识别时显示动画默认开启

    // -------- 弹窗风格 --------
    val USE_ROUND_STYLE = true                                      // 圆角风格默认开启
    val IS_EXPENSE_RED = false                                      // 支出是否显示为红色默认关闭
    val IS_INCOME_UP = true                                         // 收入是否显示向上箭头默认开启

    // -------- 记账小面板 --------
    val FLOAT_GRAVITY_POSITION: String = "right"                    // 记账小面板默认位置：右侧
    val FLOAT_TIMEOUT_OFF = 0                                       // 超时时间默认0（不超时）
    val FLOAT_TIMEOUT_ACTION: String = "POP_EDIT_WINDOW"           // 超时操作默认值
    val FLOAT_CLICK: String = "POP_EDIT_WINDOW"                    // 点击事件默认值
    val FLOAT_LONG_CLICK: String = "NO_ACCOUNT"                    // 长按事件默认值

    // -------- 记账面板 --------
    val CONFIRM_DELETE_BILL: Boolean = false                        // 删除账单前二次确认默认关闭

    // ===================================================================
    // AI助理 (settings_ai_assistant.xml)
    // ===================================================================

    // -------- AI配置 --------
    val FEATURE_AI_AVAILABLE: Boolean = false                       // AI功能可用性默认关闭（需要配置API后启用）
    val API_PROVIDER: String = "DeepSeek"                          // API提供商默认值
    val API_KEY: String = ""                                       // API密钥默认值
    val API_URI: String = ""                                       // API地址默认值
    val API_MODEL: String = ""                                     // API模型默认值

    // -------- 提示词管理 --------
    /** AI账单识别提示词 - 从原始数据中提取账单信息 */
    val AI_BILL_RECOGNITION_PROMPT: String = """
# 角色
你是一个严谨的账单数据提取专家。你的任务是从原始文本（如短信、账单详情、语音转文字）中准确提取结构化的交易信息。

# 基准日期与时间
基准日期时间：{{TIME}}
1. 所有相对日期（如“今天”、“昨天”、“刚才”、“上周五”）必须相对于此基准时间转换为绝对日期。
2. 如果文本中没有年份，默认补全为基准时间的年份。

# 提取字段要求
1. money：提取金额。如果是转账，提取转账总金额。
2. type：判断交易类型：
   - "Expend"（支出）：消费、支付、支出、减少。
   - "Income"（收入）：工资、退款、转入、获得、分红。
   - "Transfer"（转账）：本人账户间划转（如 支付宝转到建行）。
3. shopName：提取交易发生的商户名称（如：汉堡王、星巴克）。
4. remark：提取具体的交易内容或备注（如：买午餐、打球、电影票）。如果识别到 shopName，通常 remark 可以与之相同或更具体。
5. timeText：必须输出格式为 "yyyy-MM-dd HH:mm:ss" 的日期字符串。
6. accountNameFrom：提取支出的账户（如：微信、支付宝、某银行）。如果是转账，提取转出端。

# 输出格式
只返回一个干净的 JSON 对象。不要包含任何 Markdown 代码块标签、不要解释、不要废话。
JSON 样例：{"money":20.0, "type":"Expend", "shopName":"汉堡王", "remark":"买午餐", "timeText":"2026-02-18 12:30:00", "accountNameFrom":"微信"}
""".trimIndent()

    /** AI资产映射提示词 - 将账单映射到对应资产账户 */
    val AI_ASSET_MAPPING_PROMPT: String = """
# 角色
你是一个专家级的金融资产账户匹配器。你的任务是分析账单线索（如 "支付宝"、"招商银行卡"），并将其映射到用户定义的 Asset Data 列表中最精确的名称。

# 规则
1. 从 Asset Data 列表中寻找语义最接近的项。
2. 如果存在模糊对应（如 "招行" 对应 "招商银行"），应选择该项。
3. 如果完全找不到对应项，对应的值留空字符串。
4. 输出必须是 JSON 格式。

# 输出结构
{"asset1": "映射到的账户名1", "asset2": "映射到的账户名2(若有)"}
""".trimIndent()

    /** AI分类识别提示词 - 自动分类账单 */
    val AI_CATEGORY_RECOGNITION_PROMPT: String = """
# 任务
基于提供的账单内容（金额、备注、商户），从 Category Data 列表中选出一个最合适的分类。

# 规则
1. 必须完全匹配 Category Data 列表中的某个完整名称。
2. 优先匹配二级分类（如 "餐饮/::/午餐"）。
3. 如果存在模糊对应，选择语义最相近的。
4. 如果无法确定分类，直接输出：其他。
5. 只输出分类全名字符串，不要有额外内容。
""".trimIndent()

    val AI_SUMMARY_PROMPT: String = ""

    // -------- AI功能 --------
    val AI_MONTHLY_SUMMARY: Boolean = false                         // 使用AI进行账单总结（月度）默认关闭
    val RULE_MATCH_INCLUDE_DISABLED: Boolean = false               // 禁用规则参与匹配默认关闭

    // ===================================================================
    // 数据管理 (settings_data_management.xml)
    // ===================================================================

    // -------- 自动备份 --------
    val AUTO_BACKUP = false                                         // 自动备份默认关闭
    val BACKUP_KEEP_COUNT = 10                                      // 默认保留10个备份文件

    // -------- 本地备份 --------
    val LOCAL_BACKUP_PATH = ""                                      // 本地备份路径默认值

    // -------- WebDAV备份 --------
    val USE_WEBDAV = false                                          // 启用WebDAV默认关闭
    val WEBDAV_URL = "https://dav.jianguoyun.com/dav/"              // WebDAV服务器URL默认值（示例：坚果云）
    val WEBDAV_USER = ""                                            // WebDAV用户名默认值
    val WEBDAV_PASSWORD = ""                                        // WebDAV密码默认值

    // ===================================================================
    // 系统设置 (settings_system.xml)
    // ===================================================================

    // -------- 外观设置 --------
    val SYSTEM_LANGUAGE: String = "SYSTEM"                          // 系统语言默认跟随系统
    val UI_FOLLOW_SYSTEM_ACCENT: Boolean = true                     // 跟随系统强调色默认开启
    val UI_THEME_COLOR: String = "MATERIAL_DEFAULT"                 // 主题色标识默认值
    val UI_PURE_BLACK: Boolean = false                              // 纯黑暗色默认关闭

    // -------- 更新设置 --------
    val CHECK_APP_UPDATE = true                                     // 应用更新默认开启
    val CHECK_RULE_UPDATE = true                                    // 规则更新默认开启
    val UPDATE_CHANNEL: String = "Stable"                          // 更新渠道默认稳定版

    // -------- 高级功能 --------
    val DEBUG_MODE = false                                          // 调试模式默认关闭
    val SEND_ERROR_REPORT = true                                    // 错误报告默认开启

    // ===================================================================
    // 其他设置（不在设置页面显示，但需要保留）
    // ===================================================================

    // -------- 自动记账相关（内部使用） --------
    const val IGNORE_ASSET: Boolean = false                         // 忽略资产默认关闭
    const val PROACTIVELY_MODEL: Boolean = true                     // 主动模式默认开启
    const val SHOW_AUTO_BILL_TIP: Boolean = true                   // 自动记账提示默认开启
    val SETTING_REMIND_BOOK: Boolean = false                        // 记账提醒默认关闭
    const val WECHAT_PACKAGE: String = "com.tencent.mm"            // 微信包名

    // 数据过滤关键字 - 白名单（逗号分隔存储）
    val DATA_FILTER = listOf(
        "银行", "信用卡", "借记卡", "公积金",
        "元", "￥", "¥", "人民币",
        "消费", "支付", "支出", "转出", "取出", "取款",
        "收入", "转入", "存入", "存款", "退款",
        "还款", "贷款", "借款", "逾期",
        "转账",
        "账户", "余额",
        "交易", "动账", "账单",
    ).joinToString(",")

    // 数据过滤关键字 - 黑名单（逗号分隔存储），匹配白名单后排除
    const val DATA_FILTER_BLACKLIST = ""

    // 监听应用白名单（逗号分隔存储）
    val APP_FILTER = listOf(
        "cmb.pb", // 招商银行
        "cn.gov.pbc.dcep", // 数字人民币
        "com.sankuai.meituan", // 美团
        "com.unionpay", // 云闪付
        "com.tencent.mm", // 微信
        "com.eg.android.AlipayGphone", // 支付宝
        "com.jingdong.app.mall", // 京东
        "com.taobao.taobao", // 淘宝
        "com.xunmeng.pinduoduo", // 拼多多
        "com.sankuai.waimai", // 美团外卖
        "me.ele", // 饿了么
        "com.icbc", // 工商银行

        // 核心钱包/聚合支付
        "com.huawei.wallet", // 华为钱包
        "com.mipay.wallet", // 小米支付
        "com.oppo.wallet", // OPPO 钱包
        "com.coloros.wallet", // OPPO 钱包（ColorOS）
        "com.vivo.wallet", // vivo 钱包
        "com.google.android.apps.walletnfcrel", // Google Pay
        "com.paypal.android.p2pmobile", // PayPal

        // 出行/本地生活
        "com.sdu.didi.psnger", // 滴滴出行
        "com.wudaokou.hippo", // 盒马

        // 电商/内容平台
        "com.ss.android.ugc.aweme", // 抖音
        "com.smile.gifmaker", // 快手
        "com.achievo.vipshop", // 唯品会
        "com.suning.mobile.ebuy", // 苏宁易购
        "com.xiaomi.youpin", // 小米有品

        // 金融理财/支付工具
        "com.jd.jrapp", // 京东金融
        "com.baidu.wallet", // 度小满金融

        // 运营商缴费
        "com.greenpoint.android.mc10086", // 中国移动
        "com.sinovatech.unicom.ui", // 中国联通
        "com.ct.client", // 中国电信

        // 银行类
        "com.chinamworld.main", // 建设银行
        "com.android.bankabc", // 农业银行
        "com.chinamworld.bocmbci", // 中国银行
        "com.bankcomm.Bankcomm", // 交通银行
        "com.yitong.mbank.psbc", // 邮储银行
        "com.pingan.papd", // 平安银行
        "com.ecitic.bank.mobile", // 中信银行
        "cn.com.cmbc.newmbank", // 民生银行
        "com.cebbank.mobile.cemb", // 光大银行
        "com.cib.cibmb", // 兴业银行
        "cn.com.spdb.mobilebank.per", // 浦发银行（个人）
        "com.spdbccc.app", // 浦发信用卡
        "com.cgbchina.xpt", // 广发银行
        "com.hxb.mobile.client", // 华夏银行
        "com.bankofbeijing.mobilebanking", // 北京银行
        "cn.com.shbank.mper", // 上海银行
        "com.nbbank.mobilebank", // 宁波银行
        "com.webank.wemoney", // 微众银行
        "com.mybank.android.phone", // 网商银行

    ).joinToString(",")

    // -------- 权限设置 --------
    val SMS_FILTER: String = ""                                     // 短信过滤默认值

    // -------- 同步设置 --------
    val SYNC_TYPE: String = "none"                                  // 同步类型默认值
    val LAST_SYNC_TIME: Long = 0L                                   // 最后同步时间默认值
    val LAST_BACKUP_TIME = 0L                                      // 最后备份时间默认值

    // -------- 同步哈希值 --------
    val HASH_ASSET: String = ""                                     // 资产哈希默认值
    val HASH_BILL: String = ""                                     // 账单哈希默认值
    val HASH_BOOK: String = ""                                     // 账本哈希默认值
    val HASH_CATEGORY: String = ""                                 // 分类哈希默认值
    val HASH_BAOXIAO_BILL: String = ""                             // 报销单哈希默认值

    // -------- UI设置（其他） --------
    val USE_SYSTEM_SKIN: Boolean = false                            // 系统皮肤默认关闭
    val CATEGORY_SHOW_PARENT = false                               // 显示父分类默认关闭

    // -------- 系统设置（其他） --------
    val KEY_FRAMEWORK: String = "Xposed"                           // 默认工作模式
    val HIDE_ICON: Boolean = false                                 // 是否隐藏启动图标默认关闭
    val INTRO_INDEX: Int = 0                                       // 引导页索引默认值
    val LOCAL_ID: String = ""                                      // 本地实例ID默认值
    val TOKEN: String = ""                                        // 访问令牌默认值
    val GITHUB_CONNECTIVITY: Boolean = true                        // GitHub连通性探测默认开启

    // -------- 更新设置（其他） --------
    val LAST_UPDATE_CHECK_TIME: Long = 0L                         // 检查更新时间默认值
    val CHECK_UPDATE_TYPE: String = "auto"                         // 更新类型默认值
    val RULE_VERSION: String = "none"                             // 规则版本默认值
    val RULE_UPDATE_TIME: String = "none"                         // 规则更新时间默认值

    // -------- 脚本设置 --------
    val JS_COMMON: String = ""                                     // 通用脚本默认值
    val JS_CATEGORY: String = ""                                   // 分类脚本默认值

    // -------- 其他 --------
    val DONATE_TIME: String = ""                                   // 捐赠时间默认值
}
